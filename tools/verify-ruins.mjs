// Developer check only; Gradle/player installations do not require Node.js.
import fs from 'node:fs';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';
const root=new URL('../src/main/resources/data/differentenchant/structure/ruins/',import.meta.url);
const hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');

export function inspectTemplate(path) {
  const compressed=fs.readFileSync(path),data=zlib.gunzipSync(compressed);let offset=0;
  const byte=()=>data.readUInt8(offset++);
  const int=()=>{const n=data.readInt32BE(offset);offset+=4;return n;};
  const string=()=>{const n=data.readUInt16BE(offset);offset+=2;const s=data.toString('utf8',offset,offset+n);offset+=n;return s;};
  function payload(type) {
    if(type===3)return int();
    if(type===8)return string();
    if(type===9) {const element=byte(),length=int();if(length<0||length>1000000)throw Error('Invalid NBT list');return Array.from({length},()=>payload(element));}
    if(type===10) {const value={};for(let t=byte();t!==0;t=byte()){const key=string();value[key]=payload(t);}return value;}
    throw Error('Unsupported authored NBT tag '+type);
  }
  if(byte()!==10)throw Error('Expected structure compound');string();const nbt=payload(10);
  if(offset!==data.length)throw Error('Trailing NBT data');
  const blocks=nbt.blocks.map(b=>{
    const p=nbt.palette[b.state];if(!p)throw Error('Invalid block palette index');
    return {pos:b.pos,name:p.Name,properties:Object.fromEntries(Object.entries(p.Properties??{}).sort(([a],[b])=>a<b?-1:a>b?1:0))};
  }).sort((a,b)=>a.pos[1]-b.pos[1]||a.pos[0]-b.pos[0]||a.pos[2]-b.pos[2]);
  // Block-entity loot tables may change independently of approved geometry and block states.
  return {nbt,sha256:hash(compressed),geometry_sha256:hash(JSON.stringify({size:nbt.size,blocks})),blocks:blocks.length};
}

export function verifyLocks() {
  const locks=JSON.parse(fs.readFileSync(new URL('ruins-locks.json',import.meta.url),'utf8'));
  const dataRoot=new URL('../../',root);
  const blueprint=JSON.parse(fs.readFileSync(new URL('ruins/blueprints.json',dataRoot),'utf8'));
  const results=[];
  for(const [name,lock] of Object.entries(locks)) {
    const value=inspectTemplate(new URL(name+'.nbt',root));
    if(JSON.stringify(value.nbt.size)!==JSON.stringify(lock.size)||value.geometry_sha256!==lock.geometry_sha256)
      throw Error('Approved ruin geometry changed: '+name+'. Review the intended change before updating tools/ruins-locks.json.');
    const plan=blueprint[name],byPos=new Map(value.nbt.blocks.map(b=>[b.pos.join(','),b]));
    for(const chest of plan.chests) {
      if(byPos.get(chest.pos.join(','))?.nbt?.LootTable!=='differentenchant:chests/'+chest.tier)
        throw Error('Chest loot wiring differs from blueprint: '+name+' '+chest.pos);
      if(!fs.existsSync(new URL('loot_table/chests/'+chest.tier+'.json',dataRoot)))throw Error('Missing chest loot table');
    }
    for(const shelf of plan.chiseled_shelves) {
      const [x,y,z]=shelf.pos;
      if(byPos.get([x,y+1,z].join(','))?.nbt?.metadata!=='books_'+shelf.tier)throw Error('Missing bookshelf content marker');
      if(!fs.existsSync(new URL('loot_table/chests/books_'+shelf.tier+'.json',dataRoot)))throw Error('Missing bookshelf loot table');
    }
    if(name==='workshop'&&plan.chests.filter(c=>c.tier==='study').length!==1)throw Error('Expected one main reward chest');
    results.push({name,size:value.nbt.size,blocks:value.blocks,chests:plan.chests.length,shelves:plan.chiseled_shelves.length,sha256:value.sha256,geometry_sha256:value.geometry_sha256});
  }
  const read=path=>JSON.parse(fs.readFileSync(new URL(path,dataRoot),'utf8'));
  const structure=read('worldgen/structure/abandoned_workshop.json');
  const placement=read('worldgen/structure_set/abandoned_workshop.json').placement;
  const smallPlacement=read('worldgen/structure_set/small_archive.json').placement;
  if(structure.type!=='differentenchant:ruin'||structure.template!=='differentenchant:ruins/workshop'||structure.buried!==false||structure.biomes!=='#differentenchant:ruin_land')throw Error('Medium surface structure wiring changed');
  if(placement.type!=='minecraft:random_spread'||placement.spacing<=smallPlacement.spacing||placement.separation<=0||placement.separation>=placement.spacing)throw Error('Medium structure must remain sparser than small');
  const biomes=read('tags/worldgen/biome/ruin_land.json').values;
  if(!biomes.includes('minecraft:plains')||!biomes.includes('#minecraft:is_forest'))throw Error('Missing natural forest/plains biome coverage');
  return results;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href)console.log(JSON.stringify(verifyLocks(),null,2));
