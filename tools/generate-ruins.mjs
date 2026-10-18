// Original authored architecture -> native compressed structure templates. No runtime block-by-block building.
import fs from 'node:fs';
import zlib from 'node:zlib';
import {verifyLocks} from './verify-ruins.mjs';
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
  table(x,y,z) {this.block(x,y,z,'crafting_table');this.block(x+1,y,z,'birch_slab',{type:'top',waterlogged:'false'});this.block(x,y+1,z,'candle',{candles:'2',lit:'true',waterlogged:'false'});}
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
function smallSurfaceCandidate() {
  // Same cottage language, +19% template footprint: 6x6 clear main room and 4-block headroom.
  const b=new Building('small_surface',[13,10,11],(x,z)=>x>=2&&x<=9&&z>=2&&z<=9);
  for(let x=2;x<=9;x++) for(let z=2;z<=9;z++) {
    b.block(x,0,z,(x===2||x===9||z===2||z===9)?'stone_bricks':'birch_planks');
    for(let y=1;y<=4;y++) b.block(x,y,z,(x===2||x===9||z===2||z===9)?'pale_oak_planks':'air');
    b.block(x,5,z,'birch_planks');
  }
  // Consistent stripped timber posts and ring beams, with quartz only at their feet.
  for(const x of [2,9]) for(const z of [2,9]) {
    b.block(x,1,z,'quartz_pillar',{axis:'y'});
    b.fill(x,2,z,x,5,z,'stripped_pale_oak_log',{axis:'y'});
  }
  for(const z of [2,9]) b.fill(3,5,z,8,5,z,'stripped_birch_log',{axis:'x'});
  for(const x of [2,9]) b.fill(x,5,3,x,5,8,'stripped_birch_log',{axis:'z'});
  // Paired front windows, a single readable entrance, and measured side windows.
  for(const x of [3,8]) {
    b.block(x,1,2,'smooth_quartz');b.fill(x,2,2,x,3,2,'glass_pane');b.block(x,4,2,'smooth_quartz');
  }
  b.fill(5,1,2,5,2,2,'air');
  for(const x of [4,6]) b.fill(x,1,2,x,3,2,'stripped_birch_log',{axis:'y'});
  b.block(5,3,2,'chiseled_quartz_block');
  for(const half of ['lower','upper']) b.block(5,half==='lower'?1:2,2,'birch_door',{facing:'north',half,hinge:'left',open:'false',powered:'false'});
  for(const z of [4,7]) {
    b.block(2,1,z,'smooth_quartz');b.fill(2,2,z,2,3,z,'glass_pane');b.block(2,4,z,'pale_oak_planks');
  }
  b.block(9,1,3,'smooth_quartz');b.fill(9,2,3,9,3,3,'glass_pane');
  // Shallow porch: two slim posts, one small lean-to, and a half-step approach.
  for(let x=4;x<=7;x++) {
    b.block(x,0,0,'birch_stairs',{facing:'south',half:'bottom',shape:'straight',waterlogged:'false'});
    b.block(x,0,1,'birch_planks');
    b.block(x,4,1,'birch_stairs',{facing:'south',half:'bottom',shape:'straight',waterlogged:'false'});
  }
  for(const x of [4,7]) {b.block(x,1,1,'quartz_pillar',{axis:'y'});b.fill(x,2,1,x,3,1,'stripped_birch_log',{axis:'y'});}
  b.lamp(5,3,1);
  // Each next roof course rises one block: no two-wide shelves or filled pyramidal roof mass.
  for(let x=1;x<=10;x++) for(let z=1;z<=10;z++) {
    const y=5+Math.min(x-1,10-x);
    if(x===5||x===6) b.block(x,y,z,'pale_oak_slab',{type:'bottom',waterlogged:'false'});
    else b.block(x,y,z,(z===1||z===10)?'pale_oak_stairs':'birch_stairs',{facing:x<5?'east':'west',half:'bottom',shape:'straight',waterlogged:'false'});
    if((z===2||z===9)&&x>=3&&x<=8) for(let fill=6;fill<y;fill++) b.block(x,fill,z,'pale_oak_planks');
  }
  for(const z of [2,9]) {b.fill(5,6,z,6,7,z,'glass_pane');b.fill(5,8,z,6,8,z,'chiseled_quartz_block');}
  // A low oriel/reading bay changes the side silhouette without making the main roof wider.
  b.fill(9,0,3,11,0,8,'birch_planks');
  b.fill(9,1,4,9,4,7,'air');
  for(let x=10;x<=11;x++) for(let z=3;z<=8;z++) {
    for(let y=1;y<=3;y++) b.block(x,y,z,(x===11||z===3||z===8)?'pale_oak_planks':'air');
    b.block(x,4,z,x===10?'birch_planks':'birch_stairs',x===10?{}:{facing:'west',half:'bottom',shape:'straight',waterlogged:'false'});
    b.block(12,4,z,'pale_oak_slab',{type:'bottom',waterlogged:'false'});
  }
  for(const z of [3,8]) b.fill(11,1,z,11,3,z,'stripped_pale_oak_log',{axis:'y'});
  for(let z=4;z<=7;z++) {b.block(11,1,z,'smooth_quartz');b.fill(11,2,z,11,3,z,'glass_pane');}
  // Furnish the perimeter, leaving the door-to-centre aisle and both chest lids clear.
  b.fill(3,1,8,5,2,8,'bookshelf');b.shelf(4,1,8,'north','small');
  b.table(7,1,8);b.block(8,1,7,'birch_stairs',{facing:'south',half:'bottom',shape:'straight',waterlogged:'false'});
  b.chest(3,1,3,'small','east');b.chest(3,1,6,'small','east');
  b.block(10,1,7,'lectern',{facing:'west',has_book:'false',powered:'false'});
  b.block(10,1,5,'amethyst_block');b.block(10,2,5,'amethyst_cluster',{facing:'up',waterlogged:'false'});
  b.fill(5,0,4,5,0,6,'smooth_quartz');b.block(4,1,7,'purple_carpet');
  b.lamp(6,4,5);b.block(3,4,8,'cobweb');b.save();
}
function smallBuried() {
  const b=new Building('small_buried',[12,6,10],(x,z)=>x>=1&&x<=10&&z>=1&&z<=8);
  for(let x=1;x<=10;x++) for(let z=1;z<=8;z++) {
    b.block(x,0,z,'stone_bricks');
    if(x>1&&x<10&&z>1&&z<8) b.block(x,0,z,'birch_planks');
    for(let y=1;y<=3;y++) b.block(x,y,z,x===1||x===10||z===1||z===8?'pale_oak_planks':'air');
    b.block(x,4,z,'birch_planks');
  }
  for(const x of [1,10]) for(const z of [1,5,8]) b.fill(x,1,z,x,4,z,'birch_log',{axis:'y'});
    // A sealed timber archive with a low, ribbed ceiling, not the surface roof copied underground.
    for(const z of [2,5,7]) b.fill(2,3,z,9,3,z,'stripped_pale_oak_log',{axis:'x'});
    b.fill(1,5,1,10,5,8,'mossy_stone_bricks');
    b.fill(5,1,1,6,3,1,'cracked_stone_bricks');
  b.fill(4,0,3,7,0,6,'polished_andesite');b.fill(5,0,4,6,0,5,'calcite');
  b.fill(2,1,7,3,2,7,'bookshelf');b.shelf(4,1,7,'north','small');
  b.table(7,1,6);b.block(8,1,5,'birch_stairs',{facing:'south',half:'bottom',shape:'straight',waterlogged:'false'});
  b.chest(2,1,3,'small','east');b.chest(9,1,6,'small','west');
  b.block(8,1,3,'lectern',{facing:'west',has_book:'false',powered:'false'});b.block(9,1,3,'amethyst_block');
  b.block(2,1,5,'lectern',{facing:'east',has_book:'false',powered:'false'});b.block(6,1,6,'white_carpet');
  b.block(2,3,2,'cobweb');b.lamp(5,3,4);b.save();
}
smallSurfaceCandidate();smallBuried();
function workshop() {
  // Three real volumes: forward central research hall, recessed two-storey wings and axial vestibule.
  const b=new Building('workshop',[23,20,18],()=>true);
  const stair=facing=>({facing,half:'bottom',shape:'straight',waterlogged:'false'});
  function body(x1,x2,z1,z2,top,cap) {
    for(let x=x1;x<=x2;x++) for(let z=z1;z<=z2;z++) for(let y=0;y<=top;y++) {
      const edge=x===x1||x===x2||z===z1||z===z2;
      const floor=y%5===0||cap&&y===top;
      b.block(x,y,z,y===0?(edge?'stone_bricks':'birch_planks'):floor?(edge?'smooth_quartz':'birch_planks'):edge?'pale_oak_planks':'air');
    }
  }
  body(6,16,4,15,13,false);body(1,6,7,15,10,true);body(16,21,7,15,10,true);
  // Posts, waist lines and openings follow each room, rather than printing one facade grid.
  for(const [x,z,top] of [[6,4,13],[16,4,13],[6,15,13],[16,15,13],[1,7,9],[1,15,9],[21,7,9],[21,15,9]]) {
    b.fill(x,1,z,x,top,z,'stripped_pale_oak_log',{axis:'y'});
    for(const y of [1,6,11]) if(y<=top) b.block(x,y,z,'quartz_pillar',{axis:'y'});
  }
  function window(x1,x2,z,base,height=2) {
    b.fill(x1,base+1,z,x2,base+1,z,'smooth_quartz');
    b.fill(x1,base+2,z,x2,base+1+height,z,'glass_pane');
    b.fill(x1,base+2+height,z,x2,base+2+height,z,'stripped_birch_log',{axis:'x'});
  }
  // Wing fronts have high, narrow laboratory glazing below and wider archive glazing above.
  for(const [x1,x2] of [[3,4],[18,19]]) {
    window(x1,x2,7,5);window(x1,x2,15,0);window(x1,x2,15,5);
  }
  b.fill(3,3,7,4,3,7,'glass_pane');window(18,19,7,0);
  for(const x of [1,21]) for(const z of [9,13]) {
    b.block(x,1,z,'smooth_quartz');
    b.fill(x,x===1?3:2,z,x,3,z,'glass_pane');b.fill(x,7,z,x,8,z,'glass_pane');
    b.block(x,6,z,'smooth_quartz');
  }
  for(const base of [0,5]) {
    window(8,9,15,base);window(13,14,15,base);
    for(const x of [6,16]) b.fill(x,base+2,5,x,base+3,5,'glass_pane');
  }
  // Central research window and a projecting paired frame give the elevation a public-building axis.
  window(9,13,4,5,3);
  for(const x of [8,14]) {
    b.block(x,1,3,'quartz_pillar',{axis:'y'});b.fill(x,2,3,x,4,3,'stripped_pale_oak_log',{axis:'y'});
    b.fill(x,6,3,x,9,3,'quartz_pillar',{axis:'y'});
  }
  for(const y of [5,10]) b.fill(8,y,3,14,y,3,'smooth_quartz');
  b.block(11,10,3,'amethyst_block');
  // The main pitched roof contains the third-floor study; it is not a lid above another box.
  for(let x=5;x<=17;x++) for(let z=3;z<=16;z++) {
    const y=13+Math.min(x-5,17-x);
    if(x===11) b.block(x,y,z,'pale_oak_slab',{type:'bottom',waterlogged:'false'});
    else b.block(x,y,z,(z===3||z===16)?'quartz_stairs':'birch_stairs',stair(x<11?'east':'west'));
    if(x>=6&&x<=16&&z>=4&&z<=15) for(let fill=14;fill<y;fill++)
      b.block(x,fill,z,z===4||z===15?'pale_oak_planks':'air');
  }
  for(const z of [4,15]) {
    b.fill(10,12,z,12,15,z,'glass_pane');b.block(11,16,z,'glass_pane');
    b.fill(9,12,z,9,14,z,'quartz_pillar',{axis:'y'});b.fill(13,12,z,13,14,z,'quartz_pillar',{axis:'y'});
    b.block(10,16,z,'quartz_stairs',stair('east'));b.block(12,16,z,'quartz_stairs',stair('west'));b.block(11,17,z,'chiseled_quartz_block');
  }
  for(const x of [6,16]) {b.fill(x,12,8,x,13,10,'glass_pane');b.block(x,11,9,'smooth_quartz');}
  // Each two-storey wing has its own small gable, ending against the central walls, not through them.
  for(const [lo,hi] of [[0,7],[15,22]]) for(let x=lo;x<=hi;x++) for(let z=6;z<=16;z++) {
    if(x>=6&&x<=16) continue;
    const y=10+Math.min(x-lo,hi-x),middle=(lo+hi)/2;
    if(x===Math.floor(middle)||x===Math.ceil(middle)) b.block(x,y,z,'pale_oak_slab',{type:'bottom',waterlogged:'false'});
    else b.block(x,y,z,z===6||z===16?'quartz_stairs':'birch_stairs',stair(x<middle?'east':'west'));
    if(x>=lo+1&&x<=hi-1&&z>=7&&z<=15) for(let fill=11;fill<y;fill++)
      b.block(x,fill,z,z===7||z===15?'pale_oak_planks':'air');
  }
  b.fill(3,11,7,4,11,7,'glass_pane');b.fill(18,11,7,19,11,7,'glass_pane');
  // A compact axial entry compresses the approach before the larger public research hall.
  body(9,13,1,3,4,true);b.fill(10,1,3,12,3,4,'air');
  for(let x=9;x<=13;x++) b.block(x,0,0,'birch_stairs',stair('south'));
  for(const x of [9,13]) b.fill(x,1,1,x,3,1,'quartz_pillar',{axis:'y'});
  for(const x of [10,12]) {b.block(x,1,1,'smooth_quartz');b.fill(x,2,1,x,3,1,'glass_pane');}
  for(const half of ['lower','upper']) b.block(11,half==='lower'?1:2,1,'birch_door',{facing:'north',half,hinge:'left',open:'false',powered:'false'});
  b.block(11,3,1,'chiseled_quartz_block');
  for(let x=8;x<=14;x++) for(let z=0;z<=3;z++) {
    const y=4+Math.min(x-8,14-x);
    b.block(x,y,z,x===11?'pale_oak_slab':z===0?'quartz_stairs':'birch_stairs',x===11?{type:'bottom',waterlogged:'false'}:stair(x<11?'east':'west'));
    if(z===1&&x>=9&&x<=13) for(let fill=5;fill<y;fill++) b.block(x,fill,z,'pale_oak_planks');
  }
  b.lamp(11,3,2);
  // Real connecting arches make the side wings part of the circulation on both public floors.
  for(const base of [0,5]) {
    b.fill(6,base+1,9,6,base+3,11,'air');b.fill(16,base+1,9,16,base+3,12,'air');
    b.fill(6,base+4,9,6,base+4,11,'stripped_birch_log',{axis:'z'});
    b.fill(16,base+4,9,16,base+4,12,'stripped_birch_log',{axis:'z'});
  }
  // Ground: public reception splits into the occupied west laboratory and east tools/reading room.
  b.fill(10,0,5,12,0,12,'smooth_quartz');b.table(8,1,6);
  b.block(14,1,6,'lectern',{facing:'north',has_book:'false',powered:'false'});
  b.chest(8,1,13,'common','east');b.chest(5,1,8,'common','west');b.chest(20,1,8,'common','west');
  b.table(2,1,8);b.block(2,2,8,'candle',{candles:'2',lit:'false',waterlogged:'false'});
  b.fill(2,1,14,4,2,14,'bookshelf');b.block(2,1,13,'bookshelf');b.block(2,4,14,'cobweb');
  b.block(3,1,11,'spawner');b.marker(3,2,11,'zombie_spawner');b.spawners.push([3,1,11]);
  b.table(17,1,8);b.block(19,1,8,'barrel',{facing:'up',open:'false'});b.fill(20,1,10,20,2,13,'bookshelf');
  b.block(18,1,10,'white_carpet');b.lamp(11,4,6);b.lamp(18,4,10);
  b.block(1,0,14,'cracked_stone_bricks');b.block(21,0,8,'mossy_stone_bricks');
  // Second: central machine chamber, west archive and east materials laboratory, each with its own use.
  b.fill(10,6,7,12,6,9,'polished_deepslate');
  for(const [x,z] of [[11,7],[10,8],[12,8],[11,9]]) b.block(x,7,z,'obsidian');
  for(const [x,z] of [[12,7],[10,9]]) b.block(x,7,z,'amethyst_block');
  for(const [x,z] of [[10,7],[11,8],[12,9]]) b.block(x,7,z,'air');
  manifests.incomplete_machine={controller:[11,7,8],missing:[[10,7,7],[11,7,8],[12,7,9]]};
  for(const x of [9,13]) b.fill(x,5,6,x,5,10,'smooth_quartz');
  for(const z of [6,10]) b.fill(9,5,z,13,5,z,'smooth_quartz');
  for(const x of [8,14]) b.block(x,6,5,'lectern',{facing:'south',has_book:'false',powered:'false'});
  b.fill(2,6,8,2,7,14,'bookshelf');b.shelf(2,6,9,'east');b.shelf(2,6,12,'east');
  b.table(3,6,8);b.block(4,6,10,'lectern',{facing:'east',has_book:'false',powered:'false'});b.chest(5,6,13,'research');
  b.fill(20,6,9,20,7,13,'bookshelf');b.shelf(20,6,10,'west');b.shelf(20,6,12,'west');
  b.table(17,6,8);b.block(18,6,9,'brewing_stand',{has_bottle_0:'false',has_bottle_1:'false',has_bottle_2:'false'});
  b.block(19,6,8,'barrel',{facing:'up',open:'false'});b.chest(19,6,14,'research');
  b.lamp(8,9,12);b.lamp(14,9,8);b.lamp(4,9,12);b.lamp(18,9,12);
  // Third: the quiet central loft. No third floor over the wings; the roof shapes the private study.
  b.fill(7,11,8,7,12,10,'bookshelf');b.fill(10,11,14,12,12,14,'bookshelf');
  b.shelf(10,11,14,'north','high');b.shelf(12,11,14,'north','high');
  b.table(8,11,6);b.block(9,11,7,'birch_stairs',stair('north'));
  b.block(9,11,13,'white_bed',{facing:'west',part:'foot',occupied:'false'});b.block(8,11,13,'white_bed',{facing:'west',part:'head',occupied:'false'});
  b.chest(10,11,13,'research','north');b.chest(12,11,6,'study','south');b.chest(15,11,6,'research','south');
  b.block(8,11,11,'lectern',{facing:'east',has_book:'false',powered:'false'});b.fill(9,11,9,11,11,10,'blue_carpet');
  for(const z of [6,14]) b.fill(7,14,z,15,14,z,'stripped_birch_log',{axis:'x'});
  b.lamp(10,13,6);b.lamp(11,13,14);b.block(7,14,14,'cobweb');
  // Continuous half-step spiral; upper exits open onto real rooms, and the main gallery stays clear.
  b.fill(13,1,11,15,14,13,'air');b.fill(14,1,12,14,15,12,'stripped_pale_oak_log',{axis:'y'});
  const ring=[[1,0],[1,1],[0,1],[-1,1],[-1,0],[-1,-1],[0,-1],[1,-1]];
  for(let step=0;step<=20;step++) {
    const [dx,dz]=ring[step%8],feet=1+step/2,y=Math.ceil(feet)-1;
    b.block(14+dx,y,12+dz,'birch_slab',{type:Number.isInteger(feet)?'top':'bottom',waterlogged:'false'});
    b.stairs.push({x:14+dx,z:12+dz,feet});
  }
  for(const base of [5,10]) for(const x of [12,16]) b.block(x,base+1,11,'birch_fence');
  b.save();
  Object.assign(manifests.workshop,{floors:[0,5,10],entry_door:[11,1,1],entry_approach:[[11,1,0]],
    ground_route:[[11,1,5],[11,1,10],[17,1,10],[17,1,12],[15,1,12]],
    reward_route:[[12,11,12],[11,11,12],[11,11,8]],reward_chest:[12,11,6],
    spiral_landings:[
      {step:10,path:[[14,14],[11,14],[8,14],[8,11],[4,11],[8,11],[8,10],[17,10],[19,10],[17,10],[8,10],[8,14],[14,14],[14,13]]},
      {step:20,path:[[12,12],[11,12],[11,8],[11,12],[12,12],[13,12]]}
    ],review_views:[
      {id:'01-front',camera:[11,9,-21],look:[11,8,8]},
      {id:'02-diagonal',camera:[34,12,-13],look:[11,8,9]},
      {id:'03-side',camera:[39,9,11],look:[11,8,10]},
      {id:'04-ground-spawner',camera:[5,1,9],look:[3,1,11]},
      {id:'05-second-machine',camera:[14,7,10],look:[11,7,8]},
      {id:'06-third-study',camera:[13,11,7],look:[9,12,12]}
    ]});
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
verifyLocks();
console.log('Generated 3 native templates for 2 buildings, 7 loot tables and 3 natural placements; approved geometry locks verified.');
