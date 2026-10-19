package dev.differentenchant.disenchant;
import com.mojang.serialization.MapCodec;
import dev.differentenchant.workstation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class DisenchantPartBlock extends WorkstationPartBlock {
    public DisenchantPartBlock(Properties p){super(WorkstationStructure.Kind.DISENCHANT,p);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(DisenchantPartBlock::new);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new DisenchantPartEntity(p,s);}
}
