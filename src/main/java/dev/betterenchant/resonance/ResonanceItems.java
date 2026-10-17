package dev.betterenchant.resonance;

import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;

public final class ResonanceItems {
    // Accept legacy/out-of-range integers on disk; clamp on use rather than losing an entire stack on decode.
    public static final DataComponentType<Integer> ENERGY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            id("resonance_energy"), DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final Item CORE = register("resonance_core", false);
    public static final Item ATTUNED_CORE = register("attuned_resonance_core", true);
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("better_enchant", path); }
    private static Item register(String path, boolean attuned) {
        Item.Properties properties = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(path))).rarity(Rarity.UNCOMMON);
        if (!attuned) properties.stacksTo(1).component(ENERGY, 0);
        return Registry.register(BuiltInRegistries.ITEM, id(path), new Item(properties) {
            @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
                if (!attuned) lines.accept(Component.translatable("item.better_enchant.resonance_core.energy", energy(stack), ResonancePolicy.CAPACITY).withStyle(ChatFormatting.AQUA));
                lines.accept(Component.translatable("item.better_enchant." + path + ".help").withStyle(ChatFormatting.GRAY));
                lines.accept(Component.translatable("item.better_enchant." + path + ".help_extra").withStyle(ChatFormatting.GRAY));
            }
        });
    }
    public static boolean isCore(ItemStack stack) { return stack.is(CORE) || stack.is(ATTUNED_CORE); }
    public static int energy(ItemStack stack) {
        return stack.is(ATTUNED_CORE) ? ResonancePolicy.CAPACITY : stack.is(CORE) ? ResonancePolicy.clamp(stack.getOrDefault(ENERGY, 0)) : 0;
    }
    public static void initialize() {}
    private ResonanceItems() {}
}
