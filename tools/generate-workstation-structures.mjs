import fs from 'node:fs';
import path from 'node:path';
const root='src/main/resources/assets/differentenchant/';
const json=(p,o)=>{fs.mkdirSync(path.dirname(root+p),{recursive:true});fs.writeFileSync(root+p,JSON.stringify(o,null,2)+'\n');};
const box=(from,to,texture,top=texture)=>({from,to,faces:Object.fromEntries(['north','south','west','east','up','down'].map(face=>[face,{texture:'#'+(face==='up'||face==='down'?top:texture)}]))});
const corner=i=>i>=9&&((i%9)%3!==1)&&Math.floor(i%9/3)!==1;
for(const kind of ['disenchanting','research']){
  const multipart=[];
  for(let i=0;i<18;i++){
    const model=kind+'_part_'+i;let result;
    if(kind==='disenchanting'){
      const textures={particle:'minecraft:block/amethyst_block',amethyst:'minecraft:block/amethyst_block',gold:'minecraft:block/gold_block',red:'minecraft:block/redstone_block'};
      result=i<9?{textures,elements:[box([0,0,0],[16,16,16],i===4?'gold':'amethyst')]}:i===13?{parent:'differentenchant:block/disenchanting_core'}:corner(i)?{parent:'minecraft:block/amethyst_cluster'}:{textures,elements:[box([0,0,0],[16,3,16],'amethyst'),box([2,3,2],[14,9,14],'red'),box([1,9,1],[15,11,15],'gold')]};
    }else{
      const textures={particle:'minecraft:block/quartz_block_side',stone:i===4?'minecraft:block/chiseled_quartz_block':'minecraft:block/quartz_block_side',top:i===4?'minecraft:block/chiseled_quartz_block_top':'minecraft:block/quartz_block_top',trim:'minecraft:block/quartz_pillar',books:'minecraft:block/bookshelf',wood:'minecraft:block/oak_planks'};
      result=i<9?{textures,elements:[box([0,0,0],[16,15,16],'stone','top'),box([1,15,1],[15,16,15],'stone','top'),box([0,15,0],[16,16,1],'trim'),box([0,15,15],[16,16,16],'trim'),box([0,15,1],[1,16,15],'trim'),box([15,15,1],[16,16,15],'trim')]}:i===13?{parent:'differentenchant:block/lost_research_table'}:corner(i)?{textures,elements:[box([0,0,0],[16,2,16],'trim'),box([1,2,1],[15,16,15],'books','wood'),box([0,2,0],[1,16,1],'trim'),box([15,2,0],[16,16,1],'trim'),box([0,2,15],[1,16,16],'trim'),box([15,2,15],[16,16,16],'trim')]}:{parent:'minecraft:block/lectern'};
    }
    json('models/block/'+model+'.json',result);
    if(kind==='research'&&i>=9&&!corner(i))for(const [facing,y] of [['north',0],['east',90],['south',180],['west',270]])multipart.push({when:{part:String(i),facing},apply:{model:'differentenchant:block/'+model,y}});
    else multipart.push({when:{part:String(i)},apply:{model:'differentenchant:block/'+model}});
  }
  json('blockstates/'+kind+'_part.json',{multipart});
}
const p=root+'models/block/disenchanting_core.json',core=JSON.parse(fs.readFileSync(p));core.textures.trim='minecraft:block/amethyst_block';core.textures.copper='minecraft:block/gold_block';core.textures.stone='minecraft:block/amethyst_block';core.textures.particle='minecraft:block/amethyst_block';fs.writeFileSync(p,JSON.stringify(core,null,2)+'\n');
