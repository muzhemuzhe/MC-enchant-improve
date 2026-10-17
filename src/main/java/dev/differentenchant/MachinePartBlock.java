package dev.differentenchant;

import java.util.List;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleMenuProvider;
import dev.differentenchant.enchanting.*;

public final class MachinePartBlock extends BaseEntityBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 17);
    public MachinePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 0).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(MachinePartBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(PART, BlockStateProperties.HORIZONTAL_FACING);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MachineControllerBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, DifferentEnchant.PART_ENTITY,
                (world, pos, blockState, entity) -> entity.checkIntegrity());
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof MachineControllerBlockEntity part) {
            if (!player.isShiftKeyDown()) {
                var controller=MachineAccess.controller(server,pos);
                if (controller!=null && !player.isSpectator()) player.openMenu(new SimpleMenuProvider(
                        (id,inventory,owner)->new MachineEnchantmentMenu(id,inventory,server,controller),Component.translatable("container.differentenchant.title")));
                return InteractionResult.SUCCESS;
            }
            player.sendSystemMessage(Component.literal("[Differentenchant] Formed 3x3x2; controller=" + part.controller.toShortString()
                    + "; facing=" + state.getValue(BlockStateProperties.HORIZONTAL_FACING) + "; part=" + part.index
                    + ". Break any part to disassemble."));
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MachineControllerBlockEntity part && part.machineId != null) {
            // Loot keeps its original tool, explosion radius and entity parameters.
            builder.withParameter(LootContextParams.BLOCK_STATE, part.original);
            if (part.originalEntity != null) {
                BlockEntity old = BlockEntity.loadStatic(part.getBlockPos(), part.original, part.originalEntity, builder.getLevel().registryAccess());
                if (old != null) builder.withParameter(LootContextParams.BLOCK_ENTITY, old);
            }
            return part.original.getDrops(builder);
        }
        return List.of();
    }
}
