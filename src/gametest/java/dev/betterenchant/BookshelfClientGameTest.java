package dev.betterenchant;

import dev.betterenchant.client.MachineEnchantmentScreen;
import dev.betterenchant.enchanting.*;
import dev.betterenchant.structure.StructurePattern;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Real two-layer open-front library, live network previews, actual payment and disk reload. */
public final class BookshelfClientGameTest implements FabricClientGameTest {
    private static final BlockPos ORIGIN=new BlockPos(0,-59,0);
    private static void check(boolean condition,String text) {if(!condition) throw new AssertionError(text);}
    private static void ready(ClientGameTestContext c,int shelves) {
        c.waitFor(client->client.player.containerMenu instanceof MachineEnchantmentMenu menu && menu.bookshelfCount()==shelves && menu.preview()!=null && menu.controlState()!=null);
    }
    private static void cursor(ClientGameTestContext c,int x,int y) {
        var point=c.computeOnClient(client->{double scale=client.getWindow().getGuiScale();return new double[]{((client.screen.width-MachineGuiLayout.WIDTH)/2+x)*scale,((client.screen.height-MachineGuiLayout.HEIGHT)/2+y)*scale};});
        c.getInput().setCursorPos(point[0],point[1]);c.waitTick();
    }
    private static void screenshot(ClientGameTestContext c,String name) {
        c.runOnClient(client->client.getToastManager().clear());c.getInput().setCursorPos(1,1);c.waitTicks(2);c.takeScreenshot(name);
    }
    private static void shift(ClientGameTestContext c,int slotIndex) {
        var slot=MachineGuiLayout.slot(slotIndex);cursor(c,slot.x()+8,slot.y()+8);
        // Established Fabric TestInput modifier workaround; normal container logic and packets still execute.
        c.runOnClient(client->{var event=new net.minecraft.client.input.MouseButtonEvent((client.screen.width-MachineGuiLayout.WIDTH)/2+slot.x()+8,
                (client.screen.height-MachineGuiLayout.HEIGHT)/2+slot.y()+8,new net.minecraft.client.input.MouseButtonInfo(0,1));
            client.screen.mouseClicked(event,false);client.screen.mouseReleased(event);});c.waitTicks(3);
    }
    private static void exactPreview(ClientGameTestContext c,net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        var expected=world.getServer().computeOnServer(server->((MachineEnchantmentMenu)server.getPlayerList().getPlayers().getFirst().containerMenu).previewSnapshot().offers());
        c.runOnClient(client->check(((MachineEnchantmentMenu)client.player.containerMenu).preview().offers().equals(expected),"client displays the exact server snapshot"));
    }
    @Override public void runTest(ClientGameTestContext c) {
        c.getInput().resizeWindow(1280,720);
        var reload=c.computeOnClient(client->{client.getLanguageManager().setSelected("zh_cn");client.options.languageCode="zh_cn";return client.reloadResourcePacks();});
        c.waitFor(client->reload.isDone() && client.getOverlay()==null);TestWorldSave save;
        try(var world=c.worldBuilder().create()) {
            save=world.getWorldSave();
            world.getServer().runOnServer(server->{
                var level=server.overworld();
                for(var cell:StructurePattern.CELLS) level.setBlockAndUpdate(FormationService.position(ORIGIN,cell,0),FormationService.originalBlock(cell.material()).defaultBlockState());
                check(FormationService.form(level,ORIGIN,Direction.NORTH).startsWith("Formed"),"actual machine formed");
                var master=(MachineControllerBlockEntity)level.getBlockEntity(ORIGIN);master.setMachineTier(5);
                BookshelfFixtures.playerLayout(level,ORIGIN,36);
                check(MachineAccess.shelves(level,master)==36,"18 shelves on each machine layer");
                level.setBlockAndUpdate(new BlockPos(7,-55,-7),Blocks.GLASS.defaultBlockState());
                var p=server.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.SURVIVAL);p.experienceLevel=60;p.getInventory().setSelectedSlot(8);
                p.getInventory().setItem(9,new ItemStack(Items.DIAMOND_SWORD));p.getInventory().setItem(10,new ItemStack(Items.LAPIS_LAZULI,12));
            });
            // Camera platform is a test fixture, outside the scan. The library itself is normal placed bookshelves.
            world.getServer().runCommand("tp @a 7.5 -54 -6.5");c.waitTicks(5);c.getInput().lookAt(ORIGIN);c.waitTicks(3);
            screenshot(c,"bookshelves-01-world-two-layers-36");
            world.getServer().runCommand("tp @a 0.5 -58 0.5");c.waitTicks(5);c.getInput().lookAt(ORIGIN);c.waitTicks(3);
            c.getInput().pressKey(o->o.keyUse);c.waitForScreen(MachineEnchantmentScreen.class);shift(c,2);shift(c,3);ready(c,36);exactPreview(c,world);
            var original=c.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).preview().offers());
            int fullQuote=c.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).buttonIdFor(2));
            long sourceSeed=world.getServer().computeOnServer(server->EnchantingSeedData.forPlayer(server.getPlayerList().getPlayers().getFirst(),2));
            screenshot(c,"bookshelves-02-gui-36");cursor(c,40,105);c.takeScreenshot("bookshelves-03-cap-tooltip");
            world.getServer().runOnServer(server->server.overworld().setBlockAndUpdate(ORIGIN.offset(3,0,-1),Blocks.BOOKSHELF.defaultBlockState()));
            c.waitTicks(4);ready(c,36);
            c.runOnClient(client->{var menu=(MachineEnchantmentMenu)client.player.containerMenu;check(menu.buttonIdFor(2)==fullQuote && menu.preview().offers().equals(original),"37th physical shelf gives no new quote or benefit");});
            screenshot(c,"bookshelves-04-physical-37-effective-36");
            world.getServer().runOnServer(server->server.overworld().setBlockAndUpdate(ORIGIN.offset(3,0,-1),Blocks.AIR.defaultBlockState()));
            var upper=BookshelfFixtures.position(ORIGIN,BookshelfFixtures.PLAYER_LAYOUT.getLast());
            world.getServer().runOnServer(server->server.overworld().setBlockAndUpdate(upper,Blocks.AIR.defaultBlockState()));ready(c,35);exactPreview(c,world);
            c.runOnClient(client->{var menu=(MachineEnchantmentMenu)client.player.containerMenu;check(menu.buttonIdFor(2)!=fullQuote,"upper-layer removal refreshes preview");client.gameMode.handleInventoryButtonClick(menu.containerId,fullQuote);});c.waitTicks(3);
            world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();check(p.experienceLevel==60 && p.containerMenu.getSlot(1).getItem().getCount()==12 && !p.containerMenu.getSlot(0).getItem().isEnchanted(),"old36 click cannot pay for changed35 context");});
            screenshot(c,"bookshelves-05-removed-upper-35");
            for(int amount:new int[]{15,16,36}) {
                world.getServer().runOnServer(server->BookshelfFixtures.playerLayout(server.overworld(),ORIGIN,amount));ready(c,amount);exactPreview(c,world);
                screenshot(c,"bookshelves-06-live-count-"+amount);
            }
            var lower=BookshelfFixtures.position(ORIGIN,BookshelfFixtures.PLAYER_LAYOUT.getFirst());
            int before=c.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).buttonIdFor(2));
            world.getServer().runOnServer(server->server.overworld().setBlockAndUpdate(lower,Blocks.AIR.defaultBlockState()));ready(c,35);
            c.runOnClient(client->check(((MachineEnchantmentMenu)client.player.containerMenu).buttonIdFor(2)!=before,"lower-layer removal also invalidates quote"));
            world.getServer().runOnServer(server->server.overworld().setBlockAndUpdate(lower,Blocks.BOOKSHELF.defaultBlockState()));ready(c,36);exactPreview(c,world);
            c.runOnClient(client->check(((MachineEnchantmentMenu)client.player.containerMenu).preview().offers().equals(original),"returning to36 restores the same actual outcomes"));
            world.getServer().runOnServer(server->check(EnchantingSeedData.forPlayer(server.getPlayerList().getPlayers().getFirst(),2)==sourceSeed,"bookshelf changes do not rotate source randomness"));
            var expected=c.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).preview().offers().get(2));
            var option=MachineGuiLayout.option(2);cursor(c,option.x()+50,option.y()+9);c.takeScreenshot("bookshelves-07-actual36-preview");
            c.getInput().pressMouse(0);c.waitFor(client->client.player.containerMenu.getSlot(0).getItem().isEnchanted());
            world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();var actual=EnchantmentHelper.getEnchantmentsForCrafting(p.containerMenu.getSlot(0).getItem());
                check(!expected.hidden() && actual.size()==expected.revealed().size(),"full exact client preview count committed");
                for(var entry:expected.revealed()) check(actual.getLevel(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(entry.id()).orElseThrow())==entry.level(),"each preview entry matches committed enchantment");
                check(p.experienceLevel==57 && p.containerMenu.getSlot(1).getItem().getCount()==9,"exact unchanged random fees");
            });
            cursor(c,MachineGuiLayout.slot(0).x()+8,MachineGuiLayout.slot(0).y()+8);c.runOnClient(client->client.getToastManager().clear());c.takeScreenshot("bookshelves-08-committed-result");
            c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);
        }
        try(var world=save.open()) {
            c.waitTicks(5);
            world.getServer().runOnServer(server->{var level=server.overworld();var master=(MachineControllerBlockEntity)level.getBlockEntity(ORIGIN);
                check(MachineAccess.complete(level,master) && MachineAccess.shelves(level,master)==36,"machine and both bookshelf layers persist after actual reload");
                var p=server.getPlayerList().getPlayers().getFirst();check(p.experienceLevel==57 && p.getInventory().countItem(Items.DIAMOND_SWORD)==1 && p.getInventory().countItem(Items.LAPIS_LAZULI)==9,"result and fees persisted exactly once");
                level.destroyBlock(FormationService.position(ORIGIN,StructurePattern.CELLS.getFirst(),0),false);
                check(level.getBlockState(ORIGIN).is(Blocks.ENCHANTING_TABLE),"normal disassembly restores controller after reload");
                for(var offset:BookshelfFixtures.PLAYER_LAYOUT) check(level.getBlockState(BookshelfFixtures.position(ORIGIN,offset)).is(Blocks.BOOKSHELF),"disassembly does not touch external bookshelves");
            });
        }
    }
}
