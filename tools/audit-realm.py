"""Package/preservation audit for the Realm milestone; writes review evidence only."""
from pathlib import Path
import hashlib, json, re, shutil, subprocess, zipfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/validation/realm'
BASE='6c8fc99'
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
def digest(data):return hashlib.sha256(data).hexdigest()
def dump(name,value):(OUT/name).write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
OUT.mkdir(parents=True,exist_ok=True)
jar=ROOT/'build/libs/differentenchant-0.14.0.jar'
z=zipfile.ZipFile(jar)
matched=[]
for p in sorted((ROOT/'src/main/resources').rglob('*')):
    if not p.is_file():continue
    name=p.relative_to(ROOT/'src/main/resources').as_posix()
    expected=p.read_bytes()
    if name=='fabric.mod.json':expected=expected.replace(b'${version}',b'0.14.0')
    assert z.read(name)==expected, name
    matched.append(name)
assert not any('differentenchant_tests' in n or 'RealmClientGameTest' in n for n in z.namelist())
baseline=git('ls-tree','-r','--name-only',BASE,'src/main','docs/screenshots','docs/validation').decode().splitlines()
allow={
    'src/main/java/dev/differentenchant/DifferentEnchant.java',
    'src/main/resources/fabric.mod.json','src/main/resources/differentenchant.mixins.json',
    'src/main/resources/assets/differentenchant/lang/en_us.json',
    'src/main/resources/assets/differentenchant/lang/zh_cn.json',
    'src/main/resources/data/minecraft/tags/block/mineable/axe.json',
    'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json'
}
unchanged=[]
for name in baseline:
    if name in allow:continue
    data=(ROOT/name).read_bytes();original=git('show',BASE+':'+name)
    if b'\x00' not in data[:8000] and Path(name).suffix.lower() not in {'.png','.jpg','.nbt','.jar'}:
        data=data.replace(b'\r\n',b'\n');original=original.replace(b'\r\n',b'\n')
    assert data==original, 'Unexpected baseline modification: '+name
    unchanged.append(name)
screens=[]
log=(ROOT/'.tools/realm-client-final.log').read_text(encoding='utf8',errors='replace')
cameras={m[0]:{'camera':[float(v) for v in m[1].split(',')],'look_at':[int(v) for v in m[2].split(',')]} for m in re.findall(r'REALM CAMERA ([\w-]+) camera=([\d.,-]+) target=([\d,-]+)',log)}
cameras['07-sky-midnight']=cameras['06-sky-day']
for p in sorted((ROOT/'build/client-gametest/screenshots').glob('*_realm-*.png')):
    name=p.name.split('_realm-',1)[1]
    destination=OUT/'screenshots'/name;destination.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,destination)
    screens.append({'file':'screenshots/'+name,'sha256':digest(p.read_bytes()),'raw_source':str(p.relative_to(ROOT)),'seed':2026102001,'dimension':'differentenchant:enchanter_realm','unaltered':True,**cameras[name.removesuffix('.png')]})
assert len(screens)==8
dump('screenshots.json',{'generator':'RealmClientGameTest.java (camera positions computed from survey)','images':screens})
for p in (ROOT/'.tools').glob('realm-*.log'):shutil.copy2(p,OUT/p.name)
shutil.copy2(ROOT/'build/client-gametest/realm-client-report.txt',OUT/'client-report.txt')
shutil.copy2(ROOT/'build/gametest-results.xml',OUT/'server-results.xml')
server=ET.parse(OUT/'server-results.xml');cases=server.findall('.//testcase')
assert not server.findall('.//failure') and not server.findall('.//error')
units=[]
unitout=OUT/'unit-results';unitout.mkdir(exist_ok=True)
for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
    suite=ET.parse(p).getroot();assert suite.attrib['failures']=='0' and suite.attrib['errors']=='0'
    units.append({'suite':suite.attrib['name'],'tests':int(suite.attrib['tests'])});shutil.copy2(p,unitout/p.name)
dump('artifact-audit.json',{'baseline':BASE,'branch':git('branch','--show-current').decode().strip(),'jar':str(jar.relative_to(ROOT)),'sha256':digest(jar.read_bytes()),'matched_resources':len(matched),'no_test_content_in_jar':True,'unchanged_baseline_files':len(unchanged),'allowed_baseline_integrations':sorted(allow),'junit_tests':sum(s['tests'] for s in units),'server_tests':len(cases),'unit_suites':units})
dump('preserved-files.json',unchanged)
print(json.dumps({'jar_sha256':digest(jar.read_bytes()),'resources':len(matched),'preserved':len(unchanged),'junit':sum(s['tests'] for s in units),'server':len(cases)},indent=2))
