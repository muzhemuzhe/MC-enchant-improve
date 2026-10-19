package dev.differentenchant.disenchant;
import dev.differentenchant.workstation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
/** Stable entrypoint shared with the existing disenchanting menu. */
public final class DisenchantStructure {
    public static BlockPos position(BlockPos core,int index){return WorkstationStructure.position(core,index);}
    public static Block original(int index){return WorkstationStructure.original(WorkstationStructure.Kind.DISENCHANT,index);}
    public static boolean form(ServerLevel level,BlockPos core){return WorkstationStructure.form(level,core,WorkstationStructure.Kind.DISENCHANT).equals("formed");}
    public static DisenchantPartEntity controller(ServerLevel level,BlockPos p){return (DisenchantPartEntity)WorkstationStructure.controller(level,p,WorkstationStructure.Kind.DISENCHANT);}
    private DisenchantStructure(){}
}
