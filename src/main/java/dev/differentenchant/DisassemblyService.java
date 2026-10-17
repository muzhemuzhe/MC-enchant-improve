package dev.differentenchant;

import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import dev.differentenchant.structure.StructurePattern;

public final class DisassemblyService {
    static boolean belongs(MachineControllerBlockEntity candidate, MachineControllerBlockEntity source, int index) {
        return Objects.equals(candidate.machineId, source.machineId) && candidate.controller.equals(source.controller)
                && candidate.rotation == source.rotation && candidate.index == index
                && candidate.getBlockPos().equals(FormationService.position(source.controller, StructurePattern.CELLS.get(index), source.rotation))
                && candidate.getBlockState().getValue(MachinePartBlock.PART) == index;
    }
    public static void checkIntegrity(ServerLevel level, MachineControllerBlockEntity source) {
        if (FormationService.BUSY.contains(level)) return;
        for (var cell : StructurePattern.CELLS) {
            BlockPos p = FormationService.position(source.controller, cell, source.rotation);
            if (!level.hasChunkAt(p)) continue;
            if (!(level.getBlockEntity(p) instanceof MachineControllerBlockEntity other) || !belongs(other, source, cell.index())) {
                disassemble(level, source, null);
                return;
            }
        }
    }
    public static void disassemble(ServerLevel level, MachineControllerBlockEntity source, BlockPos removed) {
        if (!FormationService.BUSY.add(level)) return;
        var restored = new ArrayList<BlockPos>();
        try {
            // The removed controller may already be absent from the world's block-entity lookup.
            source.dropCore(level);
            if (level.hasChunkAt(source.controller) && level.getBlockEntity(source.controller) instanceof MachineControllerBlockEntity master
                    && belongs(master, source, 13)) master.dropCore(level);
            for (var cell : StructurePattern.CELLS) {
                BlockPos p = FormationService.position(source.controller, cell, source.rotation);
                if (p.equals(removed) || !level.hasChunkAt(p)) continue;
                if (level.getBlockEntity(p) instanceof MachineControllerBlockEntity part && belongs(part, source, cell.index())) {
                    FormationService.restore(level, new FormationService.Snapshot(p, part.original, part.originalEntity));
                    restored.add(p);
                }
            }
        } finally {
            FormationService.BUSY.remove(level);
        }
        for (BlockPos p : restored) FormationService.notifyLoadedNeighbors(level, p);
    }
    private DisassemblyService() {}
}
