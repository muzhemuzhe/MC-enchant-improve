package dev.differentenchant.lost;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
public final class StarBridges {
    private record Sample(ServerLevel level,Vec3 feet,boolean ground){}
    private static final Map<UUID,Sample> PREVIOUS=new HashMap<>();
    public static boolean naturalSupport(ServerPlayer p){var pos=BlockPos.containing(p.getX(),p.getY()-.05,p.getZ());return p.onGround()&&p.level().hasChunkAt(pos)&&!p.level().getBlockState(pos).getCollisionShape(p.level(),pos).isEmpty();}
    public static void tick(ServerPlayer p){
        if(!p.isAlive()){forget(p);return;}
        boolean ground=naturalSupport(p);var now=new Sample(p.level(),p.position(),ground);var old=PREVIOUS.put(p.getUUID(),now);
        if(old==null||old.level!=p.level()||!old.ground||ground||!p.isSprinting()||p.isShiftKeyDown()||p.isSpectator()||p.getAbilities().flying||p.isFallFlying()||p.isInWater()||p.isInLava()||p.isPassenger()||p.onClimbable()||p.isAutoSpinAttack()||p.getY()>old.feet.y+.05||p.getY()<old.feet.y-.35||BatchEffects.level(p,p.getItemBySlot(EquipmentSlot.FEET),"star_bridge")==0)return;
        var move=p.position().subtract(old.feet);if(move.horizontalDistance()<.025||move.horizontalDistance()>1)return;
        var data=StarBridgeData.get(p.level().getServer());long time=p.level().getServer().overworld().getGameTime();if(!data.ready(p.getUUID(),time))return;
        double dx=move.x/move.horizontalDistance(),dz=move.z/move.horizontalDistance();
        int x=(int)Math.floor(p.getX()),z=(int)Math.floor(p.getZ());double top=Math.min(old.feet.y,p.getY()-.01);int added=0;
        for(int i=0;i<3;i++){double sx=x+dx*i+.5,sz=z+dz*i+.5;var pos=BlockPos.containing(sx,top-.05,sz);var box=new AABB(sx-.49,top-.125,sz-.49,sx+.49,top,sz+.49);
            if(!LoadedArea.ray(p.level(),new Vec3(box.minX,top,box.minZ),new Vec3(box.maxX,top,box.maxZ))||!p.level().hasChunkAt(pos)||!p.level().getBlockState(pos).isAir()||!p.level().getFluidState(pos).isEmpty()||p.level().getBlockCollisions(null,box).iterator().hasNext())break;
            if(p.level().addFreshEntity(new StarStep(p.level(),p,sx,top,sz)))added++;
        }
        if(added>0)data.spend(p.getUUID(),time);
    }
    public static void forget(ServerPlayer p){PREVIOUS.remove(p.getUUID());}
    public static void clear(){PREVIOUS.clear();}
    private StarBridges(){}
}
