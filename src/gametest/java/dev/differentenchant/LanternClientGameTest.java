package dev.differentenchant;

import dev.differentenchant.ruins.*;
import java.nio.file.*;
import java.util.Map;
import java.util.LinkedHashMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;

/** Opt-in lantern-only render, interaction, night lighting and natural-generation checks. */
public final class LanternClientGameTest implements FabricClientGameTest {
    private static void check(boolean condition,String message) {LanternGameTests.check(condition,message);}
    private static void teleport(ClientGameTestContext c,TestSingleplayerContext world,RuinPiece piece,BlockPos local) {
        var p=RuinGameTests.world(piece,local);
        world.getServer().runCommand("tp @a "+(p.getX()+.5)+" "+p.getY()+" "+(p.getZ()+.5));c.waitTicks(5);
    }
    private static void camera(ClientGameTestContext c,TestSingleplayerContext world,RuinPiece piece,BlockPos local,BlockPos target,String image) {
        teleport(c,world,piece,local);c.getInput().lookAt(RuinGameTests.world(piece,target));c.waitTicks(5);world.getClientLevel().waitForChunksRender();
        c.runOnClient(client->{check(client.player.isAlive()&&client.screen==null,"unobstructed live game view");client.options.hideGui=true;client.getToastManager().clear();client.gui.getChat().clearMessages(true);});
        c.takeScreenshot("lantern-"+image);
    }
    private static void walk(ClientGameTestContext c,RuinPiece piece,int x,int feet,int z) {
        var p=RuinGameTests.world(piece,new BlockPos(x,feet,z));double tx=p.getX()+.5,tz=p.getZ()+.5;
        for(int tick=0;tick<100;tick++) {
            if(c.computeOnClient(client->Math.abs(client.player.getX()-tx)<.16&&Math.abs(client.player.getZ()-tz)<.16&&client.player.getY()>=p.getY()-.08)) break;
            float yaw=c.computeOnClient(client->(float)(Math.toDegrees(Math.atan2(tz-client.player.getZ(),tx-client.player.getX()))-90));
            c.getInput().lookAt(yaw,0);c.getInput().holdKey(o->o.keyUp);c.waitTick();
        }
        c.getInput().releaseKey(o->o.keyUp);c.waitTicks(2);
        c.runOnClient(client->check(Math.abs(client.player.getX()-tx)<.45&&Math.abs(client.player.getZ()-tz)<.45
            &&client.player.getY()>=p.getY()-.15&&client.player.getY()<=p.getY()+.55&&!client.player.getAbilities().flying,"walkable gallery/stair "+p+" actual "+client.player.position()));
    }
    private static Map<String,String> caches(TestSingleplayerContext world,RuinPiece piece) {
        return world.getServer().computeOnServer(s->{var result=new LinkedHashMap<String,String>();
            for(var p:LanternCachePolicy.select(piece.cacheSeed())) {var at=LanternGameTests.world(piece,p);var barrel=(BarrelBlockEntity)s.overworld().getBlockEntity(at);
                check(barrel!=null,"persisted cache exists");result.put(at.toShortString(),barrel.saveWithFullMetadata(s.registryAccess()).toString());}
            return result;
        });
    }
    @Override public void runTest(ClientGameTestContext c) {
        c.getInput().resizeWindow(1280,720);c.runOnClient(client->{client.options.renderDistance().set(6);client.options.simulationDistance().set(5);});
        var report=new StringBuilder();
        try(var world=c.worldBuilder().create()) {
            world.getServer().runCommand("time set day");world.getServer().runCommand("weather clear");world.getServer().runCommand("difficulty peaceful");
            var origin=world.getServer().computeOnServer(s->new BlockPos(0,s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,0,0),0));
            var piece=world.getServer().computeOnServer(s->{s.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR);
                var placed=LanternGameTests.place(s.overworld(),origin,Rotation.NONE,2026101807L);LanternGameTests.verify(s.overworld(),placed,false);return placed;});
            for(var view:RuinGameTests.BLUEPRINTS.getAsJsonObject("lantern_sanctum").getAsJsonArray("review_views")) {
                var v=view.getAsJsonObject();camera(c,world,piece,RuinGameTests.point(v.getAsJsonArray("camera")),RuinGameTests.point(v.getAsJsonArray("look")),v.get("id").getAsString());
            }
            world.getServer().runCommand("time set midnight");c.waitTicks(40);
            camera(c,world,piece,new BlockPos(40,22,-8),new BlockPos(16,12,16),"07-night-exterior");
            camera(c,world,piece,new BlockPos(8,17,11),new BlockPos(22,19,21),"08-night-crown");
            world.getServer().runOnServer(s->{var p=RuinGameTests.world(piece,new BlockPos(23,18,15));
                check(s.overworld().getBrightness(LightLayer.BLOCK,p)>=10,"actual propagated crown light, no night vision");});
            world.getServer().runCommand("time set day");
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();});
            teleport(c,world,piece,new BlockPos(15,2,1));c.getInput().lookAt(RuinGameTests.world(piece,new BlockPos(15,2,2)));c.waitTicks(3);c.getInput().pressKey(o->o.keyUse);c.waitTicks(5);
            world.getServer().runOnServer(s->check(s.overworld().getBlockState(RuinGameTests.world(piece,new BlockPos(15,2,2))).getValue(DoorBlock.OPEN),"real use opens lantern entrance"));
            walk(c,piece,15,2,5);walk(c,piece,15,2,22);walk(c,piece,7,2,22);walk(c,piece,7,2,21);
            for(int i=0;i<7;i++) walk(c,piece,7,3+i,20-i);
            // Turn on the north landing before approaching the next stair's low edge.
            walk(c,piece,7,9,13);walk(c,piece,9,9,13);
            for(int i=0;i<7;i++) walk(c,piece,9,10+i,14+i);
            walk(c,piece,9,16,22);walk(c,piece,16,16,24);
            report.append("Flat template CacheSeed 2026101807: real door use; both switchback stair flights and all three floors walked without flight/jump.\n")
                .append("Flat selected caches: ").append(LanternCachePolicy.select(piece.cacheSeed())).append(".\n");
            // Test-only cutaway: remove the carpet above one selected barrel; no production marker remains.
            var selected=LanternCachePolicy.select(piece.cacheSeed()).getFirst();var barrel=LanternGameTests.world(piece,selected);
            world.getServer().runOnServer(s->{s.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR);s.overworld().setBlock(barrel.above(),Blocks.AIR.defaultBlockState(),2);});
            camera(c,world,piece,new BlockPos(selected.x()+2,5,selected.z()-2),new BlockPos(selected.x(),1,selected.z()),"09-hidden-barrel-cutaway");
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();});
            teleport(c,world,piece,new BlockPos(selected.x()+1,2,selected.z()));c.getInput().lookAt(barrel);c.waitTicks(3);c.getInput().pressKey(o->o.keyUse);
            c.waitFor(client->client.player.containerMenu instanceof ChestMenu&&client.screen!=null);
            world.getServer().runOnServer(s->check(!((BarrelBlockEntity)s.overworld().getBlockEntity(barrel)).isEmpty(),"real barrel interaction rolls native loot"));
            c.runOnClient(client->client.options.hideGui=false);c.takeScreenshot("lantern-10-hidden-barrel-loot");c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);
            report.append("Day/night screenshots without night vision; crown block-light >=10. Test-only one-carpet cutaway, actual barrel use and loot GUI.\n");
        }
        TestWorldSave save;RuinPiece natural;Map<String,String> before;
        try(var world=c.worldBuilder().setUseConsistentSettings(false).adjustSettings(s->{s.setSeed("20261018");s.setGenerateStructures(true);}).create()) {
            save=world.getWorldSave();world.getServer().runCommand("time set day");world.getServer().runCommand("weather clear");world.getServer().runCommand("difficulty peaceful");
            world.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR));
            var located=world.getServer().computeOnServer(s->{var level=s.overworld();var holder=s.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(Ruins.id("lantern_sanctum")).orElseThrow();
                var found=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),new BlockPos(0,80,0),100,false);
                check(found!=null,"natural lantern located in seeded Overworld");return found.getFirst();});
            world.getServer().runCommand("tp @a "+located.getX()+" 130 "+located.getZ());c.waitTicks(15);world.getClientLevel().waitForChunksRender();
            natural=world.getServer().computeOnServer(s->{var level=s.overworld();var structure=s.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(Ruins.id("lantern_sanctum")).orElseThrow().value();
                var start=level.structureManager().getStartForStructure(SectionPos.of(located),structure,level.getChunk(located));
                check(start!=null&&start.isValid()&&start.getPieces().getFirst() instanceof RuinPiece,"native generated start, not template stamping");
                var piece=(RuinPiece)start.getPieces().getFirst();LanternGameTests.verify(level,piece,false);return piece;});
            camera(c,world,natural,new BlockPos(54,26,-24),new BlockPos(16,12,16),"11-natural-overworld");
            report.append("Natural Overworld seed 20261018, origin ").append(natural.templatePosition()).append(", rotation ").append(natural.getRotation()).append(", CacheSeed ").append(natural.cacheSeed()).append(".\n")
                .append("Natural selected caches: ").append(LanternCachePolicy.select(natural.cacheSeed())).append(".\n");
            before=caches(world,natural);
        }
        try(var world=save.open()) {
            c.waitTicks(5);check(before.equals(caches(world,natural)),"all unopened barrel loot references/seeds survive actual disk save/reopen");
            world.getServer().runOnServer(s->{var level=s.overworld();var structure=s.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(Ruins.id("lantern_sanctum")).orElseThrow().value();
                var savedStart=level.structureManager().getStartForStructure(SectionPos.of(natural.templatePosition()),structure,level.getChunk(natural.templatePosition()));
                check(savedStart!=null&&savedStart.isValid()&&savedStart.getPieces().getFirst() instanceof RuinPiece restored&&restored.cacheSeed()==natural.cacheSeed(),"native saved structure piece keeps CacheSeed on disk");});
            report.append("Natural cache block entities identical and native structure CacheSeed preserved after actual disk save/reopen.\n");
        }
        try {var path=Path.of("../reports/lantern/interaction.txt");Files.createDirectories(path.getParent());Files.writeString(path,report.toString());}catch(Exception e){throw new RuntimeException(e);}
        c.runOnClient(client->client.options.hideGui=false);
    }
}
