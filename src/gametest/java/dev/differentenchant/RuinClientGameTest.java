package dev.differentenchant;

import com.google.gson.*;
import dev.differentenchant.ruins.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.GameType;

/** Normal seeded Overworld. Locate finds native starts; chunks generate them, never /place or fixture stamping. */
public final class RuinClientGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) {RuinGameTests.check(value,message);}
    private static RuinPiece find(ClientGameTestContext c,TestSingleplayerContext world,String id) {
        var located=world.getServer().computeOnServer(s->{
            var level=s.overworld();var holder=level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(Ruins.id(id)).orElseThrow();
            // Photograph an open-biome natural small start as well as testing the forest-capable placement.
            // No clearing leaves or stamping a template to improve the picture.
            if(id.equals("small_archive") || id.equals("abandoned_workshop")) for(var from:new BlockPos[]{new BlockPos(-1600,80,-1600),new BlockPos(1600,80,0),new BlockPos(0,80,-1600),new BlockPos(1600,80,1600)}) {
                var candidate=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),from,80,false);
                if(candidate!=null && level.getBiome(candidate.getFirst()).is(net.minecraft.world.level.biome.Biomes.PLAINS)) return candidate.getFirst();
            }
            var result=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),new BlockPos(0,80,0),80,false);
            check(result!=null,"natural start located: "+id);return result.getFirst();
        });
        world.getServer().runCommand("tp @a "+located.getX()+" 120 "+located.getZ());c.waitTicks(10);world.getClientLevel().waitForChunksRender();
        var piece=world.getServer().computeOnServer(s->{
            var level=s.overworld();var structure=level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(Ruins.id(id)).orElseThrow().value();
            var start=level.structureManager().getStartForStructure(SectionPos.of(located),structure,level.getChunk(located));
            check(start!=null && start.isValid() && start.getPieces().getFirst() instanceof RuinPiece,"natural persisted structure start: "+id);
            return (RuinPiece)start.getPieces().getFirst();
        });
        c.waitTicks(5);return piece;
    }
    private static void camera(ClientGameTestContext c,TestSingleplayerContext world,RuinPiece piece,BlockPos local,BlockPos target,String image) {
        var pos=RuinGameTests.world(piece,local);var at=RuinGameTests.world(piece,target);
        world.getServer().runCommand("tp @a "+(pos.getX()+.5)+" "+pos.getY()+" "+(pos.getZ()+.5));c.waitTicks(5);
        c.getInput().lookAt(at);c.waitTicks(5);world.getClientLevel().waitForChunksRender();
        c.runOnClient(client->{check(client.player.isAlive() && client.screen==null,"valid camera, no death/menu overlay");client.options.hideGui=true;client.getToastManager().clear();client.gui.getChat().clearMessages(true);});c.takeScreenshot(image);
    }
    private static void walkSpiral(ClientGameTestContext c,TestSingleplayerContext world,RuinPiece piece) {
        RuinTraversal.run(c,world,piece,false);
        world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();});
        var plan=RuinGameTests.BLUEPRINTS.getAsJsonObject("workshop");
        boolean spawned=false;
        c.takeScreenshot("ruins-08-spiral-walked-to-third-floor");
        var cage=RuinGameTests.world(piece,RuinGameTests.point(plan.getAsJsonArray("spawners").get(0).getAsJsonArray()));
        // Glazed studies are bright by day. Test the unchanged native cage under actual night conditions.
        world.getServer().runCommand("time set midnight");
        world.getServer().runCommand("tp @a "+(cage.getX()+3.5)+" "+cage.getY()+" "+(cage.getZ()+.5));
        for(int tick=0;tick<900;tick++) {
            spawned=world.getServer().computeOnServer(s->!s.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
                    new net.minecraft.world.phys.AABB(cage).inflate(6,2,6)).isEmpty());
            if(spawned) break;c.waitTick();
        }
        check(spawned,"native cage actually produces a zombie while player is near");
        world.getServer().runCommand("time set day");
        world.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR));
    }
    @Override public void runTest(ClientGameTestContext c) {
        c.getInput().resizeWindow(1280,720);c.runOnClient(client->{client.options.renderDistance().set(5);client.options.simulationDistance().set(5);});
        TestWorldSave save;RuinPiece workshop;Map<String,String> savedInventories;
        var records=new JsonArray();
        var actualWorkshop=new java.util.concurrent.atomic.AtomicReference<RuinPiece>();
        try(var world=c.worldBuilder().setUseConsistentSettings(false).adjustSettings(s->{s.setSeed("20261017");s.setGenerateStructures(true);}).create()) {
            save=world.getWorldSave();world.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR));c.waitTicks(5);
            c.runOnClient(client->check(client.player.isSpectator() && client.player.isAlive(),"actual live spectator before aerial camera teleport"));
            world.getServer().runCommand("time set day");world.getServer().runCommand("weather clear");world.getServer().runCommand("difficulty normal");
            // Camera aid only; structures, lighting blocks, loot and terrain remain the natural result.
            world.getServer().runCommand("effect give @a minecraft:night_vision infinite 0 true");
            for(String id:new String[]{"small_archive","buried_archive","abandoned_workshop"}) {
                var piece=find(c,world,id);String name=id.equals("small_archive")?"small_surface":id.equals("buried_archive")?"small_buried":"workshop";
                world.getServer().runOnServer(s->RuinGameTests.verify(s.overworld(),piece,name));
                var record=new JsonObject();record.addProperty("structure",id);record.addProperty("template",name);record.addProperty("origin",piece.templatePosition().toShortString());record.addProperty("rotation",piece.getRotation().name());record.addProperty("bounds",piece.getBoundingBox().toString());records.add(record);
                if(name.equals("small_surface")) {
                    camera(c,world,piece,new BlockPos(17,7,-8),new BlockPos(5,2,4),"ruins-01-small-surface-natural");
                    camera(c,world,piece,new BlockPos(5,4,-5),new BlockPos(5,2,4),"ruins-01a-small-surface-front");
                    camera(c,world,piece,new BlockPos(5,1,2),new BlockPos(5,2,7),"ruins-02-small-surface-interior");
                } else if(name.equals("small_buried")) {
                    world.getServer().runOnServer(s->{var middle=RuinGameTests.world(piece,new BlockPos(5,0,5));int ground=s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,middle.getX(),middle.getZ());check(ground-piece.templatePosition().getY()>=14,"naturally buried below intact ground");record.addProperty("ground_y",ground);});
                    camera(c,world,piece,new BlockPos(5,1,2),new BlockPos(6,2,7),"ruins-03-small-underground-natural");
                } else {
                    actualWorkshop.set(piece);
                    String biome=world.getServer().computeOnServer(s->s.overworld().getBiome(piece.templatePosition()).unwrapKey().orElseThrow().identifier().toString());
                    record.addProperty("biome",biome);
                    camera(c,world,piece,new BlockPos(-19,12,-16),new BlockPos(11,8,9),"ruins-medium-world-generated");
                    for(var view:RuinGameTests.BLUEPRINTS.getAsJsonObject("workshop").getAsJsonArray("review_views")) {
                        var v=view.getAsJsonObject();camera(c,world,piece,RuinGameTests.point(v.getAsJsonArray("camera")),RuinGameTests.point(v.getAsJsonArray("look")),"ruins-workshop-"+v.get("id").getAsString()+"-natural");
                    }
                    walkSpiral(c,world,piece);
                }
            }
            workshop=Objects.requireNonNull(actualWorkshop.get(),"verified natural workshop");
            savedInventories=world.getServer().computeOnServer(s->captureInventories(s.overworld(),workshop));
        }
        try(var world=save.open()) {
            c.waitTicks(5);world.getServer().runOnServer(s->{
                check(savedInventories.equals(captureInventories(s.overworld(),workshop)),"all 14 native chest/shelf inventories persist exactly after disk reconnect");
                RuinGameTests.verify(s.overworld(),workshop,"workshop");
            });
            camera(c,world,workshop,reviewShelf().offset(3,0,0),reviewShelf(),"ruins-09-reloaded-shelf-books");
        }
        try {var path=Path.of("../reports/ruins/natural-placements.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(records));}catch(Exception error){throw new RuntimeException(error);}
        c.runOnClient(client->client.options.hideGui=false);
    }
    private static BlockPos reviewShelf() {
        return RuinGameTests.point(RuinGameTests.BLUEPRINTS.getAsJsonObject("workshop").getAsJsonArray("chiseled_shelves").get(0).getAsJsonObject().getAsJsonArray("pos"));
    }
    private static Map<String,String> captureInventories(net.minecraft.server.level.ServerLevel level,RuinPiece piece) {
        var result=new LinkedHashMap<String,String>();var plan=RuinGameTests.BLUEPRINTS.getAsJsonObject("workshop");
        for(String type:new String[]{"chests","chiseled_shelves"})for(var entry:plan.getAsJsonArray(type)) {
            var local=RuinGameTests.point(entry.getAsJsonObject().getAsJsonArray("pos"));
            var entity=level.getBlockEntity(RuinGameTests.world(piece,local));check(entity!=null,"persisted inventory entity present");
            result.put(type+local.toShortString(),entity.saveWithFullMetadata(level.registryAccess()).toString());
        }
        check(result.size()==14,"all eight chests and six shelves captured");return result;
    }
}
