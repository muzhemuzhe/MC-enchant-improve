package dev.differentenchant;

import dev.differentenchant.ruins.*;
import java.util.ArrayList;
import java.util.Collections;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Only the lantern prototype: real templates, per-chunk placement and native loot. */
public final class LanternGameTests {
    static void check(boolean condition,String message) {RuinGameTests.check(condition,message);}
    static RuinPiece place(ServerLevel level,BlockPos pos,Rotation rotation,long seed) {
        var piece=new RuinPiece(level.getStructureManager(),Ruins.id(LanternCachePolicy.TEMPLATE),pos,rotation,seed);
        piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(seed),BoundingBox.infinite(),ChunkPos.containing(pos),pos);
        return piece;
    }
    static BlockPos world(RuinPiece piece,LanternCachePolicy.Position p) {return RuinGameTests.world(piece,new BlockPos(p.x(),p.y(),p.z()));}
    static void verify(ServerLevel level,RuinPiece piece,boolean openLoot) {
        check(piece.template().getSize().equals(new BlockPos(33,26,33)),"single native template dimensions");
        var settings=piece.placeSettings().copy().setBoundingBox(null);
        int glass=piece.template().filterBlocks(piece.templatePosition(),settings,Blocks.GLASS).size();
        int ribs=piece.template().filterBlocks(piece.templatePosition(),settings,Blocks.QUARTZ_PILLAR).size()
            +piece.template().filterBlocks(piece.templatePosition(),settings,Blocks.STRIPPED_BIRCH_LOG).size();
        check(glass>1000&&glass>ribs*1.5,"glass dominates pale wall ribs and ring beams");
        check(piece.template().filterBlocks(piece.templatePosition(),settings,Blocks.SPAWNER).isEmpty(),"no new mob mechanics");
        int selected=0;
        for(var candidate:LanternCachePolicy.CANDIDATES) {
            var pos=world(piece,candidate);var carpet=level.getBlockState(pos.above());
            check(carpet.is(Blocks.RED_CARPET)||carpet.is(Blocks.YELLOW_CARPET),"carpet covers every cache candidate "+candidate);
            boolean chosen=LanternCachePolicy.select(piece.cacheSeed()).contains(candidate);
            check(level.getBlockState(pos).is(Blocks.BARREL)==chosen,"only selected subset replaces normal floor");
            if(chosen) {
                selected++;var barrel=(BarrelBlockEntity)level.getBlockEntity(pos);
                check(barrel!=null && barrel.getLootTable().equals(ResourceKey.create(Registries.LOOT_TABLE,Ruins.id(LanternCachePolicy.LOOT))),"native deferred loot table");
                check(barrel.getLootTableSeed()==LanternCachePolicy.lootSeed(piece.cacheSeed(),candidate),"stable per-position loot seed");
                var saved=barrel.saveWithFullMetadata(level.registryAccess());
                var restored=(BarrelBlockEntity)BlockEntity.loadStatic(pos,level.getBlockState(pos),saved,level.registryAccess());
                check(restored!=null && restored.getLootTableSeed()==barrel.getLootTableSeed() && restored.getLootTable().equals(barrel.getLootTable()),"unopened loot survives serialization");
                if(openLoot) {barrel.unpackLootTable(null);check(!barrel.isEmpty(),"native cache generated real items");}
            }
        }
        check(selected>=4&&selected<=7,"4..7 concealed caches, never all sixteen");
        var plan=RuinGameTests.BLUEPRINTS.getAsJsonObject("lantern_sanctum");
        var authoredCandidates=plan.getAsJsonArray("hidden_barrel_candidates");
        check(authoredCandidates.size()==LanternCachePolicy.CANDIDATES.size(),"manifest and runtime candidate counts agree");
        for(int i=0;i<authoredCandidates.size();i++) {
            var p=LanternCachePolicy.CANDIDATES.get(i);
            check(RuinGameTests.point(authoredCandidates.get(i).getAsJsonArray()).equals(new BlockPos(p.x(),p.y(),p.z())),"manifest and runtime cache coordinates agree");
        }
        for(var light:plan.getAsJsonArray("light_core"))
            check(level.getBlockState(RuinGameTests.world(piece,RuinGameTests.point(light.getAsJsonArray()))).getLightEmission()==15,"luminous crown intact");
        for(var shelf:plan.getAsJsonArray("chiseled_shelves")) {
            var pos=RuinGameTests.world(piece,RuinGameTests.point(shelf.getAsJsonObject().getAsJsonArray("pos")));
            var books=(ChiseledBookShelfBlockEntity)level.getBlockEntity(pos);check(books!=null&&!books.isEmpty(),"research shelves contain actual books");
            for(int slot=0;slot<6;slot++) if(!books.getItem(slot).isEmpty()) check(books.getItem(slot).is(Items.ENCHANTED_BOOK),"random enchanted shelf contents");
        }
        var door=RuinGameTests.world(piece,new BlockPos(15,2,2));
        check(level.getBlockState(door).getValue(DoorBlock.FACING)==piece.getRotation().rotate(net.minecraft.core.Direction.NORTH),"entrance rotates natively");
        // Both switchback flights retain two air blocks of player headroom.
        for(var step:plan.getAsJsonArray("staircase_walk")) {
            var p=step.getAsJsonObject();var feet=RuinGameTests.world(piece,new BlockPos(p.get("x").getAsInt(),p.get("feet").getAsInt(),p.get("z").getAsInt()));
            check(level.getBlockState(feet).isAir()&&level.getBlockState(feet.above()).isAir(),"stair headroom "+p);
        }
        check(level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(Ruins.id("lantern_sanctum")).orElseThrow().value() instanceof RuinStructure,"natural structure registered");
        check(level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(Ruins.id("lantern_sanctum")).isPresent(),"natural placement registered");
    }
    static void placeChunks(ServerLevel level,RuinPiece piece,boolean reverse) {
        var box=piece.getBoundingBox();var chunks=new ArrayList<ChunkPos>();
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) chunks.add(new ChunkPos(x,z));
        if(reverse) Collections.reverse(chunks);
        for(var chunk:chunks) piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(chunk.pack()),
            new BoundingBox(chunk.getMinBlockX(),level.getMinY(),chunk.getMinBlockZ(),chunk.getMaxBlockX(),level.getMaxY()-1,chunk.getMaxBlockZ()),chunk,piece.templatePosition());
    }
    @GameTest(padding=48,maxTicks=200) public void rotatedChunkGenerationCachesAndPersistence(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(4,4,4));
        for(var rotation:Rotation.values()) {
            var piece=new RuinPiece(level.getStructureManager(),Ruins.id(LanternCachePolicy.TEMPLATE),origin,rotation,2026101807L);
            placeChunks(level,piece,false);verify(level,piece,true);
            var context=StructurePieceSerializationContext.fromLevel(level);
            var restored=(RuinPiece)Ruins.PIECE.load(context,piece.createTag(context));
            check(restored.cacheSeed()==piece.cacheSeed()&&restored.getRotation()==rotation,"piece seed and rotation persist");
            placeChunks(level,restored,true);verify(level,restored,true);
        }
        h.succeed();
    }
}
