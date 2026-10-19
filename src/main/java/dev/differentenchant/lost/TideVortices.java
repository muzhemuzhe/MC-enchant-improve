package dev.differentenchant.lost;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.*;
/** Bounded local fields: no blocks, chunk loads, persistent actors or references to current hands. */
public final class TideVortices {
    private record Vortex(ServerLevel level,Vec3 center,int rank,long end,UUID owner){}
    private static final List<Vortex> ACTIVE=new ArrayList<>();
    private static final Set<UUID> TRIGGERED=new HashSet<>();
    public static void impact(ThrownTrident trident,Vec3 center) {
        if(!(trident.level() instanceof ServerLevel level)||TRIGGERED.contains(trident.getUUID()))return;
        int rank=BatchEffects.level(trident,trident.getWeaponItem(),"undertow");if(rank==0)return;TRIGGERED.add(trident.getUUID());
        var owner=trident.getOwner();UUID key=owner==null?trident.getUUID():owner.getUUID();
        if(ACTIVE.size()>=32)ACTIVE.removeFirst();if(ACTIVE.stream().filter(v->v.owner.equals(key)).count()>=2)ACTIVE.stream().filter(v->v.owner.equals(key)).findFirst().ifPresent(ACTIVE::remove);
        ACTIVE.add(new Vortex(level,center,rank,level.getGameTime()+40,key));level.playSound(null,center.x,center.y,center.z,SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE,SoundSource.PLAYERS,.6f,1f);
    }
    public static int count(){return ACTIVE.size();}
    public static long count(UUID owner){return ACTIVE.stream().filter(v->v.owner.equals(owner)).count();}
    public static java.util.List<Vec3> centers(){return ACTIVE.stream().map(Vortex::center).toList();}
    public static void tick(MinecraftServer server) {
        ACTIVE.removeIf(v->v.level.getGameTime()>=v.end||!v.level.hasChunkAt(net.minecraft.core.BlockPos.containing(v.center)));
        var forces=new IdentityHashMap<LivingEntity,Vec3>();
        for(var v:ACTIVE){double radius=v.rank==1?2.5:3.5;
            for(var e:v.level.getEntitiesOfClass(LivingEntity.class,new AABB(v.center,v.center).inflate(radius),e->e.isAlive()&&!e.isSpectator()&&!e.getUUID().equals(v.owner)).stream().limit(32).toList()){
                if(e instanceof net.minecraft.world.entity.player.Player p&&(p.getAbilities().flying||p.isFallFlying()))continue;
                var delta=v.center.subtract(e.position());double distance=delta.horizontalDistance();if(distance<.35||distance>radius)continue;
                if(!LoadedArea.ray(v.level,e.getEyePosition(),v.center.add(0,.3,0)))continue;
                var clip=v.level.clip(new net.minecraft.world.level.ClipContext(e.getEyePosition(),v.center.add(0,.3,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,e));if(clip.getType()!=HitResult.Type.MISS)continue;
                var pull=new Vec3(delta.x/distance,0,delta.z/distance).scale(v.rank==1?.025:.04);forces.merge(e,pull,Vec3::add);
            }
            if(v.level.getGameTime()%4==0)for(int i=0;i<12;i++){double a=(i+v.level.getGameTime()*.3)*Math.PI/6;v.level.sendParticles(ParticleTypes.SPLASH,v.center.x+Math.cos(a)*radius*.7,v.center.y+.1,v.center.z+Math.sin(a)*radius*.7,1,0,.05,0,0);}
        }
        forces.forEach((e,f)->{if(f.length()>.06)f=f.normalize().scale(.06);var old=e.getDeltaMovement();var next=old.add(f);double speed=next.horizontalDistance();if(speed>.4)next=new Vec3(next.x*.4/speed,old.y,next.z*.4/speed);e.setDeltaMovement(next);e.hurtMarked=true;});
        // Trigger IDs live only while a trident is loaded; reload cannot repeat a hit already embedded in a block.
        if(server.getTickCount()%200==0)TRIGGERED.removeIf(id->{for(var level:server.getAllLevels())if(level.getEntity(id)!=null)return false;return true;});
    }
    public static void clear(){ACTIVE.clear();TRIGGERED.clear();}
    private TideVortices(){}
}
