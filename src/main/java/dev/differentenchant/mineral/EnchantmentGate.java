package dev.differentenchant.mineral;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;

/** Reusable, registry-driven gate. Only the supplied item, never worn/offhand gear. */
public final class EnchantmentGate {
    public static final TagKey<Enchantment> PROTECTION=tag("mines_luminous_stone");
    public static final TagKey<Enchantment> FIRE=tag("mines_harmonic_silver");
    public static final TagKey<Enchantment> LOST=tag("lost_enchantments");
    private static TagKey<Enchantment> tag(String path){return TagKey.create(Registries.ENCHANTMENT,Identifier.fromNamespaceAndPath("differentenchant",path));}
    public static boolean allows(ItemStack stack,TagKey<Enchantment> rule){
        if(stack.isEmpty())return false;
        return matches(stack.get(DataComponents.ENCHANTMENTS),rule)||matches(stack.get(DataComponents.STORED_ENCHANTMENTS),rule);
    }
    private static boolean matches(ItemEnchantments enchantments,TagKey<Enchantment> rule){
        return enchantments!=null&&enchantments.entrySet().stream().anyMatch(e->e.getIntValue()>=1&&e.getIntValue()<=255
            &&e.getKey().isBound()&&e.getKey().unwrapKey().isPresent()&&e.getKey().is(rule));
    }
    private EnchantmentGate(){}
}
