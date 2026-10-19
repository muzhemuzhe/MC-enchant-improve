package dev.differentenchant.disenchant;
import dev.differentenchant.workstation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public final class DisenchantPartEntity extends WorkstationPartEntity {
    public DisenchantPartEntity(BlockPos p,BlockState s){super(DisenchantContent.ENTITY,p,s);}
    @Override public WorkstationStructure.Kind kind(){return WorkstationStructure.Kind.DISENCHANT;}
}
