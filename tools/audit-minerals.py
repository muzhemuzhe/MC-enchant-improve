"""Audit production scope and collect unedited native 0.19.0 test evidence."""
from pathlib import Path
import hashlib, json, re, shutil, subprocess, sys, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/validation/minerals'
BASE = '9f2fb496cec0b8bbe1759019cdbf6a033aacd00f'
VERSION = '0.19.0'
def git(*args, **kwargs):
    return subprocess.check_output(['git', *args], cwd=ROOT, **kwargs)
def sha(data):
    return hashlib.sha256(data).hexdigest()
def dump(name, value):
    (OUT/name).write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
def baseline(name):
    return git('show', BASE+':'+name).decode().replace('\r\n', '\n')
OUT.mkdir(parents=True, exist_ok=True)
assert git('branch', '--show-current').decode().strip() == 'feature/enchanter-dimension'

# Only the entrypoint, two translations and two biome feature lists integrate minerals.
entry = 'src/main/java/dev/differentenchant/DifferentEnchant.java'
text = (ROOT/entry).read_text(encoding='utf8')
old = baseline(entry)
added = '        dev.differentenchant.mineral.MineralContent.initialize();\n'
assert text.replace(added, '') == old
allowed = {entry}
for lang in ('en_us', 'zh_cn'):
    name = f'src/main/resources/assets/differentenchant/lang/{lang}.json'
    a, b = json.loads(baseline(name)), json.loads((ROOT/name).read_text(encoding='utf8'))
    assert all(b[k] == v for k, v in a.items())
    assert len(b.keys()-a.keys()) == 6
    allowed.add(name)
for biome in ('mint_fields', 'azure_woodland'):
    name = f'src/main/resources/data/differentenchant/worldgen/biome/{biome}.json'
    a, b = json.loads(baseline(name)), json.loads((ROOT/name).read_text(encoding='utf8'))
    for stage in (6, 7):
        expected = ['luminous_mainland','luminous_islands','silver_deep','silver_islands'] if stage == 6 else ['rift_crystals']
        assert b['features'][stage] == a['features'][stage]+['differentenchant:'+n for n in expected]
        b['features'][stage] = a['features'][stage]
    assert a == b
    allowed.add(name)
preserved = []
for line in git('ls-tree', '-r', BASE, 'src/main', 'docs/screenshots', 'docs/validation').decode().splitlines():
    meta, name = line.split('\t', 1)
    if name not in allowed:
        preserved.append((name, meta.split()[2]))
objects = git('hash-object', '--stdin-paths', input=('\n'.join(n for n,_ in preserved)+'\n').encode()).decode().splitlines()
assert len(objects) == len(preserved)
for (name,want),got in zip(preserved, objects):
    assert want == got, 'unrelated edit: '+name
dump('preserved-files.json', [n for n,_ in preserved])

jar = ROOT/f'build/libs/differentenchant-{VERSION}.jar'
with zipfile.ZipFile(jar) as z:
    matched = []
    for p in sorted((ROOT/'src/main/resources').rglob('*')):
        if not p.is_file(): continue
        name = p.relative_to(ROOT/'src/main/resources').as_posix()
        expected = p.read_bytes()
        if name == 'fabric.mod.json': expected = expected.replace(b'${version}', VERSION.encode())
        assert z.read(name) == expected, name
        matched.append(name)
    packed = {n for n in z.namelist() if n.startswith(('assets/','data/')) and not n.endswith('/')}
    assert packed == {n for n in matched if n.startswith(('assets/','data/'))}
    assert not any('GameTest' in n or 'differentenchant_tests' in n for n in z.namelist())
    assert b'probe' not in z.read('data/differentenchant/tags/enchantment/mines_harmonic_silver.json')

def hashes():
    return {p.relative_to(ROOT/'src/main/resources').as_posix():sha(p.read_bytes()) for p in sorted((ROOT/'src/main/resources').rglob('*')) if p.is_file()}
before = hashes()
subprocess.run([sys.executable, str(ROOT/'tools/generate-realm.py')], cwd=ROOT, check=True)
assert before == hashes(), 'full generator must retain identical mineral definitions'
dump('generator-replay.json', {'identical_resources':len(before), 'sha256':before})

full = (ROOT/'.tools/minerals-client-5.log').read_text(encoding='utf8', errors='replace')
photo = (ROOT/'.tools/minerals-photo-1.log').read_text(encoding='utf8', errors='replace')
assert 'BUILD SUCCESSFUL' in full and 'MINERAL PHOTO_ONLY' not in full
assert 'BUILD SUCCESSFUL' in photo and 'MINERAL PHOTO_ONLY' in photo
names = ['01-natural-luminous','02-natural-silver','03-natural-underside-cluster','04-natural-wall-cluster']
for stem in ('05-protection-book','06-wrong-netherite','07-fire-sword','08-lost-book'):
    names.extend((stem+'-digging', stem+'-result'))
names.extend(('09-inventory-en','10-inventory-zh'))
targets = [(70,148,333),(78,147,311),(74,147,329),(66,51,588)]
images = []
(OUT/'screenshots').mkdir(exist_ok=True)
for i,name in enumerate(names):
    raw = max((ROOT/'build/client-gametest/screenshots').glob('*_minerals-'+name+'.png'), key=lambda p:p.stat().st_mtime)
    shutil.copy2(raw, OUT/'screenshots'/f'{name}.png')
    images.append({'file':f'screenshots/{name}.png', 'raw_source':raw.relative_to(ROOT).as_posix(),
        'sha256':sha(raw.read_bytes()), 'unaltered':True, 'seed':2026102001,
        'dimension':'differentenchant:enchanter_realm', 'native_night_vision':True,
        'source_log':'minerals-photo-1.log' if i<4 else 'minerals-client-5.log',
        'target':targets[i] if i<4 else None,
        'fixture':'inventory display items' if i>=12 else 'native attack in survival with test flight' if i>=4 else 'natural generation, creative flight'})
assert len(images) == 14
dump('screenshots.json', {'source':'Native OpenGL client, no scene block placement or image edits', 'images':images})
for p in (ROOT/'.tools').glob('minerals-*.log'):
    shutil.copy2(p, OUT/p.name)
for kind in ('client','photo','legacy'):
    shutil.copy2(ROOT/f'build/client-gametest/minerals-{kind}-report.txt', OUT/f'{kind}-report.txt')
shutil.copy2(ROOT/'build/gametest-results.xml', OUT/'server-results.xml')
server = ET.parse(OUT/'server-results.xml')
assert not server.findall('.//failure') and not server.findall('.//error')
assert len(server.findall('.//testcase')) == 57
units = []
(OUT/'unit-results').mkdir(exist_ok=True)
for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
    suite = ET.parse(p).getroot()
    assert suite.attrib['failures'] == '0' and suite.attrib['errors'] == '0'
    units.append({'suite':suite.attrib['name'],'tests':int(suite.attrib['tests'])})
    shutil.copy2(p, OUT/'unit-results'/p.name)
assert sum(x['tests'] for x in units) == 36

# Historical world is read-only evidence; only a byte copy was opened in Minecraft.
legacy = json.loads((ROOT/'.tools/minerals-legacy-source.json').read_text(encoding='utf8'))
source = Path(legacy['source'])
for name,want in legacy['all_files'].items():
    assert sha((source/name).read_bytes()) == want, 'original save touched: '+name
assert set(legacy['all_files']) == {p.relative_to(source).as_posix() for p in source.rglob('*') if p.is_file()}
legacy['original_files_unchanged'] = True
legacy['opened_copy_report'] = (OUT/'legacy-report.txt').read_text(encoding='utf8')
dump('legacy-source-audit.json', legacy)
result = {'baseline':BASE, 'branch':'feature/enchanter-dimension', 'main':git('rev-parse','main').decode().strip(),
    'jar':jar.relative_to(ROOT).as_posix(), 'sha256':sha(jar.read_bytes()), 'resources':len(matched),
    'no_test_content_or_stale_resources':True, 'unchanged_baseline_files':len(preserved),
    'junit_tests':36, 'server_tests':57, 'raw_screenshots':len(images), 'legacy_original_files':len(legacy['all_files']),
    'legacy_original_unchanged':True, 'full_client_log':'minerals-client-5.log', 'unit_suites':units}
dump('artifact-audit.json', result)
print(json.dumps(result, ensure_ascii=False, indent=2))
