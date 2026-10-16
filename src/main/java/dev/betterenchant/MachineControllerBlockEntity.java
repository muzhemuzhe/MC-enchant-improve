package dev.betterenchant;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** All parts retain recovery data, even when their controller chunk is absent. */
public final class MachineControllerBlockEntity extends BlockEntity {
    UUID machineId;
    BlockPos controller = BlockPos.ZERO;
    int rotation;
    int index = -1;
    BlockState original = Blocks.AIR.defaultBlockState();
    CompoundTag originalEntity;
    List<BlockState> controllerSnapshot = List.of();

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BetterEnchant.PART_ENTITY, pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (machineId != null) out.store("machine", UUIDUtil.CODEC, machineId);
        out.store("controller", BlockPos.CODEC, controller);
        out.putInt("rotation", rotation);
        out.putInt("index", index);
        if (index >= 0 && index < 18) {
            var offset = dev.betterenchant.structure.StructurePattern.CELLS.get(index).offset();
            out.store("local_offset", BlockPos.CODEC, new BlockPos(offset.x(), offset.y(), offset.z()));
        }
        out.store("original", BlockState.CODEC, original);
        out.storeNullable("original_entity", CompoundTag.CODEC, originalEntity);
        if (!controllerSnapshot.isEmpty()) out.store("snapshot", BlockState.CODEC.listOf(), controllerSnapshot);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        machineId = in.read("machine", UUIDUtil.CODEC).orElse(null);
        controller = in.read("controller", BlockPos.CODEC).orElse(BlockPos.ZERO);
        rotation = Math.floorMod(in.getIntOr("rotation", 0), 4);
        index = in.getIntOr("index", -1);
        original = in.read("original", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());
        originalEntity = in.read("original_entity", CompoundTag.CODEC).orElse(null);
        controllerSnapshot = in.read("snapshot", BlockState.CODEC.listOf()).orElse(List.of());
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server && machineId != null) DisassemblyService.disassemble(server, this, pos);
    }

    public void checkIntegrity() {
        if (level instanceof ServerLevel server && machineId != null && server.getGameTime() % 20 == index) {
            DisassemblyService.checkIntegrity(server, this);
        }
    }
}
