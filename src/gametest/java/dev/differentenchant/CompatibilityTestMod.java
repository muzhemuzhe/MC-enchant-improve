package dev.differentenchant;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Test-only third-party-style registry and tag fixture; never present in the release JAR. */
public final class CompatibilityTestMod implements ModInitializer {
    public static Item TAGGED_TOOL, PREVIEW_TOOL;
    public static net.minecraft.world.level.block.Block TAGGED_BOOKSHELF;
    @Override public void onInitialize() {
        var key=ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("differentenchant_tests","tagged_tool"));
        TAGGED_TOOL=Registry.register(BuiltInRegistries.ITEM,key,new Item(new Item.Properties().setId(key).stacksTo(1).enchantable(10)));
        var previewKey=ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("differentenchant_tests","preview_tool"));
        PREVIEW_TOOL=Registry.register(BuiltInRegistries.ITEM,previewKey,new Item(new Item.Properties().setId(previewKey).stacksTo(1).enchantable(10)));
        var shelfKey=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("differentenchant_tests","tagged_bookshelf"));
        TAGGED_BOOKSHELF=Registry.register(BuiltInRegistries.BLOCK,shelfKey,new net.minecraft.world.level.block.Block(
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().setId(shelfKey).strength(1.5F)));
    }
}
