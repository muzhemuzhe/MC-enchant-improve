package dev.differentenchant.research;
import com.mojang.serialization.MapCodec;
import dev.differentenchant.workstation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class ResearchPartBlock extends WorkstationPartBlock {
    public ResearchPartBlock(Properties p){super(WorkstationStructure.Kind.RESEARCH,p);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(ResearchPartBlock::new);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new ResearchPartEntity(p,s);}
}
