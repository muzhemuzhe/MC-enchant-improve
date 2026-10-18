package dev.differentenchant.research;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ResearchContent {
    public static Identifier id(String path){return Identifier.fromNamespaceAndPath("differentenchant",path);}
    public static final ResearchTableBlock TABLE=Registry.register(BuiltInRegistries.BLOCK,id("lost_research_table"),new ResearchTableBlock(BlockBehaviour.Properties.of()
        .setId(ResourceKey.create(Registries.BLOCK,id("lost_research_table"))).strength(2.5f).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion()));
    public static final Item TABLE_ITEM=Registry.register(BuiltInRegistries.ITEM,id("lost_research_table"),new BlockItem(TABLE,new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id("lost_research_table")))));
    public static final Item LOST_SCROLL=Registry.register(BuiltInRegistries.ITEM,id("lost_scroll"),new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id("lost_scroll"))).rarity(Rarity.UNCOMMON)));
    public static final MenuType<ResearchMenu> MENU=Registry.register(BuiltInRegistries.MENU,id("lost_research"),new MenuType<>(ResearchMenu::new,FeatureFlags.DEFAULT_FLAGS));
    public static void initialize() {
        try{ResearchConfig.load();}catch(java.io.IOException|IllegalArgumentException e){throw new IllegalStateException("Invalid research configuration",e);}
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(ResearchActionPayload.TYPE,ResearchActionPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().registerLarge(ResearchPreviewPayload.TYPE,ResearchPreviewPayload.CODEC,4*1024*1024);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(ResearchActionPayload.TYPE,(payload,context)->{if(context.player().containerMenu instanceof ResearchMenu menu)menu.handle(context.player(),payload);});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server->ResearchWorldData.get(server).observeNewInputs(server.registryAccess()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,resources,success)->{if(success)ResearchWorldData.get(server).observeNewInputs(server.registryAccess());});
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output->output.accept(TABLE_ITEM));
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output->output.accept(LOST_SCROLL));
        LostEffects.initialize();
    }
    private ResearchContent(){}
}
