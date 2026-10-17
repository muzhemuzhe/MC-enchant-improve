package dev.betterenchant.enchanting;

import java.util.*;
import dev.betterenchant.*;
import dev.betterenchant.structure.StructurePattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.chunk.LevelChunk;

/** Machine ownership and whole-machine bookshelf scanning without acquiring chunks. */
public final class MachineAccess {
    public static MachineControllerBlockEntity controller(ServerLevel level, BlockPos partPos) {
        if (!level.hasChunkAt(partPos) || !(level.getBlockEntity(partPos) instanceof MachineControllerBlockEntity part)) return null;
        BlockPos origin=part.controllerPosition();
        if (!level.hasChunkAt(origin) || !(level.getBlockEntity(origin) instanceof MachineControllerBlockEntity master)
                || master.machineId()==null || !master.machineId().equals(part.machineId()) || !origin.equals(master.getBlockPos()) || master.partIndex()!=13) return null;
        return complete(level,master) ? master : null;
    }
    public static boolean complete(ServerLevel level, MachineControllerBlockEntity master) {
        for(var cell:StructurePattern.CELLS) {
            BlockPos p=FormationService.position(master.getBlockPos(),cell,master.rotation());
            if(!level.hasChunkAt(p)) return false;
            if(!(level.getBlockEntity(p) instanceof MachineControllerBlockEntity part)
                    || !Objects.equals(master.machineId(),part.machineId()) || !master.getBlockPos().equals(part.controllerPosition())
                    || part.partIndex()!=cell.index() || part.rotation()!=master.rotation() || !level.getBlockState(p).is(BetterEnchant.PART)
                    || level.getBlockState(p).getValue(MachinePartBlock.PART)!=cell.index()) return false;
        }
        return master.machineId()!=null;
    }
    public static int shelves(ServerLevel level, MachineControllerBlockEntity master) {
        BlockPos origin=master.getBlockPos();
        return BookshelfScan.count(new BookshelfScan.View() {
            private BlockPos position(StructurePattern.Offset offset) {return origin.offset(offset.x(),offset.y(),offset.z());}
            private LevelChunk loadedChunk(StructurePattern.Offset offset) {
                var p=position(offset);
                // A ticket/holder alone does not mean FULL data is ready. Never request a chunk or wait for it.
                return level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(p.getX()),SectionPos.blockToSectionCoord(p.getZ()));
            }
            @Override public boolean isLoaded(StructurePattern.Offset offset) {return loadedChunk(offset)!=null;}
            @Override public boolean isProvider(StructurePattern.Offset offset) {
                var chunk=loadedChunk(offset);
                return chunk!=null && chunk.getBlockState(position(offset)).is(BlockTags.ENCHANTMENT_POWER_PROVIDER);
            }
        });
    }
    private MachineAccess() {}
}
