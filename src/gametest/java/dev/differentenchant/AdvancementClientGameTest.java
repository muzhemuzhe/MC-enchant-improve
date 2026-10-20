package dev.differentenchant;

import java.util.*;
import java.nio.file.*;
import dev.differentenchant.client.ResearchScreen;
import dev.differentenchant.research.*;
import dev.differentenchant.enchanting.*;
import dev.differentenchant.resonance.ResonanceItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;

/** Actual placement, loot chest, merchant/cursor packets, research and native advancement screens. */
public final class AdvancementClientGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void language(ClientGameTestContext c,String code){var f=c.computeOnClient(m->{m.getLanguageManager().setSelected(code);m.options.languageCode=code;return m.reloadResourcePacks();});c.waitFor(m->f.isDone()&&m.getOverlay()==null);c.waitTicks(5);}
    private static void aim(ClientGameTestContext c,double x,double y,double z){var a=c.computeOnClient(m->{var e=m.player.getEyePosition();double dx=x-e.x,dy=y-e.y,dz=z-e.z;return new float[]{(float)(Math.toDegrees(Math.atan2(dz,dx))-90),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)))};});c.getInput().lookAt(a[0],a[1]);c.waitTicks(3);}
    private static void click(ClientGameTestContext c,int x,int y,int width,int height){var a=c.computeOnClient(m->{double s=m.getWindow().getGuiScale();return new double[]{((m.screen.width-width)/2+x)*s,((m.screen.height-height)/2+y)*s};});c.getInput().setCursorPos(a[0],a[1]);c.waitTicks(2);c.getInput().pressMouse(0);c.waitTicks(3);}
    private static void shot(ClientGameTestContext c,String name){c.runOnClient(m->m.gui.getChat().clearMessages(true));c.waitTicks(16);c.takeScreenshot("advancements-"+name);}
    private static void close(ClientGameTestContext c){c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);c.waitTicks(3);}
    private static void assertDone(TestSingleplayerContext w,String id,boolean value){w.getServer().runOnServer(s->check(AdvancementGameTests.done(s.getPlayerList().getPlayers().getFirst(),id)==value,"native progress "+id+" = "+value));}
    private static Identifier id(String name){return Identifier.fromNamespaceAndPath("differentenchant",name);}
    private static void tree(ClientGameTestContext c,String screenshot,String hover){
        c.runOnClient(m->m.getToastManager().clear());c.getInput().pressKey(o->o.keyAdvancements);c.waitForScreen(AdvancementsScreen.class);c.waitTicks(5);
        c.runOnClient(m->{var a=m.player.connection.getAdvancements();var root=a.getTree().get(id("old_arts_reborn"));check(root!=null,"root is visible even before completion");a.setSelectedTab(root.holder(),true);});c.waitTicks(5);
        if(hover!=null){var point=c.computeOnClient(m->{var screen=(AdvancementsScreen)m.screen;var t=m.player.connection.getAdvancements().getTree();int minX=99999,maxX=-99999,minY=99999,maxY=-99999;for(var key:AdvancementGameTests.IDS){var n=t.get(id(key));if(n==null)continue;var w=screen.getAdvancementWidget(n);if(w==null)continue;minX=Math.min(minX,w.getX());maxX=Math.max(maxX,w.getX()+28);minY=Math.min(minY,w.getY());maxY=Math.max(maxY,w.getY()+27);}var node=t.get(id(hover));check(node!=null,"child is sent to native client tree");var widget=screen.getAdvancementWidget(node);check(widget!=null,"child has native widget");double scale=m.getWindow().getGuiScale();return new double[]{((screen.width-252)/2+9+117-(maxX+minX)/2+widget.getX()+13)*scale,((screen.height-140)/2+18+56-(maxY+minY)/2+widget.getY()+13)*scale};});c.getInput().setCursorPos(point[0],point[1]);c.waitTicks(10);}else c.getInput().setCursorPos(1,1);
        shot(c,screenshot);c.getInput().pressKey(o->o.keyAdvancements);c.waitForScreen(null);c.waitTicks(3);
    }
    @Override public void runTest(ClientGameTestContext c){
        c.getInput().resizeWindow(1280,720);language(c,"zh_cn");TestWorldSave save;StringBuilder report=new StringBuilder();
        try(var w=c.worldBuilder().create()){
            save=w.getWorldSave();w.getServer().runCommand("time set day");w.getServer().runCommand("weather clear");w.getServer().runCommand("difficulty peaceful");
            w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();for(var key:AdvancementGameTests.IDS)check(!AdvancementGameTests.done(p,key),"new world begins with no mod progress");
                var cp=new BlockPos(0,-60,0);s.overworld().setBlockAndUpdate(cp,Blocks.CHEST.defaultBlockState());var chest=(ChestBlockEntity)s.overworld().getBlockEntity(cp);boolean found=false;for(long seed=1;seed<=32&&!found;seed++){chest.clearContent();chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,Identifier.withDefaultNamespace("chests/stronghold_library")),seed);chest.unpackLootTable(null);for(int i=0;i<27;i++)if(chest.getItem(i).is(ResearchContent.LOST_SCROLL))found=true;}check(found,"real stronghold library loot produced scroll");p.teleportTo(.5,-60,-2.5);});
            c.waitTicks(5);assertDone(w,"lost_chapter",false);aim(c,.5,-59.5,.5);c.getInput().pressKey(o->o.keyUse);c.waitForScreen(ContainerScreen.class);c.waitTicks(5);assertDone(w,"lost_chapter",false);
            var slot=c.computeOnClient(m->{for(int i=0;i<27;i++)if(m.player.containerMenu.getSlot(i).getItem().is(ResearchContent.LOST_SCROLL)){var q=m.player.containerMenu.getSlot(i);return new int[]{q.x+8,q.y+8};}throw new AssertionError("visible library loot scroll");});click(c,slot[0],slot[1],176,166);assertDone(w,"lost_chapter",true);assertDone(w,"old_arts_reborn",false);shot(c,"01-scroll-cursor-toast-zh");close(c);tree(c,"02-child-before-root-zh","lost_chapter");
            // Existing native merchant transaction, no root or core advancement yet.
            w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();var l=s.overworld();var v=net.minecraft.world.entity.EntityType.VILLAGER.create(l,net.minecraft.world.entity.EntitySpawnReason.COMMAND);v.setPos(3.5,-60,0);v.setNoAi(true);v.setVillagerData(v.getVillagerData().withProfession(l.registryAccess(),dev.differentenchant.enchanter.EnchanterContent.PROFESSION_KEY).withLevel(2));v.refreshBrain(l);l.addFreshEntity(v);p.teleportTo(3.5,-60,-2.5);p.getInventory().add(EnchanterGameTests.book(l,"minecraft:vanishing_curse",1));});c.waitTicks(6);aim(c,3.5,-59,0);c.getInput().pressKey(o->o.keyUse);c.waitForScreen(MerchantScreen.class);c.waitTicks(5);click(c,45,28,276,166);assertDone(w,"attuned_core",false);click(c,228,45,276,166);assertDone(w,"attuned_core",true);shot(c,"03-traded-core-cursor-toast-zh");close(c);
            for(int k:new int[]{1,2,0}){
                c.runOnClient(m->m.getToastManager().clear());
                var core=new BlockPos(9+k*7,-59,0);int last=k==0?13:k==1?10:1;
                w.getServer().runOnServer(s->{AutoAssemblyGameTests.frame(s.overworld(),core,k,last);var p=s.getPlayerList().getPlayers().getFirst();p.getInventory().setItem(7,new ItemStack(AutoAssemblyGameTests.original(k,last).getBlock().asItem()));if(k==0)p.teleportTo(core.getX()-.5,-58,-.5);else if(k==1)p.teleportTo(core.getX()+.5,-58.0625,.5);else p.teleportTo(core.getX()+.5,-60,-3.5);});c.getInput().pressKey(o->o.keyHotbarSlots[7]);c.waitTicks(4);assertDone(w,AdvancementGameTests.formation(k),false);
                if(k==0)aim(c,core.getX()+.5,-59,.5);else if(k==1)aim(c,core.getX()+.5,-59,-.5);else aim(c,core.getX()+.5,-59.5,.001);c.getInput().pressKey(o->o.keyUse);c.waitFor(m->m.level.getBlockState(core).is(k==0?DifferentEnchant.PART:k==1?ResearchContent.PART:dev.differentenchant.disenchant.DisenchantContent.PART));assertDone(w,AdvancementGameTests.formation(k),true);shot(c,"1"+k+"-actual-formation-toast-zh");
                if(k!=0)assertDone(w,"old_arts_reborn",false);
                if(k==1){
                    tree(c,"20-research-before-root-zh","place_of_learning");w.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().teleportTo(core.getX()+.5,-60,-2.5));c.waitTicks(4);aim(c,core.getX()+.5,-58.5,.5);c.getInput().pressKey(o->o.keyUse);c.waitForScreen(ResearchScreen.class);w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();var menu=(ResearchMenu)p.containerMenu;ResearchGameTests.fill(menu,s.overworld(),ResearchGameTests.find(s.overworld(),ResearchPolicy.Result::scroll),1);});c.waitFor(m->m.player.containerMenu.getSlot(3).hasItem());assertDone(w,"price_of_knowledge",false);shot(c,"21-unknown-preview-no-failure");click(c,188,66,230,218);assertDone(w,"price_of_knowledge",true);shot(c,"22-committed-failure-toast-zh");close(c);tree(c,"23-deep-child-root-unfinished-zh","price_of_knowledge");
                }
            }
            tree(c,"30-complete-tree-zh",null);tree(c,"31-root-tooltip-zh","old_arts_reborn");tree(c,"32-disenchant-tooltip-zh","disenchant");language(c,"en_us");tree(c,"33-complete-tree-en",null);for(var key:AdvancementGameTests.IDS)tree(c,"34-"+key+"-tooltip-en",key);
            w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();for(var key:AdvancementGameTests.IDS)check(AdvancementGameTests.done(p,key),"all six actual triggers completed");});report.append("All six native progress entries awarded by actual survival placement, actual stronghold loot cursor pickup, native merchant cursor trade and committed failed research. Ancestors remained unfinished until their own formation. Both languages/screens/icons tested.\n");
        }
        try(var w=save.open()){
            w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();for(var key:AdvancementGameTests.IDS)check(AdvancementGameTests.done(p,key),"native advancement JSON survives server stop and disk reopen "+key);});tree(c,"40-after-real-server-restart-en",null);report.append("Native player advancement progress survived full integrated server shutdown and on-disk reopen.\n");
        }
        try(var w=c.worldBuilder().create()){
            w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();for(var key:AdvancementGameTests.IDS)check(!AdvancementGameTests.done(p,key),"another world has separate native progress");});
            language(c,"en_us");var core=new BlockPos(0,-59,0);w.getServer().runOnServer(s->{AutoAssemblyGameTests.frame(s.overworld(),core,1,-1);s.getPlayerList().getPlayers().getFirst().teleportTo(.5,-60,-2.5);});c.waitTicks(6);aim(c,.5,-58.5,.5);c.getInput().pressKey(o->o.keyUse);c.waitForScreen(ResearchScreen.class);w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();ResearchGameTests.fill((ResearchMenu)p.containerMenu,s.overworld(),ResearchGameTests.find(s.overworld(),ResearchPolicy.Result::scroll),1);});c.waitFor(m->m.player.containerMenu.getSlot(3).hasItem());assertDone(w,"lost_chapter",false);assertDone(w,"price_of_knowledge",false);assertDone(w,"attuned_core",false);click(c,188,66,230,218);assertDone(w,"lost_chapter",true);assertDone(w,"price_of_knowledge",true);assertDone(w,"place_of_learning",false);assertDone(w,"old_arts_reborn",false);shot(c,"50-first-scroll-and-failure-two-toasts-en");close(c);language(c,"zh_cn");tree(c,"51-deepest-child-without-formed-parents-zh","price_of_knowledge");report.append("Fresh world does not inherit progress. First failed research in the second world grants scroll plus failure together; machine-input core and unfinished formation ancestors remain unawarded. Native English dual toast and Chinese deep-child page verified.\n");
        }
        try{Files.writeString(Path.of("advancements-client-report.txt"),report.toString());}catch(Exception e){throw new RuntimeException(e);}
    }
}
