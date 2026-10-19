package dev.differentenchant.workstation;

import java.util.*;
import dev.differentenchant.structure.StructurePattern;
import dev.differentenchant.disenchant.*;
import dev.differentenchant.research.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

/** Only the two independent workstations share this recovery machinery; enchanting stays unchanged. */
public final class WorkstationStructure {
    public enum Kind {
        DISENCHANT, RESEARCH;
        public Block part(){return this==DISENCHANT?DisenchantContent.PART:ResearchContent.PART;}
        public Block core(){return this==DISENCHANT?DisenchantContent.CORE:ResearchContent.TABLE;}
    }
    private static final Set<ServerLevel> BUSY=Collections.newSetFromMap(new IdentityHashMap<>());
    private record Snapshot(BlockPos pos,BlockState state,CompoundTag entity) {}
    public static BlockPos position(BlockPos core,int index){var o=StructurePattern.CELLS.get(index).offset();return core.offset(o.x(),o.y(),o.z());}
    public static boolean corner(int index){var o=StructurePattern.CELLS.get(index).offset();return index>=9&&o.x()!=0&&o.z()!=0;}
    public static Direction inward(int index){var o=StructurePattern.CELLS.get(index).offset();return o.x()<0?Direction.EAST:o.x()>0?Direction.WEST:o.z()<0?Direction.SOUTH:Direction.NORTH;}
    public static Block original(Kind kind,int index) {
        if(index==13)return kind.core();
        if(index<9)return kind==Kind.DISENCHANT?(index==4?Blocks.GOLD_BLOCK:Blocks.AMETHYST_BLOCK):(index==4?Blocks.CHISELED_QUARTZ_BLOCK:Blocks.QUARTZ_BLOCK);
        return kind==Kind.DISENCHANT?(corner(index)?Blocks.AMETHYST_CLUSTER:Blocks.REDSTONE_BLOCK):(corner(index)?Blocks.BOOKSHELF:Blocks.LECTERN);
    }
    public static BlockState template(Kind kind,int index) {
        var state=original(kind,index).defaultBlockState();
        if(kind==Kind.DISENCHANT&&corner(index))state=state.setValue(BlockStateProperties.FACING,Direction.UP);
        if(kind==Kind.RESEARCH&&index>=9&&index!=13&&!corner(index))state=state.setValue(BlockStateProperties.HORIZONTAL_FACING,inward(index));
        return state;
    }
    public static boolean loaded(Level level,BlockPos p){return !level.isOutsideBuildHeight(p)&&(level instanceof ServerLevel server?server.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)!=null:level.hasChunkAt(p));}
    public static boolean matches(Kind kind,int index,BlockState state) {
        if(!state.is(original(kind,index)))return false;
        if(kind==Kind.DISENCHANT&&corner(index))return state.getValue(BlockStateProperties.FACING)==Direction.UP;
        if(kind==Kind.RESEARCH&&index>=9&&index!=13&&!corner(index))return state.getValue(BlockStateProperties.HORIZONTAL_FACING)==inward(index)&&!state.getValue(LecternBlock.HAS_BOOK);
        return true;
    }
    /** Translation suffix; failure performs no writes. */
    public static String form(ServerLevel level,BlockPos core,Kind kind) {
        if(BUSY.contains(level))return "busy";
        var snapshots=new ArrayList<Snapshot>();
        for(int i=0;i<18;i++) {
            var p=position(core,i);if(!loaded(level,p))return "unloaded";var state=level.getBlockState(p);var entity=level.getBlockEntity(p);
            if(kind==Kind.RESEARCH&&state.is(Blocks.LECTERN)&&((state.getValue(LecternBlock.HAS_BOOK))||entity instanceof LecternBlockEntity lectern&&!lectern.getBook().isEmpty()))return "lectern_book";
            if(!matches(kind,i,state))return "structure";
            if(entity!=null&&!(kind==Kind.RESEARCH&&state.is(Blocks.LECTERN)&&entity instanceof LecternBlockEntity))return "block_entity";
            if(kind==Kind.DISENCHANT&&corner(i)&&!state.canSurvive(level,p))return "support";
            snapshots.add(new Snapshot(p,state,entity==null?null:entity.saveWithFullMetadata(level.registryAccess())));
        }
        UUID id=UUID.randomUUID();BUSY.add(level);
        try {
            for(int i=0;i<18;i++) {
                var s=snapshots.get(i);var formed=kind.part().defaultBlockState().setValue(WorkstationPartBlock.PART,i)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING,s.state().hasProperty(BlockStateProperties.HORIZONTAL_FACING)?s.state().getValue(BlockStateProperties.HORIZONTAL_FACING):Direction.NORTH)
                    .setValue(BlockStateProperties.WATERLOGGED,s.state().hasProperty(BlockStateProperties.WATERLOGGED)&&s.state().getValue(BlockStateProperties.WATERLOGGED));
                if(!level.setBlock(s.pos(),formed,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))throw new IllegalStateException("Workstation part placement failed");
                if(!(level.getBlockEntity(s.pos()) instanceof WorkstationPartEntity part))throw new IllegalStateException("Missing workstation part entity");
                part.machine=id;part.core=core.immutable();part.index=i;part.original=s.state();part.originalEntity=s.entity();part.setChanged();
            }
        } catch(RuntimeException error){for(var snapshot:snapshots)restore(level,snapshot);throw error;}
        finally{BUSY.remove(level);}
        for(var s:snapshots){level.sendBlockUpdated(s.pos(),level.getBlockState(s.pos()),level.getBlockState(s.pos()),Block.UPDATE_CLIENTS);notify(level,s.pos());if(level.getBlockState(s.pos()).getValue(BlockStateProperties.WATERLOGGED))level.scheduleTick(s.pos(),Fluids.WATER,Fluids.WATER.getTickDelay(level));}
        return "formed";
    }
    public static boolean belongs(WorkstationPartEntity part,WorkstationPartEntity source,int index) {
        return source.machine!=null&&source.kind()==part.kind()&&source.machine.equals(part.machine)&&source.core.equals(part.core)&&part.index==index
            &&part.getBlockPos().equals(position(source.core,index))&&part.getBlockState().is(source.kind().part())&&part.getBlockState().getValue(WorkstationPartBlock.PART)==index;
    }
    private static boolean projectionMatches(WorkstationPartEntity part) {
        var state=part.getBlockState();var original=part.original;
        var facing=original.hasProperty(BlockStateProperties.HORIZONTAL_FACING)?original.getValue(BlockStateProperties.HORIZONTAL_FACING):Direction.NORTH;
        boolean wet=original.hasProperty(BlockStateProperties.WATERLOGGED)&&original.getValue(BlockStateProperties.WATERLOGGED);
        return state.getValue(BlockStateProperties.HORIZONTAL_FACING)==facing&&state.getValue(BlockStateProperties.WATERLOGGED)==wet;
    }
    /** Works on synchronized client block entities as well, without requesting chunk data. */
    public static WorkstationPartEntity controller(Level level,BlockPos p,Kind kind) {
        if(!loaded(level,p)||!(level.getBlockEntity(p) instanceof WorkstationPartEntity part)||part.kind()!=kind||part.machine==null||!loaded(level,part.core))return null;
        if(!(level.getBlockEntity(part.core) instanceof WorkstationPartEntity master)||!belongs(master,part,13))return null;
        for(int i=0;i<18;i++){var target=position(master.core,i);if(!loaded(level,target)||!(level.getBlockEntity(target) instanceof WorkstationPartEntity other)||!belongs(other,master,i)||!matches(kind,i,other.original)||!projectionMatches(other))return null;}
        return master;
    }
    public static void check(ServerLevel level,WorkstationPartEntity source) {
        if(BUSY.contains(level))return;
        // Older disenchanters keep their saved materials; they dismantle instead of converting for free.
        if(source.index<0||source.index>=18||!matches(source.kind(),source.index,source.original)||!projectionMatches(source)){disassemble(level,source,null);return;}
        for(int i=0;i<18;i++){var p=position(source.core,i);if(loaded(level,p)&&(!(level.getBlockEntity(p) instanceof WorkstationPartEntity other)||!belongs(other,source,i)||!matches(source.kind(),i,other.original)||!projectionMatches(other))){disassemble(level,source,null);return;}}
    }
    public static void disassemble(ServerLevel level,WorkstationPartEntity source,BlockPos removed) {
        if(!BUSY.add(level))return;var changed=new ArrayList<BlockPos>();
        try {
            // Index order guarantees all nine supports precede every cluster/lectern. Same column is same chunk.
            for(int i=0;i<18;i++){var p=position(source.core,i);if(p.equals(removed)||!loaded(level,p))continue;
                if(level.getBlockEntity(p) instanceof WorkstationPartEntity part&&belongs(part,source,i)) {
                    var snapshot=new Snapshot(p,part.original,part.originalEntity);
                    if(snapshot.state().is(Blocks.AMETHYST_CLUSTER)&&(p.below().equals(removed)||!snapshot.state().canSurvive(level,p))) {
                        // Losing the support is ordinary breakage, not a second restored cluster plus its drops.
                        level.setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
                        Block.dropResources(snapshot.state(),level,p);
                    } else restore(level,snapshot);
                    changed.add(p);
                }
            }
        } finally{BUSY.remove(level);}
        for(var p:changed)notify(level,p);
    }
    private static void restore(ServerLevel level,Snapshot s) {
        level.setBlock(s.pos(),s.state(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        if(s.entity()!=null){var be=BlockEntity.loadStatic(s.pos(),s.state(),s.entity(),level.registryAccess());if(be!=null){level.setBlockEntity(be);be.setChanged();}}
        if(s.state().hasProperty(BlockStateProperties.WATERLOGGED)&&s.state().getValue(BlockStateProperties.WATERLOGGED))level.scheduleTick(s.pos(),Fluids.WATER,Fluids.WATER.getTickDelay(level));
    }
    private static void notify(ServerLevel level,BlockPos p){for(var d:Direction.values())if(!loaded(level,p.relative(d)))return;level.updateNeighborsAt(p,level.getBlockState(p).getBlock());}
    private WorkstationStructure(){}
}
