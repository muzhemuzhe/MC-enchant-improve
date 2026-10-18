// Authored lantern geometry only. The accepted cottages/workshop are deliberately independent.
export function lanternSanctum(Building,manifests) {
  const b=new Building('lantern_sanctum',[33,26,33]);
  const inside=(x,z,r,c)=>Math.abs(x-16)<=r&&Math.abs(z-16)<=r&&Math.abs(x-16)+Math.abs(z-16)<=c;
  const edge=(x,z,r,c)=>inside(x,z,r,c)&&[[1,0],[-1,0],[0,1],[0,-1]].some(([dx,dz])=>!inside(x+dx,z+dz,r,c));
  const stair=facing=>({facing,half:'bottom',shape:'straight',waterlogged:'false'});
  const profile=y=>y===0?[13,20]:y<=7?[14,21]:y<=14?[15,22]:y<=20?[14,21]:y<=24?[34-y,41-y]:[10,17];
  for(let y=0;y<26;y++) {
    const [r,c]=profile(y);
    for(let x=0;x<33;x++) for(let z=0;z<33;z++) {
      if(!inside(x,z,r,c)) continue;
      const rim=edge(x,z,r,c),dx=Math.abs(x-16),dz=Math.abs(z-16);
      const rib=(dx<=1&&dz===r)||(dz<=1&&dx===r)||(rim&&Math.abs(dx-dz)<=1);
      if(y===0) b.block(x,y,z,rim?'smooth_quartz':'stone_bricks');
      else if(y===1||y===8||y===15) {
        const atrium=y>1&&dx<=5&&dz<=5;
        b.block(x,y,z,atrium?'air':rim?'smooth_quartz':(dx===6||dz===6)?'pale_oak_planks':'birch_planks');
      } else if(y===24||y===25) {
        b.block(x,y,z,y===25?'pale_oak_slab':rim?'smooth_quartz':(x+z)%3===0?'sea_lantern':'pale_oak_planks',y===25?{type:'bottom',waterlogged:'false'}:{});
      } else if(rim) {
        const band=y===7||y===14||y===20||y===23;
        b.block(x,y,z,rib?'quartz_pillar':band?'stripped_birch_log':'glass',rib?{axis:'y'}:band?{axis:dx>dz?'z':'x'}:{});
      } else b.block(x,y,z,'air');
    }
  }
  // Single bright woven carpet, including every concealed cache candidate (normal floor underneath).
  for(let x=3;x<=29;x++) for(let z=3;z<=29;z++) if(inside(x,z,12,19))
    b.block(x,2,z,(x+z)%2?'red_carpet':'yellow_carpet');
  // Axial framed entrance, shallow approach. No glass in the walk-through opening.
  for(const x of [14,18]) b.fill(x,2,2,x,5,2,'quartz_pillar',{axis:'y'});
  b.fill(14,6,2,18,6,2,'chiseled_quartz_block');b.fill(15,2,2,17,5,2,'air');
  for(let x=14;x<=18;x++) {b.block(x,0,0,'quartz_stairs',stair('south'));b.block(x,1,1,'smooth_quartz');}
  for(const [x,hinge] of [[15,'left'],[16,'right']]) for(const half of ['lower','upper'])
    b.block(x,half==='lower'?2:3,2,'birch_door',{facing:'north',half,hinge,open:'false',powered:'false'});
  b.fill(17,2,2,17,3,2,'glass');b.lamp(16,5,2);
  // Research galleries frame a real two-storey void rather than filling the lantern with solid slabs.
  for(const floor of [8,15]) for(let x=10;x<=22;x++) for(let z=10;z<=22;z++) {
    if((x===10||x===22||z===10||z===22)&&!(x===10&&z>=14&&z<=17)) b.block(x,floor+1,z,'birch_fence');
  }
  // Two broad switchback flights, with real headroom and a landing at each storey.
  const walk=[];
  for(let i=0;i<7;i++) {
    const y=2+i,z=20-i;
    b.fill(6,y+1,z,8,y+3,z,'air');
    for(let x=6;x<=8;x++) b.block(x,y,z,'birch_stairs',stair('north'));
    walk.push({x:7,z,feet:y+1});
  }
  for(let i=0;i<7;i++) {
    const y=9+i,z=14+i;
    b.fill(9,y+1,z,10,y+3,z,'air');
    for(let x=9;x<=10;x++) b.block(x,y,z,'birch_stairs',stair('south'));
    walk.push({x:9,z,feet:y+1});
  }
  // Quiet ground-floor alcoves; the carpeted central hall remains unobstructed.
  for(const x of [8,24]) {b.fill(x,2,6,x,3,8,'bookshelf');b.block(x,2,9,'lectern',{facing:x<16?'east':'west',has_book:'false',powered:'false'});}
  b.table(20,2,25);
  for(const [x,z] of [[11,6],[21,6],[6,24],[26,24]]) {b.block(x,2,z,'chiseled_quartz_block');b.block(x,3,z,'candle',{candles:'3',lit:'true',waterlogged:'false'});}
  // Small research islands on the second ring, not another combat dungeon.
  b.fill(25,9,12,25,10,19,'bookshelf');b.shelf(25,9,13,'west');b.shelf(25,9,17,'west');
  b.fill(12,9,25,19,10,25,'bookshelf');b.shelf(13,9,25,'north');b.shelf(18,9,25,'north');
  b.table(20,9,8);b.block(21,9,10,'lectern',{facing:'south',has_book:'false',powered:'false'});
  b.block(23,9,22,'amethyst_block');b.block(23,10,22,'amethyst_cluster',{facing:'up',waterlogged:'false'});
  // Warm luminous crown, visible through all eight glazed facets. No dark opaque roof mass.
  const lights=[];
  for(let x=5;x<=27;x++) for(let z=5;z<=27;z++) if(inside(x,z,11,18)&&!inside(x,z,9,14)&&(x+z)%2===0) {
    b.block(x,16,z,'smooth_quartz');b.block(x,17,z,(x+z)%4===0?'pearlescent_froglight':'ochre_froglight',{axis:'y'});
    b.block(x,18,z,'end_rod',{facing:'up'});lights.push([x,17,z]);
  }
  // Central suspended prism, over the void, with luminous arms rather than a solid opaque cube.
  for(let y=18;y<=23;y++) b.block(16,y,16,y===21?'amethyst_block':'sea_lantern');
  for(const [dx,dz] of [[1,0],[-1,0],[0,1],[0,-1]]) for(let step=1;step<=4;step++) {
    b.block(16+dx*step,21,16+dz*step,step===4?'ochre_froglight':'end_rod',step===4?{axis:'y'}:{facing:dx>0?'east':dx<0?'west':dz>0?'south':'north'});
  }
  for(const [x,z] of [[9,9],[23,9],[9,23],[23,23]]) b.lamp(x,7,z);
  b.save();
  Object.assign(manifests.lantern_sanctum,{floors:[1,8,15],entry_door:[15,2,2],
    hidden_barrel_candidates:[9,13,19,23].flatMap(x=>[9,13,19,23].map(z=>[x,1,z])),
    hidden_barrels:{minimum:4,maximum:7,loot:'differentenchant:chests/lantern_cache'},
    staircase_walk:walk,light_core:lights,review_views:[
      {id:'01-front',camera:[16,11,-24],look:[16,11,16]},
      {id:'02-diagonal',camera:[40,22,-8],look:[16,12,16]},
      {id:'03-overhead',camera:[16,59,16],look:[16,1,16]},
      {id:'04-ground-hall',camera:[16,3,5],look:[17,5,23]},
      {id:'05-research',camera:[7,10,10],look:[23,10,20]},
      {id:'06-luminous-crown',camera:[8,17,11],look:[22,19,21]}
    ]});
}
