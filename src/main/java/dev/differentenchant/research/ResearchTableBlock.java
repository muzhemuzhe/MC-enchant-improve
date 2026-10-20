package dev.differentenchant.research;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Workstation sessions own temporary items; the block never stores a second inventory copy. */
public final class ResearchTableBlock extends Block {
    public static final VoxelShape SHAPE=Shapes.or(Block.box(0,10,0,16,12,16),Block.box(1,0,1,4,10,4),Block.box(12,0,1,15,10,4),
        Block.box(1,0,12,4,10,15),Block.box(12,0,12,15,10,15),Block.box(3,12,3,13,15,13));
    public ResearchTableBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends Block> codec(){return simpleCodec(ResearchTableBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(BlockStateProperties.HORIZONTAL_FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(BlockStateProperties.HORIZONTAL_FACING,rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(level instanceof ServerLevel server&&!player.isSpectator()) {
            String status="assembly";
            player.sendSystemMessage(Component.translatable("workstation.differentenchant."+status));
        }
        return InteractionResult.SUCCESS;
    }
}
