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
    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
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
        }
    }
}
