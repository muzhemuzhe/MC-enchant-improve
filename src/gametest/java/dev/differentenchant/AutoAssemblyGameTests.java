package dev.differentenchant;

import java.util.*;
import dev.differentenchant.workstation.*;
import dev.differentenchant.structure.StructurePattern;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class AutoAssemblyGameTests {
    static BlockState original(int kind,int i){return kind==0?FormationService.originalBlock(StructurePattern.CELLS.get(i).material()).defaultBlockState():WorkstationStructure.template(kind==1?WorkstationStructure.Kind.RESEARCH:WorkstationStructure.Kind.DISENCHANT,i);}
    static BlockPos pos(BlockPos core,int i){return WorkstationStructure.position(core,i);}
    static void frame(ServerLevel l,BlockPos core,int kind,int missing){for(int i=0;i<18;i++)l.setBlock(pos(core,i),i==missing?Blocks.AIR.defaultBlockState():original(kind,i),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);}
    static boolean formed(ServerLevel l,BlockPos p,int kind){return WorkstationStructure.loaded(l,p)&&l.getBlockState(p).is(kind==0?DifferentEnchant.PART:kind==1?dev.differentenchant.research.ResearchContent.PART:dev.differentenchant.disenchant.DisenchantContent.PART);}
    static UUID id(ServerLevel l,BlockPos p,int k){return k==0?((MachineControllerBlockEntity)l.getBlockEntity(p)).machineId:((WorkstationPartEntity)l.getBlockEntity(p)).machineId();}
    @GameTest(maxTicks=400) public void everyLastCellOfAllThreeMachinesAutomaticallyForms(GameTestHelper h){last(h,0);}
    private static void last(GameTestHelper h,int n){
        if(n==54){h.succeed();return;}int k=n/18,i=n%18;var p=h.absolutePos(new BlockPos(3,3,3));
        if(formed(h.getLevel(),p,k))h.getLevel().destroyBlock(p,false);
        frame(h.getLevel(),p,k,i);h.runAfterDelay(2,()->{h.assertTrue(!formed(h.getLevel(),p,k),"missing last cell never forms "+k+"/"+i);h.getLevel().setBlock(pos(p,i),original(k,i),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);h.assertTrue(!formed(h.getLevel(),p,k),"formation is deferred beyond state propagation");h.runAfterDelay(2,()->{h.assertTrue(formed(h.getLevel(),p,k),"automatic formation for last cell "+k+"/"+i);h.getLevel().destroyBlock(p,false);last(h,n+1);});});
    }
    @GameTest(maxTicks=40) public void wrongStateRejectsAndCorrectionAutomaticallyForms(GameTestHelper h){
        var l=h.getLevel();var r=h.absolutePos(new BlockPos(3,3,3));var d=h.absolutePos(new BlockPos(6,3,3));frame(l,r,1,-1);frame(l,d,2,-1);
        var rp=pos(r,10);l.setBlockAndUpdate(rp,l.getBlockState(rp).setValue(LecternBlock.FACING,Direction.NORTH));var dp=pos(d,9);l.setBlock(dp,l.getBlockState(dp).setValue(BlockStateProperties.FACING,Direction.DOWN),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        h.runAfterDelay(3,()->{h.assertTrue(!formed(l,r,1)&&!formed(l,d,2),"wrong lectern and cluster direction rejected");l.setBlockAndUpdate(rp,original(1,10));l.setBlockAndUpdate(dp,original(2,9));h.runAfterDelay(3,()->{h.assertTrue(formed(l,r,1)&&formed(l,d,2),"state correction schedules automatic formation");h.succeed();});});
    }
    @GameTest(maxTicks=60) public void stableIdentityAndRepairAfterBreakingAllThree(GameTestHelper h){
        var l=h.getLevel();var cores=new ArrayList<BlockPos>();for(int k=0;k<3;k++){var p=h.absolutePos(new BlockPos(k==1?5:k==2?3:2,3,k==2?5:2));cores.add(p);frame(l,p,k,-1);}
        h.runAfterDelay(3,()->{var ids=new ArrayList<UUID>();for(int k=0;k<3;k++){h.assertTrue(formed(l,cores.get(k),k),"auto formed "+k+" at "+cores.get(k)+" states "+WorkstationGameTests.states(l,cores.get(k)));ids.add(id(l,cores.get(k),k));for(int v=0;v<20;v++)l.setBlockAndUpdate(cores.get(k).offset(0,0,k==2?2:-2),v%2==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());}
            h.runAfterDelay(3,()->{for(int k=0;k<3;k++){var p=cores.get(k);h.assertTrue(ids.get(k).equals(id(l,p,k)),"neighbor updates never reform an assembled machine");l.destroyBlock(pos(p,10),false);for(int i=0;i<18;i++)if(i!=10)h.assertTrue(l.getBlockState(pos(p,i)).equals(original(k,i)),"break restores every surviving material");l.setBlockAndUpdate(pos(p,10),original(k,10));}
                h.runAfterDelay(3,()->{for(int k=0;k<3;k++)h.assertTrue(formed(l,cores.get(k),k)&&!ids.get(k).equals(id(l,cores.get(k),k)),"repaired structure reforms with new identity");h.succeed();});
            });
        });
    }
    @GameTest(maxTicks=900) public void savedUnformedFramesAutoAssembleAfterRealChunkReload(GameTestHelper h){
        var l=h.getLevel();var cores=List.of(new BlockPos(16383,-58,16383),new BlockPos(20479,-58,20479),new BlockPos(24575,-58,24575));var chunks=new LinkedHashSet<net.minecraft.world.level.ChunkPos>();
        for(var core:cores)for(int i=0;i<18;i++){var p=pos(core,i);chunks.add(new net.minecraft.world.level.ChunkPos(p.getX()>>4,p.getZ()>>4));}for(var chunk:chunks)l.setChunkForced(chunk.x(),chunk.z(),true);for(int k=0;k<3;k++)frame(l,cores.get(k),k,5);int[] phase={0};long[] ready={0};
        h.runAfterDelay(10,()->{for(int k=0;k<3;k++){var p=pos(cores.get(k),5);l.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4).setBlockState(p,original(k,5),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);}for(var chunk:chunks)l.setChunkForced(chunk.x(),chunk.z(),false);phase[0]=1;});
        h.runAtTickTime(899,()->{for(var chunk:chunks)l.setChunkForced(chunk.x(),chunk.z(),false);});
        h.succeedWhen(()->{h.assertTrue(phase[0]>0,"wait for test-only on-disk complete frames");if(phase[0]==1){for(var chunk:chunks)h.assertTrue(l.getChunkSource().getChunkNow(chunk.x(),chunk.z())==null,"wait for native unload of every machine chunk");for(var chunk:chunks)h.assertTrue(l.getChunkSource().getChunkNow(chunk.x(),chunk.z())==null,"no auto loader acquired unloaded chunks");for(var chunk:chunks)l.setChunkForced(chunk.x(),chunk.z(),true);phase[0]=2;ready[0]=l.getGameTime()+6;}h.assertTrue(l.getGameTime()>=ready[0],"wait for deferred palette scan after reload");for(int k=0;k<3;k++)h.assertTrue(formed(l,cores.get(k),k),"chunk-load discovery automatically forms complete saved frame "+k);h.assertTrue(l.getPoiManager().getType(cores.get(0)).isEmpty(),"assembled enchanting controller is not a villager POI");for(var chunk:chunks)l.setChunkForced(chunk.x(),chunk.z(),false);});
    }
    @GameTest(maxTicks=40) public void overlappingCandidatesCannotShareFormedParts(GameTestHelper h){
        var l=h.getLevel();var a=h.absolutePos(new BlockPos(2,3,3));var b=h.absolutePos(new BlockPos(4,3,3));frame(l,a,0,-1);frame(l,b,0,-1);
        h.runAfterDelay(3,()->{boolean first=formed(l,a,0),second=formed(l,b,0);h.assertTrue(first!=second,"overlapping complete frames form one owner, never share technical parts");var winner=first?a:b;var waiting=first?b:a;l.destroyBlock(winner,false);h.runAfterDelay(3,()->{h.assertTrue(formed(l,waiting,0)&&l.getBlockState(winner).isAir(),"restored shared materials can form the other frame without duplicating the broken core");h.succeed();});});
    }
}
