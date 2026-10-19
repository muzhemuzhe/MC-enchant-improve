package dev.differentenchant.mixin;
import dev.differentenchant.lost.ArrowParry;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractArrow.class)
public abstract class ArrowParryMixin {
    @Inject(method="onHitEntity",at=@At("HEAD"),cancellable=true)
    private void parry(EntityHitResult hit,CallbackInfo ci){if(ArrowParry.reflect((AbstractArrow)(Object)this,hit))ci.cancel();}
}
