package dev.differentenchant.disenchant;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

public final class DisenchantContent {
    public static Identifier id(String path){return Identifier.fromNamespaceAndPath("differentenchant",path);}
    public static final DisenchantCoreBlock CORE=Registry.register(BuiltInRegistries.BLOCK,id("disenchanting_core"),new DisenchantCoreBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,id("disenchanting_core"))).strength(3.5f,6).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final Item CORE_ITEM=Registry.register(BuiltInRegistries.ITEM,id("disenchanting_core"),new BlockItem(CORE,new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id("disenchanting_core")))));
    public static final DisenchantPartBlock PART=Registry.register(BuiltInRegistries.BLOCK,id("disenchanting_part"),new DisenchantPartBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,id("disenchanting_part"))).strength(3.5f,6).noOcclusion().lightLevel(state->dev.differentenchant.workstation.WorkstationStructure.corner(state.getValue(DisenchantPartBlock.PART))?5:0).pushReaction(PushReaction.BLOCK)));
    public static final BlockEntityType<DisenchantPartEntity> ENTITY=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id("disenchanting_part"),FabricBlockEntityTypeBuilder.create(DisenchantPartEntity::new,PART).build());
    public static final MenuType<DisenchantMenu> MENU=Registry.register(BuiltInRegistries.MENU,id("disenchanting"),new MenuType<>(DisenchantMenu::new,FeatureFlags.DEFAULT_FLAGS));
    public static void initialize(){net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output->output.accept(CORE_ITEM));}
    private DisenchantContent(){}
}
