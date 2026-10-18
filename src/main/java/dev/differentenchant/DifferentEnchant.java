package dev.differentenchant;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import dev.differentenchant.enchanting.*;

public final class DifferentEnchant implements ModInitializer {
    public static final MenuType<MachineEnchantmentMenu> ENCHANTMENT_MENU = Registry.register(BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath("differentenchant", "random_enchanting"),new MenuType<>(MachineEnchantmentMenu::new,FeatureFlags.DEFAULT_FLAGS));
    public static final net.minecraft.world.item.Item RUNIC_STONE=Registry.register(BuiltInRegistries.ITEM,
            Identifier.fromNamespaceAndPath("differentenchant","runic_stone"),new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("differentenchant","runic_stone"))).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final Identifier PART_ID = Identifier.fromNamespaceAndPath("differentenchant", "machine_part");
    public static final MachinePartBlock PART = Registry.register(BuiltInRegistries.BLOCK, PART_ID,
            new MachinePartBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, PART_ID))
                    .strength(3.5F, 6.0F).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final BlockEntityType<MachineControllerBlockEntity> PART_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, PART_ID,
            FabricBlockEntityTypeBuilder.create(MachineControllerBlockEntity::new, PART).build());

    @Override
    public void onInitialize() {
        dev.differentenchant.ruins.Ruins.initialize();
        dev.differentenchant.resonance.ResonanceItems.initialize();
        dev.differentenchant.research.ResearchContent.initialize();
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(EnchantingPreviewPayload.TYPE,EnchantingPreviewPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().registerLarge(DirectedPreviewPayload.TYPE,DirectedPreviewPayload.CODEC,4*1024*1024);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(DirectedActionPayload.TYPE,DirectedActionPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(DirectedActionPayload.TYPE,(payload,context)->{
            if(context.player().containerMenu instanceof MachineEnchantmentMenu menu) menu.handleDirected(context.player(),payload);
        });
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS).register(output->output.accept(RUNIC_STONE));
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS).register(output->{
            output.accept(dev.differentenchant.resonance.ResonanceItems.CORE);
            output.accept(dev.differentenchant.resonance.ResonanceItems.ATTUNED_CORE);
        });
        try { EnchantingConfig.load(); } catch (java.io.IOException | IllegalArgumentException error) { throw new IllegalStateException("Invalid Differentenchant server configuration",error); }
        EnchantingCommands.register();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,resources,success)->{
            if (success) EnchantmentCandidates.invalidate();
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.getBlockState(hit.getBlockPos()).is(Blocks.ENCHANTING_TABLE)) return InteractionResult.PASS;
            if (player.isSpectator()) return InteractionResult.FAIL;
            boolean emptyHands=player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
            // Vanilla skips block use in this exact case, allowing item placement against the table.
            if (player.isSecondaryUseActive() && !emptyHands) return InteractionResult.PASS;
            if (hand==InteractionHand.MAIN_HAND && player.isShiftKeyDown() && emptyHands && player.mayBuild()
                    && level instanceof ServerLevel server) {
                String result = FormationService.form(server, hit.getBlockPos(), player.getDirection());
                player.sendSystemMessage(Component.literal("[Differentenchant] " + result));
            }
            // Consume on both sides: client sends one use packet; server never reaches vanilla menu opening.
            return InteractionResult.SUCCESS;
        });
    }
}
