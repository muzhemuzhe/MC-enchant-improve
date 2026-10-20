package dev.differentenchant;

import java.util.*;
import dev.differentenchant.automation.AssemblyActor;
import dev.differentenchant.research.*;
import dev.differentenchant.resonance.ResonanceItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;

public final class AdvancementGameTests {
    static final List<String> IDS=List.of("old_arts_reborn","attuned_core","lost_chapter","place_of_learning","price_of_knowledge","disenchant");
    static AdvancementHolder holder(ServerPlayer p,String id){var h=p.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("differentenchant",id));if(h==null)throw new AssertionError("missing advancement "+id);return h;}
    static boolean done(ServerPlayer p,String id){return p.getAdvancements().getOrStartProgress(holder(p,id)).isDone();}
    static void reset(ServerPlayer p,String id){for(var key:holder(p,id).value().criteria().keySet())p.getAdvancements().revoke(holder(p,id),key);}
    static String formation(int k){return k==0?"old_arts_reborn":k==1?"place_of_learning":"disenchant";}
    static void lastBlock(ServerPlayer p,BlockPos core,int kind,int index){
        var l=p.level();var target=AutoAssemblyGameTests.pos(core,index);var support=target.below();
        if(index<9)l.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());
        p.teleportTo(core.getX()+3.5,core.getY()+2,core.getZ()+3.5);p.setYRot(180);p.setXRot(80);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AutoAssemblyGameTests.original(kind,index).getBlock().asItem()));p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        p.gameMode.useItemOn(p,l,p.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false));
        if(!(index==13?l.getBlockState(target).is(AutoAssemblyGameTests.original(kind,index).getBlock()):l.getBlockState(target).equals(AutoAssemblyGameTests.original(kind,index))))throw new AssertionError("actual placement state "+kind+"/"+index+": "+l.getBlockState(target));
        if(AssemblyActor.current()!=null)throw new AssertionError("actor scope leaked");
    }
    @GameTest(maxTicks=150) public void actualPlayerPlacementAttributesCenterEdgeAndFloorForEachMachine(GameTestHelper h){formationCase(h,0);}
    private static void formationCase(GameTestHelper h,int n){
        if(n==9){h.succeed();return;}int kind=n/3,index=new int[]{13,10,0}[n%3];var l=h.getLevel();var core=h.absolutePos(new BlockPos(3,3,3));var a=BatchTwoGameTests.player(h);var observer=BatchTwoGameTests.player(h);observer.teleportTo(core.getX()+3.5,core.getY()+2,core.getZ()+3.5);
        AutoAssemblyGameTests.frame(l,core,kind,index);h.runAfterDelay(2,()->{
            for(String id:IDS)h.assertTrue(!done(a,id)&&!done(observer,id),"incomplete frame grants nothing");
            lastBlock(a,core,kind,index);h.assertTrue(!done(a,formation(kind)),"placement alone before deferred formation grants nothing");
            h.runAfterDelay(3,()->{h.assertTrue(AutoAssemblyGameTests.formed(l,core,kind)&&done(a,formation(kind)),"successful deferred formation credits actual last block player "+kind+"/"+index);h.assertTrue(!done(observer,formation(kind)),"nearby observer gets nothing");if(kind!=0)h.assertTrue(!done(a,"old_arts_reborn"),"parent is not a prerequisite or automatic award");var progress=a.getAdvancements().getOrStartProgress(holder(a,formation(kind)));var date=progress.getCriterion("committed").getObtained();l.setBlockAndUpdate(core.offset(0,0,2),Blocks.STONE.defaultBlockState());h.runAfterDelay(2,()->{h.assertTrue(date.equals(progress.getCriterion("committed").getObtained()),"no repeated criterion timestamp");l.destroyBlock(core,false);lastBlock(a,core,kind,13);h.runAfterDelay(3,()->{h.assertTrue(AutoAssemblyGameTests.formed(l,core,kind)&&date.equals(progress.getCriterion("committed").getObtained()),"real disassembly and player reassembly do not reaward completed progress");l.destroyBlock(core,false);formationCase(h,n+1);});});});
        });
    }
    @GameTest(maxTicks=35) public void commandFormationHasNoOwnerAndLaterUnownedMutationClearsStaleOwner(GameTestHelper h){
        var l=h.getLevel();var p=BatchTwoGameTests.player(h);var core=h.absolutePos(new BlockPos(3,3,3));AutoAssemblyGameTests.frame(l,core,0,13);
        h.runAfterDelay(2,()->{lastBlock(p,core,0,13);l.setBlockAndUpdate(core,Blocks.AIR.defaultBlockState());l.setBlockAndUpdate(core,Blocks.ENCHANTING_TABLE.defaultBlockState());h.runAfterDelay(3,()->{h.assertTrue(AutoAssemblyGameTests.formed(l,core,0)&&!done(p,"old_arts_reborn"),"unowned final state mutation does not inherit prior actor");l.destroyBlock(core,false);AutoAssemblyGameTests.frame(l,core,1,-1);h.runAfterDelay(3,()->{h.assertTrue(AutoAssemblyGameTests.formed(l,core,1)&&!done(p,"place_of_learning"),"command/fixture formation never awards by proximity");h.succeed();});});});
    }
    @GameTest(maxTicks=30) public void lastActualBuilderWinsAndDisconnectBeforeDeferredFormationIsSaved(GameTestHelper h){
        var l=h.getLevel();var a=BatchTwoGameTests.player(h);var b=BatchTwoGameTests.player(h);var core=h.absolutePos(new BlockPos(3,3,3));a.teleportTo(core.getX()+3.5,core.getY()+2,core.getZ()+3.5);b.teleportTo(core.getX()+3.5,core.getY()+2,core.getZ()+3.5);AutoAssemblyGameTests.frame(l,core,0,13);l.setBlockAndUpdate(AutoAssemblyGameTests.pos(core,10),Blocks.AIR.defaultBlockState());
        h.runAfterDelay(2,()->{lastBlock(a,core,0,13);lastBlock(b,core,0,10);b.getAdvancements().save();b.disconnect();h.runAfterDelay(3,()->{h.assertTrue(AutoAssemblyGameTests.formed(l,core,0)&&!done(a,"old_arts_reborn")&&done(b,"old_arts_reborn"),"only final actual builder credited even after disconnect");b.getAdvancements().reload(l.getServer().getAdvancements());h.assertTrue(done(b,"old_arts_reborn"),"post-disconnect deferred award persisted through native JSON reload");h.succeed();});});
    }
    @GameTest public void itemCriteriaUseRealInventoryAndStayIndependentAcrossPlayers(GameTestHelper h){
        var p=BatchTwoGameTests.player(h);var other=BatchTwoGameTests.player(h);var master=EnchantingMenuGameTests.machine(h);master.coreInventory().setItem(0,new ItemStack(ResonanceItems.ATTUNED_CORE));
        var menu=EnchantingMenuGameTests.menu(h,master,p);p.inventoryMenu.broadcastChanges();h.assertTrue(!done(p,"attuned_core")&&!done(other,"attuned_core"),"machine contents are not player acquisition");menu.quickMoveStack(p,38);p.inventoryMenu.broadcastChanges();h.assertTrue(done(p,"attuned_core")&&!done(other,"attuned_core"),"native machine take credits only recipient");menu.removed(p);p.containerMenu=p.inventoryMenu;
        var item=new ItemEntity(h.getLevel(),p.getX(),p.getY(),p.getZ(),new ItemStack(ResearchContent.LOST_SCROLL));h.getLevel().addFreshEntity(item);item.playerTouch(p);p.inventoryMenu.broadcastChanges();h.assertTrue(item.isRemoved()&&done(p,"lost_chapter")&&!done(p,"old_arts_reborn"),"actual dropped scroll pickup awards before root");
        var v=EnchanterGameTests.villager(h,2);var trade=EnchanterGameTests.menu(other,v);trade.getSlot(0).set(EnchanterGameTests.book(h.getLevel(),"minecraft:vanishing_curse",1));trade.broadcastChanges();h.assertTrue(!done(other,"attuned_core"),"trade preview alone does not award");trade.quickMoveStack(other,2);other.inventoryMenu.broadcastChanges();h.assertTrue(done(other,"attuned_core"),"actual native trade acquisition awards");trade.removed(other);other.containerMenu=other.inventoryMenu;h.succeed();
    }
    @GameTest public void onlyCommittedFailedResearchAwardsAndDeliversScroll(GameTestHelper h){
        var p=BatchTwoGameTests.player(h);var core=h.absolutePos(new BlockPos(3,3,3));var m=ResearchGameTests.menu(h.getLevel(),core,p);var recipe=ResearchGameTests.find(h.getLevel(),ResearchPolicy.Result::scroll);ResearchGameTests.fill(m,h.getLevel(),recipe,2);
        h.assertTrue(!done(p,"price_of_knowledge")&&!done(p,"lost_chapter")&&m.getSlot(3).getItem().is(Items.BOOK),"unknown scroll preview does not grant either advancement");m.setCarried(new ItemStack(Items.STONE,64));m.clicked(3,0,ContainerInput.PICKUP,p);h.assertTrue(!done(p,"price_of_knowledge")&&m.getSlot(2).getItem().getCount()==2,"blocked delivery does not award/consume");m.setCarried(ItemStack.EMPTY);var a=m.getSlot(0).getItem().copy();var b=m.getSlot(1).getItem().copy();m.clicked(3,0,ContainerInput.PICKUP,p);
        h.assertTrue(done(p,"price_of_knowledge")&&done(p,"lost_chapter")&&m.getCarried().is(ResearchContent.LOST_SCROLL),"actual cursor delivery immediately grants both");h.assertTrue(m.getSlot(2).getItem().getCount()==1&&ItemStack.matches(a,m.getSlot(0).getItem())&&ItemStack.matches(b,m.getSlot(1).getItem()),"core consumed once and exact books retained");h.assertTrue(!done(p,"place_of_learning")&&!done(p,"old_arts_reborn"),"deep child never auto awards ancestors");m.removed(p);p.containerMenu=p.inventoryMenu;
        reset(p,"price_of_knowledge");p.getInventory().clearContent();var success=ResearchGameTests.menu(h.getLevel(),core,p);ResearchGameTests.fill(success,h.getLevel(),ResearchGameTests.find(h.getLevel(),r->!r.scroll()),1);success.clicked(3,0,ContainerInput.QUICK_MOVE,p);h.assertTrue(!done(p,"price_of_knowledge")&&p.getInventory().countItem(Items.ENCHANTED_BOOK)==1,"successful research is not failure");success.removed(p);p.containerMenu=p.inventoryMenu;
        var canceled=ResearchGameTests.menu(h.getLevel(),core,p);ResearchGameTests.fill(canceled,h.getLevel(),recipe,1);h.getLevel().destroyBlock(core,false);canceled.clicked(3,0,ContainerInput.SWAP,p);h.assertTrue(!done(p,"price_of_knowledge")&&canceled.getSlot(2).getItem().getCount()==1,"destroyed table cancels pending output without advancement");canceled.removed(p);p.containerMenu=p.inventoryMenu;h.succeed();
    }
    @GameTest public void sixNativeDefinitionsAndOutOfOrderVisibility(GameTestHelper h){
        var p=BatchTwoGameTests.player(h);for(String id:IDS){var a=holder(p,id).value();h.assertTrue(a.display().isPresent()&&a.display().get().getType()==AdvancementType.TASK&&!a.display().get().isHidden()&&a.rewards().equals(AdvancementRewards.EMPTY),"normal visible task with no rewards "+id);}
        p.getAdvancements().award(holder(p,"price_of_knowledge"),"committed");var tree=h.getLevel().getServer().getAdvancements().tree();var root=tree.get(holder(p,"old_arts_reborn").id());var visible=new HashSet<Identifier>();net.minecraft.server.advancements.AdvancementVisibilityEvaluator.evaluateVisibility(root,n->p.getAdvancements().getOrStartProgress(n.holder()).isDone(),(n,v)->{if(v)visible.add(n.holder().id());});h.assertTrue(visible.contains(holder(p,"old_arts_reborn").id())&&visible.contains(holder(p,"price_of_knowledge").id())&&!done(p,"old_arts_reborn")&&!done(p,"place_of_learning"),"native deep-child visibility includes unfinished ancestors without awarding them");p.getAdvancements().save();p.getAdvancements().reload(h.getLevel().getServer().getAdvancements());h.assertTrue(done(p,"price_of_knowledge")&&!done(p,"old_arts_reborn"),"native disk save and reload preserve independent progress");h.succeed();
    }
}
