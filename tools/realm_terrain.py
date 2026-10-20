"""Native 26.1.2 mainland + correlated rift + lifted-plate definitions.

The same 2D plate field cuts the source hole and outlines its raised core.
Only the core gets a finite underside; the ordinary mainland stays grounded.
"""

def build(data, state):
    def noise(name, xz=1., y=0.):
        return {'type':'minecraft:noise','noise':'differentenchant:'+name,'xz_scale':xz,'y_scale':y}
    def binary(kind,a,b):return {'type':'minecraft:'+kind,'argument1':a,'argument2':b}
    def add(a,b):return binary('add',a,b)
    def mul(a,b):return binary('mul',a,b)
    def minimum(a,b):return binary('min',a,b)
    def maximum(a,b):return binary('max',a,b)
    def unary(kind,a):return {'type':'minecraft:'+kind,'argument':a}
    def clamp(a,lo,hi):return {'type':'minecraft:clamp','input':a,'min':lo,'max':hi}
    def gradient(a,b,x,y):return {'type':'minecraft:y_clamped_gradient','from_y':a,'to_y':b,'from_value':x,'to_value':y}
    def cache(name,value):
        data('worldgen/density_function/'+name,unary('flat_cache',value));return 'differentenchant:'+name
    def choose(value,lo,hi,inside,outside):
        return {'type':'minecraft:range_choice','input':value,'min_inclusive':lo,'max_exclusive':hi,'when_in_range':inside,'when_out_of_range':outside}

    for name,octave,amps in [('land',-9,[1,.5,.25]),('hills',-7,[1,.5]),('ridges',-8,[1,.5]),
                             ('river',-7,[1,.4]),('climate',-9,[1,.5]),('uplift',-8,[1,.45,.2]),
                             ('underside',-6,[1,.5,.25]),('fracture',-4,[1,.5]),('debris',-4,[1,.4])]:
        data('worldgen/noise/'+name,{'firstOctave':octave,'amplitudes':amps})

    field=cache('rift_field',noise('uplift'))
    ground_mask=cache('mainland_mask',add(.18,mul(-1,field)))
    lift_mask=cache('lift_mask',add(field,-.30))
    valley=mul(-30,clamp(add(.16,mul(-1,unary('abs',noise('river')))),0,.16))
    source=cache('land_height',add(118,add(valley,add(mul(18,noise('land')),add(mul(11,noise('hills')),mul(26,unary('square',noise('ridges'))))))))
    lifted=cache('lifted_height',add(source,92))
    thickness=cache('lifted_thickness',clamp(add(mul(2800,lift_mask),mul(12,noise('underside',.55))),0,62))
    bottom=cache('lifted_bottom',add(lifted,mul(-1,thickness)))
    y=gradient(-64,320,-64,320)
    # Outside the local rupture the mainland extends all the way to world bottom.
    # Inside it the entire original vertical column is removed, including under cores.
    mainland=minimum(mul(5,ground_mask),mul(.055,add(source,mul(-1,y))))
    upper=mul(.055,add(lifted,mul(-1,y)))
    # Beds use parent-space Y: raised rock retains the same geological sequence.
    parent_y=add(y,-92)
    bedding=choose(parent_y,60,65,-.18,choose(parent_y,76,81,-.15,choose(parent_y,92,97,-.12,.04)))
    torn=add(mul(.44,noise('underside',1.,1.8)),add(bedding,mul(.16,noise('fracture',1.,2.))))
    torn=choose(add(lifted,mul(-1,y)),0,16,0,torn)
    lower=add(mul(.045,add(y,mul(-1,bottom))),torn)
    plate=minimum(mul(5,lift_mask),minimum(upper,lower))
    # Broken ledges grow from the outer fault wall, but stop before the island core.
    rim=minimum(mul(3,add(field,-.165)),mul(3,add(.235,mul(-1,field))))
    ledges=minimum(rim,minimum(add(noise('fracture',.45),-.20),minimum(
        mul(.07,add(add(source,-8),mul(-1,y))),mul(.07,add(y,add(28,mul(-1,source)))))))
    # Sparse debris chains follow the same fault belt; no independent lower floor.
    belt=minimum(mul(3,add(field,-.18)),mul(3,add(.36,mul(-1,field))))
    shards=minimum(belt,minimum(add(noise('debris',1.1,1.3),-.68),minimum(mul(.05,add(y,-36)),mul(.05,add(166,mul(-1,y))))))
    final=unary('interpolated',maximum(mainland,maximum(plate,maximum(ledges,shards))))

    def block(name):return {'type':'minecraft:block','result_state':state(name)}
    def condition(test,result):return {'type':'minecraft:condition','if_true':test,'then_run':result}
    def above(height):return {'type':'minecraft:y_above','anchor':{'absolute':height},'surface_depth_multiplier':0,'add_stone_depth':False}
    def depth(offset):return {'type':'minecraft:stone_depth','offset':offset,'add_surface_depth':False,'secondary_depth_range':0,'surface_type':'floor'}
    def band(lo,hi):return {'type':'minecraft:noise_threshold','noise':'differentenchant:uplift','min_threshold':lo,'max_threshold':hi}
    def strata(shift):
        rules=[]
        for height,material in [(104,'realm_chalkstone'),(92,'realm_slate'),(84,'realm_chalkstone'),
                                (72,'realm_slate'),(60,'realm_chalkstone'),(48,'realm_slate'),(40,'realm_chalkstone')]:
            rules.append(condition(above(height+shift),block(material)))
        rules.append(block('realm_slate'))
        return {'type':'minecraft:sequence','sequence':rules}
    soil=lambda minimum_y:{'type':'minecraft:sequence','sequence':[
        condition(above(minimum_y),condition({'type':'minecraft:steep'},block('realm_chalkstone'))),
        condition(above(minimum_y),condition(depth(0),block('mint_turf'))),
        condition(above(minimum_y-3),condition(depth(2),block('realm_loam')))]}
    surface={'type':'minecraft:sequence','sequence':[
        condition({'type':'minecraft:not','invert':above(-60)}, {'type':'minecraft:block','result_state':{'Name':'minecraft:bedrock'}}),
        condition(band(-2,.18),soil(86)),condition(band(.30,2),soil(178)),
        condition(band(.30,2),strata(92)),strata(0)]}
    router={k:0 for k in ['barrier','fluid_level_floodedness','fluid_level_spread','lava','temperature','vegetation',
                         'continents','erosion','depth','ridges','preliminary_surface_level','final_density','vein_toggle','vein_ridged','vein_gap']}
    router.update(temperature=noise('land'),vegetation=noise('climate'),continents=ground_mask,erosion=noise('hills'),
                  depth=upper,ridges=noise('ridges'),preliminary_surface_level=choose(field,.30,2,lifted,source),final_density=final)
    data('worldgen/noise_settings/enchanter_realm',{
        'aquifers_enabled':False,'default_block':state('realm_chalkstone'),'default_fluid':{'Name':'minecraft:air'},
        'disable_mob_generation':True,'legacy_random_source':False,
        'noise':{'height':384,'min_y':-64,'size_horizontal':1,'size_vertical':1},'noise_router':router,
        'ore_veins_enabled':False,'sea_level':-64,'spawn_target':[],'surface_rule':surface})
