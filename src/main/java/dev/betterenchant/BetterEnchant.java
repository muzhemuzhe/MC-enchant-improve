package dev.betterenchant;

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

public final class BetterEnchant implements ModInitializer {
    public static final Identifier PART_ID = Identifier.fromNamespaceAndPath("better_enchant", "machine_part");
    public static final MachinePartBlock PART = Registry.register(BuiltInRegistries.BLOCK, PART_ID,
            new MachinePartBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, PART_ID))
                    .strength(3.5F, 6.0F).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final BlockEntityType<MachineControllerBlockEntity> PART_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, PART_ID,
            FabricBlockEntityTypeBuilder.create(MachineControllerBlockEntity::new, PART).build());

    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                    || !player.getOffhandItem().isEmpty() || player.isSpectator()
                    || !player.mayBuild() || !level.getBlockState(hit.getBlockPos()).is(Blocks.ENCHANTING_TABLE)) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel server) {
                String result = FormationService.form(server, hit.getBlockPos(), player.getDirection());
                player.sendSystemMessage(Component.literal("[Better Enchant] " + result));
            }
            return InteractionResult.SUCCESS;
        });
    }
}
