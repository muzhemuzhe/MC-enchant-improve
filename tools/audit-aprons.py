"""Collect raw 0.18 evidence; never overwrite an earlier validation directory."""
from pathlib import Path
import hashlib, json, re, shutil, subprocess, sys, zipfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/validation/long-aprons'
BASE='bfbede382b87c2e997d478828eb0dc606f542e85'
VERSION='0.18.0'
LOG=sys.argv[1] if len(sys.argv)>1 else 'apron-client-7'
def git(*args,**kw):return subprocess.check_output(['git',*args],cwd=ROOT,**kw)
def sha(data):return hashlib.sha256(data).hexdigest()
def dump(name,value):(OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
OUT.mkdir(parents=True,exist_ok=True)
assert git('branch','--show-current').decode().strip()=='feature/enchanter-dimension'
log=(ROOT/'.tools'/f'{LOG}.log').read_text(encoding='utf8',errors='replace')
assert 'BUILD SUCCESSFUL' in log and 'Client gametests failed' not in log and 'EDGE PROBE diagnostic' not in log and 'APRON CAMERA_ONLY' not in log
jar=ROOT/f'build/libs/differentenchant-{VERSION}.jar'
with zipfile.ZipFile(jar) as z:
    matched=[]
    for p in sorted((ROOT/'src/main/resources').rglob('*')):
        if not p.is_file():continue
        name=p.relative_to(ROOT/'src/main/resources').as_posix();expected=p.read_bytes()
        if name=='fabric.mod.json':expected=expected.replace(b'${version}',VERSION.encode())
        assert z.read(name)==expected,name
        matched.append(name)
    packed={n for n in z.namelist() if n.startswith(('assets/','data/')) and not n.endswith('/')}
    expected={n for n in matched if n.startswith(('assets/','data/'))}
    assert packed==expected,('stale resources',packed-expected)
    assert not any('differentenchant_tests' in n or 'RealmClientGameTest' in n for n in z.namelist())
def allowed(name):
    return (name.startswith('src/main/resources/data/differentenchant/worldgen/density_function/')
        or name=='src/main/resources/data/differentenchant/worldgen/noise_settings/enchanter_realm.json'
        or name=='src/main/resources/data/differentenchant/worldgen/noise/uplift.json'
        or name=='src/main/java/dev/differentenchant/realm/FaultEdgeDensity.java')
preserved=[]
for entry in git('ls-tree','-r',BASE,'src/main','docs/screenshots','docs/validation').decode().splitlines():
    meta,name=entry.split('\t',1)
    if not allowed(name):preserved.append((name,meta.split()[2]))
objects=git('hash-object','--stdin-paths',input=('\n'.join(n for n,_ in preserved)+'\n').encode()).decode().splitlines()
for (name,want),got in zip(preserved,objects):assert want==got,'unrelated edit: '+name
adapter='src/main/java/dev/differentenchant/realm/FaultEdgeDensity.java'
assert (ROOT/adapter).read_text(encoding='utf8').replace('Codec.intRange(32,512)','Codec.intRange(32,256)')==git('show',BASE+':'+adapter).decode().replace('\r\n','\n')
dump('preserved-files.json',[n for n,_ in preserved])
cameras={name:(coords,target,LOG+'.log') for name,coords,target in re.findall(r'APRON CAMERA ([\w-]+) camera=([\d.,-]+) target=([\d, -]+)',log)}
retake=(ROOT/'.tools/apron-camera-1.log').read_text(encoding='utf8',errors='replace')
assert 'BUILD SUCCESSFUL' in retake and 'APRON CAMERA_ONLY' in retake and 'EDGE PROBE diagnostic' not in retake
for name,coords,target in re.findall(r'APRON CAMERA ([\w-]+) camera=([\d.,-]+) target=([\d, -]+)',retake):
    assert name.startswith('seed-20261022-')
    cameras[name]=(coords,target,'apron-camera-1.log')
images=[];(OUT/'screenshots').mkdir(exist_ok=True)
for name,(coords,target,source_log) in cameras.items():
    raw=max((ROOT/'build/client-gametest/screenshots').glob('*_apron-'+name+'.png'),key=lambda p:p.stat().st_mtime)
    shutil.copy2(raw,OUT/'screenshots'/f'{name}.png')
    images.append({'file':f'screenshots/{name}.png','seed':int(name.split('-')[1]),'raw_source':raw.relative_to(ROOT).as_posix(),
        'dimension':'differentenchant:enchanter_realm','camera':[float(v) for v in coords.split(',')],
        'look_at':[int(v) for v in target.split(',')],'source_log':source_log,'coordinate_kind':'player teleport position; native eye offset applies','sha256':sha(raw.read_bytes()),'unaltered':True,'mode':'creative flight'})
assert len(images)==12,len(images)
dump('screenshots.json',{'source':'Actual OpenGL Client GameTest; no scene block writes','images':images})
for p in (ROOT/'.tools').glob('apron-*.log'):shutil.copy2(p,OUT/p.name)
shutil.copy2(ROOT/'build/client-gametest/apron-client-report.txt',OUT/'client-report.txt')
shutil.copy2(ROOT/'build/client-gametest/apron-camera-report.txt',OUT/'camera-report.txt')
shutil.copy2(ROOT/'build/gametest-results.xml',OUT/'server-results.xml')
server=ET.parse(OUT/'server-results.xml');assert not server.findall('.//failure') and not server.findall('.//error')
units=[];(OUT/'unit-results').mkdir(exist_ok=True)
for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
    suite=ET.parse(p).getroot();assert suite.attrib['failures']=='0' and suite.attrib['errors']=='0'
    units.append({'suite':suite.attrib['name'],'tests':int(suite.attrib['tests'])});shutil.copy2(p,OUT/'unit-results'/p.name)
def resource_hashes():return {p.relative_to(ROOT/'src/main/resources').as_posix():sha(p.read_bytes()) for p in sorted((ROOT/'src/main/resources').rglob('*')) if p.is_file()}
before=resource_hashes();subprocess.run([sys.executable,str(ROOT/'tools/generate-realm.py')],cwd=ROOT,check=True);assert before==resource_hashes()
dump('generator-replay.json',{'identical_resources':len(before),'sha256':before})
result={'baseline':BASE,'branch':'feature/enchanter-dimension','main':git('rev-parse','main').decode().strip(),
    'jar':jar.relative_to(ROOT).as_posix(),'sha256':sha(jar.read_bytes()),'resources':len(matched),'no_test_content_or_stale_resources':True,
    'unchanged_baseline_files':len(preserved),'junit_tests':sum(x['tests'] for x in units),'server_tests':len(server.findall('.//testcase')),
    'raw_screenshots':len(images),'full_client_log':LOG+'.log','unit_suites':units}
dump('artifact-audit.json',result);print(json.dumps(result,ensure_ascii=False,indent=2))
