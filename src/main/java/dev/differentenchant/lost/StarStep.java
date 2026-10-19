package dev.differentenchant.lost;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import dev.differentenchant.research.ResearchContent;
/** A real, owner-specific collision shape; never a world block, inventory material or saved entity. */
public final class StarStep extends Display.BlockDisplay {
    public static final EntityType<StarStep> TYPE=Registry.register(BuiltInRegistries.ENTITY_TYPE,ResearchContent.id("star_step"),EntityType.Builder.<StarStep>of(StarStep::new,MobCategory.MISC).sized(.98f,.125f).noSave().noSummon().noLootTable().clientTrackingRange(4).updateInterval(1).build(ResourceKey.create(Registries.ENTITY_TYPE,ResearchContent.id("star_step"))));
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(StarStep.class,EntityDataSerializers.INT);
    private long expires;
    public StarStep(EntityType<? extends StarStep> type,Level level){super(type,level);}
    public StarStep(ServerLevel level,net.minecraft.server.level.ServerPlayer owner,double x,double top,double z){this(TYPE,level);entityData.set(OWNER,owner.getId());setPos(x,top-.125,z);expires=level.getGameTime()+40;setBlockState(net.minecraft.world.level.block.Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());setGlowColorOverride(0xa6ddff);setGlowingTag(true);setTransformation(new com.mojang.math.Transformation(new org.joml.Vector3f(-.49f,0,-.49f),new org.joml.Quaternionf(),new org.joml.Vector3f(.98f,.125f,.98f),new org.joml.Quaternionf()));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){super.defineSynchedData(data);data.define(OWNER,-1);}
    @Override public boolean canBeCollidedWith(Entity other){return other!=null&&other.getId()==entityData.get(OWNER)&&!isRemoved();}
    @Override public boolean isPushable(){return false;}
    @Override protected void readAdditionalSaveData(ValueInput input){}
    @Override protected void addAdditionalSaveData(ValueOutput output){}
    @Override public void tick(){
        super.tick();
        if(level() instanceof ServerLevel server){var owner=server.getEntity(entityData.get(OWNER));
            if(server.getGameTime()>=expires||!(owner instanceof net.minecraft.server.level.ServerPlayer p)||!p.isAlive()||owner.distanceToSqr(this)>36||BatchEffects.level(p,p.getItemBySlot(EquipmentSlot.FEET),"star_bridge")==0){discard();return;}
            if(server.getGameTime()%4==0)for(int i=0;i<4;i++){double t=i*.25;for(var offset:new double[][]{{-.49+t,.49},{.49,-.49+t},{.49-t,-.49},{-.49,.49-t}})server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,getX()+offset[0],getY()+.135,getZ()+offset[1],1,0,0,0,0);}
        }
    }
    public static void initialize(){}
}
