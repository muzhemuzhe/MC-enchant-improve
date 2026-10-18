package dev.differentenchant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import dev.differentenchant.ruins.RuinPiece;
import dev.differentenchant.ruins.Ruins;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

/** Opt-in building review: flat daytime placement and real entrance, helix and reward-chest use. */
public final class MediumRuinReviewClientGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) {RuinGameTests.check(value,message);}

    private static void teleport(ClientGameTestContext c,TestSingleplayerContext world,BlockPos origin,BlockPos p) {
        world.getServer().runCommand("tp @a "+(origin.getX()+p.getX()+.5)+" "+(origin.getY()+p.getY())+" "+(origin.getZ()+p.getZ()+.5));c.waitTicks(5);
    }
    private static void camera(ClientGameTestContext c,TestSingleplayerContext world,BlockPos origin,BlockPos p,BlockPos target,String image) {
        teleport(c,world,origin,p);c.getInput().lookAt(origin.offset(target));c.waitTicks(5);world.getClientLevel().waitForChunksRender();
        c.runOnClient(client->{check(client.player.isAlive() && client.screen==null,"live unobstructed review camera");
            client.options.hideGui=true;client.getToastManager().clear();client.gui.getChat().clearMessages(true);});
        c.takeScreenshot(image);
    }
    private static void walk(ClientGameTestContext c,BlockPos origin,int x,double feet,int z) {
        double tx=origin.getX()+x+.5,tz=origin.getZ()+z+.5,ty=origin.getY()+feet;
        for(int tick=0;tick<90;tick++) {
            if(c.computeOnClient(client->{double dx=tx-client.player.getX(),dz=tz-client.player.getZ();return dx*dx+dz*dz<.035 && client.player.getY()>=ty-.08;})) break;
            float yaw=c.computeOnClient(client->(float)(Math.toDegrees(Math.atan2(tz-client.player.getZ(),tx-client.player.getX()))-90));
            c.getInput().lookAt(yaw,0);c.getInput().holdKey(o->o.keyUp);c.waitTick();
        }
        c.getInput().releaseKey(o->o.keyUp);c.waitTicks(2);
        // The player's width can overlap the next half-slab while the centre is still on this waypoint.
        c.runOnClient(client->check(Math.abs(client.player.getX()-tx)<.45 && Math.abs(client.player.getZ()-tz)<.45
            && client.player.getY()>=ty-.15 && client.player.getY()<=ty+.55 && !client.player.getAbilities().flying,
            "walk without flight/jump: "+x+","+feet+","+z+" actual "+client.player.position()));
    }
    private static void walkPath(ClientGameTestContext c,BlockPos origin,com.google.gson.JsonArray path) {
        for(var entry:path) {var p=RuinGameTests.point(entry.getAsJsonArray());walk(c,origin,p.getX(),p.getY(),p.getZ());}
    }
    @Override public void runTest(ClientGameTestContext c) {
        c.getInput().resizeWindow(1280,720);
        try(var world=c.worldBuilder().create()) {
            var origin=world.getServer().computeOnServer(s->new BlockPos(0,s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,0,0),0));
            var placed=new AtomicReference<RuinPiece>();
            world.getServer().runOnServer(s->{
                s.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR);
                s.getCommands().getDispatcher().register(Commands.literal("medium_ruin_candidate").executes(command->{
                    placed.set(RuinGameTests.place(s.overworld(),origin,"workshop",Rotation.NONE));return 1;
                }));
            });
            world.getServer().runCommand("time set day");world.getServer().runCommand("weather clear");world.getServer().runCommand("difficulty peaceful");
            world.getServer().runCommand("medium_ruin_candidate");c.waitTicks(5);
            var plan=RuinGameTests.BLUEPRINTS.getAsJsonObject("workshop");
            world.getServer().runOnServer(s->{
                RuinGameTests.verify(s.overworld(),placed.get(),"workshop");
                check(s.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(Ruins.id("abandoned_workshop")).isPresent(),"existing worldgen structure registered");
                check(s.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(Ruins.id("abandoned_workshop")).isPresent(),"existing structure set registered");
                check(plan.getAsJsonArray("floors").size()==3,"three furnished storeys");
            });
            for(var view:plan.getAsJsonArray("review_views")) {
                var v=view.getAsJsonObject();camera(c,world,origin,RuinGameTests.point(v.getAsJsonArray("camera")),RuinGameTests.point(v.getAsJsonArray("look")),"medium-massing-"+v.get("id").getAsString());
            }
            // The room view shows context; this separate close view exposes all three missing machine parts.
            var machine=RuinGameTests.point(RuinGameTests.BLUEPRINTS.getAsJsonObject("incomplete_machine").getAsJsonArray("controller"));
            camera(c,world,origin,machine.above(),machine.below(2),"medium-massing-09-machine-missing-parts");
            c.runOnClient(client->client.options.hideGui=false);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();});
            var door=RuinGameTests.point(plan.getAsJsonArray("entry_door"));
            teleport(c,world,origin,door.offset(0,-1,-3));
            // Move to the porch first: the door must be in normal block-interaction reach.
            walkPath(c,origin,plan.getAsJsonArray("entry_approach"));c.getInput().lookAt(origin.offset(door));c.waitTicks(3);c.getInput().pressKey(o->o.keyUse);c.waitTicks(5);
            world.getServer().runOnServer(s->check(s.overworld().getBlockState(origin.offset(door)).getValue(DoorBlock.OPEN),"real use opens front door"));
            walkPath(c,origin,plan.getAsJsonArray("ground_route"));
            int index=0;
            for(var entry:plan.getAsJsonArray("spiral_walk")) {
                var step=entry.getAsJsonObject();double feet=step.get("feet").getAsDouble();
                walk(c,origin,step.get("x").getAsInt(),feet,step.get("z").getAsInt());
                for(var landing:plan.getAsJsonArray("spiral_landings")) if(index==landing.getAsJsonObject().get("step").getAsInt()) {
                    for(var p:landing.getAsJsonObject().getAsJsonArray("path")) {
                        var point=p.getAsJsonArray();walk(c,origin,point.get(0).getAsInt(),feet,point.get(1).getAsInt());
                    }
                }
                index++;
            }
            walkPath(c,origin,plan.getAsJsonArray("reward_route"));
            var chest=RuinGameTests.point(plan.getAsJsonArray("reward_chest"));c.getInput().lookAt(origin.offset(chest));c.waitTicks(3);c.getInput().pressKey(o->o.keyUse);
            c.waitFor(client->client.player.containerMenu instanceof ChestMenu && client.screen!=null);
            world.getServer().runOnServer(s->check(s.getPlayerList().getPlayers().getFirst().containerMenu instanceof ChestMenu
                && s.overworld().getBlockEntity(origin.offset(chest)) instanceof ChestBlockEntity box && !box.isEmpty(),"real third-floor reward chest opens with native loot"));
            c.takeScreenshot("medium-massing-08-reward-chest");c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);
            var verifiedRotations=new com.google.gson.JsonArray();verifiedRotations.add(Rotation.NONE.name());
            for(var rotation:Rotation.values()) {
                if(rotation==Rotation.NONE)continue;
                var anchor=origin.offset(rotation.ordinal()*64,0,0);
                world.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR));
                teleport(c,world,anchor,new BlockPos(0,25,0));world.getClientLevel().waitForChunksRender();
                var rotated=world.getServer().computeOnServer(s->RuinGameTests.place(s.overworld(),anchor,"workshop",rotation));
                world.getServer().runOnServer(s->RuinGameTests.verify(s.overworld(),rotated,"workshop"));
                RuinTraversal.run(c,world,rotated,true);verifiedRotations.add(rotation.name());
            }
            try {
                var report=Path.of("../reports/medium-massing/interaction.txt");Files.createDirectories(report.getParent());
                Files.writeString(report,"Daytime empty superflat, native template command placement at "+origin.toShortString()+".\nTemplate contents: one zombie cage, missing machine centre/two crystals, six filled chiseled shelves, eight nonempty loot chests.\nReal door use and walking from grass through axial vestibule and public hall.\nAll 21 helix waypoints walked without flight/jump; visited both second-floor wings and returned, then entered the third-floor loft.\nThird-floor reward chest opened by actual use packet.\nNo natural-terrain generation, actual mob spawning, full enchanting regression or disk reload tested in this review.\n");
                var rotationsReport=Path.of("../reports/medium-final/rotations.json");Files.createDirectories(rotationsReport.getParent());Files.writeString(rotationsReport,verifiedRotations.toString());
            } catch(Exception e) {throw new RuntimeException(e);}
        } finally {c.runOnClient(client->client.options.hideGui=false);}
    }
}
