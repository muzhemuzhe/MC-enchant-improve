package dev.betterenchant.enchanting;

import java.util.*;
import dev.betterenchant.*;
import dev.betterenchant.structure.StructurePattern;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.EnchantingTableBlock;

/** Machine ownership and vanilla-style bookshelf scanning without acquiring chunks. */
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
        int count=0;
        BlockPos origin=master.getBlockPos();
        for(BlockPos offset:EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            BlockPos provider=origin.offset(offset);
            BlockPos gap=origin.offset(offset.getX()/2,offset.getY(),offset.getZ()/2);
            if(!level.hasChunkAt(provider) || !level.hasChunkAt(gap)) continue;
            if(!level.getBlockState(provider).is(BlockTags.ENCHANTMENT_POWER_PROVIDER)) continue;
            boolean ownPart=level.getBlockEntity(gap) instanceof MachineControllerBlockEntity part
                    && Objects.equals(master.machineId(),part.machineId()) && origin.equals(part.controllerPosition());
            if(ownPart || level.getBlockState(gap).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER)) count++;
        }
        return count;
    }
    private MachineAccess() {}
}
