package dev.differentenchant;

import java.util.*;
import dev.differentenchant.enchanting.*;
import dev.differentenchant.structure.StructurePattern;
import dev.differentenchant.structure.StructurePattern.Offset;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;

public final class BookshelfGameTests {
    @GameTest public void bothMachineLayersBothRingsAndFourDirections(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var level=h.getLevel();var origin=master.getBlockPos();
        var providers=List.of(new Offset(2,-1,0),new Offset(0,0,-2),new Offset(-3,-1,3),new Offset(3,0,-3),new Offset(3,0,0));
        for(var p:providers) level.setBlockAndUpdate(BookshelfFixtures.position(origin,p),Blocks.BOOKSHELF.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(2,0,0),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(4,0,0),Blocks.BOOKSHELF.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(0,1,2),Blocks.BOOKSHELF.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(0,-2,2),Blocks.BOOKSHELF.defaultBlockState());
        for(var facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            level.destroyBlock(FormationService.position(origin,StructurePattern.CELLS.getFirst(),master.rotation()),false);
            for(var cell:StructurePattern.CELLS) level.setBlockAndUpdate(FormationService.position(origin,cell,FormationService.rotation(facing)),FormationService.originalBlock(cell.material()).defaultBlockState());
            h.assertTrue(FormationService.form(level,origin,facing).startsWith("Formed"),"rotate fixture");
            master=(MachineControllerBlockEntity)level.getBlockEntity(origin);
            h.assertTrue(MachineAccess.complete(level,master),"bookshelves preserve structure");
            h.assertTrue(MachineAccess.shelves(level,master)==5,"both layers, touching/second rings, no air-path condition: "+facing);
        }
        h.succeed();
    }
    @GameTest public void unloadedPositionsAreSkippedWithoutAcquiringChunks(GameTestHelper h) {
        var level=h.getLevel();var remote=new net.minecraft.core.BlockPos(1_000_000,64,1_000_000);
        for(var p:BookshelfScan.OFFSETS) h.assertTrue(!level.hasChunkAt(BookshelfFixtures.position(remote,p)),"fixture scan locations are truly unloaded");
        int before=level.getChunkSource().getLoadedChunksCount();
        var coordinateOnly=new MachineControllerBlockEntity(remote,DifferentEnchant.PART.defaultBlockState());
        h.assertTrue(MachineAccess.shelves(level,coordinateOnly)==0,"world adapter skips all unloaded blocks");
        h.assertTrue(level.getChunkSource().getLoadedChunksCount()==before,"scan does not create or acquire a remote chunk");
        for(var p:BookshelfScan.OFFSETS) h.assertTrue(!level.hasChunkAt(BookshelfFixtures.position(remote,p)),"all scan chunks remain unloaded");
        h.succeed();
    }
    @GameTest public void taggedBookshelvesBoundaryCountsAndOneCountPerBlock(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var level=h.getLevel();var origin=master.getBlockPos();
        h.assertTrue(CompatibilityTestMod.TAGGED_BOOKSHELF.defaultBlockState().is(net.minecraft.tags.BlockTags.ENCHANTMENT_POWER_PROVIDER),"third-party registry block participates through native tag");
        for(int amount:List.of(0,1,15,16,32,35,36,37,60,80)) {
            for(int i=0;i<BookshelfScan.OFFSETS.size();i++) level.setBlockAndUpdate(BookshelfFixtures.position(origin,BookshelfScan.OFFSETS.get(i)),
                    i>=amount?Blocks.AIR.defaultBlockState():i%2==0?Blocks.BOOKSHELF.defaultBlockState():CompatibilityTestMod.TAGGED_BOOKSHELF.defaultBlockState());
            h.assertTrue(MachineAccess.shelves(level,master)==Math.min(amount,36),"actual vanilla/tagged physical count at "+amount);
        }
        h.succeed();
    }
    @GameTest public void dynamicCountsInvalidateQuotesAndActualResultMatchesPreview(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);master.setMachineTier(5);
        var player=EnchantingMenuGameTests.player(h,master);var menu=EnchantingMenuGameTests.menu(h,master,player);
        long seed=EnchantingSeedData.forPlayer(player,2);int nativeSeed=player.getEnchantmentSeed(),lastEffective=0;
        var snapshots=new HashMap<Integer,List<EnchantingPreviewPayload.Offer>>();
        for(int amount:List.of(1,15,16,32,35,36,37,80,37,36,35,16,15,0,36)) {
            int before=menu.buttonIdFor(2),effective=Math.min(amount,36);
            BookshelfFixtures.scanLayout(h.getLevel(),master.getBlockPos(),amount);menu.broadcastChanges();
            h.assertTrue(menu.bookshelfCount()==effective,"menu effective count: "+amount);
            if(effective!=lastEffective) h.assertTrue(menu.buttonIdFor(2)!=before && !menu.clickMenuButton(player,before),"stale shelf quote rejected before payment");
            else h.assertTrue(menu.buttonIdFor(2)==before,"above-cap edits do not generate a new quote");
            var offers=menu.previewSnapshot().offers();
            if(snapshots.containsKey(effective)) h.assertTrue(snapshots.get(effective).equals(offers),"return to same context preserves all offers");
            snapshots.put(effective,offers);lastEffective=effective;
            h.assertTrue(player.experienceLevel==40 && menu.getSlot(1).getItem().getCount()==12,"environment edits do not charge");
            h.assertTrue(EnchantingSeedData.forPlayer(player,2)==seed && player.getEnchantmentSeed()==nativeSeed,"same source randomness");
        }
        var expected=menu.previewSnapshot().offers().get(2);
        h.assertTrue(!expected.hidden() && menu.clickMenuButton(player,menu.buttonIdFor(2)),"commit actual36 offer");
        var actual=EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem());
        h.assertTrue(actual.size()==expected.revealed().size(),"full preview length matches actual result");
        for(var entry:expected.revealed()) h.assertTrue(actual.getLevel(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(entry.id()).orElseThrow())==entry.level(),"actual result matches server preview");
        h.assertTrue(player.experienceLevel==37 && menu.getSlot(1).getItem().getCount()==9,"unchanged exact costs");
        menu.removed(player);h.succeed();
    }
}
