package dev.differentenchant.mixin;

import dev.differentenchant.enchanter.BookRecycling;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(MerchantMenu.class)
public abstract class RecyclingAutofillMixin {
    @Shadow @Final private Merchant trader;
    @Shadow @Final private MerchantContainer tradeContainer;
    @Unique private boolean differentenchant$recyclingAutofill;
    @Inject(method="tryMoveItems",at=@At("HEAD"))
    private void differentenchant$begin(int index,CallbackInfo ci){
        var offers=trader.getOffers();differentenchant$recyclingAutofill=index>=0&&index<offers.size()&&BookRecycling.isOffer(offers.get(index));
    }
    @Inject(method="tryMoveItems",at=@At("RETURN"))
    private void differentenchant$end(int index,CallbackInfo ci){differentenchant$recyclingAutofill=false;}
    @Redirect(method="moveFromInventoryToPaymentSlot",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/trading/ItemCost;test(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean differentenchant$fillRealBook(ItemCost cost,ItemStack stack){
        return differentenchant$recyclingAutofill&&cost.item().value()==Items.ENCHANTED_BOOK?BookRecycling.validBook(stack):cost.test(stack);
    }
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true)
    private void differentenchant$guardShift(Player player,int slot,CallbackInfoReturnable<ItemStack> cir){
        var offer=tradeContainer.getActiveOffer();
        if(slot==2&&BookRecycling.isOffer(offer)&&(offer.isOutOfStock()||!(offer.satisfiedBy(tradeContainer.getItem(0),tradeContainer.getItem(1))||offer.satisfiedBy(tradeContainer.getItem(1),tradeContainer.getItem(0)))))cir.setReturnValue(ItemStack.EMPTY);
    }
}
