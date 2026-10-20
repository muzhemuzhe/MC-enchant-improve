package dev.differentenchant.disenchant;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class DisenchantCoreBlock extends Block {
    public DisenchantCoreBlock(Properties p){super(p);}
    @Override protected MapCodec<? extends Block> codec(){return simpleCodec(DisenchantCoreBlock::new);}
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return DisenchantPartBlock.CORE_SHAPE;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(level instanceof ServerLevel server&&!player.isSpectator()) {
            String status="assembly";
            player.sendSystemMessage(Component.translatable("workstation.differentenchant."+status));
        }
        return InteractionResult.SUCCESS;
    }
}
