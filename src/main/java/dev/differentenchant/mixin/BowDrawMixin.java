package dev.differentenchant.mixin;
import dev.differentenchant.lost.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(BowItem.class)
public abstract class BowDrawMixin {
    @Redirect(method="releaseUsing",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
    private float swift(int ticks,ItemStack bow,Level level,LivingEntity user,int remaining){return BatchRules.bowPower(ticks,BatchEffects.level(user,bow,"swift_draw"));}
}
