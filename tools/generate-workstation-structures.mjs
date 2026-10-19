// Formed-only geometry, authored in whole-machine pixel coordinates then clipped to existing cells.
// Vanilla/previous Differentenchant textures plus two original 16px accents. Unformed cores/gameplay data stay unchanged.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
const root='src/main/resources/assets/differentenchant/';
const json=(p,o)=>{fs.mkdirSync(path.dirname(root+p),{recursive:true});fs.writeFileSync(root+p,JSON.stringify(o,null,2)+'\n');};
const faces=['north','south','west','east','up','down'];
const box=(from,to,texture,{uvRotation=0,...options}={})=>({from,to,faces:Object.fromEntries(faces.map(face=>[face,{texture:'#'+texture,...(uvRotation&&(face==='up'||face==='down')?{rotation:uvRotation}:{}),...(['gold','paper','book','crystal'].includes(texture)?{uv:[0,0,16,16]}:{})}])),...options});
const solid=(from,to,t,r)=>({from,to,t,...(r?{r}:{})});
function crc(bytes){let c=0xffffffff;for(const byte of bytes){c^=byte;for(let i=0;i<8;i++)c=(c>>>1)^((c&1)?0xedb88320:0);}return(c^0xffffffff)>>>0;}
function png(name,pixel){const chunk=(name,bytes)=>{const n=Buffer.from(name),size=Buffer.alloc(4),sum=Buffer.alloc(4);size.writeUInt32BE(bytes.length);sum.writeUInt32BE(crc(Buffer.concat([n,bytes])));return Buffer.concat([size,n,bytes,sum]);};const h=Buffer.alloc(13);h.writeUInt32BE(16,0);h.writeUInt32BE(16,4);h[8]=8;h[9]=6;const data=[];for(let y=0;y<16;y++){data.push(0);for(let x=0;x<16;x++)data.push(...pixel(x,y));}fs.writeFileSync(root+'textures/block/'+name+'.png',Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',h),chunk('IDAT',zlib.deflateSync(Buffer.from(data))),chunk('IEND',Buffer.alloc(0))]));}
const color=h=>[...h.match(/../g).map(v=>parseInt(v,16)),255];
// Original 16px restrained parchment and matte gilding; no imported assets.
png('altar_gilt',(x,y)=>color((x+y*3)%11===0?'e4d3a3':(x*7+y)%13===0?'b69b60':'cfb77b'));
png('altar_page',(x,y)=>color(x===14?'c9b995':x===1?'f0e5c8':y>=3&&y<=12&&y%3===0&&x>=3&&x<=11-(y%2)*3?'a8997d':(x+y*3)%17===0?'e2d6b8':'eee3c7'));

const corner=i=>i>=9&&i%3!==1&&Math.floor(i%9/3)!==1;
const inward=i=>i===10?180:i===12?90:i===14?270:0;
const rotate=(b,angle)=>{let {from:a,to:c}=b;for(let n=0;n<angle/90;n++){const f=[16-c[2],a[1],a[0]],t=[16-a[2],c[1],c[0]];a=f;c=t;}let r=b.r;if(r){let o=r.origin;for(let n=0;n<angle/90;n++)o=[16-o[2],o[1],o[0]];const quarter=angle/90;r={...r,origin:o,axis:quarter%2?'z':'x',angle:r.angle*(quarter>=2?-1:1)};}return {...b,from:a,to:c,uvRotation:angle,...(r?{r}:{})};};
const textures={
  research:{particle:'minecraft:block/quartz_block_side',stone:'minecraft:block/quartz_block_side',deck:'minecraft:block/quartz_block_top',trim:'minecraft:block/quartz_pillar',gold:'differentenchant:block/altar_gilt',wood:'minecraft:block/stripped_oak_log',books:'minecraft:block/bookshelf',book:'differentenchant:block/research_book',paper:'differentenchant:block/altar_page',crystal:'differentenchant:block/research_crystal'},
  disenchanting:{particle:'minecraft:block/amethyst_block',stone:'minecraft:block/obsidian',deck:'minecraft:block/purple_terracotta',trim:'minecraft:block/amethyst_block',gold:'differentenchant:block/altar_gilt',crystal:'differentenchant:block/research_crystal',echo:'minecraft:block/sculk_catalyst_top'}
};
// An octagonal plinth: corner cutouts, continuous mouldings and a single recessed deck, not nine cubes.
function octagon(out,y0,y1,inset,cut,t){
  out.push(solid([inset+cut,y0,inset],[48-inset-cut,y1,48-inset],t));
  out.push(solid([inset,y0,inset+cut],[inset+cut,y1,48-inset-cut],t));
  out.push(solid([48-inset-cut,y0,inset+cut],[48-inset,y1,48-inset-cut],t));
}
function base(kind){
  const a=[];octagon(a,0,2,0,4,'stone');octagon(a,2,3,0,4,'gold');
  octagon(a,3,12,1,4,'stone');octagon(a,12,14,0,4,kind==='research'?'stone':'trim');
  octagon(a,14,16,1,4,'deck');
  // Four uninterrupted inset border strips; the silhouette stays stepped and cut at the corners.
  for(const [f,t]of [[[6,15.9,1],[42,16.02,2]],[[6,15.9,46],[42,16.02,47]],[[1,15.9,6],[2,16.02,42]],[[46,15.9,6],[47,16.02,42]]])a.push(solid(f,t,'gold'));
  // Thin radial runes drawn in geometry, with a recessed violet/ivory channel on either side.
  const material=kind==='research'?'gold':'crystal';
  for(const offset of [21,26]){a.push(solid([offset,15.95,6],[offset+1,16.03,42],material));a.push(solid([6,15.95,offset],[42,16.03,offset+1],material));}
  // Inset marks on the four exterior faces break up the wall without a per-block grid.
  for(const x of [10,23,36]){
    a.push(solid([x,5,.95],[x+2,10,1.05],'gold'),solid([x,5,46.95],[x+2,10,47.05],'gold'));
    a.push(solid([.95,5,x],[1.05,10,x+2],'gold'),solid([46.95,5,x],[47.05,10,x+2],'gold'));
  }
  return a;
}
function clip(a,i){
  const x=i%3*16,z=Math.floor(i/3)*16;
  return a.flatMap(b=>{const from=[Math.max(b.from[0],x),b.from[1],Math.max(b.from[2],z)],to=[Math.min(b.to[0],x+16),b.to[1],Math.min(b.to[2],z+16)];return from[0]<to[0]&&from[2]<to[2]?[{...b,from:[from[0]-x,from[1],from[2]-z],to:[to[0]-x,to[1],to[2]-z]}]:[];});
}
function upperRail(i){
  // Continuous low gallery ring ties the book modules and reading wings together.
  const x=i%3,z=Math.floor(i%9/3),a=[];
  if(x===0)a.push(solid([1,0,0],[3,4,16],'stone'),solid([1,4,0],[3,4.5,16],'gold'));
  if(x===2)a.push(solid([13,0,0],[15,4,16],'stone'),solid([13,4,0],[15,4.5,16],'gold'));
  if(z===0)a.push(solid([0,0,1],[16,4,3],'stone'),solid([0,4,1],[16,4.5,3],'gold'));
  if(z===2)a.push(solid([0,0,13],[16,4,15],'stone'),solid([0,4,13],[16,4.5,15],'gold'));
  return a;
}
function cabinet(i){
  const a=upperRail(i);
  a.push(solid([3,0,3],[13,2,13],'gold'),solid([4,2,4],[12,8,12],'wood'));
  // Small recessed book faces, with thick quartz framing and a tiered stone cap.
  a.push(solid([3,2,3],[5,10,5],'stone'),solid([11,2,3],[13,10,5],'stone'),solid([3,2,11],[5,10,13],'stone'),solid([11,2,11],[13,10,13],'stone'));
  a.push(solid([3,8,3],[13,10,13],'stone'),solid([4,10,4],[12,11,12],'gold'),solid([5,11,5],[11,12,11],'stone'));
  const panels=[[[5,2,3.9],[11,8,4],'north'],[[5,2,12],[11,8,12.1],'south'],[[3.9,2,5],[4,8,11],'west'],[[12,2,5],[12.1,8,11],'east']];
  const decorative=panels.map(([f,t,face])=>({from:f,to:t,faces:{[face]:{texture:'#books',uv:[1,1,15,8]}}}));
  // Ivory corner finial gives each low cabinet a clear architectural silhouette.
  const x=i%3===0?3:10,z=Math.floor(i%9/3)===0?3:10;
  a.push(solid([x,10,z],[x+3,14,z+3],'stone'),solid([x+.5,14,z+.5],[x+2.5,15,z+2.5],'gold'));
  return {a,decorative};
}
function readingWing(){
  const a=[solid([5,0,2],[11,3,15],'stone'),solid([6,3,4],[10,7,13],'stone')];
  // Open underside; stepped slope rises toward the central book.
  for(let z=0;z<16;z+=4){const height=12-z/4;a.push(solid([2,height-2,z],[14,height,z+4],'stone'),solid([2,height,z],[3,height+.5,z+4],'gold'),solid([13,height,z],[14,height+.5,z+4],'gold'));}
  // Two whole leaves tilt inward as one manuscript, rather than repeated page tiles.
  const tilt={origin:[8,11.5,8],axis:'x',angle:22.5};
  a.push(solid([3,11,1],[13,11.3,15],'book',tilt),solid([4,11.3,2],[7.5,11.65,14],'paper',tilt),solid([8.5,11.3,2],[12,11.65,14],'paper',tilt),solid([7.5,11.3,2],[8.5,11.7,14],'book',tilt));
  return a;
}
function researchCore(){
  const a=[solid([1,0,1],[15,3,15],'stone'),solid([2,3,2],[14,4,14],'gold'),solid([4,4,4],[12,12,12],'stone'),solid([2,12,2],[14,14,14],'gold'),solid([1,14,1],[15,16,15],'stone')];
  // Wide open grimoire: stepped pages, a spine and scroll rolls, above the surrounding reading wings.
  const left={origin:[8,17,8],axis:'z',angle:-22.5},right={...left,angle:22.5};
  a.push(solid([1,16.5,3],[7.5,17,13],'book',left),solid([8.5,16.5,3],[15,17,13],'book',right),solid([2,17,4],[7.5,17.5,12],'paper',left),solid([8.5,17,4],[14,17.5,12],'paper',right),solid([7.5,16.5,3],[8.5,17.5,13],'gold'));
  a.push(solid([1,17,1],[15,18,2],'paper'),solid([1,16.5,.5],[3,18.5,2.5],'gold'),solid([13,16.5,.5],[15,18.5,2.5],'gold'));
  // A restrained suspended crystal over the middle of the open book.
  a.push(solid([7,21,7],[9,22,9],'gold'),solid([6.5,22,6.5],[9.5,25,9.5],'crystal'),solid([7.5,25,7.5],[8.5,26,8.5],'crystal'));
  return a;
}
function conduit(){
  return [solid([3,0,0],[13,2,16],'stone'),solid([3,2,0],[5,5,16],'stone'),solid([11,2,0],[13,5,16],'stone'),solid([4,5,0],[5,5.5,16],'gold'),solid([11,5,0],[12,5.5,16],'gold'),solid([6,2,0],[10,3,16],'crystal'),solid([5,3,6],[11,4,7],'gold'),solid([5,3,9],[11,4,10],'gold'),solid([7,3,7],[9,5,9],'crystal')];
}
function extractionCore(){
  const a=[solid([1,0,1],[15,2,15],'stone'),solid([2,2,2],[14,3,14],'gold'),solid([4,3,4],[12,8,12],'stone'),solid([2,8,2],[14,10,14],'gold'),solid([1,10,1],[15,12,15],'stone')];
  // Hollow crucible, inset well and four small clasps. No solid gold plates or redstone cubes.
  a.push(solid([3,12,3],[13,12.5,13],'echo'),solid([1,12,1],[3,15,15],'stone'),solid([13,12,1],[15,15,15],'stone'),solid([3,12,1],[13,15,3],'stone'),solid([3,12,13],[13,15,15],'stone'));
  a.push(solid([1,15,1],[3,16,15],'gold'),solid([13,15,1],[15,16,15],'gold'),solid([3,15,1],[13,16,3],'gold'),solid([3,15,13],[13,16,15],'gold'));
  a.push(solid([6,13,6],[10,18,10],'crystal'),solid([7,18,7],[9,21,9],'crystal'),solid([7.5,21,7.5],[8.5,22,8.5],'crystal'));
  for(const [x,z]of [[2,7],[12,7],[7,2],[7,12]])a.push(solid([x,12,z],[x+2,18,z+2],'gold'));
  return a;
}
const shapes={};
for(const kind of ['disenchanting','research']){
  const multipart=[],floor=base(kind);shapes[kind]=[];
  for(let i=0;i<18;i++){
    const model=kind+'_part_'+i;let a=[],decorative=[];
    if(i<9)a=clip(floor,i);
    else if(kind==='research'){
      if(i===13)a=researchCore();
      else if(corner(i))({a,decorative}=cabinet(i));
      else a=[...upperRail(i),...readingWing().map(b=>rotate(b,inward(i)))];
    }else if(i===13)a=extractionCore();
    else if(corner(i)){
      // Mount is inside the native cluster footprint; the cluster keeps its exact native collision.
      a=[solid([3,0,3],[13,.6,13],'stone'),solid([3,.6,3],[4,1,13],'gold'),solid([12,.6,3],[13,1,13],'gold'),solid([4,.6,3],[12,1,4],'gold'),solid([4,.6,12],[12,1,13],'gold')];
      multipart.push({when:{part:String(i)},apply:{model:'minecraft:block/amethyst_cluster'}});
    }else a=conduit().map(b=>rotate(b,inward(i)));
    shapes[kind].push(a);
    json('models/block/'+model+'.json',{ambientocclusion:true,textures:textures[kind],elements:[...a.map(b=>box(b.from,b.to,b.t,{uvRotation:b.uvRotation,...(b.r?{rotation:b.r}:{})})),...decorative]});
    // Only the research center follows the saved core facing. Relative radial parts are already oriented.
    if(kind==='research'&&i===13)for(const [facing,y]of [['north',0],['east',90],['south',180],['west',270]])multipart.push({when:{part:String(i),facing},apply:{model:'differentenchant:block/'+model,y}});
    else multipart.push({when:{part:String(i)},apply:{model:'differentenchant:block/'+model}});
  }
  json('blockstates/'+kind+'_part.json',{multipart});
}
// Use identical solid geometry for selection/collision, built once. No server rendering classes or assets.
function bounds(b){if(!b.r)return [...b.from,...b.to];const {origin:o,axis,angle}=b.r;const radians=angle*Math.PI/180,co=Math.cos(radians),si=Math.sin(radians),points=[];for(const x of [b.from[0],b.to[0]])for(const y of [b.from[1],b.to[1]])for(const z of [b.from[2],b.to[2]]){const p=[x-o[0],y-o[1],z-o[2]];const q=axis==='x'?[p[0],p[1]*co-p[2]*si,p[1]*si+p[2]*co]:[p[0]*co-p[1]*si,p[0]*si+p[1]*co,p[2]];points.push(q.map((v,j)=>v+o[j]));}return [...[0,1,2].map(j=>Math.min(...points.map(p=>p[j]))),...[0,1,2].map(j=>Math.max(...points.map(p=>p[j])))].map(v=>Math.round(v*1000000)/1000000);}
const java=`package dev.differentenchant.workstation;

import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.*;

/** Generated by tools/generate-workstation-structures.mjs; formed visuals only. */
public final class WorkstationVisualShapes {
    private static VoxelShape shape(double... values) {
        var result=Shapes.empty();
        for(int i=0;i<values.length;i+=6)result=Shapes.or(result,Block.box(values[i],values[i+1],values[i+2],values[i+3],values[i+4],values[i+5]));
        return result.optimize();
    }
${Object.entries(shapes).map(([kind,parts])=>`    private static final VoxelShape[] ${kind.toUpperCase()}={\n${parts.map(a=>'        shape('+a.filter(b=>b.to[1]-b.from[1]>=.3).flatMap(bounds).join(',')+')').join(',\n')}\n    };`).join('\n')}
    private static final Map<Direction,VoxelShape> RESEARCH_CORE=Shapes.rotateHorizontal(RESEARCH[13]);
    public static VoxelShape get(WorkstationStructure.Kind kind,int part,Direction facing) {
        return kind==WorkstationStructure.Kind.DISENCHANT?DISENCHANTING[part]:part==13?RESEARCH_CORE.get(facing):RESEARCH[part];
    }
    private WorkstationVisualShapes(){}
}
`;
fs.writeFileSync('src/main/java/dev/differentenchant/workstation/WorkstationVisualShapes.java',java);
console.log('Generated 36 formed-only models, connected plinths, 2 blockstates and matching collision geometry. Unformed models untouched.');
