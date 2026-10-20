// Original pixel art on the official vanilla villager UV layout; no copied profession pixels.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import d from './enchanter-definitions.mjs';
const root='src/main/resources/';
const write=(p,b)=>{fs.mkdirSync(path.dirname(root+p),{recursive:true});fs.writeFileSync(root+p,b);};
const json=(p,j)=>write(p,JSON.stringify(j,null,2)+'\n');
function crc(bytes){let c=0xffffffff;for(const byte of bytes){c^=byte;for(let i=0;i<8;i++)c=(c>>>1)^((c&1)?0xedb88320:0);}return(c^0xffffffff)>>>0;}
function png(p,w,h,pixel){const chunk=(name,b)=>{const n=Buffer.from(name),s=Buffer.alloc(4),c=Buffer.alloc(4);s.writeUInt32BE(b.length);c.writeUInt32BE(crc(Buffer.concat([n,b])));return Buffer.concat([s,n,b,c]);},head=Buffer.alloc(13),data=[];head.writeUInt32BE(w,0);head.writeUInt32BE(h,4);head[8]=8;head[9]=6;for(let y=0;y<h;y++){data.push(0);for(let x=0;x<w;x++)data.push(...pixel(x,y));}write(p,Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',head),chunk('IDAT',zlib.deflateSync(Buffer.from(data))),chunk('IEND',Buffer.alloc(0))]));}
const color=h=>[...h.match(/../g).map(n=>parseInt(n,16)),255];
for(const t of [d.bottle,d.recycling,d.crystal]){
  json('data/differentenchant/villager_trade/enchanter/'+t.id+'.json',{wants:{id:t.emeralds?'minecraft:emerald':'minecraft:enchanted_book',count:t.emeralds||1},gives:{id:t.item,count:t.count},max_uses:t.stock,xp:t.xp,reputation_discount:t.discount});
  json('data/differentenchant/trade_set/enchanter/level_'+t.level+'.json',{amount:1,trades:['differentenchant:enchanter/'+t.id]});
}
json('data/minecraft/tags/point_of_interest_type/acquirable_job_site.json',{replace:false,values:['differentenchant:enchanter']});
json('assets/differentenchant/models/item/condensed_experience_crystal.json',{parent:'minecraft:item/generated',textures:{layer0:'differentenchant:item/condensed_experience_crystal'}});
json('assets/differentenchant/items/condensed_experience_crystal.json',{model:{type:'minecraft:model',model:'differentenchant:item/condensed_experience_crystal'}});
const mask=[
'................','.......##.......','......####......','.....######.....','....########....',
'....########....','...##########...','...##########...','....########....','....########.#..',
'....###########.','.....#########..','.....########...','......######....','.......###......','................'
];
png('assets/differentenchant/textures/item/condensed_experience_crystal.png',16,16,(x,y)=>{
  if(mask[y][x]!== '#'){if((x===12&&y===2)||(x===13&&y===1)||(x===14&&y===2)||(x===13&&y===3))return color('eee999');return[0,0,0,0];}
  const edge=[[1,0],[-1,0],[0,1],[0,-1]].some(([a,b])=>mask[y+b]?.[x+a]!=='#');
  return color(edge?'205846':x<=5?'3c9862':x<=7?(y<7?'e6ffb3':'a8dc66'):x<=9?(y<10?'a9ef70':'71b747'):y<8?'66bd63':'378354');
});
png('assets/differentenchant/textures/entity/villager/profession/enchanter.png',64,64,(x,y)=>{
  const robe=y>=44&&y<64&&x<28,body=y>=26&&y<38&&x>=16&&x<44;
  const sleeve=x>=44&&x<60&&y>=26&&y<34,fold=x>=40&&x<64&&y>=42&&y<46;
  const top=x>=6&&x<22&&y>=38&&y<44;
  if(!(robe||body||sleeve||fold||top))return[0,0,0,0];
  let h=(x+3*y)%13===0?'b6a6d1':(x%7===0?'8c79ac':'a08cbe');
  if(robe){
    const side=x<6||x>=14&&x<20;h=side?(x%3===0?'827098':'9280ab'):(x+y)%11===0?'b8a7d0':'ab98c7';
    if(y===54)h='65577e';if(y===62||y===45&&x>=6&&x<14)h='d5c08b';
    if(x>=6&&x<14&&(x===7||x===12)&&y>=46&&y<=52)h='c5af7f';
    if((x===9||x===10)&&y===55)h='e4d29b';
    if((x===9&&y===48)||(x===8&&y===49)||(x===10&&y===49)||(x===9&&y===50))h='e4d29b';
    if(y>=59&&x%6===2)h='78678e';
  }
  if(sleeve&&y===32)h='cdb886';return color(h);
});
json('assets/differentenchant/textures/entity/villager/profession/enchanter.png.mcmeta',{villager:{hat:'none'}});
fs.mkdirSync(root+'assets/differentenchant/textures/entity/zombie_villager/profession',{recursive:true});
fs.copyFileSync(root+'assets/differentenchant/textures/entity/villager/profession/enchanter.png',root+'assets/differentenchant/textures/entity/zombie_villager/profession/enchanter.png');
json('assets/differentenchant/textures/entity/zombie_villager/profession/enchanter.png.mcmeta',{villager:{hat:'none'}});
for(const lang of ['en_us','zh_cn']){const p='assets/differentenchant/lang/'+lang+'.json';let j=JSON.parse(fs.readFileSync(root+p));const zh=lang==='zh_cn';Object.assign(j,{
 'entity.differentenchant.villager.enchanter':zh?'附魔师':'Enchanter',
 'item.differentenchant.condensed_experience_crystal':zh?'凝萃经验结晶':'Condensed Experience Crystal',
 'item.differentenchant.condensed_experience_crystal.help':zh?'右键立即获得 %s 点经验。':'Use to instantly gain %s experience points.',
 'trade.differentenchant.any_book':zh?'接受任意存有附魔的附魔书，包括诅咒。':'Accepts any non-empty enchanted book, including curses.'
});json(p,j);}
fs.writeFileSync('src/main/java/dev/differentenchant/enchanter/EnchanterBalance.java','package dev.differentenchant.enchanter;\n\n/** Generated from tools/enchanter-definitions.mjs, the initial test balance. */\npublic final class EnchanterBalance {\n    public static final int CRYSTAL_XP='+d.crystalXP+';\n    public static final int BOTTLE_PRICE='+d.bottle.emeralds+', BOTTLE_COUNT='+d.bottle.count+', BOTTLE_STOCK='+d.bottle.stock+';\n    public static final int RECYCLING_STOCK='+d.recycling.stock+', RECYCLING_XP='+d.recycling.xp+';\n    public static final int CRYSTAL_PRICE='+d.crystal.emeralds+', CRYSTAL_STOCK='+d.crystal.stock+';\n    private EnchanterBalance(){}\n}\n');
console.log('Generated native 26.1 trades/sets, POI tag, 16px crystal, 64px original profession overlay and balance constants.');
