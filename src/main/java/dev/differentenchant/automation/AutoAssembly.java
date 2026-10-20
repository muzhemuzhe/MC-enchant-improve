package dev.differentenchant.automation;

import java.util.*;
import dev.differentenchant.*;
import dev.differentenchant.structure.StructurePattern;
import dev.differentenchant.workstation.WorkstationStructure;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded, deferred server work triggered by state changes or chunk loads; never acquires chunks. */
public final class AutoAssembly {
    private enum Kind { ENCHANT, RESEARCH, DISENCHANT }
    private record Candidate(BlockPos core,Kind kind){}
    private record Pending(long due,ServerPlayer actor){}
    private static final Map<ServerLevel,Map<Candidate,Pending>> PENDING=new IdentityHashMap<>();
    private static final Map<ServerLevel,Map<ChunkPos,Long>> CHUNKS=new IdentityHashMap<>();
    private static Block original(Kind k,int i){return k==Kind.ENCHANT?FormationService.originalBlock(StructurePattern.CELLS.get(i).material()):WorkstationStructure.original(k==Kind.RESEARCH?WorkstationStructure.Kind.RESEARCH:WorkstationStructure.Kind.DISENCHANT,i);}
    private static boolean core(BlockState s){return s.is(Blocks.ENCHANTING_TABLE)||s.is(dev.differentenchant.research.ResearchContent.TABLE)||s.is(dev.differentenchant.disenchant.DisenchantContent.CORE);}
    private static final class Materials {
        private static final Set<Block> BLOCKS=new HashSet<>();
        static {for(var kind:Kind.values())for(int i=0;i<18;i++)BLOCKS.add(original(kind,i));}
    }
    public static void changed(ServerLevel level,BlockPos pos,BlockState state){
        changed(level,pos,state,false);
    }
    private static void changed(ServerLevel level,BlockPos pos,BlockState state,boolean observation){
        if(level.isDebug()||!Materials.BLOCKS.contains(state.getBlock()))return;
        // Small fixed set, including state changes to lecterns/clusters; technical formed parts never match.
        for(var kind:Kind.values())for(int i=0;i<18;i++)if(state.is(original(kind,i))){
            var o=StructurePattern.CELLS.get(i).offset();var center=pos.offset(-o.x(),-o.y(),-o.z());
            if(WorkstationStructure.loaded(level,center)&&level.getBlockState(center).is(original(kind,13))){
                var key=new Candidate(center.immutable(),kind);var queue=PENDING.computeIfAbsent(level,k->new LinkedHashMap<>());
                var actor=AssemblyActor.current();var owner=actor!=null&&actor.level()==level?actor:null;
                queue.compute(key,(k,previous)->observation&&previous!=null?previous:new Pending(previous==null?level.getGameTime()+1:previous.due,owner));
            }
        }
    }
    private static void loaded(ServerLevel level,ChunkPos pos){CHUNKS.computeIfAbsent(level,k->new LinkedHashMap<>()).putIfAbsent(pos,level.getGameTime()+1);}
    private static void scanLoadedBorder(ServerLevel level,ChunkPos loaded){
        // A controller may be one cell outside this chunk. Palette checks skip sections without controllers.
        for(int cx=loaded.x()-1;cx<=loaded.x()+1;cx++)for(int cz=loaded.z()-1;cz<=loaded.z()+1;cz++){
            var chunk=level.getChunkSource().getChunkNow(cx,cz);if(chunk==null)continue;
            int minX=Math.max(cx*16,loaded.x()*16-1),maxX=Math.min(cx*16+15,loaded.x()*16+16);
            int minZ=Math.max(cz*16,loaded.z()*16-1),maxZ=Math.min(cz*16+15,loaded.z()*16+16);
            var sections=chunk.getSections();
            for(int si=0;si<sections.length;si++){var section=sections[si];if(!section.maybeHas(AutoAssembly::core))continue;
                int y0=level.getMinY()+si*16;
                for(int y=0;y<16;y++)for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
                    var state=section.getBlockState(x&15,y,z&15);if(core(state))changed(level,new BlockPos(x,y0+y,z),state,true);
                }
            }
        }
    }
    private static void tick(ServerLevel level){
        var chunks=CHUNKS.get(level);
        if(chunks!=null){var ready=new ArrayList<ChunkPos>();chunks.entrySet().removeIf(e->{if(e.getValue()>level.getGameTime()||level.getChunkSource().getChunkNow(e.getKey().x(),e.getKey().z())==null)return false;ready.add(e.getKey());return true;});for(var p:ready)scanLoadedBorder(level,p);if(chunks.isEmpty())CHUNKS.remove(level);}
        var pending=PENDING.get(level);if(pending==null)return;
        var ready=new LinkedHashMap<Candidate,Pending>();pending.entrySet().removeIf(e->{if(e.getValue().due>level.getGameTime())return false;ready.put(e.getKey(),e.getValue());return true;});
        for(var entry:ready.entrySet()){
            var c=entry.getKey();
            boolean allLoaded=true;for(var cell:StructurePattern.CELLS){var o=cell.offset();if(!WorkstationStructure.loaded(level,c.core.offset(o.x(),o.y(),o.z()))){allLoaded=false;break;}}
            if(!allLoaded||!level.getBlockState(c.core).is(original(c.kind,13)))continue;
            boolean formed;
            if(c.kind==Kind.ENCHANT){FormationService.form(level,c.core,Direction.NORTH);formed=level.getBlockState(c.core).is(DifferentEnchant.PART);}
            else formed=WorkstationStructure.form(level,c.core,c.kind==Kind.RESEARCH?WorkstationStructure.Kind.RESEARCH:WorkstationStructure.Kind.DISENCHANT).equals("formed");
            if(formed){
                var actor=entry.getValue().actor;
                if(actor!=null){
                    dev.differentenchant.advancement.ModAdvancements.committed(actor,switch(c.kind){case ENCHANT->"old_arts_reborn";case RESEARCH->"place_of_learning";case DISENCHANT->"disenchant";});
                    // Disconnect can save the player before this deferred tick; retain native progress in that case too.
                    if(actor.hasDisconnected())actor.getAdvancements().save();
                }
                level.playSound(null,c.core,SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.BLOCKS,.35f,1.25f);level.sendParticles(ParticleTypes.ENCHANT,c.core.getX()+.5,c.core.getY()+1,c.core.getZ()+.5,12,.7,.3,.7,.12);}
        }
        if(pending.isEmpty())PENDING.remove(level);
    }
    public static void initialize(){
        ServerTickEvents.END_LEVEL_TICK.register(AutoAssembly::tick);
        ServerChunkEvents.CHUNK_LOAD.register((level,chunk,generated)->loaded(level,chunk.getPos()));
        ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register((level,chunk,before,after)->{
            if(after.isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)&&!before.isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL))loaded(level,chunk.getPos());
            else if(!after.isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)){var pending=CHUNKS.get(level);if(pending!=null)pending.remove(chunk.getPos());}
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level,chunk)->{var pending=CHUNKS.get(level);if(pending!=null)pending.remove(chunk.getPos());});
        ServerLevelEvents.UNLOAD.register((server,level)->{PENDING.remove(level);CHUNKS.remove(level);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{PENDING.clear();CHUNKS.clear();});
    }
    private AutoAssembly(){}
}
