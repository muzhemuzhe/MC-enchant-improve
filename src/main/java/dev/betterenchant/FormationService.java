package dev.betterenchant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import dev.betterenchant.structure.StructurePattern;
import dev.betterenchant.structure.StructurePattern.*;
import dev.betterenchant.structure.StructureValidator;

public final class FormationService {
    static final Set<ServerLevel> BUSY = Collections.newSetFromMap(new IdentityHashMap<>());
    record Snapshot(BlockPos pos, BlockState state, CompoundTag entity) {}
    public static Block originalBlock(Material material) {
        return switch (material) {
            case FLOOR -> Blocks.POLISHED_DEEPSLATE;
            case CORNER -> Blocks.AMETHYST_BLOCK;
            case EDGE -> Blocks.OBSIDIAN;
            case CONTROLLER -> Blocks.ENCHANTING_TABLE;
        };
    }
    public static int rotation(Direction facing) {
        return switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
    }
    static BlockPos pos(Offset p) { return new BlockPos(p.x(), p.y(), p.z()); }
    public static BlockPos position(BlockPos controller, Cell cell, int rotation) {
        Offset o = cell.offset().rotate(rotation);
        return controller.offset(o.x(), o.y(), o.z());
    }
    public static String form(ServerLevel level, BlockPos controller, Direction facing) {
        if (BUSY.contains(level)) return "Busy; try again.";
        int rotation = rotation(facing);
        var plan = StructureValidator.validate(new StructureValidator.View() {
            public boolean isLoaded(Offset p) { return !level.isOutsideBuildHeight(pos(p)) && level.hasChunkAt(pos(p)); }
            public boolean matches(Offset p, Material expected) { return level.getBlockState(pos(p)).is(originalBlock(expected)); }
        }, new Offset(controller.getX(), controller.getY(), controller.getZ()), rotation);
        if (!plan.valid()) return "Formation rejected: " + plan.failure();
        var snapshots = new ArrayList<Snapshot>();
        for (var placement : plan.placements()) {
            BlockPos p = pos(placement.position());
            BlockEntity entity = level.getBlockEntity(p);
            snapshots.add(new Snapshot(p, level.getBlockState(p), entity == null ? null : entity.saveWithFullMetadata(level.registryAccess())));
        }
        UUID id = UUID.randomUUID();
        BUSY.add(level);
        try {
            for (int i = 0; i < snapshots.size(); i++) {
                Snapshot s = snapshots.get(i);
                BlockState formed = BetterEnchant.PART.defaultBlockState().setValue(MachinePartBlock.PART, i)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
                if (!level.setBlock(s.pos(), formed, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) throw new IllegalStateException("Part placement failed");
                if (!(level.getBlockEntity(s.pos()) instanceof MachineControllerBlockEntity part)) throw new IllegalStateException("Missing part entity");
                part.machineId = id;
                part.controller = controller.immutable();
                part.rotation = rotation;
                part.index = i;
                part.original = s.state();
                part.originalEntity = s.entity();
                if (s.pos().equals(controller)) part.controllerSnapshot = snapshots.stream().map(Snapshot::state).toList();
                part.setChanged();
            }
        } catch (RuntimeException failure) {
            for (Snapshot snapshot : snapshots) restore(level, snapshot);
            throw failure;
        } finally {
            BUSY.remove(level);
        }
        for (Snapshot s : snapshots) notifyLoadedNeighbors(level, s.pos());
        return "Formed 18/18, facing " + facing + ".";
    }
    static void restore(ServerLevel level, Snapshot snapshot) {
        level.setBlock(snapshot.pos(), snapshot.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (snapshot.entity() != null) {
            BlockEntity restored = BlockEntity.loadStatic(snapshot.pos(), snapshot.state(), snapshot.entity(), level.registryAccess());
            if (restored != null) { level.setBlockEntity(restored); restored.setChanged(); }
        }
    }
    static void notifyLoadedNeighbors(ServerLevel level, BlockPos pos) {
        // Vanilla neighbor dispatch reads all six states. Do not dispatch at an unloaded boundary.
        for (Direction direction : Direction.values()) if (!level.hasChunkAt(pos.relative(direction))) return;
        level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
    }
    private FormationService() {}
}
