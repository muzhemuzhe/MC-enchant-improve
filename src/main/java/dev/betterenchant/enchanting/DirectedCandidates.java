package dev.betterenchant.enchanting;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;

/** Directed uses the full registry and supported_items, not the random acquisition pool. */
public final class DirectedCandidates {
    public static final TagKey<Enchantment> EXCLUDED=TagKey.create(Registries.ENCHANTMENT,Identifier.fromNamespaceAndPath("better_enchant","excluded_from_directed"));
    public record Pool(Map<String,DirectedEnchantPolicy.Candidate> candidates,Map<String,Holder<Enchantment>> holders,Map<String,Integer> existing) {
        public Pool { candidates=Collections.unmodifiableMap(new TreeMap<>(candidates));holders=Map.copyOf(holders);existing=Map.copyOf(existing); }
    }
    public static Pool forItem(RegistryAccess access,ItemStack item,DirectedEnchantPolicy.Parameters p) {
        var candidates=new TreeMap<String,DirectedEnchantPolicy.Candidate>();var holders=new HashMap<String,Holder<Enchantment>>();var existing=new HashMap<String,Integer>();
        var current=EnchantmentHelper.getEnchantmentsForCrafting(item);
        for(var holder:current.keySet()) existing.put(holder.unwrapKey().orElseThrow().identifier().toString(),current.getLevel(holder));
        if(item.getCount()!=1 || !EnchantmentHelper.canStoreEnchantments(item)) return new Pool(candidates,holders,existing);
        var registry=access.lookupOrThrow(Registries.ENCHANTMENT);
        for(var key:registry.registryKeySet()) {
            var holder=registry.get(key).orElseThrow();var e=holder.value();
            if(holder.is(EXCLUDED) || (!p.allowTreasure() && holder.is(EnchantmentTags.TREASURE)) || (!p.allowCurses() && holder.is(EnchantmentTags.CURSE))
                    || !e.isSupportedItem(item)) continue;
            String id=key.identifier().toString();var excluded=new HashSet<String>();
            for(var other:registry.registryKeySet()) if(!Enchantment.areCompatible(holder,registry.get(other).orElseThrow())) excluded.add(other.identifier().toString());
            candidates.put(id,new DirectedEnchantPolicy.Candidate(id,e.getWeight(),e.getMinLevel(),e.getMaxLevel(),excluded));holders.put(id,holder);
        }
        return new Pool(candidates,holders,existing);
    }
    public static boolean compatible(ItemStack item) {
        var current=EnchantmentHelper.getEnchantmentsForCrafting(item);
        for(var a:current.keySet()) for(var b:current.keySet()) if(!a.equals(b) && !Enchantment.areCompatible(a,b)) return false;
        return true;
    }
    private DirectedCandidates() {}
}
