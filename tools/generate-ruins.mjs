// Original authored architecture -> native compressed structure templates. No runtime block-by-block building.
import fs from 'node:fs';
import zlib from 'node:zlib';
const root=new URL('../src/main/resources/data/differentenchant/',import.meta.url);
const write=(path,data)=>{const url=new URL(path,root);fs.mkdirSync(new URL('.',url),{recursive:true});fs.writeFileSync(url,data);};
const json=(path,data)=>write(path,JSON.stringify(data,null,2)+'\n');
const tag=(type,value)=>({type,value}), I=n=>tag(3,n), S=s=>tag(8,s), L=(type,items)=>tag(9,{type,items}), C=value=>tag(10,value);
const int=n=>{const b=Buffer.alloc(4);b.writeInt32BE(n);return b;};
const text=s=>{const b=Buffer.from(s),len=Buffer.alloc(2);len.writeUInt16BE(b.length);return Buffer.concat([len,b]);};
function payload(t) {
  if(t.type===3) return int(t.value);
  if(t.type===8) return text(t.value);
  if(t.type===9) return Buffer.concat([Buffer.from([t.value.type]),int(t.value.items.length),...t.value.items.map(x=>payload(tag(t.value.type,x)))]);
  if(t.type===10) return Buffer.concat([...Object.entries(t.value).map(([k,v])=>Buffer.concat([Buffer.from([v.type]),text(k),payload(v)])),Buffer.from([0])]);
  throw new Error('Unsupported NBT tag');
}
const manifests={};
class Building {
  constructor(name,size,inside) {this.name=name;this.size=size;this.inside=inside;this.blocks=new Map();this.chests=[];this.shelves=[];this.stairs=[];this.spawners=[];}
  block(x,y,z,name,properties={},nbt) {
    if(x<0||y<0||z<0||x>=this.size[0]||y>=this.size[1]||z>=this.size[2]) throw new Error(`Outside ${this.name}: ${x},${y},${z}`);
    this.blocks.set(`${x},${y},${z}`,{pos:[x,y,z],name:name.includes(':')?name:'minecraft:'+name,properties,nbt});
  }
  fill(x1,y1,z1,x2,y2,z2,name,props={}) {for(let y=y1;y<=y2;y++) for(let x=x1;x<=x2;x++) for(let z=z1;z<=z2;z++) this.block(x,y,z,name,props);}
  marker(x,y,z,metadata) {this.block(x,y,z,'structure_block',{mode:'data'},{id:S('minecraft:structure_block'),mode:S('DATA'),metadata:S(metadata)});}
  chest(x,y,z,tier,facing='north') {this.block(x,y,z,'chest',{facing,type:'single',waterlogged:'false'},{id:S('minecraft:chest'),LootTable:S('differentenchant:chests/'+tier)});this.chests.push({pos:[x,y,z],tier});}
  shelf(x,y,z,facing,tier='medium') {this.block(x,y,z,'chiseled_bookshelf',{facing});this.marker(x,y+1,z,'books_'+tier);this.shelves.push({pos:[x,y,z],tier});}
  table(x,y,z) {this.block(x,y,z,'crafting_table');this.block(x+1,y,z,'spruce_slab',{type:'top',waterlogged:'false'});this.block(x,y+1,z,'candle',{candles:'2',lit:'true',waterlogged:'false'});}
  lamp(x,y,z) {this.block(x,y,z,'lantern',{hanging:'true',waterlogged:'false'});}
  save() {
    const palettes=[],indices=new Map(),blocks=[];
    for(const b of this.blocks.values()) {
      const key=JSON.stringify([b.name,b.properties]);let state=indices.get(key);
      if(state===undefined) {state=palettes.length;indices.set(key,state);const p={Name:S(b.name)};if(Object.keys(b.properties).length) p.Properties=C(Object.fromEntries(Object.entries(b.properties).map(([k,v])=>[k,S(v)])));palettes.push(p);}
      const data={pos:L(3,b.pos),state:I(state)};if(b.nbt) data.nbt=C(b.nbt);blocks.push(data);
    }
    const nbt=C({DataVersion:I(4790),size:L(3,this.size),palette:L(10,palettes),blocks:L(10,blocks),entities:L(10,[])});
    write('structure/ruins/'+this.name+'.nbt',zlib.gzipSync(Buffer.concat([Buffer.from([10]),text(''),payload(nbt)])));
    manifests[this.name]={size:this.size,blocks:blocks.length,chests:this.chests,chiseled_shelves:this.shelves,spawners:this.spawners,spiral_walk:this.stairs};
  }
}
const aged=(x,y,z)=>{const n=(x*31+y*17+z*13)%19;return n<2?'mossy_stone_bricks':n<5?'cracked_stone_bricks':'stone_bricks';};
function small(buried) {
  const b=new Building(buried?'small_buried':'small_surface',[12,6,10],(x,z)=>x>=1&&x<=10&&z>=1&&z<=8);
  for(let x=1;x<=10;x++) for(let z=1;z<=8;z++) {
    b.block(x,0,z,'stone_bricks');
    for(let y=1;y<=3;y++) b.block(x,y,z,x===1||x===10||z===1||z===8?aged(x,y,z):'air');
    b.block(x,4,z,'polished_deepslate');
    if(x>=3&&x<=8) b.block(x,5,z,'spruce_planks');
  }
  for(const x of [1,10]) for(const z of [2,7]) b.fill(x,1,z,x,4,z,'chiseled_stone_bricks');
  // Buttresses and a shallow porch distinguish the compact archive from a rectangular room.
  for(const x of [0,11]) for(const z of [2,7]) {
    b.fill(x,0,z,x,2,z,'stone_bricks');
    b.block(x,3,z,'stone_brick_stairs',{facing:x===0?'east':'west',half:'bottom',shape:'straight',waterlogged:'false'});
  }
  for(const x of [1,10]) for(const z of [4,5]) b.block(x,2,z,buried?'cracked_stone_bricks':'iron_bars');
  for(const x of [5,6]) {b.fill(x,1,1,x,3,1,buried?'cracked_stone_bricks':'air');b.block(x,0,0,'stone_bricks');}
  for(const x of [4,7]) b.fill(x,0,0,x,3,0,'chiseled_stone_bricks');
  for(const x of [5,6]) b.block(x,3,0,'stone_brick_slab',{type:'top'});
  b.fill(4,4,0,7,4,0,'deepslate_brick_slab',{type:'bottom'});
  for(let z=1;z<=8;z++) for(const x of [3,8]) b.block(x,5,z,'spruce_stairs',{facing:x===3?'east':'west',half:'bottom',shape:'straight',waterlogged:'false'});
  b.fill(4,0,3,7,0,6,'deepslate_tiles');b.fill(5,0,4,6,0,5,'polished_andesite');
  b.fill(2,1,7,3,2,7,'bookshelf');b.shelf(4,1,7,'north','small');
  b.table(7,1,6);b.block(8,1,5,'spruce_stairs',{facing:'south',half:'bottom',shape:'straight',waterlogged:'false'});
  b.chest(2,1,3,'small','east');b.chest(9,1,6,'small','west');
  b.block(8,1,3,'obsidian');b.block(9,1,3,'amethyst_block');b.block(8,2,3,'stone_brick_slab',{type:'bottom'});
  b.block(2,3,2,'cobweb');b.lamp(5,3,5);b.save();
}
small(false);small(true);
function workshop() {
  const footprint=(x,z)=>(x>=2&&x<=17&&z>=2&&z<=12)||(x>=0&&x<=5&&z>=4&&z<=10)||(x>=14&&x<=19&&z>=0&&z<=5)||(x>=7&&x<=12&&z>=0&&z<=2);
  const b=new Building('workshop',[20,15,15],footprint);
  for(let x=0;x<20;x++) for(let z=0;z<15;z++) if(footprint(x,z)) {
    const wall=[[1,0],[-1,0],[0,1],[0,-1]].some(([dx,dz])=>!footprint(x+dx,z+dz));
    for(let y=0;y<=12;y++) {
      let material=y%4===0?'stone_bricks':wall?aged(x,y,z):'air';
      if(y>0&&y%4===0) material='polished_deepslate';
      b.block(x,y,z,material);
    }
    if(wall) for(const y of [6,10]) if((x+z)%4===0) b.block(x,y,z,'iron_bars');
    if(wall&&z<=2) b.block(x,2,z,(x+z)%4===0?'iron_bars':aged(x,2,z));
    const rise=x>=14&&z<=5?Math.min(x-14,19-x,z,5-z):Math.min(2,Math.max(0,Math.min(z-2,12-z)));
    const roofY=12+Math.min(2,rise);
    if(roofY>12) b.fill(x,13,z,x,roofY,z,'spruce_planks');
    b.block(x,roofY,z,'spruce_stairs',{facing:z<7?'south':'north',half:'bottom',shape:'straight',waterlogged:'false'});
  }
  // Clear front arch, with a framed entrance and broad stone porch.
  b.fill(8,1,0,11,3,2,'air');
  for(const x of [7,12]) b.fill(x,1,0,x,3,0,'chiseled_stone_bricks');
  b.fill(8,3,0,11,3,0,'stone_brick_slab',{type:'top'});
  for(const [x,z] of [[2,2],[2,12],[17,12],[0,4],[0,10],[19,0],[19,5]]) b.fill(x,1,z,x,11,z,'stone_bricks');
  for(const x of [3,5]) {b.fill(x,0,1,x,11,1,'stone_bricks');b.block(x,12,1,'spruce_slab',{type:'bottom'});}
  for(const y of [3,7,11]) b.fill(3,y,1,6,y,1,'stone_brick_stairs',{facing:'south',half:'top',shape:'straight',waterlogged:'false'});
  // Inlaid floors and freestanding columns give each storey a readable plan.
  for(const floor of [0,4,8]) {
    b.fill(8,floor,3,10,floor,11,'deepslate_tiles');
    for(const [x,z] of [[6,5],[11,5],[6,10],[11,10]]) b.fill(x,floor+1,z,x,floor+3,z,'polished_deepslate');
    for(const z of [4,10]) b.block(9,floor,z,'chiseled_stone_bricks');
  }
  // Ground floor: partitioned storage/work bay; cage isolated from the lit entrance.
  b.fill(7,1,7,7,3,11,'stone_bricks');b.fill(7,1,8,7,2,9,'air');
  b.chest(3,1,5,'common','east');b.chest(16,1,2,'common','south');b.chest(11,1,11,'common');
  b.table(4,1,3);b.block(16,1,5,'cauldron');b.block(17,1,6,'barrel',{facing:'up',open:'false'});
  b.block(4,2,3,'candle',{candles:'2',lit:'false',waterlogged:'false'});
  b.block(3,1,11,'bookshelf');b.block(4,1,11,'bookshelf');
  b.block(4,1,9,'spawner');b.marker(4,2,9,'zombie_spawner');b.spawners.push([4,1,9]);
  b.lamp(10,3,3);b.block(2,3,10,'cobweb');
  // Second storey: exact machine pattern with centre and two amethyst corners missing.
  b.fill(7,5,6,9,5,8,'polished_deepslate');
  for(const [x,z] of [[8,6],[7,7],[9,7],[8,8]]) b.block(x,6,z,'obsidian');
  for(const [x,z] of [[9,6],[7,8]]) b.block(x,6,z,'amethyst_block');
  for(const [x,z] of [[7,6],[8,7],[9,8]]) b.block(x,6,z,'air');
  manifests.incomplete_machine={controller:[8,6,7],missing:[[7,6,6],[8,6,7],[9,6,8]]};
  b.fill(1,5,5,1,6,9,'bookshelf');b.shelf(1,5,6,'east');b.shelf(1,5,8,'east');
  b.shelf(3,5,3,'south');b.shelf(5,5,3,'south');b.fill(2,5,11,5,6,11,'bookshelf');
  b.table(11,5,3);b.chest(3,5,10,'research','east');b.chest(16,5,4,'research','west');
  b.block(12,5,10,'barrel',{facing:'up',open:'false'});b.block(13,5,10,'amethyst_block');
  b.lamp(9,7,5);b.lamp(4,7,8);
  // Top storey: library and overseer's study, higher-value containers.
  b.fill(1,9,5,1,10,9,'bookshelf');b.fill(3,9,11,5,10,11,'bookshelf');
  b.shelf(3,9,4,'south','high');b.shelf(5,9,4,'south','high');
  b.table(15,9,1);b.chest(16,9,3,'study');b.chest(3,9,9,'study','east');b.chest(11,9,11,'study');
  b.block(8,9,3,'white_bed',{facing:'west',part:'foot',occupied:'false'});b.block(7,9,3,'white_bed',{facing:'west',part:'head',occupied:'false'});
  b.fill(8,9,5,10,9,5,'blue_carpet');b.block(2,11,10,'cobweb');b.lamp(9,11,7);b.lamp(16,11,2);
  // Two complete helical turns: eight half-slabs per four-block storey, three-block headroom.
  b.fill(13,1,8,15,11,10,'air');b.fill(14,1,9,14,11,9,'polished_deepslate');
  const ring=[[1,0],[1,1],[0,1],[-1,1],[-1,0],[-1,-1],[0,-1],[1,-1]];
  for(let step=0;step<=16;step++) {
    const [dx,dz]=ring[step%8],top=1+step/2,y=Math.ceil(top)-1;
    b.block(14+dx,y,9+dz,'stone_brick_slab',{type:Number.isInteger(top)?'top':'bottom',waterlogged:'false'});
    b.stairs.push({x:14+dx,z:9+dz,feet:top});
  }
  // Leave the entire east landing corridor open, connecting each storey to its rooms.
  for(const floor of [4,8]) for(const [x,z] of [[12,8],[12,9],[12,10],[13,7],[14,7],[15,7],[13,11],[14,11],[15,11]])
    b.block(x,floor+1,z,'spruce_fence');
  b.save();
}
workshop();
json('ruins/blueprints.json',manifests);
// Native loot functions query the registry/tag; no fixed vanilla enchantment whitelist.
const count=(min,max)=>({function:'minecraft:set_count',count:{type:'minecraft:uniform',min,max}});
const item=(name,weight=1,functions=[])=>({type:'minecraft:item',name,weight,...(functions.length?{functions}:{})});
const enchanted=(min,max,weight=1)=>item('minecraft:book',weight,[{function:'minecraft:enchant_with_levels',levels:{type:'minecraft:uniform',min,max},options:'#minecraft:in_enchanting_table'}]);
const pool=(rolls,entries)=>({rolls,entries});
for(const [tier,min,max,rolls,chance] of [['small',8,18,3,20],['common',10,20,4,12],['research',16,26,4,8],['study',22,30,5,4]]) {
  const entries=[item('minecraft:book',12,[count(1,3)]),item('minecraft:lapis_lazuli',12,[count(2,6)]),item('minecraft:paper',6,[count(2,5)]),item('minecraft:experience_bottle',3,[count(1,2)]),item('minecraft:iron_ingot',4,[count(1,3)]),enchanted(min,max,tier==='study'?9:3)];
  const pools=[pool({type:'minecraft:uniform',min:2,max:rolls},entries),pool(1,[{type:'minecraft:empty',weight:chance-1},item('differentenchant:runic_stone')]),pool(1,[{type:'minecraft:empty',weight:31},item('minecraft:lapis_block')])];
  if(tier==='study') pools.push(pool(1,[{type:'minecraft:empty',weight:49},item('differentenchant:resonance_core')]));
  json('loot_table/chests/'+tier+'.json',{type:'minecraft:chest',pools});
}
for(const [tier,min,max] of [['small',8,18],['medium',15,25],['high',22,30]])
  json('loot_table/chests/books_'+tier+'.json',{type:'minecraft:chest',pools:[pool({type:'minecraft:uniform',min:2,max:4},[enchanted(min,max)])]});
json('tags/worldgen/biome/ruin_land.json',{values:['minecraft:plains','minecraft:sunflower_plains','#minecraft:is_forest','#minecraft:is_taiga','minecraft:meadow']});
json('tags/worldgen/biome/ruin_buried.json',{values:['#differentenchant:ruin_land','minecraft:lush_caves','minecraft:dripstone_caves']});
for(const [id,template,buried,spacing,separation,salt] of [['small_archive','small_surface',false,20,8,1017341],['buried_archive','small_buried',true,24,10,1017342],['abandoned_workshop','workshop',false,36,16,1017343]]) {
  json('worldgen/structure/'+id+'.json',{type:'differentenchant:ruin',template:'differentenchant:ruins/'+template,buried,biomes:'#differentenchant:'+(buried?'ruin_buried':'ruin_land'),spawn_overrides:{},step:buried?'underground_structures':'surface_structures',terrain_adaptation:'none'});
  json('worldgen/structure_set/'+id+'.json',{structures:[{structure:'differentenchant:'+id,weight:1}],placement:{type:'minecraft:random_spread',salt,spacing,separation,exclusion_zone:{other_set:'minecraft:villages',chunk_count:10}}});
}
console.log('Generated 3 native templates for 2 buildings, 7 loot tables and 3 natural placements.');
