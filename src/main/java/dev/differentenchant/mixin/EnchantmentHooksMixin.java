package dev.differentenchant.mixin;
import dev.differentenchant.lost.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHooksMixin {
    @Inject(method="processAmmoUse",at=@At("RETURN"),cancellable=true)
    private static void feather(ServerLevel level,ItemStack weapon,ItemStack ammo,int amount,CallbackInfoReturnable<Integer> cir){
        if(cir.getReturnValue()>0&&weapon.getItem() instanceof CrossbowItem&&ammo.is(Items.ARROW)){int rank=BatchEffects.level(level.registryAccess(),weapon,"returning_feather");if(rank>0&&BatchRules.spareArrow(rank,level.getRandom().nextInt(rank+1)))cir.setReturnValue(0);}
    }
    @Inject(method="doPostAttackEffectsWithItemSource",at=@At("TAIL"))
    private static void flame(ServerLevel level,Entity victim,DamageSource source,ItemStack weapon,CallbackInfo ci){BatchEffects.borrowFire(victim,source,weapon);}
}
