package dev.differentenchant.mixin;

import dev.differentenchant.enchanter.BookRecycling;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.*;

@Mixin(MerchantResultSlot.class)
public abstract class RecyclingResultSlotMixin extends Slot {
    @Shadow @Final private MerchantContainer slots;
    public RecyclingResultSlotMixin(Container c,int slot,int x,int y){super(c,slot,x,y);}
    @Override public boolean mayPickup(Player player){
        var offer=slots.getActiveOffer();
        if(BookRecycling.isOffer(offer))return !offer.isOutOfStock()&&(offer.satisfiedBy(slots.getItem(0),slots.getItem(1))||offer.satisfiedBy(slots.getItem(1),slots.getItem(0)));
        return super.mayPickup(player);
    }
}
