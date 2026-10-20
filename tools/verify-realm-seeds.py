"""Compare canonical block names in actual saved native region files across client runs.
No world writes. Registry numeric IDs are intentionally excluded (not persistent identities).
"""
from pathlib import Path
import gzip, hashlib, io, json, struct, zlib

ROOT=Path(__file__).resolve().parents[1]
def nbt(raw):
    f=io.BytesIO(raw)
    def read(fmt):return struct.unpack('>'+fmt,f.read(struct.calcsize('>'+fmt)))[0]
    def string():return f.read(read('H')).decode('utf8')
    def payload(kind):
        if kind in [1,2,3,4,5,6]:return read({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind])
        if kind==7:return f.read(read('i'))
        if kind==8:return string()
        if kind==9:
            element=read('B');count=read('i');return [payload(element) for _ in range(count)]
        if kind==10:
            result={}
            while (child:=read('B'))!=0:
                name=string();result[name]=payload(child)
            return result
        if kind in [11,12]:return [read('i' if kind==11 else 'q') for _ in range(read('i'))]
        raise ValueError(kind)
    kind=read('B');string();return payload(kind)

def chunk(region,x,z):
    index=(x&31)+(z&31)*32
    offset=int.from_bytes(region[index*4:index*4+3],'big')*4096
    if not offset:return None
    length=int.from_bytes(region[offset:offset+4],'big');compression=region[offset+4]&127
    raw=region[offset+5:offset+4+length]
    return nbt(zlib.decompress(raw) if compression==2 else gzip.decompress(raw) if compression==1 else raw)

def fingerprint(path):
    raw=path.read_bytes();chunks={}
    for z in [30,31]:
        for x in [30,31]:
            value=chunk(raw,x,z)
            if value is None:return None
            chunks[x,z]={section['Y']:section.get('block_states') for section in value['sections']}
    digest=hashlib.sha256()
    counts={}
    for z in range(480,512):
        for x in range(480,512):
            for y in range(50,224):
                state=chunks[x>>4,z>>4].get(y>>4)
                if not state:name='minecraft:air'
                else:
                    palette=state['palette'];bits=max(4,(len(palette)-1).bit_length());perlong=64//bits
                    index=(x&15)+(z&15)*16+(y&15)*256
                    number=0 if len(palette)==1 else ((state['data'][index//perlong]&((1<<64)-1))>>((index%perlong)*bits))&((1<<bits)-1)
                    name=palette[number]['Name']
                digest.update(name.encode()+b'\0');counts[name]=counts.get(name,0)+1
    return digest.hexdigest(),counts

paths=sorted((ROOT/'build/client-gametest/saves').glob('*/dimensions/differentenchant/enchanter_realm/region/r.0.0.mca'),key=lambda p:p.stat().st_mtime,reverse=True)
results=[]
for path in paths:
    value=fingerprint(path)
    if value:
        results.append({'world':path.parents[4].name,'canonical_sha256':value[0],'materials':value[1]})
    if len(results)==4:break
assert len(results)==4, 'Need two independent client test runs, each with two same-seed worlds'
assert len({r['canonical_sha256'] for r in results})==1, json.dumps(results,indent=2)
out={'seed':2026102001,'chunks':[[30,30],[31,30],[30,31],[31,31]],'block_count':178176,'separate_worlds':results,'all_identical_across_processes':True}
(ROOT/'docs/validation/realm/saved-seed-replay.json').write_text(json.dumps(out,indent=2)+'\n',encoding='utf8')
print(json.dumps(out,indent=2))
