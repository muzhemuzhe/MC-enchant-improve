package dev.differentenchant.enchanter;

import dev.differentenchant.resonance.ResonanceItems;
import net.minecraft.core.component.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.MerchantOffer;

/** One narrowly identified Differentenchant exchange, not a global relaxation of ItemCost. */
public final class BookRecycling {
    public static boolean isOffer(MerchantOffer offer){
        if(offer==null)return false;var cost=offer.getItemCostA();
        return cost.item().value()==Items.ENCHANTED_BOOK&&cost.count()==1&&cost.components().equals(DataComponentExactPredicate.EMPTY)
            &&offer.getItemCostB().isEmpty()&&offer.getResult().is(ResonanceItems.ATTUNED_CORE)&&offer.getResult().getCount()==1;
    }
    public static boolean validBook(ItemStack stack){
        if(!stack.is(Items.ENCHANTED_BOOK)||stack.isEmpty())return false;
        var stored=stack.get(DataComponents.STORED_ENCHANTMENTS);
        return stored!=null&&stored.entrySet().stream().anyMatch(e->e.getIntValue()>0&&e.getKey().isBound()&&e.getKey().unwrapKey().isPresent());
    }
    public static boolean satisfied(ItemStack a,ItemStack b){return validBook(a)&&b.isEmpty();}
    private BookRecycling(){}
}
