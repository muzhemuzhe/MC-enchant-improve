package dev.differentenchant.disenchant;

import java.util.*;
import dev.differentenchant.structure.StructurePattern;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Separate ownership/recovery from the enchanting machine. Never requests an absent chunk. */
public final class DisenchantStructure {
    static final Set<ServerLevel> BUSY=Collections.newSetFromMap(new IdentityHashMap<>());
    public static BlockPos position(BlockPos core,int index){var o=StructurePattern.CELLS.get(index).offset();return core.offset(o.x(),o.y(),o.z());}
    public static Block original(int index){return switch(StructurePattern.CELLS.get(index).material()){case FLOOR->Blocks.DEEPSLATE_BRICKS;case CORNER->Blocks.CHISELED_DEEPSLATE;case EDGE->Blocks.CUT_COPPER;case CONTROLLER->DisenchantContent.CORE;};}
    static boolean loaded(ServerLevel level,BlockPos p){return !level.isOutsideBuildHeight(p)&&level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)!=null;}
    public static boolean form(ServerLevel level,BlockPos core) {
        if(BUSY.contains(level))return false;
        var states=new ArrayList<BlockState>();
        for(int i=0;i<18;i++){var p=position(core,i);if(!loaded(level,p)||!level.getBlockState(p).is(original(i))||level.getBlockEntity(p)!=null)return false;states.add(level.getBlockState(p));}
        var id=UUID.randomUUID();BUSY.add(level);
        try {
            for(int i=0;i<18;i++) {
                var p=position(core,i);if(!level.setBlock(p,DisenchantContent.PART.defaultBlockState().setValue(DisenchantPartBlock.PART,i),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))throw new IllegalStateException("Disenchanter placement failed");
                if(!(level.getBlockEntity(p) instanceof DisenchantPartEntity part))throw new IllegalStateException("Missing disenchanter part");
                part.machine=id;part.core=core.immutable();part.index=i;part.original=states.get(i);part.setChanged();
            }
        } catch(RuntimeException error){for(int i=0;i<18;i++)level.setBlock(position(core,i),states.get(i),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);throw error;}
        finally{BUSY.remove(level);}
        for(int i=0;i<18;i++)notify(level,position(core,i));return true;
    }
    static boolean belongs(DisenchantPartEntity part,DisenchantPartEntity source,int index) {
        return source.machine!=null&&source.machine.equals(part.machine)&&source.core.equals(part.core)&&part.index==index&&part.getBlockPos().equals(position(source.core,index))
            &&part.getBlockState().is(DisenchantContent.PART)&&part.getBlockState().getValue(DisenchantPartBlock.PART)==index;
    }
    public static DisenchantPartEntity controller(ServerLevel level,BlockPos p) {
        if(!loaded(level,p)||!(level.getBlockEntity(p) instanceof DisenchantPartEntity part)||part.machine==null||!loaded(level,part.core))return null;
        if(!(level.getBlockEntity(part.core) instanceof DisenchantPartEntity master)||!belongs(master,part,13))return null;
        for(int i=0;i<18;i++){var target=position(master.core,i);if(!loaded(level,target)||!(level.getBlockEntity(target) instanceof DisenchantPartEntity other)||!belongs(other,master,i))return null;}
        return master;
    }
    static void check(ServerLevel level,DisenchantPartEntity source) {
        if(BUSY.contains(level))return;
        for(int i=0;i<18;i++){var p=position(source.core,i);if(loaded(level,p)&&(!(level.getBlockEntity(p) instanceof DisenchantPartEntity other)||!belongs(other,source,i))){disassemble(level,source,null);return;}}
    }
    static void disassemble(ServerLevel level,DisenchantPartEntity source,BlockPos removed) {
        if(!BUSY.add(level))return;var changed=new ArrayList<BlockPos>();
        try{for(int i=0;i<18;i++){var p=position(source.core,i);if(p.equals(removed)||!loaded(level,p))continue;
            if(level.getBlockEntity(p) instanceof DisenchantPartEntity part&&belongs(part,source,i)){level.setBlock(p,part.original,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);changed.add(p);}
        }}finally{BUSY.remove(level);}for(var p:changed)notify(level,p);
    }
    static void notify(ServerLevel level,BlockPos p){for(var d:Direction.values())if(!loaded(level,p.relative(d)))return;level.updateNeighborsAt(p,level.getBlockState(p).getBlock());}
    private DisenchantStructure(){}
}
