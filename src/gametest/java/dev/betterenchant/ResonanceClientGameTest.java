package dev.betterenchant;

import dev.betterenchant.client.MachineEnchantmentScreen;
import dev.betterenchant.enchanting.*;
import dev.betterenchant.resonance.*;
import dev.betterenchant.structure.StructurePattern;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import static dev.betterenchant.enchanting.MachineGuiLayout.*;

/** Real slot clicks and network commits: 0 -> 3 -> 6 -> 9 -> 11 -> 12, transfer, disk reload, repeat. */
public final class ResonanceClientGameTest implements FabricClientGameTest {
    private static final BlockPos FIRST=new BlockPos(15,-59,15), SECOND=FIRST.offset(12,0,0);
    private static void check(boolean condition,String message) {if(!condition) throw new AssertionError(message);}
    private static void ready(ClientGameTestContext c) {
        c.waitFor(client->client.player.containerMenu instanceof MachineEnchantmentMenu m && m.preview()!=null && m.controlState()!=null);
    }
    private static void language(ClientGameTestContext c,String name) {
        var reload=c.computeOnClient(client->{client.getLanguageManager().setSelected(name);client.options.languageCode=name;return client.reloadResourcePacks();});
        c.waitFor(client->reload.isDone() && client.getOverlay()==null);
    }
    private static void cursor(ClientGameTestContext c,int x,int y) {
        var point=c.computeOnClient(client->{double scale=client.getWindow().getGuiScale();return new double[]{((client.screen.width-WIDTH)/2+x)*scale,((client.screen.height-HEIGHT)/2+y)*scale};});
        c.getInput().setCursorPos(point[0],point[1]);c.waitTicks(2);
    }
    private static void click(ClientGameTestContext c,int x,int y) {cursor(c,x,y);c.getInput().pressMouse(0);c.waitTicks(3);}
    private static void shot(ClientGameTestContext c,String name) {c.runOnClient(client->client.getToastManager().clear());c.getInput().setCursorPos(1,1);c.waitTicks(3);c.takeScreenshot(name);}
    private static void shiftSlot(ClientGameTestContext c,int index) {
        var p=slot(index);cursor(c,p.x()+8,p.y()+8);
        c.runOnClient(client->{var e=new net.minecraft.client.input.MouseButtonEvent((client.screen.width-WIDTH)/2+p.x()+8,(client.screen.height-HEIGHT)/2+p.y()+8,new net.minecraft.client.input.MouseButtonInfo(0,1));
            client.screen.mouseClicked(e,false);client.screen.mouseReleased(e);});c.waitTicks(3);ready(c);
    }
    private static void shiftItem(ClientGameTestContext c,Item item,boolean fresh) {
        int index=c.computeOnClient(client->{for(int i=2;i<38;i++) {var stack=client.player.containerMenu.getSlot(i).getItem();if(stack.is(item) && (!fresh || !stack.isEnchanted())) return i;}throw new AssertionError("Missing inventory item "+item);});
        shiftSlot(c,index);
    }
    private static void open(ClientGameTestContext c,TestSingleplayerContext world,BlockPos pos) {
        world.getServer().runCommand("tp @a "+(pos.getX()+.5)+" -58 "+(pos.getZ()+.5));c.waitTicks(5);
        c.getInput().lookAt(pos);c.waitTicks(3);c.getInput().pressKey(o->o.keyUse);c.waitForScreen(MachineEnchantmentScreen.class);ready(c);
    }
    private static void close(ClientGameTestContext c) {c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);}
    private static MachineControllerBlockEntity master(net.minecraft.server.MinecraftServer s,BlockPos pos) {return (MachineControllerBlockEntity)s.overworld().getBlockEntity(pos);}
    private static void energy(ClientGameTestContext c,int expected,boolean attuned) {
        c.waitFor(client->{var stack=client.player.containerMenu.getSlot(CORE_SLOT).getItem();return ResonanceItems.energy(stack)==expected && stack.is(attuned?ResonanceItems.ATTUNED_CORE:ResonanceItems.CORE);});
    }
    private static void build(net.minecraft.server.MinecraftServer s,BlockPos pos) {
        for(var cell:StructurePattern.CELLS) s.overworld().setBlockAndUpdate(FormationService.position(pos,cell,0),FormationService.originalBlock(cell.material()).defaultBlockState());
        check(FormationService.form(s.overworld(),pos,Direction.NORTH).startsWith("Formed"),"normal complete assembly");
        BookshelfFixtures.playerLayout(s.overworld(),pos,36);
    }
    private static void random(ClientGameTestContext c,TestSingleplayerContext world,int option,int expected) {
        shiftItem(c,Items.DIAMOND_SWORD,true);ready(c);
        if(expected==3) shot(c,"resonance-20-before-active-random-zero");
        if(expected==9) shot(c,"resonance-21-middle-active-random-6");
        if(expected==12) shot(c,"resonance-22-before-final-active-random-11");
        var snapshot=c.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).preview());
        int quote=c.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).buttonIdFor(option));
        int before=world.getServer().computeOnServer(s->s.getPlayerList().getPlayers().getFirst().experienceLevel);
        var r=option(option);click(c,r.x()+60,r.y()+8);
        c.waitFor(client->client.player.containerMenu.getSlot(0).getItem().isEnchanted());energy(c,expected,expected==12);ready(c);
        if(expected==12) shot(c,"resonance-23-upgrade-enchanted-result-tier2");
        world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(p.experienceLevel==before-option-1,"only actual random fee charged");
            var result=EnchantmentHelper.getEnchantmentsForCrafting(p.containerMenu.getSlot(0).getItem());
            for(var e:snapshot.offers().get(option).revealed()) check(result.getLevel(s.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(e.id()).orElseThrow())==e.level(),"revealed actual server outcome committed");});
        c.runOnClient(client->{var m=(MachineEnchantmentMenu)client.player.containerMenu;client.gameMode.handleInventoryButtonClick(m.containerId,quote);});c.waitTicks(3);
        world.getServer().runOnServer(s->check(s.getPlayerList().getPlayers().getFirst().experienceLevel==before-option-1,"replay cannot charge fees or resonance again"));
        shiftSlot(c,0);
    }
    private static void add(ClientGameTestContext c,String id) {
        ready(c);var state=c.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).controlState());
        int index=-1;for(int i=0;i<state.rows().size();i++) if(state.rows().get(i).id().toString().equals(id)) index=i;
        check(index>=0,"legal directed enchantment");cursor(c,100,40);c.getInput().scroll(100);c.waitTick();
        int offset=Math.min(index,Math.max(0,state.rows().size()-3));if(offset>0) {c.getInput().scroll(-offset);c.waitTick();}
        var r=plus(index-offset);click(c,r.x()+6,r.y()+6);
        c.waitFor(client->((MachineEnchantmentMenu)client.player.containerMenu).controlState()!=null && ((MachineEnchantmentMenu)client.player.containerMenu).controlState().revision()!=state.revision());
    }
    @Override public void runTest(ClientGameTestContext c) {
        c.getInput().resizeWindow(1280,720);language(c,"zh_cn");TestWorldSave save;
        try(var world=c.worldBuilder().create()) {
            save=world.getWorldSave();world.getServer().runOnServer(s->{
                build(s,FIRST);build(s,SECOND);var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.SURVIVAL);p.experienceLevel=80;p.getInventory().setSelectedSlot(8);
                p.getInventory().setItem(9,new ItemStack(ResonanceItems.CORE));p.getInventory().setItem(10,new ItemStack(Items.LAPIS_LAZULI,32));
                for(int i=11;i<=15;i++) p.getInventory().setItem(i,new ItemStack(Items.DIAMOND_SWORD));
            });
            open(c,world,FIRST);
            // Normal cursor pickup and placement for the new slot; shift transfer is exercised below.
            var inventory=slot(2);click(c,inventory.x()+8,inventory.y()+8);var core=slot(CORE_SLOT);click(c,core.x()+8,core.y()+8);energy(c,0,false);
            shiftItem(c,Items.LAPIS_LAZULI,false);shot(c,"resonance-01-before-zero-zh");
            cursor(c,core.x()+8,core.y()+8);c.takeScreenshot("resonance-02-item-core-tooltip-zh");
            random(c,world,2,3);shot(c,"resonance-03-charged-3");
            random(c,world,2,6);shot(c,"resonance-04-charged-6");
            cursor(c,core.x()+8,core.y()+8);c.takeScreenshot("resonance-05-partial-item-tooltip-6");
            close(c);open(c,world,FIRST);energy(c,6,false);shot(c,"resonance-06-reopened-still-6");close(c);
        }
        try(var world=save.open()) {
            c.waitTicks(5);open(c,world,FIRST);energy(c,6,false);shot(c,"resonance-07-disk-reload-still-6");
            world.getServer().runOnServer(s->check(master(s,FIRST).machineTier()==1 && MachineAccess.complete(s.overworld(),master(s,FIRST)),"cross-chunk machine survives disk reload"));
            shiftSlot(c,CORE_SLOT);close(c);open(c,world,SECOND);shiftItem(c,ResonanceItems.CORE,false);energy(c,6,false);
            world.getServer().runOnServer(s->check(master(s,FIRST).coreInventory().isEmpty() && ResonanceItems.energy(master(s,SECOND).coreInventory().getItem(0))==6,"same item transferred between distinct machines"));
            shiftItem(c,Items.LAPIS_LAZULI,false);shot(c,"resonance-08-transfer-other-machine-6");
            random(c,world,2,9);shot(c,"resonance-09-charged-9");random(c,world,1,11);shot(c,"resonance-10-charged-11-tier1");
            random(c,world,0,12);c.waitFor(client->((MachineEnchantmentMenu)client.player.containerMenu).machineTier()==2);shot(c,"resonance-11-full-tier2-upgrade");
            var core=slot(CORE_SLOT);cursor(c,core.x()+8,core.y()+8);c.takeScreenshot("resonance-12-item-attuned-tooltip-zh");
            world.getServer().runOnServer(s->check(master(s,FIRST).machineTier()==1 && master(s,SECOND).machineTier()==2,"only the charged machine upgrades"));
            shiftSlot(c,CORE_SLOT);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.getInventory().setItem(16,new ItemStack(ResonanceItems.CORE));p.getInventory().setItem(17,new ItemStack(BetterEnchant.RUNIC_STONE,3));p.getInventory().setItem(18,new ItemStack(Items.DIAMOND_SWORD));});
            c.waitTicks(3);shiftItem(c,ResonanceItems.CORE,false);energy(c,0,false);shiftSlot(c,1);shiftItem(c,BetterEnchant.RUNIC_STONE,false);shiftItem(c,Items.DIAMOND_SWORD,true);
            click(c,DIRECTED_TAB.x()+20,23);c.waitFor(client->((MachineEnchantmentMenu)client.player.containerMenu).isDirected());ready(c);
            add(c,"minecraft:sharpness");add(c,"minecraft:sharpness");add(c,"minecraft:unbreaking");energy(c,0,false);
            var planned=c.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).controlState());check(planned.levels()>=15,"batch actual fee exceeds core capacity");
            shot(c,"resonance-13-directed-repeat-plan-no-charge");
            int before=world.getServer().computeOnServer(s->s.getPlayerList().getPlayers().getFirst().experienceLevel);
            click(c,ENCHANT_BUTTON.x()+20,ENCHANT_BUTTON.y()+8);energy(c,12,true);ready(c);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(p.experienceLevel==before-planned.levels() && master(s,SECOND).machineTier()==2,"repeat batch pays actual fee and never upgrades to three");check(p.getInventory().countItem(ResonanceItems.ATTUNED_CORE)==1,"first attuned core retained by player");});
            shot(c,"resonance-14-repeat-attuned-still-tier2");language(c,"en_us");shot(c,"resonance-15-directed-attuned-en");
            cursor(c,core.x()+8,core.y()+8);c.takeScreenshot("resonance-16-item-attuned-tooltip-en");close(c);
        }
        try(var world=save.open()) {
            c.waitTicks(5);open(c,world,SECOND);energy(c,12,true);c.waitFor(client->((MachineEnchantmentMenu)client.player.containerMenu).machineTier()==2);
            shot(c,"resonance-17-upgrade-and-result-persisted");
            shiftSlot(c,CORE_SLOT);world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(p.getInventory().countItem(ResonanceItems.ATTUNED_CORE)==2,"both finished cores remain, no extra output");check(java.util.stream.IntStream.range(0,36).mapToObj(p.getInventory()::getItem).anyMatch(stack->stack.is(ResonanceItems.ATTUNED_CORE) && stack.getCount()==2),"finished cores stack together normally");});
            // Leave a partially charged item installed through actual unload/reload, then tear down.
            world.getServer().runOnServer(s->master(s,SECOND).coreInventory().setItem(0,ResonanceGameTests.core(7)));energy(c,7,false);close(c);
            world.getServer().runCommand("tp @a 2048 -58 2048");c.waitTicks(20);
            boolean unloaded=false;
            for(int i=0;i<500;i++) {
                unloaded=world.getServer().computeOnServer(s->s.overworld().getChunkSource().getChunkNow(SECOND.getX()>>4,SECOND.getZ()>>4)==null);
                if(unloaded) break;c.waitTick();
            }
            check(unloaded,"machine chunk actually unloaded while player was away");
            open(c,world,SECOND);energy(c,7,false);shot(c,"resonance-18-chunk-reload-partial-7");
            world.getServer().runOnServer(s->{var m=master(s,SECOND);check(m.machineTier()==2,"tier survives chunk unload");s.overworld().destroyBlock(FormationService.position(SECOND,StructurePattern.CELLS.getFirst(),0),false);
                var drops=s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(SECOND).inflate(4));
                check(drops.stream().filter(e->e.getItem().is(ResonanceItems.CORE) && ResonanceItems.energy(e.getItem())==7).mapToInt(e->e.getItem().getCount()).sum()==1,"teardown returns the installed partial core once");});
            c.waitForScreen(null);c.getInput().lookAt(SECOND);c.waitTicks(3);c.takeScreenshot("resonance-19-dismantled-core-drop");
        }
    }
}
