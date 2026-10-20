"""Collect this iteration's raw evidence without altering historical Realm evidence."""
from pathlib import Path
import hashlib, json, re, shutil, subprocess, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/validation/fault-slopes'
BASE = '9782bb8e6a1269f2aca4e22a0903d1399eff3e2e'
VERSION = '0.17.0'
def git(*args, **kwargs):
    return subprocess.check_output(['git', *args], cwd=ROOT, **kwargs)
def sha(data): return hashlib.sha256(data).hexdigest()
def dump(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
OUT.mkdir(parents=True, exist_ok=True)
assert git('branch', '--show-current').decode().strip() == 'feature/enchanter-dimension'
jar = ROOT / f'build/libs/differentenchant-{VERSION}.jar'
with zipfile.ZipFile(jar) as z:
    matched = []
    for p in sorted((ROOT / 'src/main/resources').rglob('*')):
        if not p.is_file(): continue
        name = p.relative_to(ROOT / 'src/main/resources').as_posix()
        expected = p.read_bytes()
        if name == 'fabric.mod.json': expected = expected.replace(b'${version}', VERSION.encode())
        assert z.read(name) == expected, name
        matched.append(name)
    assert not any('differentenchant_tests' in n or 'RealmClientGameTest' in n for n in z.namelist())
    assert 'data/differentenchant/worldgen/noise/islands.json' not in z.namelist(), 'stale old generator resource'
    for name in ['continent_mask','continent_thickness','continent_bottom']:
        assert f'data/differentenchant/worldgen/density_function/{name}.json' not in z.namelist(), 'stale global suspension field'

def realm_integration(name):
    return (name.startswith('src/main/resources/data/differentenchant/worldgen/density_function/')
        or name == 'src/main/resources/data/differentenchant/worldgen/noise_settings/enchanter_realm.json'
        or name == 'src/main/java/dev/differentenchant/realm/RealmContent.java')

# Git object hashing applies repository text filters and preserves binary checks.
entries = git('ls-tree','-r',BASE,'src/main','docs/screenshots','docs/validation').decode().splitlines()
preserved, integrated = [], []
for entry in entries:
    meta, name = entry.split('\t',1)
    if realm_integration(name): integrated.append(name)
    else: preserved.append((name, meta.split()[2]))
objects = git('hash-object','--stdin-paths',input=('\n'.join(n for n,_ in preserved)+'\n').encode()).decode().splitlines()
assert len(objects) == len(preserved)
for (name, expected), actual in zip(preserved, objects): assert actual == expected, 'unrelated edit: '+name
registration='        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE,id("fault_edge_distance"),FaultEdgeDensity.CODEC);\n'
content='src/main/java/dev/differentenchant/realm/RealmContent.java'
assert (ROOT/content).read_text(encoding='utf8').replace(registration,'')==git('show',BASE+':'+content).decode().replace('\r\n','\n')
dump('preserved-files.json', [name for name,_ in preserved])

log = (ROOT / '.tools/edge-client-4.log').read_text(encoding='utf8', errors='replace')
assert 'BUILD SUCCESSFUL' in log and 'Client gametests failed' not in log and 'EDGE PROBE diagnostic' not in log
cameras = {m[0]: {'camera':[float(v) for v in m[1].split(',')], 'look_at':[int(v) for v in m[2].split(',')]}
           for m in re.findall(r'EDGE CAMERA ([\w-]+) camera=([\d.,-]+) target=([\d, -]+)', log)}
assert len(cameras) == 15
images = []
dest = OUT / 'screenshots'; dest.mkdir(exist_ok=True)
for name, camera in cameras.items():
    p = max((ROOT/'build/client-gametest/screenshots').glob('*_edge-'+name+'.png'), key=lambda p:p.stat().st_mtime)
    shutil.copy2(p,dest/(name+'.png'))
    images.append({'file':'screenshots/'+name+'.png','seed':int(name.split('-')[1]),'dimension':'differentenchant:enchanter_realm',
                   'raw_source':p.relative_to(ROOT).as_posix(),'sha256':sha(p.read_bytes()),'unaltered':True,**camera})
dump('screenshots.json', {'source':'RealmClientGameTest; actual native generation, camera-only photography','images':images})
for name in ['edge-client-1','edge-client-2','edge-client-3','edge-probe-1','edge-probe-2','edge-probe-3','edge-probe-4','edge-probe-5','edge-client-4','edge-build-initial','edge-build-distance','edge-build','edge-server','edge-restore']:
    shutil.copy2(ROOT/'.tools'/f'{name}.log',OUT/f'{name}.log')
shutil.copy2(ROOT/'build/client-gametest/edge-client-report.txt',OUT/'client-report.txt')
shutil.copy2(ROOT/'build/gametest-results.xml',OUT/'server-results.xml')
server = ET.parse(OUT/'server-results.xml')
assert not server.findall('.//failure') and not server.findall('.//error')
units = []
unitout = OUT/'unit-results'; unitout.mkdir(exist_ok=True)
for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
    suite = ET.parse(p).getroot(); assert suite.attrib['failures']=='0' and suite.attrib['errors']=='0'
    units.append({'suite':suite.attrib['name'],'tests':int(suite.attrib['tests'])}); shutil.copy2(p,unitout/p.name)
result = {'baseline':BASE,'branch':'feature/enchanter-dimension','main':git('rev-parse','main').decode().strip(),
    'jar':jar.relative_to(ROOT).as_posix(),'sha256':sha(jar.read_bytes()),'matched_resources':len(matched),
    'no_test_content_in_jar':True,'unchanged_baseline_files':len(preserved),'realm_integration_files':integrated,
    'junit_tests':sum(s['tests'] for s in units),'server_tests':len(server.findall('.//testcase')),'unit_suites':units,'raw_screenshots':len(images)}
for photo in images: photo['camera_mode']='creative flight, native generated terrain'
dump('screenshots.json', {'source':'RealmClientGameTest; actual native generation, camera-only photography','images':images})
dump('artifact-audit.json',result)
print(json.dumps({k:result[k] for k in ['jar','sha256','matched_resources','unchanged_baseline_files','junit_tests','server_tests','raw_screenshots']},indent=2))
