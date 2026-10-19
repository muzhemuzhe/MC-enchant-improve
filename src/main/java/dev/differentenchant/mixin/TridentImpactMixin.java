package dev.differentenchant.mixin;
import dev.differentenchant.lost.TideVortices;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ThrownTrident.class)
public abstract class TridentImpactMixin {
    @Inject(method="onHitEntity",at=@At("HEAD"))
    private void entity(EntityHitResult hit,CallbackInfo ci){TideVortices.impact((ThrownTrident)(Object)this,hit.getLocation());}
    @Inject(method="hitBlockEnchantmentEffects",at=@At("HEAD"))
    private void block(ServerLevel level,BlockHitResult hit,ItemStack weapon,CallbackInfo ci){TideVortices.impact((ThrownTrident)(Object)this,hit.getLocation());}
}
