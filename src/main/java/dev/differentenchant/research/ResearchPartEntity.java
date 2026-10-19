package dev.differentenchant.research;
import dev.differentenchant.workstation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public final class ResearchPartEntity extends WorkstationPartEntity {
    public ResearchPartEntity(BlockPos p,BlockState s){super(ResearchContent.PART_ENTITY,p,s);}
    @Override public WorkstationStructure.Kind kind(){return WorkstationStructure.Kind.RESEARCH;}
}
