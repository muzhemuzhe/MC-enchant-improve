package dev.betterenchant;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import dev.betterenchant.structure.StructurePattern;

/** Test-only fixture placement; the production activation still travels through the player's use packet. */
public class MachineClientGameTest implements FabricClientGameTest {
    private static final BlockPos ORIGIN = new BlockPos(0, -59, 0);
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void assertFormed(TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            var level = server.overworld();
            var master = (MachineControllerBlockEntity) level.getBlockEntity(ORIGIN);
            require(master != null && master.controllerSnapshot.size() == 18, "controller persisted");
            for (var cell : StructurePattern.CELLS) {
                var p = FormationService.position(ORIGIN, cell, master.rotation);
                require(level.getBlockState(p).is(BetterEnchant.PART), "formed " + cell.index());
                require(level.getBlockEntity(p) instanceof MachineControllerBlockEntity part
                        && master.machineId.equals(part.machineId), "same machine " + cell.index());
            }
        });
    }
    private static void disabledUse(ClientGameTestContext context,TestSingleplayerContext world,
            java.util.concurrent.atomic.AtomicInteger vanillaScreens,boolean sneak,String screenshot) {
        if(sneak) {context.getInput().holdKey(o->o.keyShift);context.waitTicks(3);}
        context.getInput().pressKey(o->o.keyUse);context.waitTicks(4);
        if(sneak) {context.getInput().releaseKey(o->o.keyShift);context.waitTicks(2);}
        context.runOnClient(client->{
            require(client.screen==null && client.player.containerMenu==client.player.inventoryMenu,"standalone use never opens a screen");
            require(vanillaScreens.get()==0,"no transient native enchanting screen was initialized");
            // A forged button request cannot create a server menu or charge a standalone table.
            client.gameMode.handleInventoryButtonClick(client.player.containerMenu.containerId,2);
        });context.waitTicks(3);
        world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();
            require(p.containerMenu==p.inventoryMenu && p.experienceLevel==40,"server has no enchanting menu or payment");
            require(server.overworld().getBlockState(ORIGIN).is(Blocks.ENCHANTING_TABLE),"standalone/incomplete table remains native");
        });
        context.runOnClient(client->client.getToastManager().clear());context.takeScreenshot(screenshot);
    }
    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        var vanillaScreens=new java.util.concurrent.atomic.AtomicInteger();
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT.register((client,screen,width,height)->{
            if(screen instanceof net.minecraft.client.gui.screens.inventory.EnchantmentScreen) vanillaScreens.incrementAndGet();
        });
        TestWorldSave save;
        try (var world = context.worldBuilder().create()) {
            save = world.getWorldSave();
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                // Test setup builds the same vanilla fixture a player builds, never a formed machine.
                for (var cell : StructurePattern.CELLS) level.setBlockAndUpdate(
                        FormationService.position(ORIGIN, cell, 0), FormationService.originalBlock(cell.material()).defaultBlockState());
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.CREATIVE);
                player.experienceLevel=40;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            });
            world.getServer().runCommand("tp @a 4 -57 -5");
            context.waitTicks(5);
            context.getInput().lookAt(ORIGIN);
            context.waitTicks(3);
            world.getClientLevel().waitForChunksRender();
            context.takeScreenshot("01-vanilla-template");
            world.getServer().runCommand("tp @a 0.5 -58 0.5");
            context.waitTicks(3);
            context.getInput().lookAt(ORIGIN);
            context.waitTicks(3);
            disabledUse(context,world,vanillaScreens,false,"standalone-01-creative-no-menu");
            world.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR));context.waitTicks(3);
            disabledUse(context,world,vanillaScreens,false,"standalone-07-spectator-no-menu");
            world.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SURVIVAL));context.waitTicks(3);
            disabledUse(context,world,vanillaScreens,false,"standalone-02-survival-no-menu");
            world.getServer().runOnServer(server->server.overworld().setBlockAndUpdate(FormationService.position(ORIGIN,StructurePattern.CELLS.getFirst(),0),Blocks.AIR.defaultBlockState()));
            disabledUse(context,world,vanillaScreens,true,"standalone-03-incomplete-no-menu");
            world.getServer().runOnServer(server->{server.overworld().setBlockAndUpdate(FormationService.position(ORIGIN,StructurePattern.CELLS.getFirst(),0),Blocks.POLISHED_DEEPSLATE.defaultBlockState());server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.CREATIVE);});context.waitTicks(3);
            context.getInput().holdKey(options -> options.keyShift);
            context.waitTicks(3);
            context.runOnClient(client -> {
                require(client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(ORIGIN), "crosshair really hits enchanting table");
            });
            context.getInput().pressKey(options -> options.keyUse);
            context.waitTicks(4);
            context.getInput().releaseKey(options -> options.keyShift);
            assertFormed(world);
            world.getServer().runCommand("tp @a 4 -57 -5");
            context.waitTicks(5);
            context.getInput().lookAt(ORIGIN);
            context.waitTicks(3);
            world.getClientLevel().waitForChunksRender();
            context.takeScreenshot("02-formed-machine");
        }
        try (var reopened = save.open()) {
            context.waitTicks(5);
            assertFormed(reopened);
            context.getInput().lookAt(ORIGIN);
            context.waitTicks(3);
            reopened.getClientLevel().waitForChunksRender();
            context.takeScreenshot("03-reloaded-machine");
            reopened.getServer().runOnServer(server -> {
                var level = server.overworld();
                var master = (MachineControllerBlockEntity) level.getBlockEntity(ORIGIN);
                int rotation = master.rotation;
                var target = FormationService.position(ORIGIN, StructurePattern.CELLS.get(9), rotation);
                var player = server.getPlayerList().getPlayers().getFirst();
                require(player.gameMode.destroyBlock(target), "creative player breaks part");
                for (var cell : StructurePattern.CELLS) {
                    var p = FormationService.position(ORIGIN, cell, rotation);
                    require(level.getBlockState(p).is(cell.index() == 9 ? Blocks.AIR : FormationService.originalBlock(cell.material())), "restored after reopen " + cell.index());
                }
            });
            context.waitTicks(5);
            reopened.getClientLevel().waitForChunksRender();
            context.takeScreenshot("04-disassembled-after-reload");
            reopened.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SURVIVAL));
            reopened.getServer().runCommand("tp @a 0.5 -58 0.5");context.waitTicks(4);context.getInput().lookAt(ORIGIN);context.waitTicks(3);
            disabledUse(context,reopened,vanillaScreens,false,"standalone-04-restored-no-menu");
            disabledUse(context,reopened,vanillaScreens,true,"standalone-05-restored-incomplete-no-menu");
            reopened.getServer().runOnServer(server->{for(var cell:StructurePattern.CELLS) {
                var pos=FormationService.position(ORIGIN,cell,0);
                if(server.overworld().getBlockState(pos).isAir()) server.overworld().setBlockAndUpdate(pos,FormationService.originalBlock(cell.material()).defaultBlockState());
            }});
            context.getInput().holdKey(o->o.keyShift);context.waitTicks(3);context.getInput().pressKey(o->o.keyUse);context.waitTicks(4);
            context.getInput().releaseKey(o->o.keyShift);context.waitTicks(3);assertFormed(reopened);
            context.getInput().pressKey(o->o.keyUse);context.waitForScreen(dev.betterenchant.client.MachineEnchantmentScreen.class);
            require(vanillaScreens.get()==0,"survival reassembly opens only the custom screen");context.takeScreenshot("standalone-06-survival-reassembled-machine-gui");
            context.getInput().pressKey(o->o.keyInventory);context.waitForScreen(null);
        }
    }
}
