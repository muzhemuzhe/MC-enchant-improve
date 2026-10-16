package dev.betterenchant;

import java.util.*;
import dev.betterenchant.client.MachineEnchantmentScreen;
import dev.betterenchant.enchanting.*;
import dev.betterenchant.structure.StructurePattern;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import static dev.betterenchant.enchanting.MachineGuiLayout.*;

/** Actual screen/list/buttons/slot packets, server batch commit and disk reconnects. */
public final class DirectedClientGameTest implements FabricClientGameTest {
    private static final BlockPos ORIGIN=new BlockPos(0,-59,0);
    private static final String SHARP="minecraft:sharpness",UNBREAK="minecraft:unbreaking",FIRE="minecraft:fire_aspect",LOOT="minecraft:looting",KNOCK="minecraft:knockback";
    private static void check(boolean value,String text) {if(!value) throw new AssertionError(text);}
    private static void ready(ClientGameTestContext c) {c.waitFor(client->client.player.containerMenu instanceof MachineEnchantmentMenu menu && menu.controlState()!=null);}
    private static DirectedPreviewPayload state(ClientGameTestContext c) {return c.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).controlState());}
    private static DirectedPreviewPayload.Row row(ClientGameTestContext c,String id) {return state(c).rows().stream().filter(r->r.id().toString().equals(id)).findFirst().orElseThrow();}
    private static void cursor(ClientGameTestContext c,int x,int y) {
        var point=c.computeOnClient(client->{double scale=client.getWindow().getGuiScale();return new double[]{((client.screen.width-WIDTH)/2+x)*scale,((client.screen.height-HEIGHT)/2+y)*scale};});
        c.getInput().setCursorPos(point[0],point[1]);c.waitTick();
    }
    private static void click(ClientGameTestContext c,int x,int y) {cursor(c,x,y);c.getInput().pressMouse(0);c.waitTicks(3);}
    private static void language(ClientGameTestContext c,String language) {
        var reload=c.computeOnClient(client->{client.getLanguageManager().setSelected(language);client.options.languageCode=language;return client.reloadResourcePacks();});
        c.waitFor(client->reload.isDone() && client.getOverlay()==null);c.waitTicks(2);
    }
    private static void screenshot(ClientGameTestContext c,String name) {c.runOnClient(client->client.getToastManager().clear());c.getInput().setCursorPos(1,1);c.waitTicks(2);c.takeScreenshot(name);}
    private static void open(ClientGameTestContext c) {
        c.getInput().lookAt(ORIGIN);c.waitTicks(3);c.getInput().pressKey(o->o.keyUse);c.waitForScreen(MachineEnchantmentScreen.class);ready(c);
    }
    private static void shift(ClientGameTestContext c,Item item) {
        int index=c.computeOnClient(client->{for(int i=2;i<38;i++) if(client.player.containerMenu.getSlot(i).getItem().is(item)) return i;throw new AssertionError("Missing fixture item");});
        var point=slot(index);cursor(c,point.x()+8,point.y()+8);
        // Same 26.1.2 TestInput modifiers workaround as the established client regression.
        c.runOnClient(client->{var event=new net.minecraft.client.input.MouseButtonEvent((client.screen.width-WIDTH)/2+point.x()+8,
                (client.screen.height-HEIGHT)/2+point.y()+8,new net.minecraft.client.input.MouseButtonInfo(0,1));
            client.screen.mouseClicked(event,false);client.screen.mouseReleased(event);});c.waitTicks(3);ready(c);
    }
    private static void directed(ClientGameTestContext c) {click(c,DIRECTED_TAB.x()+20,23);c.waitFor(client->((MachineEnchantmentMenu)client.player.containerMenu).isDirected() && ((MachineEnchantmentMenu)client.player.containerMenu).controlState()!=null);}
    private static int showRow(ClientGameTestContext c,String id) {
        ready(c);var snapshot=state(c);int index=-1;
        for(int i=0;i<snapshot.rows().size();i++) if(snapshot.rows().get(i).id().toString().equals(id)) index=i;
        check(index>=0,"target in legal list "+id);
        cursor(c,100,40);c.getInput().scroll(100);c.waitTick();
        int scroll=Math.min(index,Math.max(0,snapshot.rows().size()-3));
        if(scroll>0) {c.getInput().scroll(-scroll);c.waitTick();}
        return index-scroll;
    }
    private static void label(ClientGameTestContext c,String id,String expected) {
        var row=row(c,id);
        c.runOnClient(client->check(((MachineEnchantmentScreen)client.screen).plannedLabel(row).getString().equals(expected),"visible planned label: "+expected));
    }
    private static void change(ClientGameTestContext c,String id,boolean add) {
        ready(c);var snapshot=state(c);int index=-1;
        for(int i=0;i<snapshot.rows().size();i++) if(snapshot.rows().get(i).id().toString().equals(id)) index=i;
        check(index>=0,"target in legal list "+id);
        var row=snapshot.rows().get(index);check(add?row.plus():row.minus(),"requested UI control available "+id);
        int visible=showRow(c,id);
        var rect=add?plus(visible):minus(visible);click(c,rect.x()+6,rect.y()+6);
        long before=snapshot.revision();c.waitFor(client->((MachineEnchantmentMenu)client.player.containerMenu).controlState()!=null && ((MachineEnchantmentMenu)client.player.containerMenu).controlState().revision()!=before);
    }
    private static void planFive(ClientGameTestContext c) {
        change(c,SHARP,true);change(c,SHARP,true);change(c,FIRE,true);change(c,UNBREAK,true);change(c,LOOT,true);
        check(state(c).upgrades()==5 && row(c,SHARP).target()==5 && row(c,FIRE).target()==1 && row(c,UNBREAK).target()==3,"five planned upgrades");
    }
    private static net.minecraft.core.Holder.Reference<Enchantment> holder(net.minecraft.server.MinecraftServer server,String id) {return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(id)).orElseThrow();}
    private static void build(net.minecraft.server.MinecraftServer server,ItemStack gear) {
        var level=server.overworld();
        for(var cell:StructurePattern.CELLS) level.setBlockAndUpdate(FormationService.position(ORIGIN,cell,0),FormationService.originalBlock(cell.material()).defaultBlockState());
        check(FormationService.form(level,ORIGIN,Direction.NORTH).startsWith("Formed"),"formed fixture");
        for(var offset:EnchantingTableBlock.BOOKSHELF_OFFSETS.subList(0,15)) level.setBlockAndUpdate(ORIGIN.offset(offset),Blocks.BOOKSHELF.defaultBlockState());
        var player=server.getPlayerList().getPlayers().getFirst();player.setGameMode(GameType.SURVIVAL);player.experienceLevel=60;player.getInventory().setSelectedSlot(8);
        player.getInventory().setItem(9,gear);
    }
    @Override public void runTest(ClientGameTestContext c) {
        c.getInput().resizeWindow(1280,720);language(c,"en_us");TestWorldSave save;
        try(var world=c.worldBuilder().create()) {
            save=world.getWorldSave();
            world.getServer().runOnServer(server->{
                var gear=new ItemStack(Items.DIAMOND_SWORD);gear.enchant(holder(server,SHARP),3);gear.enchant(holder(server,UNBREAK),2);gear.enchant(holder(server,KNOCK),1);
                gear.setDamageValue(11);gear.set(DataComponents.CUSTOM_NAME,Component.literal("Directed fixture"));
                var tag=new net.minecraft.nbt.CompoundTag();tag.putString("keep","thirdparty-data");gear.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
                build(server,gear);
            });
            world.getServer().runCommand("give @a better_enchant:runic_stone 5");world.getServer().runCommand("tp @a 0.5 -58 0.5");c.waitTicks(5);
            open(c);shift(c,Items.DIAMOND_SWORD);shift(c,BetterEnchant.RUNIC_STONE);directed(c);
            check(state(c).upgrades()==0 && state(c).reagent().getCount()==5,"actual stone item arrived through native shift-click");
            showRow(c,SHARP);label(c,SHARP,"Sharpness III");screenshot(c,"ui-directed-01-before");
            change(c,SHARP,true);label(c,SHARP,"Sharpness IV");screenshot(c,"ui-directed-02-plus");
            change(c,SHARP,false);label(c,SHARP,"Sharpness III");screenshot(c,"ui-directed-03-minus");
            check(state(c).upgrades()==0,"actual plus/minus round trip");
            label(c,FIRE,"Fire Aspect");change(c,FIRE,true);label(c,FIRE,"Fire Aspect I");change(c,FIRE,false);label(c,FIRE,"Fire Aspect");
            // Exercise a new enchantment through I -> II -> I before completing a five-step allocation.
            change(c,SHARP,true);change(c,SHARP,true);change(c,FIRE,true);change(c,FIRE,true);change(c,UNBREAK,true);
            check(!row(c,SHARP).plus() && state(c).rows().stream().noneMatch(DirectedPreviewPayload.Row::plus),"max level and five-stone budget hide all plus controls");
            int blockedRow=showRow(c,LOOT);var disabled=plus(blockedRow);long fullRevision=state(c).revision();
            click(c,disabled.x()+6,disabled.y()+6);check(state(c).revision()==fullRevision && state(c).upgrades()==5,"disabled plus cannot submit over-budget plan");
            screenshot(c,"ui-directed-04-budget-exhausted");
            change(c,FIRE,false);check(row(c,LOOT).plus(),"cancelling restores other plus controls");showRow(c,LOOT);screenshot(c,"ui-directed-05-budget-restored");
            change(c,LOOT,true);screenshot(c,"18-directed-five-planned-en");
            cursor(c,ENCHANT_BUTTON.x()+20,ENCHANT_BUTTON.y()+8);c.takeScreenshot("19-directed-batch-cost-tooltip");
            world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();check(p.experienceLevel==60 && p.containerMenu.getSlot(1).getItem().getCount()==5,"planning no payment");check(EnchantmentHelper.getEnchantmentsForCrafting(p.containerMenu.getSlot(0).getItem()).getLevel(holder(server,SHARP))==3,"planning not changing equipment");});
            var old=state(c);var stale=new DirectedActionPayload(old.containerId(),old.revision(),DirectedActionPayload.ENCHANT,"");
            var material=slot(1);cursor(c,material.x()+8,material.y()+8);c.getInput().pressMouse(1);c.waitTicks(3);ready(c);
            check(state(c).reagent().getCount()==2 && state(c).upgrades()==2,"right pickup reduces capacity and trims newest steps");
            c.runOnClient(client->net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(stale));c.waitTicks(3);
            world.getServer().runOnServer(server->check(server.getPlayerList().getPlayers().getFirst().experienceLevel==60,"stale batch not charged"));
            click(c,material.x()+8,material.y()+8);ready(c);check(state(c).reagent().getCount()==5 && state(c).upgrades()==2,"return stones restores available budget, not cancelled steps");
            change(c,FIRE,true);change(c,UNBREAK,true);change(c,LOOT,true);
            int low=state(c).levels();world.getServer().runCommand("better_enchant tier 0 -59 0 5");
            c.waitFor(client->((MachineEnchantmentMenu)client.player.containerMenu).machineTier()==5 && ((MachineEnchantmentMenu)client.player.containerMenu).controlState()!=null);
            check(state(c).upgrades()==5 && state(c).levels()<low,"live tier recost retains targets");
            c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);
            world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();check(p.experienceLevel==60 && p.getInventory().countItem(BetterEnchant.RUNIC_STONE)==5,"close discards unpaid plans and returns stones");});
        }
        int spent;
        try(var world=save.open()) {
            c.waitTicks(5);open(c);shift(c,Items.DIAMOND_SWORD);shift(c,BetterEnchant.RUNIC_STONE);directed(c);
            check(state(c).upgrades()==0,"disk reconnect has no stale plan");planFive(c);
            world.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().giveExperienceLevels(-25));
            c.waitFor(client->client.player.experienceLevel==35 && ((MachineEnchantmentMenu)client.player.containerMenu).controlState()!=null);
            language(c,"zh_cn");ready(c);screenshot(c,"20-directed-five-planned-zh");
            screenshot(c,"ui-directed-06-chinese");cursor(c,100,40);c.takeScreenshot("ui-directed-07-tooltip");
            var expected=state(c);spent=expected.levels();check(spent>=25 && spent<=40 && expected.canEnchant(),"actual batch cost affordable before click");
            var replay=new DirectedActionPayload(expected.containerId(),expected.revision(),DirectedActionPayload.ENCHANT,"");
            click(c,ENCHANT_BUTTON.x()+20,ENCHANT_BUTTON.y()+8);
            c.waitFor(client->client.player.containerMenu.getSlot(1).getItem().isEmpty());ready(c);
            world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();var result=p.containerMenu.getSlot(0).getItem();var actual=EnchantmentHelper.getEnchantmentsForCrafting(result);
                check(p.experienceLevel==35-expected.levels(),"exact batch levels, remainder may fall below30");
                check(actual.getLevel(holder(server,SHARP))==5 && actual.getLevel(holder(server,UNBREAK))==3 && actual.getLevel(holder(server,FIRE))==1 && actual.getLevel(holder(server,LOOT))==1 && actual.getLevel(holder(server,KNOCK))==1,"all planned targets and untouched enchantment");
                check(result.getDamageValue()==11 && result.get(DataComponents.CUSTOM_NAME).getString().equals("Directed fixture") && result.get(DataComponents.CUSTOM_DATA).copyTag().getString("keep").orElseThrow().equals("thirdparty-data"),"custom data and damage preserved");
            });
            c.runOnClient(client->net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(replay));c.waitTicks(3);
            world.getServer().runOnServer(server->check(server.getPlayerList().getPlayers().getFirst().experienceLevel==35-expected.levels(),"network replay no double charge"));
            cursor(c,slot(0).x()+8,slot(0).y()+8);c.runOnClient(client->client.getToastManager().clear());c.takeScreenshot("21-directed-committed-item");
            click(c,RANDOM_TAB.x()+20,23);ready(c);
            c.runOnClient(client->{var menu=(MachineEnchantmentMenu)client.player.containerMenu;check(Arrays.stream(menu.costs).allMatch(v->v==0),"random mode rejects upgraded equipment");client.gameMode.handleInventoryButtonClick(menu.containerId,menu.buttonIdFor(0));});c.waitTicks(3);
            c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);
        }
        try(var world=save.open()) {
            c.waitTicks(5);int actualSpent=spent;
            world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();check(p.experienceLevel==35-actualSpent && p.getInventory().countItem(BetterEnchant.RUNIC_STONE)==0 && p.getInventory().countItem(Items.DIAMOND_SWORD)==1,"one result and exact costs persisted after reconnect");
                for(int i=0;i<p.getInventory().getContainerSize();i++) if(p.getInventory().getItem(i).is(Items.DIAMOND_SWORD)) check(EnchantmentHelper.getEnchantmentsForCrafting(p.getInventory().getItem(i)).getLevel(holder(server,SHARP))==5,"actual upgrade saved to disk");
            });
        }
        // Test-only third-party registry item and long enchantment descriptions in the actual directed GUI.
        try(var world=c.worldBuilder().create()) {
            world.getServer().runOnServer(server->build(server,new ItemStack(CompatibilityTestMod.PREVIEW_TOOL)));
            world.getServer().runCommand("give @a better_enchant:runic_stone 5");world.getServer().runCommand("tp @a 0.5 -58 0.5");c.waitTicks(5);
            open(c);shift(c,CompatibilityTestMod.PREVIEW_TOOL);shift(c,BetterEnchant.RUNIC_STONE);directed(c);
            check(state(c).rows().size()==6,"six modded supported enchantments, including a non-table entry");
            cursor(c,100,40);c.runOnClient(client->client.getToastManager().clear());c.takeScreenshot("22-directed-modded-long-tooltip");
            c.takeScreenshot("ui-directed-08-compact-long-name");
            c.getInput().holdShift();c.waitTicks(2);c.takeScreenshot("ui-directed-09-full-name");c.getInput().releaseShift();
            c.getInput().holdShift();c.getInput().scroll(-100);c.waitTicks(2);c.takeScreenshot("24-directed-long-tooltip-last-page");
            c.takeScreenshot("ui-directed-10-name-last-page");c.getInput().releaseShift();
            change(c,"better_enchant_tests:preview_5",true);screenshot(c,"23-directed-scrolled-modded-list");
            int fee=state(c).levels();click(c,ENCHANT_BUTTON.x()+20,ENCHANT_BUTTON.y()+8);ready(c);
            world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();check(EnchantmentHelper.getEnchantmentsForCrafting(p.containerMenu.getSlot(0).getItem()).getLevel(holder(server,"better_enchant_tests:preview_5"))==1 && p.experienceLevel==60-fee && p.containerMenu.getSlot(1).getItem().getCount()==4,"actual modded item upgrade and fees");});
            c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);
        }
    }
}
