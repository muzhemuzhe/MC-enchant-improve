package dev.differentenchant.disenchant;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class DisenchantPartEntity extends BlockEntity {
    UUID machine;BlockPos core=BlockPos.ZERO;int index=-1;BlockState original=Blocks.AIR.defaultBlockState();
    public DisenchantPartEntity(BlockPos p,BlockState s){super(DisenchantContent.ENTITY,p,s);}
    public UUID machineId(){return machine;}
    @Override protected void saveAdditional(ValueOutput out){super.saveAdditional(out);if(machine!=null)out.store("machine",UUIDUtil.CODEC,machine);out.store("core",BlockPos.CODEC,core);out.putInt("index",index);out.store("original",BlockState.CODEC,original);}
    @Override protected void loadAdditional(ValueInput in){super.loadAdditional(in);machine=in.read("machine",UUIDUtil.CODEC).orElse(null);core=in.read("core",BlockPos.CODEC).orElse(BlockPos.ZERO);index=in.getIntOr("index",-1);original=in.read("original",BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());}
    @Override public void preRemoveSideEffects(BlockPos p,BlockState s){super.preRemoveSideEffects(p,s);if(level instanceof ServerLevel server&&machine!=null)DisenchantStructure.disassemble(server,this,p);}
    public void tick(){if(level instanceof ServerLevel server&&machine!=null&&server.getGameTime()%20==index)DisenchantStructure.check(server,this);}
}
