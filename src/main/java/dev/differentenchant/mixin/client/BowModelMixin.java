package dev.differentenchant.mixin.client;
import dev.differentenchant.lost.*;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(UseDuration.class)
public abstract class BowModelMixin {
    @Inject(method="useDuration",at=@At("RETURN"),cancellable=true)
    private static void swift(ItemStack bow,LivingEntity user,CallbackInfoReturnable<Integer> cir){if(bow.getItem() instanceof BowItem){int rank=BatchEffects.level(user,bow,"swift_draw");if(rank>0)cir.setReturnValue((int)(cir.getReturnValue()*20.0/BatchRules.drawTicks(rank)));}}
}
