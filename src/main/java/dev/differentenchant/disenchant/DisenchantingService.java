package dev.differentenchant.disenchant;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;

/** Pure transaction preparation: never mutates the supplied equipment or book. */
public final class DisenchantingService {
    public record Choice(Holder<Enchantment> enchantment,int level) {
        public String id(){return enchantment.unwrapKey().orElseThrow().identifier().toString();}
        public int outputLevel(){return Math.max(1,level-1);}
        public int cost(){return Math.clamp(level+1,3,6);}
    }
    public record Prepared(ItemStack equipment,ItemStack book,int cost) {}
    public static boolean equipment(ItemStack stack){return stack.getCount()==1&&!stack.is(Items.ENCHANTED_BOOK)&&!stack.is(Items.BOOK)&&stack.has(DataComponents.ENCHANTMENTS);}
    public static List<Choice> choices(ItemStack stack) {
        if(!equipment(stack))return List.of();
        var result=new ArrayList<Choice>();
        for(var entry:stack.get(DataComponents.ENCHANTMENTS).entrySet()) {
            var h=entry.getKey();int level=entry.getIntValue();
            if(h.unwrapKey().isPresent()&&!h.is(EnchantmentTags.CURSE)&&!h.is(Enchantments.BINDING_CURSE)&&!h.is(Enchantments.VANISHING_CURSE)&&level>=1&&level<=h.value().getMaxLevel())result.add(new Choice(h,level));
        }
        result.sort(Comparator.comparing(Choice::id));return List.copyOf(result);
    }
    public static Prepared prepare(ItemStack gear,ItemStack book,String selected) {
        if(!book.is(Items.BOOK)||book.isEmpty()||selected==null)return null;
        var choice=choices(gear).stream().filter(c->c.id().equals(selected)).findFirst().orElse(null);if(choice==null)return null;
        var remainder=gear.copy();EnchantmentHelper.updateEnchantments(remainder,m->m.set(choice.enchantment(),0));
        var output=new ItemStack(Items.ENCHANTED_BOOK);EnchantmentHelper.updateEnchantments(output,m->m.set(choice.enchantment(),choice.outputLevel()));
        return new Prepared(remainder,output,choice.cost());
    }
    private DisenchantingService(){}
}
