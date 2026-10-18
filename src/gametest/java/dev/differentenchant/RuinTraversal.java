package dev.differentenchant;

import com.google.gson.JsonArray;
import dev.differentenchant.ruins.RuinPiece;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Real input-driven traversal of the locked blueprint, transformed through native piece rotation. */
final class RuinTraversal {
    private RuinTraversal() {}
    private static void walk(ClientGameTestContext c,RuinPiece piece,int x,double feet,int z) {
        var p=RuinGameTests.world(piece,new BlockPos(x,0,z));
        double tx=p.getX()+.5,tz=p.getZ()+.5,ty=piece.templatePosition().getY()+feet;
        for(int tick=0;tick<100;tick++) {
            if(c.computeOnClient(client->{double dx=tx-client.player.getX(),dz=tz-client.player.getZ();return dx*dx+dz*dz<.035 && client.player.getY()>=ty-.08;})) break;
            float yaw=c.computeOnClient(client->(float)(Math.toDegrees(Math.atan2(tz-client.player.getZ(),tx-client.player.getX()))-90));
            c.getInput().lookAt(yaw,0);c.getInput().holdKey(o->o.keyUp);c.waitTick();
        }
        c.getInput().releaseKey(o->o.keyUp);c.waitTicks(2);
        c.runOnClient(client->RuinGameTests.check(Math.abs(client.player.getX()-tx)<.45 && Math.abs(client.player.getZ()-tz)<.45
            && client.player.getY()>=ty-.15 && client.player.getY()<=ty+.55 && !client.player.getAbilities().flying,
            "real rotated passage "+piece.getRotation()+" local="+x+","+feet+","+z+" actual="+client.player.position()));
    }
    private static void path(ClientGameTestContext c,RuinPiece piece,JsonArray path) {
        for(var item:path) {var p=RuinGameTests.point(item.getAsJsonArray());walk(c,piece,p.getX(),p.getY(),p.getZ());}
    }
    static void run(ClientGameTestContext c,TestSingleplayerContext world,RuinPiece piece,boolean fromGrass) {
        var plan=RuinGameTests.BLUEPRINTS.getAsJsonObject("workshop");
        var door=RuinGameTests.point(plan.getAsJsonArray("entry_door"));
        var start=RuinGameTests.world(piece,fromGrass?door.offset(0,-1,-3):door.offset(0,0,-1));
        world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();});
        world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+start.getY()+" "+(start.getZ()+.5));c.waitTicks(5);
        path(c,piece,plan.getAsJsonArray("entry_approach"));
        c.getInput().lookAt(RuinGameTests.world(piece,door));c.waitTicks(3);c.getInput().pressKey(o->o.keyUse);c.waitTicks(5);
        world.getServer().runOnServer(s->RuinGameTests.check(s.overworld().getBlockState(RuinGameTests.world(piece,door)).getValue(DoorBlock.OPEN),"rotated door really opens"));
        path(c,piece,plan.getAsJsonArray("ground_route"));
        int index=0;
        for(var item:plan.getAsJsonArray("spiral_walk")) {
            var step=item.getAsJsonObject();double feet=step.get("feet").getAsDouble();
            walk(c,piece,step.get("x").getAsInt(),feet,step.get("z").getAsInt());
            for(var itemLanding:plan.getAsJsonArray("spiral_landings")) {
                var landing=itemLanding.getAsJsonObject();if(index!=landing.get("step").getAsInt())continue;
                for(var itemPoint:landing.getAsJsonArray("path")) {var p=itemPoint.getAsJsonArray();walk(c,piece,p.get(0).getAsInt(),feet,p.get(1).getAsInt());}
            }
            index++;
        }
        path(c,piece,plan.getAsJsonArray("reward_route"));
        var chest=RuinGameTests.world(piece,RuinGameTests.point(plan.getAsJsonArray("reward_chest")));
        c.getInput().lookAt(chest);c.waitTicks(3);c.getInput().pressKey(o->o.keyUse);
        c.waitFor(client->client.player.containerMenu instanceof ChestMenu && client.screen!=null);
        world.getServer().runOnServer(s->RuinGameTests.check(s.getPlayerList().getPlayers().getFirst().containerMenu instanceof ChestMenu
            && s.overworld().getBlockEntity(chest) instanceof ChestBlockEntity box && !box.isEmpty(),"rotated principal reward chest really opens"));
        c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);
        world.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR));
    }
}
