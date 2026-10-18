package dev.differentenchant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

/** Opt-in visual review only: command placement on empty superflat, five views, real walking and chest use. */
public final class SmallRuinReviewClientGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) {if(!value) throw new AssertionError(message);}

    private static void teleport(ClientGameTestContext c,TestSingleplayerContext world,BlockPos origin,int x,int y,int z) {
        world.getServer().runCommand("tp @a "+(origin.getX()+x+.5)+" "+(origin.getY()+y)+" "+(origin.getZ()+z+.5));
        c.waitTicks(5);
    }

    private static void camera(ClientGameTestContext c,TestSingleplayerContext world,BlockPos origin,
            int x,int y,int z,BlockPos target,String name) {
        teleport(c,world,origin,x,y,z);c.getInput().lookAt(origin.offset(target));c.waitTicks(4);
        world.getClientLevel().waitForChunksRender();
        c.runOnClient(client->{check(client.player.isAlive() && client.screen==null,"unobstructed live game view");
            client.options.hideGui=true;client.getToastManager().clear();client.gui.getChat().clearMessages(true);});
        c.takeScreenshot(name);
    }

    private static void walk(ClientGameTestContext c,BlockPos origin,int x,int z) {
        double targetX=origin.getX()+x+.5,targetZ=origin.getZ()+z+.5;
        for(int tick=0;tick<80;tick++) {
            if(c.computeOnClient(client->{double dx=targetX-client.player.getX(),dz=targetZ-client.player.getZ();return dx*dx+dz*dz<.035;})) break;
            float yaw=c.computeOnClient(client->(float)(Math.toDegrees(Math.atan2(targetZ-client.player.getZ(),targetX-client.player.getX()))-90));
            c.getInput().lookAt(yaw,0);c.getInput().holdKey(o->o.keyUp);c.waitTick();
        }
        c.getInput().releaseKey(o->o.keyUp);c.waitTicks(2);
        c.runOnClient(client->check(Math.abs(client.player.getX()-targetX)<.45 && Math.abs(client.player.getZ()-targetZ)<.45
                && client.player.getY()>=origin.getY()+(z<0?-.05:.95) && !client.player.getAbilities().flying,"actual walking through entrance and furniture aisle"));
    }

    private static void openChest(ClientGameTestContext c,TestSingleplayerContext world,BlockPos origin,BlockPos chest,String shot) {
        c.getInput().lookAt(origin.offset(chest));c.waitTicks(3);c.getInput().pressKey(o->o.keyUse);
        c.waitFor(client->client.player.containerMenu instanceof ChestMenu && client.screen!=null);
        world.getServer().runOnServer(s->{
            check(s.getPlayerList().getPlayers().getFirst().containerMenu instanceof ChestMenu,"server opened real chest menu");
            check(s.overworld().getBlockEntity(origin.offset(chest)) instanceof ChestBlockEntity box && !box.isEmpty(),"native chest loot unpacked on opening");
        });
        c.takeScreenshot(shot);c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);
    }

    @Override public void runTest(ClientGameTestContext c) {
        c.getInput().resizeWindow(1280,720);
        try(var world=c.worldBuilder().create()) {
            var origin=world.getServer().computeOnServer(s->new BlockPos(0,s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,0,0),0));
            var placed=new AtomicReference<dev.differentenchant.ruins.RuinPiece>();
            world.getServer().runOnServer(s->{
                s.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR);
                // Test-server command; release code and public gameplay commands are unchanged.
                s.getCommands().getDispatcher().register(Commands.literal("small_ruin_candidate").executes(command->{
                    placed.set(RuinGameTests.place(s.overworld(),origin,"small_surface",Rotation.NONE));return 1;
                }));
            });
            world.getServer().runCommand("time set day");world.getServer().runCommand("weather clear");world.getServer().runCommand("difficulty peaceful");
            world.getServer().runCommand("small_ruin_candidate");c.waitTicks(5);
            world.getServer().runOnServer(s->RuinGameTests.verify(s.overworld(),placed.get(),"small_surface"));
            camera(c,world,origin,5,5,-12,new BlockPos(5,4,5),"small-expanded-01-front");
            camera(c,world,origin,22,5,5,new BlockPos(6,4,5),"small-expanded-02-side");
            camera(c,world,origin,18,7,-8,new BlockPos(6,4,5),"small-expanded-03-diagonal");
            camera(c,world,origin,6,20,5,new BlockPos(6,0,5),"small-expanded-04-roof");
            camera(c,world,origin,5,1,3,new BlockPos(6,2,8),"small-expanded-05-interior");
            camera(c,world,origin,7,1,4,new BlockPos(10,2,6),"small-expanded-05a-reading-bay");
            c.runOnClient(client->client.options.hideGui=false);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();});
            teleport(c,world,origin,5,0,-2);c.getInput().lookAt(origin.offset(5,1,2));c.waitTicks(3);
            c.getInput().pressKey(o->o.keyUse);c.waitTicks(5);
            world.getServer().runOnServer(s->check(s.overworld().getBlockState(origin.offset(5,1,2)).getValue(DoorBlock.OPEN),"actual use opens the cottage door"));
            walk(c,origin,5,4);walk(c,origin,4,3);
            openChest(c,world,origin,new BlockPos(3,1,3),"small-expanded-06-first-chest-open");
            walk(c,origin,4,6);
            openChest(c,world,origin,new BlockPos(3,1,6),"small-expanded-07-second-chest-open");
            walk(c,origin,6,6);
            c.getInput().lookAt(origin.offset(7,1,8));c.waitTicks(3);c.getInput().pressKey(o->o.keyUse);
            c.waitFor(client->client.player.containerMenu instanceof CraftingMenu && client.screen!=null);
            world.getServer().runOnServer(s->check(s.getPlayerList().getPlayers().getFirst().containerMenu instanceof CraftingMenu,"research worktable opens real crafting menu"));
            c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);
            walk(c,origin,5,4);walk(c,origin,5,1);walk(c,origin,5,-2);
            try {
                var path=Path.of("../reports/small-expanded/interaction.txt");Files.createDirectories(path.getParent());
                Files.writeString(path,"Superflat daytime command placement at "+origin.toShortString()+"\nReal door use; walked without flight or jumping from grass up porch and through door, then back outside.\nBoth chests opened by client use packets with native nonempty loot.\nResearch worktable opened real crafting menu.\nSmall template only; no medium or enchanting system tests.\n");
            } catch(Exception e) {throw new RuntimeException(e);}
        } finally {c.runOnClient(client->client.options.hideGui=false);}
    }
}
