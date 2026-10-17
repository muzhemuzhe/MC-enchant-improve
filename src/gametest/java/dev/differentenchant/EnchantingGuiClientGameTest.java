package dev.differentenchant;

import java.util.*;
import dev.differentenchant.client.MachineEnchantmentScreen;
import dev.differentenchant.enchanting.*;
import dev.differentenchant.structure.StructurePattern;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Real mouse slot/hotbar interaction, all rows/tiers, large registry names and a small viewport. */
public final class EnchantingGuiClientGameTest implements FabricClientGameTest {
    private static final BlockPos ORIGIN=new BlockPos(0,-59,0);
    private static void check(boolean v,String message) { if(!v) throw new AssertionError(message); }
    private static void cursor(ClientGameTestContext c,int x,int y) {
        var p=c.computeOnClient(client->{double s=client.getWindow().getGuiScale();return new double[]{((client.screen.width-MachineGuiLayout.WIDTH)/2+x)*s,((client.screen.height-MachineGuiLayout.HEIGHT)/2+y)*s};});
        c.getInput().setCursorPos(p[0],p[1]); c.waitTick();
    }
    private static void click(ClientGameTestContext c,int x,int y) { cursor(c,x,y);c.getInput().pressMouse(0);c.waitTicks(3); }
    private static void shiftClick(ClientGameTestContext c,int x,int y) {
        cursor(c,x,y);
        // Fabric 5.1.0 TestInput supplies modifiers=0 even while holdShift is active.
        c.runOnClient(client->{
            var event=new net.minecraft.client.input.MouseButtonEvent((client.screen.width-MachineGuiLayout.WIDTH)/2+x,
                    (client.screen.height-MachineGuiLayout.HEIGHT)/2+y,new net.minecraft.client.input.MouseButtonInfo(0,1));
            client.screen.mouseClicked(event,false);client.screen.mouseReleased(event);
        });c.waitTicks(3);
    }
    private static void ready(ClientGameTestContext c) {
        c.waitFor(client->client.player.containerMenu instanceof MachineEnchantmentMenu menu && menu.preview()!=null && !menu.getSlot(0).getItem().isEmpty());
    }
    private static void clearToasts(ClientGameTestContext c) { c.runOnClient(client->client.getToastManager().clear()); c.getInput().setCursorPos(1,1); c.waitTicks(2); }
    @Override public void runTest(ClientGameTestContext c) {
        c.getInput().resizeWindow(1280,720);
        try(var world=c.worldBuilder().create()) {
            world.getServer().runOnServer(server->{
                var level=server.overworld();
                for(var cell:StructurePattern.CELLS) level.setBlockAndUpdate(FormationService.position(ORIGIN,cell,0),FormationService.originalBlock(cell.material()).defaultBlockState());
                check(FormationService.form(level,ORIGIN,Direction.NORTH).startsWith("Formed"),"form fixture");
                var master=(MachineControllerBlockEntity)level.getBlockEntity(ORIGIN); master.setMachineTier(5);
                BookshelfFixtures.playerLayout(level,ORIGIN,15);
                var player=server.getPlayerList().getPlayers().getFirst(); player.setGameMode(GameType.SURVIVAL);player.experienceLevel=40;player.getInventory().setSelectedSlot(8);
                // Find an actual four-result quote using the unchanged production algorithm, only for this fixture.
                var probe=new MachineEnchantmentMenu(77,player.getInventory(),level,master);
                probe.getSlot(0).set(new ItemStack(CompatibilityTestMod.PREVIEW_TOOL));
                int attempts=0;
                while(probe.previewSnapshot().offers().stream().noneMatch(o->o.revealed().size()==4)) {
                    check(++attempts<10000,"find four-result fixture");player.onEnchantmentPerformed(ItemStack.EMPTY,0);probe.broadcastChanges();
                }
                probe.getSlot(0).set(ItemStack.EMPTY);probe.removed(player);master.setMachineTier(1);
                player.getInventory().setItem(9,new ItemStack(CompatibilityTestMod.PREVIEW_TOOL));
                player.getInventory().setItem(10,new ItemStack(Items.LAPIS_LAZULI,12));
            });
            world.getServer().runCommand("tp @a 0.5 -58 0.5");c.waitTicks(5);
            c.getInput().lookAt(ORIGIN);c.waitTicks(3);c.getInput().pressKey(options->options.keyUse);c.waitForScreen(MachineEnchantmentScreen.class);
            // Actual cursor pickup/placement, not direct server slot manipulation.
            var from=MachineGuiLayout.slot(2);var gear=MachineGuiLayout.slot(0);var lapis=MachineGuiLayout.slot(1);
            click(c,from.x()+8,from.y()+8);click(c,gear.x()+8,gear.y()+8);
            var currency=MachineGuiLayout.slot(3);shiftClick(c,currency.x()+8,currency.y()+8);ready(c);
            // Move gear to hotbar with the number key, then shift it back through the native screen.
            cursor(c,gear.x()+8,gear.y()+8);c.takeScreenshot("14-input-slot-interaction");c.getInput().pressKey(options->options.keyHotbarSlots[0]);c.waitTicks(3);
            c.runOnClient(client->check(client.player.containerMenu.getSlot(0).getItem().isEmpty(),"hotbar shortcut removed gear"));
            var hotbar=MachineGuiLayout.slot(29);shiftClick(c,hotbar.x()+8,hotbar.y()+8);ready(c);
            clearToasts(c);c.takeScreenshot("ui-random-01-default");cursor(c,150,64);c.takeScreenshot("ui-random-02-hover");
            var tiers=new ArrayList<List<EnchantingPreviewPayload.Offer>>();
            for(int tier=1;tier<=5;tier++) {
                final int t=tier;world.getServer().runCommand("differentenchant tier 0 -59 0 "+tier);
                c.waitFor(client->((MachineEnchantmentMenu)client.player.containerMenu).machineTier()==t && ((MachineEnchantmentMenu)client.player.containerMenu).preview()!=null);
                var expected=world.getServer().computeOnServer(server->((MachineEnchantmentMenu)server.getPlayerList().getPlayers().getFirst().containerMenu).previewSnapshot().offers());
                c.runOnClient(client->{var menu=(MachineEnchantmentMenu)client.player.containerMenu;check(menu.preview().offers().equals(expected),"network exact filtered snapshot tier "+t);
                    for(var offer:menu.preview().offers()) {check(!offer.revealed().isEmpty(),"populated preview");if(t<5) check(offer.revealed().size()<=(t<3?1:2),"reveal limit");else check(!offer.hidden(),"tier five no hidden results");}
                });
                tiers.add(expected);clearToasts(c);c.takeScreenshot("09-custom-gui-tier-"+tier);
            }
            int multi=0;for(int i=0;i<3;i++) if(tiers.get(4).get(i).revealed().size()==4) multi=i;
            check(tiers.get(4).get(multi).revealed().size()==4,"real four-result preview");
            var r=MachineGuiLayout.option(multi);cursor(c,r.x()+50,r.y()+9);c.takeScreenshot("10-long-preview-first-page");
            c.getInput().scroll(-12);c.waitTicks(2);c.takeScreenshot("11-long-preview-scrolled");
            // Switching modes must not charge or rotate the random seed.
            int seed=world.getServer().computeOnServer(server->server.getPlayerList().getPlayers().getFirst().getEnchantmentSeed());
            click(c,205,23);c.waitFor(client->((MachineEnchantmentMenu)client.player.containerMenu).isDirected() && ((MachineEnchantmentMenu)client.player.containerMenu).controlState()!=null);world.getServer().runOnServer(server->check(server.getPlayerList().getPlayers().getFirst().getEnchantmentSeed()==seed,"directed tab cannot enchant"));
            c.takeScreenshot("12-directed-mode-no-stones");click(c,120,23);c.waitFor(client->!((MachineEnchantmentMenu)client.player.containerMenu).isDirected() && ((MachineEnchantmentMenu)client.player.containerMenu).controlState()!=null);
            // A real bookshelf change invalidates the old quote and updates both UI values and previews.
            int old=c.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).buttonIdFor(0));
            world.getServer().runOnServer(server->server.overworld().setBlockAndUpdate(BookshelfFixtures.position(ORIGIN,BookshelfFixtures.PLAYER_LAYOUT.getFirst()),Blocks.AIR.defaultBlockState()));
            c.waitFor(client->((MachineEnchantmentMenu)client.player.containerMenu).bookshelfCount()==14 && ((MachineEnchantmentMenu)client.player.containerMenu).preview()!=null);
            c.runOnClient(client->{var menu=(MachineEnchantmentMenu)client.player.containerMenu;check(menu.buttonIdFor(0)!=old,"shelf quote invalidated");client.gameMode.handleInventoryButtonClick(menu.containerId,old);});c.waitTicks(3);
            world.getServer().runOnServer(server->check(server.getPlayerList().getPlayers().getFirst().experienceLevel==40,"stale shelf button not charged"));
            // Disabled client buttons and server state both enforce shortages.
            world.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().giveExperienceLevels(-40));
            c.waitFor(client->client.player.experienceLevel==0);click(c,150,42);c.takeScreenshot("ui-random-03-missing-experience");
            world.getServer().runOnServer(server->check(server.getPlayerList().getPlayers().getFirst().getEnchantmentSeed()==seed,"insufficient XP cannot rotate seed"));
            world.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().containerMenu.getSlot(1).set(ItemStack.EMPTY));
            c.waitFor(client->client.player.containerMenu.getSlot(1).getItem().isEmpty());click(c,150,64);c.takeScreenshot("ui-random-07-both-shortages");
            world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();p.giveExperienceLevels(40);p.containerMenu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,1));});
            c.waitFor(client->client.player.experienceLevel==40 && client.player.containerMenu.getSlot(1).getItem().getCount()==1);click(c,150,86);c.takeScreenshot("ui-random-04-missing-lapis");
            world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();check(p.experienceLevel==40 && p.getEnchantmentSeed()==seed,"insufficient lapis no cost or reroll");p.containerMenu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,12));});
            c.waitFor(client->client.player.containerMenu.getSlot(1).getItem().getCount()==12);
            // All three real mouse targets commit exactly the preview they display.
            for(int i=0;i<3;i++) {
                final int row=i;
                var expected=c.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).preview().offers().get(row));
                int xp=world.getServer().computeOnServer(server->server.getPlayerList().getPlayers().getFirst().experienceLevel);
                var rect=MachineGuiLayout.option(i);click(c,rect.x()+60,rect.y()+8);
                c.waitFor(client->client.player.containerMenu.getSlot(0).getItem().isEnchanted());
                world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();var actual=EnchantmentHelper.getEnchantmentsForCrafting(p.containerMenu.getSlot(0).getItem());
                    check(p.experienceLevel==xp-row-1,"actual row cost");check(actual.size()==expected.revealed().size(),"full preview exact length");
                    for(var entry:expected.revealed()) check(actual.getLevel(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(entry.id()).orElseThrow())==entry.level(),"full preview exact entries");
                });
                // Clicking an already enchanted item must be inert, including after synchronization.
                ready(c);int done=c.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).buttonIdFor(row));
                c.runOnClient(client->client.gameMode.handleInventoryButtonClick(client.player.containerMenu.containerId,done));c.waitTicks(3);
                world.getServer().runOnServer(server->check(server.getPlayerList().getPlayers().getFirst().experienceLevel==xp-row-1,"enchanted repeat no charge"));
                if(i<2) {
                    c.runOnClient(client->client.gameMode.handleContainerInput(client.player.containerMenu.containerId,0,0,ContainerInput.QUICK_MOVE,client.player));c.waitTicks(3);
                    world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();p.getInventory().setItem(9,new ItemStack(CompatibilityTestMod.PREVIEW_TOOL));});c.waitTicks(3);
                    shiftClick(c,from.x()+8,from.y()+8);ready(c);
                }
            }
            // Small window exercises the same geometry at another GUI scale.
            var reload=c.computeOnClient(client->{client.getLanguageManager().setSelected("zh_cn");client.options.languageCode="zh_cn";return client.reloadResourcePacks();});
            c.waitFor(client->reload.isDone() && client.getOverlay()==null);c.waitTicks(3);
            c.runOnClient(client->check(net.minecraft.network.chat.Component.translatable("gui.differentenchant.random").getString().equals("随机附魔"),"Chinese language actually loaded"));
            c.getInput().resizeWindow(640,480);clearToasts(c);c.takeScreenshot("13-small-window-enchanted");
            c.takeScreenshot("ui-random-05-enchanted-disabled");
            click(c,gear.x()+8,gear.y()+8);c.runOnClient(client->check(!client.player.containerMenu.getCarried().isEmpty(),"small-window cursor hits real gear slot"));
            // Separate two clicks on the same slot from vanilla's real-time double-click collection.
            long placeAfter=c.computeOnClient(client->net.minecraft.util.Util.getMillis()+350);
            c.waitFor(client->net.minecraft.util.Util.getMillis()>=placeAfter);
            click(c,gear.x()+8,gear.y()+8);c.runOnClient(client->check(client.player.containerMenu.getCarried().isEmpty(),"small-window item replaced"));
            c.getInput().pressKey(options->options.keyInventory);c.waitForScreen(null);
            world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();check(p.getInventory().countItem(CompatibilityTestMod.PREVIEW_TOOL)==3,"close returns every output once");check(p.getInventory().countItem(Items.LAPIS_LAZULI)==6 && p.experienceLevel==34,"all rows exact cumulative fees");});
        }
    }
}
