// Original cuboid workbench and 16x16 pixel textures, authored procedurally. No borrowed mod assets.
import fs from 'node:fs';
import zlib from 'node:zlib';
const root=new URL('../src/main/resources/',import.meta.url);
const write=(path,data)=>{const url=new URL(path,root);fs.mkdirSync(new URL('.',url),{recursive:true});fs.writeFileSync(url,data);};
const json=(path,data)=>write(path,JSON.stringify(data,null,2)+'\n');
const linear=(base,per=base)=>({type:'minecraft:linear',base,per_level_above_first:per});
const attribute=(path,attribute,amount)=>({id:'differentenchant:'+path,attribute:'minecraft:'+attribute,amount,operation:'add_value'});
const definitions=[
  ['trail_stride','Trail Stride','行旅步幅',1,2,'#minecraft:enchantable/foot_armor',['feet'],{'minecraft:attributes':[attribute('trail_stride','step_height',linear(.5))]},'Step up an extra half block per level.','每级增加半格自动跨越高度。'],
  ['prospectors_echo',"Prospector’s Echo",'矿脉回声',1,4,'#minecraft:enchantable/mining',['mainhand'],{'minecraft:block_experience':[{effect:{type:'minecraft:add',value:linear(1)}}]},'Experience-dropping blocks yield +1 XP per level; incompatible with Silk Touch.','采集原本掉落经验的方块时，每级额外获得1经验；与精准采集互斥。'],
  ['lantern_sight','Lantern Sight','潜光',1,1,'#minecraft:enchantable/head_armor',['head'],{},'Grants night vision while submerged; lingers for 12 seconds.','潜入水下时获得夜视，离水后最多保留12秒。'],
  ['living_margin','Living Margin','生息书页',2,3,'#minecraft:enchantable/chest_armor',['chest'],{},'At 18+ food, heals 1 health per level every 10 seconds, adding exhaustion.','饥饿值至少18时，每10秒恢复每级1点生命，并增加消耗度。'],
  ['archivists_reach',"Archivist’s Reach",'学者之手',2,2,'#minecraft:enchantable/mining',['mainhand'],{'minecraft:attributes':[attribute('archivists_reach.block','block_interaction_range',linear(.5)),attribute('archivists_reach.entity','entity_interaction_range',linear(.5))]},'While holding a tool, extends block and entity interaction reach by 0.5 per level.','持工具时，每级增加0.5格方块与实体交互距离。'],
  ['warding_focus','Warding Focus','守望凝神',3,2,['minecraft:shield'],['offhand','mainhand'],{},'While actively blocking, grants Resistance I/II; lingers for 2 seconds.','持续举盾格挡时获得抗性提升I/II，放下后最多保留2秒。'],
  ['gravity_thread','Gravity Thread','引力丝线',3,2,['minecraft:bow','minecraft:crossbow'],['mainhand'],{'minecraft:post_attack':[{enchanted:'attacker',affected:'victim',effect:{type:'minecraft:apply_mob_effect',to_apply:['minecraft:levitation'],min_duration:linear(1),max_duration:linear(1),min_amplifier:0,max_amplifier:0},requirements:{condition:'minecraft:damage_source_properties',predicate:{tags:[{id:'minecraft:is_projectile',expected:true}]}}}]},'Projectile hits levitate the victim for 1/2 seconds.','远程命中令目标漂浮1/2秒。'],
  ['last_page','Last Page','终末残页',4,1,'#minecraft:enchantable/chest_armor',['chest'],{},'Survives one fatal hit with 6 health and brief absorption. 20-minute world-time cooldown; damages armor by 32. Void/admin damage excluded.','致命伤害时保留6生命并获得短暂伤害吸收；世界时间20分钟冷却，消耗32护甲耐久。虚空及管理伤害除外。']
];
for(const [id,en,zh,tier,max,supported,slots,effects] of definitions) {
  const enchantment={description:{translate:'enchantment.differentenchant.'+id},supported_items:supported,weight:[0,10,5,2,1][tier],max_level:max,min_cost:{base:1,per_level_above_first:0},max_cost:{base:60,per_level_above_first:0},anvil_cost:2,slots,effects};
  if(id==='prospectors_echo')enchantment.exclusive_set=['minecraft:silk_touch'];
  if(id==='living_margin'||id==='last_page')enchantment.exclusive_set=['differentenchant:'+(id==='living_margin'?'last_page':'living_margin')];
  json('data/differentenchant/enchantment/'+id+'.json',enchantment);
}
for(let tier=1;tier<=4;tier++)json('data/differentenchant/tags/enchantment/lost_tier_'+tier+'.json',{replace:false,values:definitions.filter(d=>d[3]===tier).map(d=>'differentenchant:'+d[0])});
json('data/differentenchant/tags/enchantment/lost_enchantments.json',{replace:false,values:[1,2,3,4].map(t=>'#differentenchant:lost_tier_'+t)});
json('data/minecraft/tags/enchantment/treasure.json',{replace:false,values:['#differentenchant:lost_enchantments']});
for(const kind of ['random','directed'])json('data/differentenchant/tags/enchantment/excluded_from_'+kind+'.json',{replace:false,values:['#differentenchant:lost_enchantments']});
json('data/differentenchant/recipe/lost_research_table.json',{type:'minecraft:crafting_shaped',category:'misc',pattern:['ABA','QLQ','III'],key:{A:'minecraft:amethyst_shard',B:'minecraft:bookshelf',Q:'minecraft:quartz',L:'minecraft:lectern',I:'minecraft:iron_ingot'},result:{id:'differentenchant:lost_research_table',count:1}});
json('data/differentenchant/loot_table/blocks/lost_research_table.json',{type:'minecraft:block',pools:[{rolls:1,entries:[{type:'minecraft:item',name:'differentenchant:lost_research_table'}],conditions:[{condition:'minecraft:survives_explosion'}]}]});
json('data/minecraft/tags/block/mineable/axe.json',{replace:false,values:['differentenchant:lost_research_table']});
const faces=texture=>Object.fromEntries(['north','south','east','west','up','down'].map(face=>[face,{uv:[0,0,16,16],texture:'#'+texture}]));
const cube=(from,to,texture)=>({from,to,faces:faces(texture)});
const elements=[];
for(const x of [1,12])for(const z of [1,12])elements.push(cube([x,0,z],[x+3,10,z+3],'frame'));
elements.push(cube([0,9,0],[16,12,16],'wood'),cube([2,7,12],[14,8,15],'wood'));
for(const [x,z] of [[3,13],[7,13],[11,13]])elements.push(cube([x,8,z],[x+2,9,z+2],'book'));
elements.push(cube([2,12,3],[10,12.5,11],'book'),cube([2.5,12.5,3.5],[5.5,13.5,10.5],'paper'),cube([6.5,12.5,3.5],[9.5,13.5,10.5],'paper'),cube([5.5,12.5,3.5],[6.5,13,10.5],'wood'));
elements.push(cube([12,12,5],[15,13,8],'frame'),cube([12.5,13,5.5],[14.5,15,7.5],'crystal'),cube([13,15,6],[14,16,7],'crystal'));
json('assets/differentenchant/models/block/lost_research_table.json',{ambientocclusion:true,textures:{particle:'differentenchant:block/research_wood',wood:'differentenchant:block/research_wood',frame:'differentenchant:block/research_frame',book:'differentenchant:block/research_book',paper:'differentenchant:block/research_paper',crystal:'differentenchant:block/research_crystal'},elements,display:{gui:{rotation:[30,225,0],translation:[0,0,0],scale:[.65,.65,.65]},ground:{rotation:[0,0,0],translation:[0,3,0],scale:[.25,.25,.25]},fixed:{rotation:[0,0,0],translation:[0,0,0],scale:[.5,.5,.5]}}});
json('assets/differentenchant/blockstates/lost_research_table.json',{variants:Object.fromEntries(['north','east','south','west'].map((f,i)=>['facing='+f,{model:'differentenchant:block/lost_research_table',y:i*90}]))});
json('assets/differentenchant/models/item/lost_research_table.json',{parent:'differentenchant:block/lost_research_table'});
json('assets/differentenchant/items/lost_research_table.json',{model:{type:'minecraft:model',model:'differentenchant:item/lost_research_table'}});
json('assets/differentenchant/models/item/lost_scroll.json',{parent:'minecraft:item/generated',textures:{layer0:'differentenchant:item/lost_scroll'}});
json('assets/differentenchant/items/lost_scroll.json',{model:{type:'minecraft:model',model:'differentenchant:item/lost_scroll'}});
// Small standalone PNG writer, nearest-neighbour pixels only.
function crc(bytes){let c=0xffffffff;for(const byte of bytes){c^=byte;for(let i=0;i<8;i++)c=(c>>>1)^((c&1)?0xedb88320:0);}return(c^0xffffffff)>>>0;}
function png(path,pixel){const chunk=(name,bytes)=>{const n=Buffer.from(name),size=Buffer.alloc(4),sum=Buffer.alloc(4);size.writeUInt32BE(bytes.length);sum.writeUInt32BE(crc(Buffer.concat([n,bytes])));return Buffer.concat([size,n,bytes,sum]);};const header=Buffer.alloc(13);header.writeUInt32BE(16,0);header.writeUInt32BE(16,4);header[8]=8;header[9]=6;const data=[];for(let y=0;y<16;y++){data.push(0);for(let x=0;x<16;x++)data.push(...pixel(x,y));}write(path,Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',zlib.deflateSync(Buffer.from(data))),chunk('IEND',Buffer.alloc(0))]));}
const color=(hex,a=255)=>[parseInt(hex.slice(0,2),16),parseInt(hex.slice(2,4),16),parseInt(hex.slice(4,6),16),a];
png('assets/differentenchant/textures/block/research_wood.png',(x,y)=>color(x===0||x===15||y===0||y===15?'887351':y%4===0?'aa956b':(x*3+y)%7===0?'dacda1':'c8b786'));
png('assets/differentenchant/textures/block/research_frame.png',(x,y)=>color(x===0||x===15?'a49d8d':x%5===0?'c6c0af':(x+y)%7===0?'f4efdc':'e1dbc9'));
png('assets/differentenchant/textures/block/research_book.png',(x,y)=>color(x<2||x>13||y<2||y>13?'55425a':x===3||y===3||x===12||y===12?'c8a45c':(x+y)%6===0?'ad86bf':'715575'));
png('assets/differentenchant/textures/block/research_paper.png',(x,y)=>color(x<2||x>13?'b7a57e':y%3===0&&x>3&&x<12?'8f8069':(x+y)%9===0?'cfc4a4':'e9dfbc'));
png('assets/differentenchant/textures/block/research_crystal.png',(x,y)=>color(x===3||y===2?'efe0ff':x<6?'c3a5ec':(x+y)%5===0?'ab80d4':'8063a9'));
png('assets/differentenchant/textures/item/lost_scroll.png',(x,y)=>{if(x<2||x>13||y<2||y>13)return[0,0,0,0];if(y===2||y===13)return color('9f7644');if(x===2||x===13)return color('bea46d');if(x>=6&&x<=9&&y>=6&&y<=9)return color((x+y)%2?'735582':'ad86bf');return color(y%4===0&&x>4&&x<11?'ad9971':'e6d5a9');});
for(const lang of ['en_us','zh_cn']) {
  const path='assets/differentenchant/lang/'+lang+'.json';const value=JSON.parse(fs.readFileSync(new URL(path,root),'utf8')),zh=lang==='zh_cn';
  for(const [id,en,cn,tier,max,supported,slots,effects,helpEn,helpZh]of definitions){value['enchantment.differentenchant.'+id]=zh?cn:en;value['enchantment.differentenchant.'+id+'.desc']=zh?helpZh:helpEn;}
  const names={
    'block.differentenchant.lost_research_table':['Lost Enchantment Research Table','失落魔法研究台'],
    'item.differentenchant.lost_scroll':['Lost Scroll','失落卷轴'],
    'item.differentenchant.lost_scroll.help':['A fixed research byproduct. Future use: enchantment conflicts.','固定研究副产物，未来用于互斥附魔研究。'],
    'research.differentenchant.research':['Research','研究'],'research.differentenchant.records':['Discoveries','配方记录'],
    'research.differentenchant.books':['Books A + B','附魔书 A + B'],'research.differentenchant.core':['Core','谐调核心'],
    'research.differentenchant.unknown':['???','???'],'research.differentenchant.grade':['Research grade %s','研究评级 %s'],
    'research.differentenchant.rarity':['Rarity %s','稀有度 %s'],
    'research.differentenchant.status.0':['Consumes 1 attuned core','消耗1颗谐调核心'],
    'research.differentenchant.status.1':['Each book must store exactly one legal enchantment.','每本书必须恰好存储一条合法附魔。'],
    'research.differentenchant.status.2':['Insert an attuned resonance core.','请放入谐调的共鸣核心。'],
    'research.differentenchant.status.3':['Collect the result first.','请先取走研究结果。'],
    'research.differentenchant.status.4':['Recorded result unavailable; nothing consumed.','已记录成果不可用，不会消耗材料。'],
    'research.differentenchant.status.5':['Research table unavailable.','研究台不可用。'],
    'research.differentenchant.sync':['Synchronizing…','同步中…'],
    'research.differentenchant.empty':['No discoveries yet. Try a pair of books.','尚无发现，请先研究一组附魔书。'],
    'research.differentenchant.record_count':['%s discoveries · Scroll to browse','%s 条发现 · 滚轮浏览'],
    'research.differentenchant.scroll_rule':['Scroll recipes keep both books; consume one core.','卷轴配方保留两本书，消耗1颗核心。'],
    'research.differentenchant.success_rule':['Successful recipes consume both books and one core.','成功配方消耗两本书及1颗核心。'],
    'item.differentenchant.attuned_resonance_core.help':['Consumed by the lost enchantment research table.','失落魔法研究台使用的研究耗材。']
  };
  for(const[key,text]of Object.entries(names))value[key]=text[zh?1:0];json(path,value);
}
json('data/differentenchant/research/prototype_catalogue.json',definitions.map(([id,en,zh,rarity,max,supported,slots,effects,helpEn,helpZh])=>({id:'differentenchant:'+id,en,zh,rarity,max,supported,description_en:helpEn,description_zh:helpZh})));
console.log('Generated independent research table model, 6 original textures, native recipe, 8 lost enchantments and rarity tags.');
