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
    def clamp(a,lo,hi):
        # 26.1.2 Clamp uses DIRECT_CODEC for input; wrap holder references in an op.
        if isinstance(a,str):a=add(a,0)
        return {'type':'minecraft:clamp','input':a,'min':lo,'max':hi}
    def gradient(a,b,x,y):return {'type':'minecraft:y_clamped_gradient','from_y':a,'to_y':b,'from_value':x,'to_value':y}
    def cache(name,value):
        data('worldgen/density_function/'+name,unary('flat_cache',value));return 'differentenchant:'+name
    def choose(value,lo,hi,inside,outside):
        return {'type':'minecraft:range_choice','input':value,'min_inclusive':lo,'max_exclusive':hi,'when_in_range':inside,'when_out_of_range':outside}

    for name,octave,amps in [('land',-9,[1,.5,.25]),('hills',-7,[1,.5]),('ridges',-8,[1,.5]),
                             ('river',-7,[1,.4]),('climate',-9,[1,.5]),('uplift',-8,[1,.45,.2]),
                             ('underside',-6,[1,.5,.25]),('fracture',-4,[1,.5]),('debris',-4,[1,.4]),
                             ('edge_width',-7,[1,.5]),('shelf_extent',-7,[1,.5]),('bed_warp',-7,[1,.5])]:
        data('worldgen/noise/'+name,{'firstOctave':octave,'amplitudes':amps})

    field=cache('rift_field',noise('uplift'))
    ground_mask=cache('mainland_mask',add(.18,mul(-1,field)))
    lift_mask=cache('lift_mask',add(field,-.30))
    valley=mul(-30,clamp(add(.16,mul(-1,unary('abs',noise('river')))),0,.16))
    source=cache('land_height',add(118,add(valley,add(mul(18,noise('land')),add(mul(11,noise('hills')),mul(26,unary('square',noise('ridges'))))))))
    y=gradient(-64,320,-64,320)
    # Only the distance primitive is custom. Its raw noise input has NO cache
    # marker: sampling a neighbor through a chunk-local cache could create seams.
    # The result is cached outside the primitive on the native global 4-block grid.
    def edge_distance(input):return {'type':'differentenchant:fault_edge_distance','input':input,'radius':256}
    ground_distance=cache('mainland_edge_distance',edge_distance(add(.18,mul(-1,noise('uplift')))))
    lift_distance=cache('lifted_edge_distance',edge_distance(add(noise('uplift'),-.30)))
    rate=cache('fault_slope_rate',clamp(mul(1/176,add(1,mul(.7,noise('edge_width',.7)))),.0042,.0082))
    center_a=cache('bed_center_a',add(74,mul(20,noise('bed_warp',.8))))
    center_b=cache('bed_center_b',add(40,mul(22,noise('bed_warp',.5))))
    extent_a=cache('shelf_extent_a',mul(32,clamp(mul(2,noise('shelf_extent')),-.8,.9)))
    extent_b=cache('shelf_extent_b',mul(-24,clamp(add(.3,mul(1.8,noise('shelf_extent',.6))),0,1)))
    def shelves(parent_y,detail):
        def bed(center,radius):
            t=clamp(add(1,mul(-1/radius,unary('abs',add(parent_y,mul(-1,center))))),0,1)
            # Smooth compact lobes, not repeating constant-height stair rings.
            return mul(unary('square',t),add(3,mul(-2,t)))
        return add(mul(extent_a,bed(center_a,15)),add(mul(extent_b,bed(center_b,21)),detail))
    def volume(name,value):
        data('worldgen/density_function/'+name,unary('cache_once',value));return 'differentenchant:'+name
    mainland_beds=volume('mainland_shelves',shelves(y,mul(2.5,noise('fracture',.7,.9))))
    def mainland_top(distance):
        ramp=clamp(add(1,mul(-1,mul(distance,rate))),0,1)
        # At the old contour the surface is below world bottom; the old vertical
        # clipping plane therefore cannot turn into another hundred-block wall.
        return add(source,mul(-1,mul(add(source,80),add(mul(.6,ramp),mul(.4,unary('square',ramp))))))
    ground_top=cache('mainland_surface',mainland_top(ground_distance))
    relief=clamp(add(mainland_top(add(ground_distance,mainland_beds)),mul(-1,ground_top)),-14,14)
    mainland=minimum(mul(5,ground_mask),mul(.055,add(add(ground_top,relief),mul(-1,y))))
    mainland=choose(field,-2,.18,choose(ground_distance,248,1000000,mul(.055,add(source,mul(-1,y))),mainland),mul(5,ground_mask))
    lifted=cache('lifted_height',add(source,92))
    edge_fall=mul(26,unary('square',clamp(add(1,mul(-1/64,lift_distance)),0,1)))
    island_top=cache('lifted_surface',add(lifted,mul(-1,edge_fall)))
    max_thickness=cache('lifted_mass_depth',add(62,mul(14,noise('underside',.55))))
    thickness=cache('lifted_thickness',minimum(clamp(mul(.78,lift_distance),0,90),max_thickness))
    bottom=cache('lifted_bottom',add(island_top,mul(-1,thickness)))
    # Shared parent-space beds respond at different strengths on the freed plate;
    # their amplitude vanishes at the rim, keeping a thin lip instead of a wall.
    parent_y=add(y,-92)
    island_beds=volume('lifted_shelves',shelves(parent_y,mul(3.5,noise('underside',.9,1.3))))
    body_depth=minimum(max_thickness,maximum(0,add(thickness,mul(.60,mul(island_beds,clamp(mul(1/48,lift_distance),0,1))))))
    body_depth=choose(lift_distance,192,1000000,max_thickness,body_depth)
    upper=mul(.055,add(island_top,mul(-1,y)))
    torn=mul(.22,noise('underside',1.,1.8))
    torn=choose(add(island_top,mul(-1,y)),0,16,0,torn)
    lower=add(mul(.045,add(y,add(body_depth,mul(-1,island_top)))),torn)
    plate=choose(field,.30,2,minimum(mul(5,lift_mask),minimum(upper,lower)),minimum(mul(5,lift_mask),upper))
    # Only sparse original fault-belt debris remains as a separate solid term.
    # Medium ledges are part of the above density, never pasted onto a cut wall.
    belt=minimum(mul(3,add(field,-.18)),mul(3,add(.36,mul(-1,field))))
    shards=minimum(belt,minimum(add(noise('debris',1.1,1.3),-.68),minimum(mul(.05,add(y,-36)),mul(.05,add(166,mul(-1,y))))))
    final=unary('interpolated',maximum(mainland,maximum(plate,shards)))

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
                  depth=upper,ridges=noise('ridges'),preliminary_surface_level=choose(field,.30,2,island_top,ground_top),final_density=final)
    data('worldgen/noise_settings/enchanter_realm',{
        'aquifers_enabled':False,'default_block':state('realm_chalkstone'),'default_fluid':{'Name':'minecraft:air'},
        'disable_mob_generation':True,'legacy_random_source':False,
        'noise':{'height':384,'min_y':-64,'size_horizontal':1,'size_vertical':1},'noise_router':router,
        'ore_veins_enabled':False,'sea_level':-64,'spawn_target':[],'surface_rule':surface})
