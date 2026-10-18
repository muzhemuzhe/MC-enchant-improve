package dev.differentenchant;

import dev.differentenchant.ruins.*;
import dev.differentenchant.resonance.ResonanceItems;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;

public final class RuinGameTests {
    static final JsonObject BLUEPRINTS;
    static {try(var reader=new InputStreamReader(java.util.Objects.requireNonNull(RuinGameTests.class.getResourceAsStream("/data/differentenchant/ruins/blueprints.json")),StandardCharsets.UTF_8)) {BLUEPRINTS=JsonParser.parseReader(reader).getAsJsonObject();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static BlockPos point(JsonArray array) {return new BlockPos(array.get(0).getAsInt(),array.get(1).getAsInt(),array.get(2).getAsInt());}
    static BlockPos world(RuinPiece piece,BlockPos local) {return net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.calculateRelativePosition(piece.placeSettings(),local).offset(piece.templatePosition());}
    static RuinPiece place(ServerLevel level,BlockPos pos,String name,Rotation rotation) {
        var piece=new RuinPiece(level.getStructureManager(),Ruins.id("ruins/"+name),pos,rotation);
        piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(20261017),BoundingBox.infinite(),new ChunkPos(pos.getX()>>4,pos.getZ()>>4),pos);
        return piece;
    }
    static void check(boolean condition,String message) {if(!condition) throw new AssertionError(message);}
    static void verify(ServerLevel level,RuinPiece piece,String name) {
        var plan=BLUEPRINTS.getAsJsonObject(name);var size=point(plan.getAsJsonArray("size"));
        check(piece.template().getSize().equals(size),"native template size "+name);
        // Native postProcess leaves settings clipped to its last chunk. Audit the whole authored template.
        var authored=piece.placeSettings().copy().setBoundingBox(null);
        int paleWood=0;
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.BIRCH_PLANKS,Blocks.PALE_OAK_PLANKS,Blocks.BIRCH_LOG,Blocks.STRIPPED_PALE_OAK_LOG})
            paleWood+=piece.template().filterBlocks(piece.templatePosition(),authored,block).size();
        check(paleWood>piece.template().filterBlocks(piece.templatePosition(),authored,Blocks.STONE_BRICKS).size(),"pale timber dominates the authored template");
        check(piece.template().filterBlocks(piece.templatePosition(),authored,Blocks.IRON_BARS).isEmpty(),"glazing replaces fortress bars");
        check(name.equals("small_buried") || !piece.template().filterBlocks(piece.templatePosition(),authored,Blocks.GLASS_PANE).isEmpty(),"surface study has real windows");
        int spawners=0;
        for(var p:BlockPos.betweenClosed(piece.getBoundingBox().minX(),piece.getBoundingBox().minY(),piece.getBoundingBox().minZ(),piece.getBoundingBox().maxX(),piece.getBoundingBox().maxY(),piece.getBoundingBox().maxZ()))
            if(level.getBlockState(p).is(Blocks.SPAWNER)) spawners++;
        check(spawners==(name.equals("workshop")?1:0),"only one workshop cage; none in small variants: "+name+" actual="+spawners+" bounds="+piece.getBoundingBox());
        for(var entry:plan.getAsJsonArray("chests")) {
            var chest=(ChestBlockEntity)level.getBlockEntity(world(piece,point(entry.getAsJsonObject().getAsJsonArray("pos"))));
            check(chest!=null,"container exists");chest.unpackLootTable(null);
            check(!chest.isEmpty(),"native loot table produced real items");
        }
        for(var entry:plan.getAsJsonArray("chiseled_shelves")) {
            var p=world(piece,point(entry.getAsJsonObject().getAsJsonArray("pos")));
            var shelf=(ChiseledBookShelfBlockEntity)level.getBlockEntity(p);check(shelf!=null,"chiseled shelf exists");int count=0;
            for(int slot=0;slot<6;slot++) {
                var stack=shelf.getItem(slot);boolean occupied=!stack.isEmpty();
                check(level.getBlockState(p).getValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(slot))==occupied,"native occupancy synchronized");
                if(occupied) {count++;check(stack.is(Items.ENCHANTED_BOOK) && !EnchantmentHelper.getEnchantmentsForCrafting(stack).isEmpty(),"real randomized stored enchantments");}
            }
            check(count>=2 && count<=4,"2..4 real books per shelf");
        }
        if(name.equals("workshop")) {
            var door=world(piece,point(plan.getAsJsonArray("entry_door")));
            check(level.getBlockState(door).is(Blocks.BIRCH_DOOR) && level.getBlockState(door).getValue(DoorBlock.FACING)==piece.getRotation().rotate(Direction.NORTH),"entrance door rotates with the template");
            int mainRewards=0;
            for(var entry:plan.getAsJsonArray("chests"))if(entry.getAsJsonObject().get("tier").getAsString().equals("study"))mainRewards++;
            check(mainRewards==1,"one principal high-tier third-floor reward chest");
            var machine=BLUEPRINTS.getAsJsonObject("incomplete_machine");
            var origin=world(piece,point(machine.getAsJsonArray("controller")));
            check(!FormationService.form(level,origin,Direction.NORTH).startsWith("Formed"),"broken display machine cannot assemble");
            for(var missing:machine.getAsJsonArray("missing"))
                check(level.getBlockState(world(piece,point(missing.getAsJsonArray()))).isAir(),"centre and two amethyst blocks absent");
            var cage=(SpawnerBlockEntity)level.getBlockEntity(world(piece,point(plan.getAsJsonArray("spawners").get(0).getAsJsonArray())));
            var nbt=cage.saveWithFullMetadata(level.registryAccess());
            check(nbt.toString().contains("minecraft:zombie"),"native zombie spawner initialized");
            // Conservative player-sized headroom checks at every half-step of the authored helix.
            for(var entry:plan.getAsJsonArray("spiral_walk")) {
                var step=entry.getAsJsonObject();double feet=step.get("feet").getAsDouble();
                var p=world(piece,new BlockPos(step.get("x").getAsInt(),(int)Math.ceil(feet),step.get("z").getAsInt()));
                check(level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir(),"headroom along spiral");
            }
        }
    }
    @GameTest(padding=40,maxTicks=100) public void templatesAllRotationsContentsAndPiecePersistence(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(20,3,20));
        for(String name:new String[]{"small_surface","small_buried","workshop"}) for(var rotation:Rotation.values()) {
            var piece=place(level,origin,name,rotation);verify(level,piece,name);
            var context=StructurePieceSerializationContext.fromLevel(level);var tag=piece.createTag(context);
            var restored=(RuinPiece)Ruins.PIECE.load(context,tag);
            h.assertTrue(restored.getRotation()==rotation && restored.templatePosition().equals(piece.templatePosition()) && restored.template().getSize().equals(piece.template().getSize()),"saved template ID, position and rotation reload");
            for(var entry:BLUEPRINTS.getAsJsonObject(name).getAsJsonArray("chiseled_shelves")) {
                var p=world(piece,point(entry.getAsJsonObject().getAsJsonArray("pos")));var old=level.getBlockEntity(p);var saved=old.saveWithFullMetadata(level.registryAccess());
                var reloaded=(ChiseledBookShelfBlockEntity)BlockEntity.loadStatic(p,level.getBlockState(p),saved,level.registryAccess());
                h.assertTrue(reloaded!=null && !reloaded.isEmpty(),"random shelf books survive block entity serialization");
            }
        }
        h.succeed();
    }
    @GameTest(maxTicks=100) public void nativeLootAndNaturalPlacementRegistries(GameTestHelper h) {
        var level=h.getLevel();var random=RandomSource.create(20261017);
        var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO))).create(LootContextParamSets.CHEST);
        long[] scores=new long[2];int cores=0;
        for(int tier=0;tier<2;tier++) {
            var table=level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,Ruins.id("chests/"+(tier==0?"small":"study"))));
            for(int trial=0;trial<2000;trial++) for(var item:table.getRandomItems(params,random)) {
                scores[tier]+=item.is(Items.ENCHANTED_BOOK)?20:item.getCount();
                if(item.is(ResonanceItems.CORE)) {cores++;h.assertTrue(ResonanceItems.energy(item)==0,"rare core is uncharged");}
                h.assertTrue(!item.is(ResonanceItems.ATTUNED_CORE),"no free attuned core");
            }
        }
        h.assertTrue(scores[1]>scores[0] && cores>0 && cores<100,"higher tier rewards and low core frequency, fixed seed");
        for(String id:new String[]{"small_archive","buried_archive","abandoned_workshop"}) {
            h.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(Ruins.id(id)).orElseThrow().value() instanceof RuinStructure,"datapack structure registered");
            h.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(Ruins.id(id)).isPresent(),"natural structure set registered");
        }
        h.succeed();
    }
}
