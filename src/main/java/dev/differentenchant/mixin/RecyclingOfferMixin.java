package dev.differentenchant.mixin;

import dev.differentenchant.enchanter.BookRecycling;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantOffer.class)
public abstract class RecyclingOfferMixin {
    @Inject(method="satisfiedBy",at=@At("HEAD"),cancellable=true)
    private void differentenchant$nonemptyRegisteredBook(ItemStack a,ItemStack b,CallbackInfoReturnable<Boolean> cir){
        if(BookRecycling.isOffer((MerchantOffer)(Object)this))cir.setReturnValue(BookRecycling.satisfied(a,b));
    }
}
