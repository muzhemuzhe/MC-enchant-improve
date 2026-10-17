package dev.differentenchant;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import dev.differentenchant.structure.StructurePattern;

public class MachineGameTests {
    private static BlockPos build(GameTestHelper helper, Direction facing) {
        BlockPos origin = helper.absolutePos(new BlockPos(3, 3, 3));
        ServerLevel level = helper.getLevel();
        for (var cell : StructurePattern.CELLS) {
            level.setBlockAndUpdate(FormationService.position(origin, cell, FormationService.rotation(facing)), FormationService.originalBlock(cell.material()).defaultBlockState());
        }
        return origin;
    }
    private static void assertFormed(GameTestHelper helper, BlockPos origin, Direction facing) {
        var level = helper.getLevel();
        for (var cell : StructurePattern.CELLS) {
            BlockPos p = FormationService.position(origin, cell, FormationService.rotation(facing));
            helper.assertTrue(level.getBlockState(p).is(DifferentEnchant.PART), "formed block " + cell.index());
            helper.assertTrue(level.getBlockEntity(p) instanceof MachineControllerBlockEntity part
                    && part.index == cell.index() && part.machineId != null, "formed data " + cell.index());
        }
    }
    private static void assertRestored(GameTestHelper helper, BlockPos origin, Direction facing, int missing) {
        for (var cell : StructurePattern.CELLS) {
            var p = FormationService.position(origin, cell, FormationService.rotation(facing));
            helper.assertTrue(helper.getLevel().getBlockState(p).is(cell.index() == missing ? Blocks.AIR : FormationService.originalBlock(cell.material())), "restored cell " + cell.index());
        }
    }
    @GameTest(maxTicks = 40)
    public void allDirectionsEveryPartAndRepeatedActivation(GameTestHelper helper) {
        for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            for (int broken = 0; broken < 18; broken++) {
                BlockPos origin = build(helper, facing);
                helper.assertTrue(FormationService.form(helper.getLevel(), origin, facing).startsWith("Formed"), "formation");
                assertFormed(helper, origin, facing);
                var id = ((MachineControllerBlockEntity) helper.getLevel().getBlockEntity(origin)).machineId;
                helper.assertTrue(FormationService.form(helper.getLevel(), origin, facing).startsWith("Formation rejected"), "idempotent activation");
                helper.assertValueEqual(((MachineControllerBlockEntity) helper.getLevel().getBlockEntity(origin)).machineId, id, "machine unchanged");
                BlockPos target = FormationService.position(origin, StructurePattern.CELLS.get(broken), FormationService.rotation(facing));
                helper.getLevel().destroyBlock(target, false);
                assertRestored(helper, origin, facing, broken);
            }
        }
        helper.succeed();
    }
    @GameTest
    public void incompleteTemplateNeverMutates(GameTestHelper helper) {
        for (int missing = 0; missing < 18; missing++) {
            BlockPos origin = build(helper, Direction.NORTH);
            helper.getLevel().setBlockAndUpdate(FormationService.position(origin, StructurePattern.CELLS.get(missing), 0), Blocks.AIR.defaultBlockState());
            helper.assertTrue(FormationService.form(helper.getLevel(), origin, Direction.NORTH).startsWith("Formation rejected"), "reject missing " + missing);
            assertRestored(helper, origin, Direction.NORTH, missing);
        }
        helper.succeed();
    }
    @GameTest
    public void persistedPartsRoundTripAndDisassemble(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = build(helper, Direction.WEST);
        FormationService.form(level, origin, Direction.WEST);
        var oldId = ((MachineControllerBlockEntity) level.getBlockEntity(origin)).machineId;
        for (var cell : StructurePattern.CELLS) {
            BlockPos p = FormationService.position(origin, cell, 3);
            var old = level.getBlockEntity(p);
            var tag = old.saveWithFullMetadata(level.registryAccess());
            level.removeBlockEntity(p);
            var reloaded = BlockEntity.loadStatic(p, level.getBlockState(p), tag, level.registryAccess());
            helper.assertTrue(reloaded instanceof MachineControllerBlockEntity part && oldId.equals(part.machineId), "saved id");
            level.setBlockEntity(reloaded);
        }
        assertFormed(helper, origin, Direction.WEST);
        helper.assertTrue(((MachineControllerBlockEntity) level.getBlockEntity(origin)).controllerSnapshot.size() == 18, "controller snapshot survives");
        level.destroyBlock(FormationService.position(origin, StructurePattern.CELLS.get(4), 3), false);
        assertRestored(helper, origin, Direction.WEST, 4);
        helper.succeed();
    }
    @GameTest
    public void originalLootAndWorldDropsAreNotDuplicated(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = build(helper, Direction.NORTH);
        FormationService.form(level, origin, Direction.NORTH);
        for (var cell : StructurePattern.CELLS) {
            BlockPos p = FormationService.position(origin, cell, 0);
            BlockState state = level.getBlockState(p);
            var part = (MachineControllerBlockEntity) level.getBlockEntity(p);
            var drops = Block.getDrops(state, level, p, part, null, new ItemStack(Items.DIAMOND_PICKAXE));
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(FormationService.originalBlock(cell.material()).asItem())
                    && drops.getFirst().getCount() == 1, "original loot for " + cell.index());
        }
        BlockPos corner = FormationService.position(origin, StructurePattern.CELLS.get(9), 0);
        var oldPart = (MachineControllerBlockEntity) level.getBlockEntity(origin);
        level.destroyBlock(corner, true);
        assertRestored(helper, origin, Direction.NORTH, 9);
        assertItemCount(helper, corner, Items.AMETHYST_BLOCK, 1);
        DisassemblyService.disassemble(level, oldPart, corner);
        assertItemCount(helper, corner, Items.AMETHYST_BLOCK, 1);
        helper.succeed();
    }
    @GameTest(maxTicks = 40)
    public void suppressedRemovalRecoveredByIntegrityCheck(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = build(helper, Direction.SOUTH);
        FormationService.form(level, origin, Direction.SOUTH);
        BlockPos target = FormationService.position(origin, StructurePattern.CELLS.get(17), 2);
        // Simulate a missing remote part without invoking normal removal callbacks.
        level.removeBlockEntity(target);
        level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        helper.runAfterDelay(22, () -> {
            assertRestored(helper, origin, Direction.SOUTH, 17);
            helper.succeed();
        });
    }
    @GameTest
    public void survivalAndCreativePlayerDropRules(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = build(helper, Direction.EAST);
        FormationService.form(level, origin, Direction.EAST);
        player.setGameMode(GameType.CREATIVE);
        requireBreak(helper, player.gameMode.destroyBlock(origin));
        assertRestored(helper, origin, Direction.EAST, 13);
        helper.assertItemEntityNotPresent(Items.ENCHANTING_TABLE);
        build(helper, Direction.EAST);
        FormationService.form(level, origin, Direction.EAST);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        helper.assertTrue(!player.preventsBlockDrops() && player.hasCorrectToolForDrops(level.getBlockState(origin)), "survival tool and drop preconditions");
        requireBreak(helper, player.gameMode.destroyBlock(origin));
        assertRestored(helper, origin, Direction.EAST, 13);
        assertItemCount(helper, origin, Items.ENCHANTING_TABLE, 1);
        build(helper, Direction.EAST);
        FormationService.form(level, origin, Direction.EAST);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos corner = FormationService.position(origin, StructurePattern.CELLS.get(9), 1);
        requireBreak(helper, player.gameMode.destroyBlock(corner));
        assertRestored(helper, origin, Direction.EAST, 9);
        assertItemCount(helper, corner, Items.AMETHYST_BLOCK, 0);
        helper.succeed();
    }
    private static void requireBreak(GameTestHelper helper, boolean result) { helper.assertTrue(result, "player destroys machine part"); }
    private static void assertItemCount(GameTestHelper helper, BlockPos origin, net.minecraft.world.item.Item item, int expected) {
        int count = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(4)).stream()
                .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertTrue(count == expected, "expected " + expected + " " + item + ", got " + count);
    }

    @GameTest(padding = 16)
    public void actualExplosionNeverDuplicatesOriginalMaterials(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = build(helper, Direction.NORTH);
        FormationService.form(level, origin, Direction.NORTH);
        level.explode(null, origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5, 6F, Level.ExplosionInteraction.TNT);
        for (var cell : StructurePattern.CELLS) helper.assertTrue(!level.getBlockState(FormationService.position(origin, cell, 0)).is(DifferentEnchant.PART), "no orphan after explosion");
        var entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(12));
        for (var entry : java.util.Map.of(Items.POLISHED_DEEPSLATE, 9, Items.OBSIDIAN, 4, Items.AMETHYST_BLOCK, 4, Items.ENCHANTING_TABLE, 1).entrySet()) {
            int count = entities.stream().filter(item -> item.getItem().is(entry.getKey())).mapToInt(item -> item.getItem().getCount()).sum();
            int remaining = 0;
            for (var cell : StructurePattern.CELLS) {
                var block = level.getBlockState(FormationService.position(origin, cell, 0)).getBlock();
                if (block.asItem() == entry.getKey()) remaining++;
            }
            helper.assertTrue(count + remaining <= entry.getValue(), "explosion remaining + dropped material count " + entry.getKey() + "=" + (count + remaining));
        }
        helper.succeed();
    }
}
