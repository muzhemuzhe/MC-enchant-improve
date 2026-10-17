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
            if(id.equals("small_archive")) for(var from:new BlockPos[]{new BlockPos(-1600,80,-1600),new BlockPos(1600,80,0),new BlockPos(0,80,-1600),new BlockPos(1600,80,1600)}) {
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
        c.runOnClient(client->{check(client.player.isAlive() && client.screen==null,"valid camera, no death/menu overlay");client.getToastManager().clear();client.gui.getChat().clearMessages(true);});c.takeScreenshot(image);
    }
    private static void walkTo(ClientGameTestContext c,RuinPiece piece,int localX,double feet,int localZ) {
            var block=RuinGameTests.world(piece,new BlockPos(localX,0,localZ));
            double x=block.getX()+.5,z=block.getZ()+.5,y=piece.templatePosition().getY()+feet;
            for(int tick=0;tick<35;tick++) {
                boolean reached=c.computeOnClient(client->{double dx=x-client.player.getX(),dz=z-client.player.getZ();return dx*dx+dz*dz<.035 && client.player.getY()>=y-.08;});
                if(reached) break;
                float yaw=c.computeOnClient(client->(float)(Math.toDegrees(Math.atan2(z-client.player.getZ(),x-client.player.getX()))-90));
                c.getInput().lookAt(yaw,0);c.getInput().holdKey(o->o.keyUp);c.waitTick();
            }
            c.getInput().releaseKey(o->o.keyUp);c.waitTicks(2);
            c.runOnClient(client->check(Math.abs(client.player.getX()-x)<.45 && Math.abs(client.player.getZ()-z)<.45 && client.player.getY()>=y-.1,"actual walking without flight/jump along spiral and landings at "+x+","+y+","+z));
    }
    private static void walkSpiral(ClientGameTestContext c,TestSingleplayerContext world,RuinPiece piece) {
        world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();});
        var first=RuinGameTests.world(piece,new BlockPos(15,1,9));
        world.getServer().runCommand("tp @a "+(first.getX()+.5)+" "+first.getY()+" "+(first.getZ()+.5));c.waitTicks(5);
        int index=0;
        for(var entry:RuinGameTests.BLUEPRINTS.getAsJsonObject("workshop").getAsJsonArray("spiral_walk")) {
            var step=entry.getAsJsonObject();double feet=step.get("feet").getAsDouble();
            walkTo(c,piece,step.get("x").getAsInt(),feet,step.get("z").getAsInt());
            if(index==8 || index==16) {
                // Actually leave the shaft, enter the room and return; headroom alone misses blocked exits.
                for(var point:new int[][]{{16,9},{16,6},{12,6},{16,6},{16,9},{15,9}}) walkTo(c,piece,point[0],feet,point[1]);
            }
            index++;
        }
        boolean spawned=false;
        var cage=RuinGameTests.world(piece,new BlockPos(4,1,9));
        for(int tick=0;tick<900;tick++) {
            spawned=world.getServer().computeOnServer(s->!s.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
                    new net.minecraft.world.phys.AABB(cage).inflate(6,2,6)).isEmpty());
            if(spawned) break;c.waitTick();
        }
        check(spawned,"native cage actually produces a zombie while player is near");
        c.takeScreenshot("ruins-08-spiral-walked-to-third-floor");
        world.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR));
    }
    @Override public void runTest(ClientGameTestContext c) {
        c.getInput().resizeWindow(1280,720);c.runOnClient(client->{client.options.renderDistance().set(5);client.options.simulationDistance().set(5);});
        TestWorldSave save;RuinPiece workshop;String savedShelf;
        var records=new JsonArray();
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
                    camera(c,world,piece,new BlockPos(-13,10,-15),new BlockPos(9,6,6),"ruins-04-workshop-exterior-natural");
                    camera(c,world,piece,new BlockPos(6,1,6),new BlockPos(3,1,10),"ruins-05-workshop-first-floor-spawner");
                    camera(c,world,piece,new BlockPos(10,6,9),new BlockPos(8,6,7),"ruins-06-workshop-second-broken-machine");
                    camera(c,world,piece,new BlockPos(12,9,8),new BlockPos(4,9,7),"ruins-07-workshop-third-library");
                    walkSpiral(c,world,piece);
                }
            }
            workshop=world.getServer().computeOnServer(s->{var registry=s.registryAccess().lookupOrThrow(Registries.STRUCTURE);var located=s.overworld().getChunkSource().getGenerator().findNearestMapStructure(s.overworld(),HolderSet.direct(registry.get(Ruins.id("abandoned_workshop")).orElseThrow()),new BlockPos(0,80,0),80,false).getFirst();return (RuinPiece)s.overworld().structureManager().getStartForStructure(SectionPos.of(located),registry.get(Ruins.id("abandoned_workshop")).orElseThrow().value(),s.overworld().getChunk(located)).getPieces().getFirst();});
            savedShelf=world.getServer().computeOnServer(s->{var shelf=(ChiseledBookShelfBlockEntity)s.overworld().getBlockEntity(RuinGameTests.world(workshop,new BlockPos(3,5,3)));return shelf.saveWithFullMetadata(s.registryAccess()).toString();});
        }
        try(var world=save.open()) {
            c.waitTicks(5);world.getServer().runOnServer(s->{
                var shelf=(ChiseledBookShelfBlockEntity)s.overworld().getBlockEntity(RuinGameTests.world(workshop,new BlockPos(3,5,3)));
                check(shelf!=null && savedShelf.equals(shelf.saveWithFullMetadata(s.registryAccess()).toString()),"natural enchanted books persist exactly after disk reconnect");
                RuinGameTests.verify(s.overworld(),workshop,"workshop");
            });
            camera(c,world,workshop,new BlockPos(12,5,9),new BlockPos(4,5,4),"ruins-09-reloaded-shelf-books");
        }
        try {var path=Path.of("../reports/ruins/natural-placements.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(records));}catch(Exception error){throw new RuntimeException(error);}
    }
}
