package dev.differentenchant;
import dev.differentenchant.workstation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
/** Real material states, including support-first placement and inward-facing empty lecterns. */
public final class WorkstationFixtures {
    static void frame(ServerLevel l,BlockPos pos,WorkstationStructure.Kind kind){for(int i=0;i<18;i++)if(i!=13)l.setBlockAndUpdate(WorkstationStructure.position(pos,i),WorkstationStructure.template(kind,i));}
    static BlockPos build(ServerLevel l,BlockPos pos,WorkstationStructure.Kind kind){if(l.getBlockEntity(pos) instanceof WorkstationPartEntity)l.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());frame(l,pos,kind);l.setBlockAndUpdate(pos,kind.core().defaultBlockState());return pos;}
    static void ensure(ServerLevel l,BlockPos pos,WorkstationStructure.Kind kind){if(WorkstationStructure.controller(l,pos,kind)==null){build(l,pos,kind);DisenchantGameTests.check(WorkstationStructure.form(l,pos,kind).equals("formed"),"real complete workstation fixture");}}
    private WorkstationFixtures(){}
}
