package dev.differentenchant.lost;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.projectile.arrow.*;
import net.minecraft.world.phys.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.ClipContext;
public final class ArrowParry {
    public static boolean reflect(AbstractArrow arrow,EntityHitResult hit){
        if(!(arrow.level() instanceof ServerLevel level)||arrow instanceof ThrownTrident||arrow.getPierceLevel()>0||!(hit.getEntity() instanceof ServerPlayer p)||!p.isBlocking())return false;
        var shield=p.getUseItem();int rank=BatchEffects.level(p,shield,"parry");var blocking=shield.get(DataComponents.BLOCKS_ATTACKS);if(rank==0||blocking==null)return false;
        int age=p.getTicksUsingItem()-blocking.blockDelayTicks();if(age<0||age>4*rank)return false;
        var look=p.getLookAngle();var incoming=arrow.position().subtract(p.position());if(new Vec3(incoming.x,0,incoming.z).normalize().dot(new Vec3(look.x,0,look.z).normalize())<=.05||arrow.getDeltaMovement().dot(look)>=0)return false;
        var direction=new Vec3(look.x,Math.max(.16,look.y),look.z).normalize();Vec3 exit=null;
        for(double lift:new double[]{0,.35,.7}){var test=p.position().add(0,1.1+lift,0).add(direction.scale(.85));var box=new AABB(test.x-.15,test.y-.15,test.z-.15,test.x+.15,test.y+.15,test.z+.15);
            if(LoadedArea.ray(level,p.getEyePosition(),test)&&level.noCollision(arrow,box)&&level.clip(new ClipContext(p.getEyePosition(),test,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS){exit=test;break;}}
        if(exit==null)return false;
        var damage=level.damageSources().arrow(arrow,arrow.getOwner()==null?arrow:arrow.getOwner());float amount=(float)Math.ceil(arrow.getDeltaMovement().length()*2);
        if(p.applyItemBlocking(level,damage,amount)<=0)return false;
        double speed=Math.clamp(arrow.getDeltaMovement().length(),.5,3.15);
        arrow.deflect(ProjectileDeflection.AIM_DEFLECT,p,EntityReference.of(p),true);arrow.setPos(exit);arrow.setDeltaMovement(direction.scale(speed));arrow.hurtMarked=true;
        blocking.onBlocked(level,p);return true;
    }
    private ArrowParry(){}
}
