package dev.betterenchant;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Test-only third-party-style registry and tag fixture; never present in the release JAR. */
public final class CompatibilityTestMod implements ModInitializer {
    public static Item TAGGED_TOOL;
    @Override public void onInitialize() {
        var key=ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("better_enchant_tests","tagged_tool"));
        TAGGED_TOOL=Registry.register(BuiltInRegistries.ITEM,key,new Item(new Item.Properties().setId(key).stacksTo(1).enchantable(10)));
    }
}
