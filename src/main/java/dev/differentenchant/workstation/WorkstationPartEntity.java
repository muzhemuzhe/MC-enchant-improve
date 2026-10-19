package dev.differentenchant.workstation;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Recovery snapshot per part; never contains player menu materials. */
public abstract class WorkstationPartEntity extends BlockEntity {
    UUID machine;BlockPos core=BlockPos.ZERO;int index=-1;BlockState original=Blocks.AIR.defaultBlockState();CompoundTag originalEntity;
    /** Client implementation is installed by the client entrypoint; dedicated servers load no client classes. */
    public static Consumer<WorkstationPartEntity> clientTick=part->{};
    protected WorkstationPartEntity(BlockEntityType<?> type,BlockPos p,BlockState s){super(type,p,s);}
    public abstract WorkstationStructure.Kind kind();
    public UUID machineId(){return machine;}
    public int partIndex(){return index;}
    public BlockPos corePosition(){return core;}
    public BlockState originalState(){return original;}
    @Override protected void saveAdditional(ValueOutput out){super.saveAdditional(out);if(machine!=null)out.store("machine",UUIDUtil.CODEC,machine);out.store("core",BlockPos.CODEC,core);out.putInt("index",index);out.store("original",BlockState.CODEC,original);out.storeNullable("original_entity",CompoundTag.CODEC,originalEntity);}
    @Override protected void loadAdditional(ValueInput in){super.loadAdditional(in);machine=in.read("machine",UUIDUtil.CODEC).orElse(null);core=in.read("core",BlockPos.CODEC).orElse(BlockPos.ZERO);index=in.getIntOr("index",-1);original=in.read("original",BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());originalEntity=in.read("original_entity",CompoundTag.CODEC).orElse(null);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return saveCustomOnly(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void preRemoveSideEffects(BlockPos p,BlockState s){super.preRemoveSideEffects(p,s);if(level instanceof ServerLevel server&&machine!=null)WorkstationStructure.disassemble(server,this,p);}
    public void tick(){if(level instanceof ServerLevel server){if(machine!=null&&server.getGameTime()%20==index)WorkstationStructure.check(server,this);}else if(index==13)clientTick.accept(this);}
}
