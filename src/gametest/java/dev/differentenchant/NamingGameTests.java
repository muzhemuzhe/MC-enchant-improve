package dev.differentenchant;

import java.nio.file.Files;
import dev.differentenchant.enchanting.*;
import dev.differentenchant.resonance.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/** Verify the shipped identity independently of the identifiers used by gameplay fixtures. */
public final class NamingGameTests {
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("differentenchant",path); }
    @GameTest public void canonicalLoaderRegistriesPayloadsAndConfiguration(GameTestHelper h) throws Exception {
        var loader=FabricLoader.getInstance();
        h.assertTrue(loader.getModContainer("differentenchant").orElseThrow().getMetadata().getName().equals("Differentenchant"),"canonical Fabric identity");
        h.assertTrue(BuiltInRegistries.BLOCK.getKey(DifferentEnchant.PART).equals(id("machine_part")),"block ID");
        h.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(DifferentEnchant.PART_ENTITY).equals(id("machine_part")),"block entity ID");
        h.assertTrue(BuiltInRegistries.MENU.getKey(DifferentEnchant.ENCHANTMENT_MENU).equals(id("random_enchanting")),"menu ID");
        h.assertTrue(BuiltInRegistries.ITEM.getKey(DifferentEnchant.RUNIC_STONE).equals(id("runic_stone")),"runic stone ID");
        h.assertTrue(BuiltInRegistries.ITEM.getKey(ResonanceItems.CORE).equals(id("resonance_core")),"core ID");
        h.assertTrue(BuiltInRegistries.ITEM.getKey(ResonanceItems.ATTUNED_CORE).equals(id("attuned_resonance_core")),"attuned core ID");
        h.assertTrue(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(ResonanceItems.ENERGY).equals(id("resonance_energy")),"persistent component ID");
        h.assertTrue(EnchantingPreviewPayload.TYPE.id().equals(id("preview")) && DirectedPreviewPayload.TYPE.id().equals(id("directed_preview"))
                && DirectedActionPayload.TYPE.id().equals(id("directed_action")),"all menu payload IDs");
        h.assertTrue(EnchantingSeedData.TYPE.id().equals(id("enchanting_seed")),"world saved salt ID");
        var config=loader.getConfigDir().resolve("differentenchant.properties");
        h.assertTrue(Files.isRegularFile(config),"new config created");
        var properties=new java.util.Properties();try(var reader=Files.newBufferedReader(config)) {properties.load(reader);}
        h.assertTrue(EnchantingConfig.parse(properties).equals(EnchantingConfig.current()),"all server settings loaded from renamed file");
        h.assertTrue(ResonancePolicy.CAPACITY==12 && DirectedEnchantPolicy.REQUIRED_LEVEL==30,"existing gameplay parameters preserved");
        h.succeed();
    }
}
