package dev.differentenchant.enchanting;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/** The sole boundary between registry/tag/item rules and the plain Java distribution. */
public final class EnchantmentCandidates {
    private static int revision;
    public static int revision() { return revision; }
    public static void invalidate() { revision++; }
    public static final TagKey<Enchantment> EXCLUDED = TagKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath("differentenchant", "excluded_from_random"));
    public record Pool(List<RandomEnchantPolicy.Candidate> candidates, Map<String, Holder<Enchantment>> holders) {
        public Pool { candidates = List.copyOf(candidates); holders = Map.copyOf(holders); }
        public List<EnchantmentInstance> resolve(List<RandomEnchantPolicy.Choice> choices) {
            return choices.stream().map(choice -> new EnchantmentInstance(Objects.requireNonNull(holders.get(choice.id())), choice.level())).toList();
        }
    }
    public static Pool forItem(RegistryAccess registries, ItemStack item) {
        if (item.isEmpty() || !item.isEnchantable() || item.getCount() != 1) return new Pool(List.of(), Map.of());
        var tag = registries.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.IN_ENCHANTING_TABLE);
        if (tag.isEmpty()) return new Pool(List.of(), Map.of());
        var holders = new TreeMap<String, Holder<Enchantment>>();
        tag.get().stream().filter(holder -> !holder.is(EnchantmentTags.TREASURE) && !holder.is(EnchantmentTags.CURSE) && !holder.is(EXCLUDED))
                .filter(holder -> item.is(Items.BOOK) || holder.value().isPrimaryItem(item))
                .forEach(holder -> holders.put(holder.unwrapKey().orElseThrow().identifier().toString(), holder));
        var candidates = new ArrayList<RandomEnchantPolicy.Candidate>();
        holders.forEach((id, holder) -> {
            var excluded = new HashSet<String>();
            holders.forEach((otherId, other) -> { if (!Enchantment.areCompatible(holder, other)) excluded.add(otherId); });
            Enchantment enchantment = holder.value();
            candidates.add(new RandomEnchantPolicy.Candidate(id, enchantment.getWeight(), enchantment.getMinLevel(), enchantment.getMaxLevel(), excluded));
        });
        return new Pool(candidates, holders);
    }
    private EnchantmentCandidates() {}
}
