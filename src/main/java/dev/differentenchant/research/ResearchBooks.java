package dev.differentenchant.research;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;

/** Book format boundary. Multi-enchantment expansion can be added here with a new mapping version. */
public final class ResearchBooks {
    public static ResearchPolicy.Book read(ItemStack stack) {
        if(!stack.is(Items.ENCHANTED_BOOK)||stack.getCount()!=1)return null;
        var stored=stack.get(DataComponents.STORED_ENCHANTMENTS);if(stored==null||stored.entrySet().size()!=1)return null;
        var entry=stored.entrySet().iterator().next();int level=entry.getIntValue();var enchantment=entry.getKey();
        if(enchantment.unwrapKey().isEmpty()||level<1||level>enchantment.value().getMaxLevel())return null;
        return new ResearchPolicy.Book(enchantment.unwrapKey().orElseThrow().identifier().toString(),level);
    }
    public static ItemStack output(RegistryAccess registries,ResearchPolicy.Result result) {
        if(result.scroll())return new ItemStack(ResearchContent.LOST_SCROLL);
        var holder=registries.lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(result.id()));
        if(holder.isEmpty()||holder.get().value().getMaxLevel()<result.level())return ItemStack.EMPTY;
        var book=new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(book,mutable->mutable.set(holder.get(),result.level()));return book;
    }
    private ResearchBooks(){}
}
