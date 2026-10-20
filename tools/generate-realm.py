"""Reproducible data pack and 16px provisional vanilla-derived palette assets.
Run with Python 3 + Pillow. Reads the locally cached, official 26.1.2 client JAR.
Does not modify any vanilla dimension, biome or noise settings.
"""
from pathlib import Path
import json, zipfile, io
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
VANILLA = zipfile.ZipFile(Path.home() / '.gradle/caches/fabric-loom/26.1.2/minecraft-client.jar')

def write(path, value):
    p = RES / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf8')

def data(path, value): write('data/differentenchant/' + path + '.json', value)
def asset(path, value): write('assets/differentenchant/' + path + '.json', value)
def state(name): return {'Name': 'differentenchant:' + name}
def noise(name, xz=1): return {'type':'minecraft:noise','noise':'differentenchant:'+name,'xz_scale':xz,'y_scale':0}
def op(kind,a,b): return {'type':'minecraft:'+kind,'argument1':a,'argument2':b}
def add(a,b): return op('add',a,b)
def mul(a,b): return op('mul',a,b)
def unary(kind,a): return {'type':'minecraft:'+kind,'argument':a}
def gradient(y1,y2,v1,v2): return {'type':'minecraft:y_clamped_gradient','from_y':y1,'to_y':y2,'from_value':v1,'to_value':v2}

for name, octave, amps in [('land',-9,[1,.6,.3]),('hills',-6,[1,.5,.25]),('ridges',-8,[1,.6,.3]),('river',-7,[1,.5]),('islands',-5,[1,.5]),('climate',-8,[1,.5])]:
    data('worldgen/noise/'+name,{'firstOctave':octave,'amplitudes':amps})

# Height field: broad rolling land + smaller hills + squared mountain ridges.
# Rivers cut down to a separate gently varying 60-65m channel level.
height=add(88,add(mul(48,noise('land')),add(mul(22,noise('hills')),mul(105,unary('square',noise('ridges'))))))
channel=add(56,mul(3,noise('land')))
river=op('min',height,add(channel,mul(160,unary('abs',noise('river')))))
data('worldgen/density_function/land_height',unary('flat_cache',river))
mainland=mul(.035,add('differentenchant:land_height',gradient(-64,320,64,-320)))
# A second density lobe, not decoration: full cross-chunk native island silhouettes.
# High threshold makes these isolated exceptional outcrops rather than an island world.
islands=add(mul(4,add(noise('islands',.65),-.64)),mul(-1,unary('square',add(gradient(-64,320,-254/10,130/10),mul(.3,noise('hills'))))))
final=unary('interpolated',op('max',mainland,islands))
surface={'type':'minecraft:sequence','sequence':[
    {'type':'minecraft:condition','if_true':{'type':'minecraft:vertical_gradient','random_name':'minecraft:bedrock_floor','true_at_and_below':{'above_bottom':0},'false_at_and_above':{'above_bottom':5}},'then_run':{'type':'minecraft:block','result_state':{'Name':'minecraft:bedrock'}}},
    {'type':'minecraft:condition','if_true':{'type':'minecraft:stone_depth','offset':0,'add_surface_depth':False,'secondary_depth_range':0,'surface_type':'floor'},'then_run':{'type':'minecraft:condition','if_true':{'type':'minecraft:water','offset':-1,'surface_depth_multiplier':0,'add_stone_depth':False},'then_run':{'type':'minecraft:block','result_state':state('mint_turf')}}},
    {'type':'minecraft:condition','if_true':{'type':'minecraft:stone_depth','offset':3,'add_surface_depth':False,'secondary_depth_range':0,'surface_type':'floor'},'then_run':{'type':'minecraft:block','result_state':state('realm_loam')}}
]}
surface['sequence'].insert(1,{'type':'minecraft:condition','if_true':{'type':'minecraft:y_above','anchor':{'absolute':122},'surface_depth_multiplier':0,'add_stone_depth':False},'then_run':{'type':'minecraft:condition','if_true':{'type':'minecraft:noise_threshold','noise':'differentenchant:hills','min_threshold':.12,'max_threshold':2.},'then_run':{'type':'minecraft:block','result_state':state('realm_chalkstone')}}})
router={k:0 for k in ['barrier','fluid_level_floodedness','fluid_level_spread','lava','temperature','vegetation','continents','erosion','depth','ridges','initial_density_without_jaggedness','final_density','vein_toggle','vein_ridged','vein_gap']}
router.pop('initial_density_without_jaggedness')
router.update(temperature=noise('land'),vegetation=noise('climate'),continents=noise('land'),erosion=noise('hills'),depth=mainland,ridges=noise('ridges'),preliminary_surface_level='differentenchant:land_height',final_density=final)
data('worldgen/noise_settings/enchanter_realm',{'aquifers_enabled':False,'default_block':state('realm_chalkstone'),'default_fluid':{'Name':'minecraft:water','Properties':{'level':'0'}},'disable_mob_generation':True,'legacy_random_source':False,'noise':{'height':384,'min_y':-64,'size_horizontal':1,'size_vertical':2},'noise_router':router,'ore_veins_enabled':False,'sea_level':63,'spawn_target':[],'surface_rule':surface})
dimension=json.loads(VANILLA.read('data/minecraft/dimension_type/overworld.json'))
dimension.pop('default_clock',None)
dimension.update(has_fixed_time=True,timelines=[],ambient_light=.16)
dimension['attributes']={
    'minecraft:visual/sky_color':'#88c9e0','minecraft:visual/fog_color':'#c9c7e7',
    'minecraft:visual/cloud_color':'#cce9e6f7','minecraft:visual/cloud_height':224,
    'minecraft:visual/ambient_light_color':'#172b30','minecraft:visual/sky_light_color':'#effffa',
    'minecraft:visual/sky_light_factor':1.,'minecraft:gameplay/sky_light_level':15.,
    'minecraft:visual/sun_angle':30.,'minecraft:visual/moon_angle':210.,
    'minecraft:visual/star_brightness':0.,'minecraft:visual/sunrise_sunset_color':'#00000000',
    'minecraft:visual/water_fog_color':'#4d999e','minecraft:visual/water_fog_end_distance':64.,
    'minecraft:gameplay/can_start_raid':False,
    'minecraft:gameplay/bed_rule':{'can_set_spawn':'never','can_sleep':'never','error_message':{'text':'The Realm has no night. Use the development return command.'}}
}
data('dimension_type/enchanter_realm',dimension)
climates=[]
for name, humidity in [('mint_fields',[-1.,0.05]),('azure_woodland',[.05,1.])]:
    climates.append({'biome':'differentenchant:'+name,'parameters':{'temperature':[-1.,1.],'humidity':humidity,'continentalness':[-1.,1.],'erosion':[-1.,1.],'depth':[-1.,1.],'weirdness':[-1.,1.],'offset':0.}})
data('dimension/enchanter_realm',{'type':'differentenchant:enchanter_realm','generator':{'type':'minecraft:noise','settings':'differentenchant:enchanter_realm','biome_source':{'type':'minecraft:multi_noise','biomes':climates}}})

data('worldgen/configured_feature/azure_tree',{'type':'differentenchant:azure_tree','config':{}})
for name,count in [('field_trees',1),('woodland_trees',7)]:
    placement=[{'type':'minecraft:count','count':count},{'type':'minecraft:in_square'},{'type':'minecraft:heightmap','heightmap':'MOTION_BLOCKING_NO_LEAVES'},{'type':'minecraft:biome'}]
    if count==1: placement.insert(0,{'type':'minecraft:rarity_filter','chance':3})
    data('worldgen/placed_feature/'+name,{'feature':'differentenchant:azure_tree','placement':placement})
for name,block,tries,count in [('meadow_grass','short_grass',32,4),('meadow_flowers','allium',8,1),('ivory_flowers','oxeye_daisy',6,1),('pink_flowers','pink_tulip',4,1)]:
    data('worldgen/configured_feature/'+name,{'type':'minecraft:simple_block','config':{'to_place':{'type':'minecraft:simple_state_provider','state':{'Name':'minecraft:'+block}}}})
    data('worldgen/placed_feature/'+name,{'feature':'differentenchant:'+name,'placement':[{'type':'minecraft:count','count':count*tries},{'type':'minecraft:in_square'},{'type':'minecraft:heightmap','heightmap':'WORLD_SURFACE_WG'},{'type':'minecraft:biome'},{'type':'minecraft:block_predicate_filter','predicate':{'type':'minecraft:matching_blocks','blocks':'minecraft:air'}}]})
for name,tree,grass in [('mint_fields','field_trees','#b3d59c'),('azure_woodland','woodland_trees','#a0c8a1')]:
    features=[[] for _ in range(11)]
    features[9]=['differentenchant:'+n for n in [tree,'meadow_grass','meadow_flowers','ivory_flowers','pink_flowers']]
    data('worldgen/biome/'+name,{'has_precipitation':False,'temperature':.65,'downfall':.6,'effects':{'water_color':'#63bfc2','grass_color':grass,'foliage_color':'#398e83','dry_foliage_color':'#c2ce91'},'carvers':[],'features':features,'spawners':{},'spawn_costs':{},'attributes':{}})

# Provisional pixel texture recolors retain vanilla's pixel grain; no generated art.
def texture(name,source,rgb,contrast=.55):
    im=Image.open(io.BytesIO(VANILLA.read('assets/minecraft/textures/block/'+source+'.png'))).convert('RGBA')
    pixels=[]
    for r,g,b,a in im.get_flattened_data():
        light=(r*.299+g*.587+b*.114)/128
        k=1+(light-1)*contrast
        pixels.append(tuple(max(0,min(255,round(v*k))) for v in rgb)+(a,))
    im.putdata(pixels)
    p=RES/'assets/differentenchant/textures/block'/f'{name}.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
texture('realm_chalkstone','stone',(153,168,176),.6)
texture('realm_loam','dirt',(123,112,130),.6)
texture('mint_turf_top','grass_block_top',(143,201,169),.5)
texture('mint_turf_side','grass_block_side',(123,112,130),.45)
texture('azure_log','birch_log',(116,168,173),.8)
texture('azure_log_top','birch_log_top',(151,191,184),.6)
texture('azure_leaves','oak_leaves',(46,112,106),.65)
# Restore a proper shallow grass fringe on the side, with loam visible below.
side=Image.open(RES/'assets/differentenchant/textures/block/mint_turf_side.png')
top=Image.open(RES/'assets/differentenchant/textures/block/mint_turf_top.png')
for x in range(16):
    for y in range(2+(x*7%3)): side.putpixel((x,y),top.getpixel((x,y)))
side.save(RES/'assets/differentenchant/textures/block/mint_turf_side.png')
for name in ['realm_chalkstone','realm_loam','mint_turf','azure_log','azure_leaves']:
    if name=='mint_turf':
        model={'parent':'minecraft:block/cube_bottom_top','textures':{'top':'differentenchant:block/mint_turf_top','bottom':'differentenchant:block/realm_loam','side':'differentenchant:block/mint_turf_side'}}
    elif name=='azure_log':
        model={'parent':'minecraft:block/cube_column','textures':{'end':'differentenchant:block/azure_log_top','side':'differentenchant:block/azure_log'}}
    else: model={'parent':'minecraft:block/'+('leaves' if name=='azure_leaves' else 'cube_all'),'textures':{'all':'differentenchant:block/'+name}}
    asset('models/block/'+name,model)
    if name=='azure_log':
        variants={'axis=y':{'model':'differentenchant:block/azure_log'},'axis=x':{'model':'differentenchant:block/azure_log','x':90,'y':90},'axis=z':{'model':'differentenchant:block/azure_log','x':90}}
    else: variants={'':{'model':'differentenchant:block/'+name}}
    asset('blockstates/'+name,{'variants':variants})
    asset('items/'+name,{'model':{'type':'minecraft:model','model':'differentenchant:block/'+name}})
    data('loot_table/blocks/'+name,{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'differentenchant:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for tag,values in {'dirt':['realm_loam','mint_turf'],'logs':['azure_log'],'logs_that_burn':['azure_log'],'leaves':['azure_leaves'],'mineable/axe':['azure_log'],'mineable/pickaxe':['realm_chalkstone'],'mineable/shovel':['mint_turf','realm_loam']}.items():
    p=RES/'data/minecraft/tags/block'/f'{tag}.json'
    value=json.loads(p.read_text('utf8')) if p.exists() else {'replace':False,'values':[]}
    for v in values:
        if 'differentenchant:'+v not in value['values']:value['values'].append('differentenchant:'+v)
    write('data/minecraft/tags/block/'+tag+'.json',value)
for lang,names in [('en_us',['Realm Chalkstone','Realm Loam','Mint Turf','Azure Log (Prototype)','Azure Leaves (Prototype)','Mint Fields','Azure Woodland',"Enchanter’s Realm"]),('zh_cn',['境域灰岩','境域沃土','薄荷草方块','青木原木（原型）','青木树叶（原型）','青绿原野','青林','附魔者维度'])]:
    p=RES/'assets/differentenchant/lang'/f'{lang}.json';value=json.loads(p.read_text('utf8'))
    keys=['block.differentenchant.'+n for n in ['realm_chalkstone','realm_loam','mint_turf','azure_log','azure_leaves']]+['biome.differentenchant.mint_fields','biome.differentenchant.azure_woodland','dimension.differentenchant.enchanter_realm']
    value.update(zip(keys,names));write('assets/differentenchant/lang/'+lang+'.json',value)

if __name__=='__main__':print('Generated Realm data and provisional 16px vanilla-derived textures.')
