// Original provisional geometry. Clip ONE machine into 18 static block models.
// Textures are references to Minecraft assets; no textures are redistributed.
import fs from 'node:fs';
const base = new URL('../src/main/resources/', import.meta.url);
const write = (path, value) => {
  const file = new URL(path, base);
  fs.mkdirSync(new URL('.', file), {recursive:true});
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n');
};
const boxes = [];
const box = (from,to,texture) => boxes.push({from,to,texture});
box([0,0,0],[48,3,48],'stone');
box([2,3,2],[46,13,46],'dark');
box([0,13,0],[48,16,48],'stone');
// Continuous purple inlay across the plinth, with gold edge bands.
for (const z of [1.5,45.5]) {
  box([2,8,z],[46,11,z+1],'crystal');
  box([2,7,z],[46,8,z+1],'gold');
}
for (const x of [1.5,45.5]) {
  box([x,8,2],[x+1,11,46],'crystal');
  box([x,7,2],[x+1,8,46],'gold');
}
// Four columns joined by an overhead square frame.
for (const x of [2,40]) for (const z of [2,40]) {
  box([x,16,z],[x+6,29,z+6],'dark');
  box([x+1,18,z+1],[x+5,27,z+5],'crystal');
  box([x-1,16,z-1],[x+7,18,z+7],'gold');
}
for (const z of [2,40]) {
  box([2,28,z],[46,31,z+6],'dark');
  box([2,31,z],[46,32,z+6],'gold');
}
for (const x of [2,40]) {
  box([x,28,2],[x+6,31,46],'dark');
  box([x,31,2],[x+6,32,46],'gold');
}
// Central console, connected by four thin conduits.
box([19,16,19],[29,24,29],'dark');
box([17,23,17],[31,26,31],'table');
box([18,26,18],[30,27,30],'gold');
box([20,27,20],[28,29,28],'crystal');
box([7,17,23],[41,19,25],'gold');
box([23,17,7],[25,19,41],'gold');
// North/front indicator makes the four world orientations visibly distinct.
box([21,19,2],[27,25,4],'blue');
box([20,18,2],[28,19,4],'gold');

const textures = {
  particle:'minecraft:block/obsidian', stone:'minecraft:block/polished_deepslate',
  dark:'minecraft:block/obsidian', crystal:'minecraft:block/amethyst_block',
  gold:'minecraft:block/gold_block', table:'minecraft:block/enchanting_table_top',
  blue:'minecraft:block/lapis_block'
};
const variants = {};
let index=0;
for (let y=0;y<2;y++) for (let z=0;z<3;z++) for (let x=0;x<3;x++,index++) {
  const start=[x*16,y*16,z*16];
  const elements=[];
  for (const b of boxes) {
    const from=b.from.map((n,a)=>Math.max(n,start[a])-start[a]);
    const to=b.to.map((n,a)=>Math.min(n,start[a]+16)-start[a]);
    if (from.some((n,a)=>n>=to[a])) continue;
    const faces=Object.fromEntries(['down','up','north','south','west','east'].map(side=>[side,{texture:'#'+b.texture}]));
    elements.push({from,to,faces});
  }
  write(`assets/better_enchant/models/block/part_${index}.json`,{textures,elements});
  for (const [facing,rotation] of [['north',0],['east',90],['south',180],['west',270]]) {
    variants[`facing=${facing},part=${index}`]={model:`better_enchant:block/part_${index}`,y:rotation};
  }
}
write('assets/better_enchant/blockstates/machine_part.json',{variants});
write('assets/better_enchant/lang/en_us.json',{'block.better_enchant.machine_part':'Formed Enchanting Machine'});
write('assets/better_enchant/lang/zh_cn.json',{'block.better_enchant.machine_part':'附魔机器部件（已组装）'});
write('data/minecraft/tags/block/mineable/pickaxe.json',{replace:false,values:['better_enchant:machine_part']});
write('data/minecraft/tags/block/needs_diamond_tool.json',{replace:false,values:['better_enchant:machine_part']});
console.log(`Generated ${index} model segments and ${Object.keys(variants).length} oriented states.`);
