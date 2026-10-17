package dev.differentenchant;

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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import dev.differentenchant.resonance.*;

/** All parts retain recovery data, even when their controller chunk is absent. */
public final class MachineControllerBlockEntity extends BlockEntity {
    UUID machineId;
    BlockPos controller = BlockPos.ZERO;
    int rotation;
    int index = -1;
    BlockState original = Blocks.AIR.defaultBlockState();
    CompoundTag originalEntity;
    List<BlockState> controllerSnapshot = List.of();
    private int machineTier = 1;
    private final SimpleContainer core = new SimpleContainer(1) {
        @Override public void setChanged() { super.setChanged(); MachineControllerBlockEntity.this.setChanged(); }
    };
    public SimpleContainer coreInventory() { return core; }

    /** Called only within a successful menu commit on the server thread. */
    public void chargeCore(int paidLevels, boolean creative) {
        if (!(level instanceof ServerLevel server) || index != 13 || creative) return;
        ItemStack stack = core.getItem(0);
        if (!stack.is(ResonanceItems.CORE) || stack.getCount() != 1) return;
        var result = ResonancePolicy.charge(ResonanceItems.energy(stack), paidLevels, false, machineTier);
        if (result.completed()) {
            ItemStack attuned = stack.transmuteCopy(ResonanceItems.ATTUNED_CORE, 1);
            attuned.remove(ResonanceItems.ENERGY);
            core.setItem(0, attuned);
            machineTier = result.tier();
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, worldPosition.getX()+.5, worldPosition.getY()+.6, worldPosition.getZ()+.5, 32, .7, .4, .7, .5);
            server.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.BLOCKS, .8f, 1.3f);
        } else {
            ItemStack charged = stack.copy(); charged.set(ResonanceItems.ENERGY, result.energy()); core.setItem(0, charged);
        }
        setChanged();
    }

    /** Drain before spawning/restoring, including when the controller itself was the broken part. */
    void dropCore(ServerLevel server) {
        if (index != 13) return;
        ItemStack drop = core.removeItemNoUpdate(0);
        setChanged();
        if (!drop.isEmpty()) net.minecraft.world.Containers.dropItemStack(server, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), drop);
    }

    public UUID machineId() { return machineId; }
    public BlockPos controllerPosition() { return controller; }
    public int partIndex() { return index; }
    public int rotation() { return rotation; }
    public int machineTier() { return machineTier; }
    public void setMachineTier(int tier) {
        if (tier < 1 || tier > dev.differentenchant.enchanting.EnchantingConfig.current().maxTier()) throw new IllegalArgumentException("Invalid machine tier");
        machineTier = tier;
        setChanged();
    }

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(DifferentEnchant.PART_ENTITY, pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (machineId != null) out.store("machine", UUIDUtil.CODEC, machineId);
        out.store("controller", BlockPos.CODEC, controller);
        out.putInt("rotation", rotation);
        out.putInt("index", index);
        out.putInt("machine_tier", machineTier);
        if (index == 13) out.store("resonance_core", ItemStack.OPTIONAL_CODEC, core.getItem(0));
        if (index >= 0 && index < 18) {
            var offset = dev.differentenchant.structure.StructurePattern.CELLS.get(index).offset();
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
        machineTier = Math.clamp(in.getIntOr("machine_tier", 1), 1, 10);
        core.setItem(0, index == 13 ? in.read("resonance_core", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY) : ItemStack.EMPTY);
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
