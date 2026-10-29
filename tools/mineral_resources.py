"""Three Realm minerals. Original deterministic pixel art; no source textures read."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

def build(root):
    res=root/'src/main/resources'
    def write(path,value):
        p=res/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
    def data(path,value):write('data/differentenchant/'+path+'.json',value)
    def asset(path,value):write('assets/differentenchant/'+path+'.json',value)
    def png(kind,name,im):
        p=res/f'assets/differentenchant/textures/{kind}/{name}.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
    data('tags/enchantment/mines_luminous_stone',{'replace':False,'values':['minecraft:'+n for n in ['protection','fire_protection','blast_protection','projectile_protection','feather_falling']]})
    data('tags/enchantment/mines_harmonic_silver',{'replace':False,'values':['minecraft:'+n for n in ['flame','fire_aspect','fire_protection']]})
    data('tags/block/realm_ore_replaceables',{'replace':False,'values':['differentenchant:realm_chalkstone','differentenchant:realm_slate']})
    for name,base in [('luminous_stone',(171,194,202)),('harmonic_silver_ore',(79,96,117))]:
        im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
        for y in range(16):
            for x in range(16):
                k=((x*73+y*127+x*y*19)%17)-8;im.putpixel((x,y),tuple(v+k for v in base)+(255,))
        if name=='luminous_stone':
            for line in [[(0,4),(4,5),(6,3),(9,4)],[(5,15),(7,11),(11,10),(13,7),(15,8)]]:
                d.line(line,fill=(139,157,195),width=1)
            for x,y in [(3,4),(7,3),(8,11),(12,9)]:d.point((x,y),fill=(201,216,225))
        else:
            for x,y in [(2,3),(9,2),(6,8),(12,11),(2,13)]:
                d.polygon([(x,y),(x+2,y-1),(x+3,y+1),(x+1,y+2)],fill=(169,189,204))
                d.point((x+1,y),fill=(217,222,227));d.point((x+2,y+1),fill=(137,134,180));d.point((x,y+1),fill=(103,167,180))
        png('block',name,im)
        asset('models/block/'+name,{'parent':'minecraft:block/cube_all','textures':{'all':'differentenchant:block/'+name}})
        asset('blockstates/'+name,{'variants':{'':{'model':'differentenchant:block/'+name}}})
        asset('items/'+name,{'model':{'type':'minecraft:model','model':'differentenchant:block/'+name}})
    im=Image.new('RGBA',(16,16),(135,126,180,255));d=ImageDraw.Draw(im)
    d.polygon([(0,0),(7,0),(5,15),(0,15)],fill=(121,157,188));d.polygon([(7,0),(12,0),(10,15),(5,15)],fill=(177,174,214));d.polygon([(12,0),(15,0),(15,15),(10,15)],fill=(91,105,155))
    d.line([(7,0),(5,15)],fill=(202,211,231));d.line([(13,2),(11,12)],fill=(151,146,201));d.point((4,3),fill=(206,213,230))
    png('block','rift_crystal_facets',im)
    elements=[]
    for a,b,rot in [([3,0,3],[12,1,12],None),([6,1,6],[10,10,9],None),([7,10,6.5],[9,12,8.5],None),
                    ([3,1,4],[6,8,6],{'origin':[5,1,5],'axis':'z','angle':22.5}),
                    ([10,1,9],[12,7,12],{'origin':[11,1,10],'axis':'x','angle':-22.5}),
                    ([4,0,10],[6,3,12],None)]:
        e={'from':a,'to':b,'faces':{face:{'uv':[0,0,16,16],'texture':'#crystal'} for face in ['up','down','north','south','east','west']}}
        if rot:e['rotation']=rot
        elements.append(e)
    asset('models/block/rift_crystal_cluster',{'parent':'minecraft:block/block','ambientocclusion':False,'textures':{'crystal':'differentenchant:block/rift_crystal_facets','particle':'differentenchant:block/rift_crystal_facets'},'elements':elements})
    rotations={'up':{},'down':{'x':180},'north':{'x':90},'south':{'x':90,'y':180},'east':{'x':90,'y':90},'west':{'x':90,'y':270}}
    asset('blockstates/rift_crystal_cluster',{'variants':{'facing='+face:{'model':'differentenchant:block/rift_crystal_cluster',**r} for face,r in rotations.items()}})
    asset('items/rift_crystal_cluster',{'model':{'type':'minecraft:model','model':'differentenchant:block/rift_crystal_cluster'}})
    for name,poly,base in [('luminous_fragment',[(3,5),(9,2),(13,7),(11,12),(5,14),(2,9)],(172,209,214)),
                           ('raw_harmonic_silver',[(2,7),(5,3),(9,4),(12,3),(14,9),(10,13),(4,12)],(143,162,181)),
                           ('rift_crystal',[(5,5),(8,1),(11,5),(10,11),(7,15),(4,11)],(155,145,203))]:
        im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im);d.polygon(poly,fill=base,outline=(76,98,132));
        d.polygon([(6,5),(8,3),(9,6),(8,12),(6,10)],fill=tuple(min(235,v+32) for v in base));d.line([(9,5),(10,8),(8,13)],fill=(105,124,172));png('item',name,im)
        asset('models/item/'+name,{'parent':'minecraft:item/generated','textures':{'layer0':'differentenchant:item/'+name}})
        asset('items/'+name,{'model':{'type':'minecraft:model','model':'differentenchant:item/'+name}})
    for name in ['luminous_stone','harmonic_silver_ore','rift_crystal_cluster']:
        data('loot_table/blocks/'+name,{'type':'minecraft:block','pools':[]})
    for name,size,discard in [('luminous_stone',7,.25),('harmonic_silver_ore',5,.15)]:
        data('worldgen/configured_feature/'+name,{'type':'differentenchant:realm_ore','config':{'size':size,'discard_chance_on_air_exposure':discard,'targets':[{'target':{'predicate_type':'minecraft:tag_match','tag':'differentenchant:realm_ore_replaceables'},'state':{'Name':'differentenchant:'+name}}]}})
    for name,count,lo,hi,ore in [('luminous_mainland',12,-48,112,'luminous_stone'),('luminous_islands',7,136,224,'luminous_stone'),('silver_deep',6,-48,48,'harmonic_silver_ore'),('silver_islands',4,128,204,'harmonic_silver_ore')]:
        data('worldgen/placed_feature/'+name,{'feature':'differentenchant:'+ore,'placement':[{'type':'minecraft:count','count':count},{'type':'minecraft:in_square'},{'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':lo},'max_inclusive':{'absolute':hi}}},{'type':'minecraft:biome'}]})
    data('worldgen/configured_feature/rift_crystals',{'type':'differentenchant:rift_crystals','config':{}})
    data('worldgen/placed_feature/rift_crystals',{'feature':'differentenchant:rift_crystals','placement':[{'type':'minecraft:rarity_filter','chance':3},{'type':'minecraft:in_square'},{'type':'minecraft:biome'}]})
    for biome in ['mint_fields','azure_woodland']:
        path=res/f'data/differentenchant/worldgen/biome/{biome}.json';value=json.loads(path.read_text('utf8'))
        for stage,names in [(6,['luminous_mainland','luminous_islands','silver_deep','silver_islands']),(7,['rift_crystals'])]:
            for n in names:
                if 'differentenchant:'+n not in value['features'][stage]:value['features'][stage].append('differentenchant:'+n)
        write(path.relative_to(res).as_posix(),value)
    for lang,names in [('en_us',['Luminous Stone','Harmonic Silver Ore','Rift Crystal Cluster','Luminous Fragment','Raw Harmonic Silver','Rift Crystal']),('zh_cn',['浮辉石','谐银矿','裂空晶簇','浮辉石碎片','谐银原矿','裂空晶体'])]:
        path=res/f'assets/differentenchant/lang/{lang}.json';value=json.loads(path.read_text('utf8'))
        keys=['block.differentenchant.'+n for n in ['luminous_stone','harmonic_silver_ore','rift_crystal_cluster']]+['item.differentenchant.'+n for n in ['luminous_fragment','raw_harmonic_silver','rift_crystal']]
        value.update(zip(keys,names));write(path.relative_to(res).as_posix(),value)
    print('Generated three mineral definitions, original pixel textures, gates and bounded Realm features.')

if __name__=='__main__':build(Path(__file__).resolve().parents[1])
