package dev.differentenchant.realm;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Only registry bootstrap; all terrain and climate choices live in the data pack. */
public final class RealmContent {
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath("differentenchant", path); }
    public static final ResourceKey<Level> REALM = ResourceKey.create(Registries.DIMENSION, id("enchanter_realm"));
    private static Block block(String name, Block template, Function<BlockBehaviour.Properties, Block> factory) {
        var id = id(name);
        var block = Registry.register(BuiltInRegistries.BLOCK, id,
                factory.apply(BlockBehaviour.Properties.ofFullCopy(template).setId(ResourceKey.create(Registries.BLOCK, id))));
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block,
                new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
        return block;
    }
    public static final Block CHALKSTONE = block("realm_chalkstone", Blocks.STONE, Block::new);
    public static final Block LOAM = block("realm_loam", Blocks.DIRT, Block::new);
    public static final Block TURF = block("mint_turf", Blocks.GRASS_BLOCK, GrassBlock::new);
    public static final Block LOG = block("azure_log", Blocks.OAK_LOG, RotatedPillarBlock::new);
    public static final Block LEAVES = block("azure_leaves", Blocks.OAK_LEAVES, p -> new TintedParticleLeavesBlock(0, p));
    public static void initialize() {
        Registry.register(BuiltInRegistries.FEATURE, id("azure_tree"), new AzureTreeFeature());
        RealmTravel.register();
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS)
                .register(out -> {out.accept(CHALKSTONE); out.accept(LOAM); out.accept(TURF); out.accept(LOG); out.accept(LEAVES);});
    }
    private RealmContent() {}
}
