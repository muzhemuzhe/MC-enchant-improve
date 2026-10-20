package dev.differentenchant.enchanter;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.block.Blocks;

public final class EnchanterContent {
    public static Identifier id(String path){return Identifier.fromNamespaceAndPath("differentenchant",path);}
    public static final ResourceKey<PoiType> POI_KEY=ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,id("enchanter"));
    public static final PoiType POI=PoiHelper.register(POI_KEY.identifier(),1,1,Blocks.ENCHANTING_TABLE);
    public static final ResourceKey<VillagerProfession> PROFESSION_KEY=ResourceKey.create(Registries.VILLAGER_PROFESSION,id("enchanter"));
    public static final VillagerProfession PROFESSION=Registry.register(BuiltInRegistries.VILLAGER_PROFESSION,PROFESSION_KEY,
        new VillagerProfession(Component.translatable("entity.differentenchant.villager.enchanter"),p->p.is(POI_KEY),p->p.is(POI_KEY),
            ImmutableSet.of(),ImmutableSet.of(),SoundEvents.VILLAGER_WORK_LIBRARIAN,
            Int2ObjectMap.ofEntries(Int2ObjectMap.entry(1,trades(1)),Int2ObjectMap.entry(2,trades(2)),Int2ObjectMap.entry(3,trades(3)))));
    public static final Item CRYSTAL=Registry.register(BuiltInRegistries.ITEM,id("condensed_experience_crystal"),
        new ExperienceCrystalItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id("condensed_experience_crystal"))).stacksTo(64).rarity(Rarity.UNCOMMON)));
    private static ResourceKey<TradeSet> trades(int level){return ResourceKey.create(Registries.TRADE_SET,id("enchanter/level_"+level));}
    public static void initialize(){CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output->output.accept(CRYSTAL));}
    private EnchanterContent(){}
}
