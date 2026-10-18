// Focused resource/geometry audit; does not run enchanting tests or modify templates.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {inspectTemplate,verifyLocks} from './verify-ruins.mjs';
const root=new URL('../src/main/resources/data/differentenchant/',import.meta.url);
const read=path=>JSON.parse(fs.readFileSync(new URL(path,root),'utf8'));
const template=inspectTemplate(new URL('structure/ruins/lantern_sanctum.nbt',root));
const cells=new Map(template.nbt.blocks.map(b=>[b.pos.join(','),template.nbt.palette[b.state].Name]));
const cell=(x,y,z)=>cells.get([x,y,z].join(','));
const plan=read('ruins/blueprints.json').lantern_sanctum;
assert.deepEqual(template.nbt.size,[33,26,33]);
assert.equal(plan.hidden_barrel_candidates.length,16);
for(const [x,y,z] of plan.hidden_barrel_candidates) {
  assert.equal(cell(x,y,z),'minecraft:birch_planks');
  assert.ok(['minecraft:red_carpet','minecraft:yellow_carpet'].includes(cell(x,y+1,z)));
}
assert.equal([...cells.values()].filter(n=>n==='minecraft:barrel').length,0,'no fixed barrel or fixed inventory in template');
// Body contour: all four rotations match; square bounding-box corners remain untouched terrain.
for(let x=0;x<33;x++) for(let z=0;z<33;z++) {
  assert.equal(cells.has([x,9,z].join(',')),cells.has([32-z,9,x].join(',')));
  if(Math.abs(x-16)+Math.abs(z-16)>22) assert.equal(cell(x,9,z),undefined);
}
let glass=0,skin=0,lights=0;
for(const b of template.nbt.blocks) {
  const name=template.nbt.palette[b.state].Name;
  if(name==='minecraft:glass')glass++;
  if(['minecraft:glass','minecraft:quartz_pillar','minecraft:stripped_birch_log'].includes(name))skin++;
  if(['minecraft:sea_lantern','minecraft:ochre_froglight','minecraft:pearlescent_froglight','minecraft:end_rod'].includes(name))lights++;
}
assert.ok(glass/skin>.6);assert.ok(lights>250);
const structure=read('worldgen/structure/lantern_sanctum.json');
assert.equal(structure.template,'differentenchant:ruins/lantern_sanctum');assert.equal(structure.buried,false);
const placement=read('worldgen/structure_set/lantern_sanctum.json').placement;
assert.ok(placement.spacing>read('worldgen/structure_set/abandoned_workshop.json').placement.spacing);
assert.ok(placement.separation>0&&placement.separation<placement.spacing);
assert.equal(read('loot_table/chests/lantern_cache.json').type,'minecraft:chest');
console.log(JSON.stringify({lantern:{size:template.nbt.size,blocks:template.blocks,glass,skin,glass_fraction:glass/skin,lights,sha256:template.sha256,geometry_sha256:template.geometry_sha256},approved:verifyLocks()},null,2));
