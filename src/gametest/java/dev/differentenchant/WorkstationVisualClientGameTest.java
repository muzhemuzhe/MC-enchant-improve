package dev.differentenchant;

import java.nio.file.*;
import java.util.*;
import dev.differentenchant.workstation.*;
import dev.differentenchant.research.*;
import dev.differentenchant.disenchant.*;
import dev.differentenchant.client.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;

/** Same camera before/after, real world models, native assembly/use and disk reload. */
public final class WorkstationVisualClientGameTest implements FabricClientGameTest {
    private static void check(boolean ok,String reason){DisenchantGameTests.check(ok,reason);}
    private static void aim(ClientGameTestContext c,double x,double y,double z){var a=c.computeOnClient(m->{var e=m.player.getEyePosition();double dx=x-e.x,dy=y-e.y,dz=z-e.z;return new float[]{(float)(Math.toDegrees(Math.atan2(dz,dx))-90),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)))};});c.getInput().lookAt(a[0],a[1]);}
    private static void photo(ClientGameTestContext c,String name){c.runOnClient(m->{m.options.hideGui=true;m.getToastManager().clear();});c.waitTicks(12);c.takeScreenshot("visual-"+name);}
    private static void view(ClientGameTestContext c,TestSingleplayerContext w,BlockPos core){w.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().teleportTo(core.getX()+3.5,-57,-3.5));c.waitTicks(5);aim(c,core.getX()+.5,-58.8,.5);}
    private static void form(ClientGameTestContext c,TestSingleplayerContext w,BlockPos core){
        w.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().teleportTo(core.getX()-.5,-58,-.5));c.waitTicks(4);
        c.getInput().pressKey(o->o.keyHotbarSlots[7]);c.waitTicks(3);c.getInput().holdKey(o->o.keyShift);c.waitTicks(4);aim(c,core.getX()+.5,core.getY()+.84,.5);c.waitTicks(3);
        c.runOnClient(m->check(m.hitResult instanceof net.minecraft.world.phys.BlockHitResult h&&h.getBlockPos().equals(core)&&m.player.getMainHandItem().isEmpty(),"native assembly targets unformed empty-hand core"));
        c.getInput().pressKey(o->o.keyUse);c.waitTicks(4);c.getInput().releaseKey(o->o.keyShift);
    }
    @Override public void runTest(ClientGameTestContext c){
        c.getInput().resizeWindow(1600,1000);var d=new BlockPos(0,-59,0);var r=new BlockPos(48,-59,0);var ids=new ArrayList<UUID>();var saveHolder=new net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave[1];
        try(var w=c.worldBuilder().create()){
            saveHolder[0]=w.getWorldSave();w.getServer().runCommand("time set day");w.getServer().runCommand("weather clear");w.getServer().runCommand("difficulty peaceful");
            w.getServer().runOnServer(s->{for(var core:List.of(d,r)){WorkstationFixtures.build(s.overworld(),core,core.equals(d)?WorkstationStructure.Kind.DISENCHANT:WorkstationStructure.Kind.RESEARCH);s.overworld().setBlockAndUpdate(new BlockPos(core.getX()+3,-58,-4),Blocks.BARRIER.defaultBlockState());}var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();});
            for(var core:List.of(r,d)){
                var kind=core.equals(d)?WorkstationStructure.Kind.DISENCHANT:WorkstationStructure.Kind.RESEARCH;String name=core.equals(d)?"disenchanter":"research";
                view(c,w,core);photo(c,name+"-01-before");
                var originals=w.getServer().computeOnServer(s->WorkstationGameTests.states(s.overworld(),core));form(c,w,core);
                ids.add(w.getServer().computeOnServer(s->{var master=WorkstationStructure.controller(s.overworld(),core,kind);check(master!=null,"native formed "+name);return master.machineId();}));
                view(c,w,core);photo(c,name+"-02-formed");
                // Low angle shows the open space beneath the reading wings and extraction channels.
                w.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().teleportTo(core.getX()+3.5,-60,-4.5));c.waitTicks(5);aim(c,core.getX()+.5,-58.65,.5);photo(c,name+"-03-low-angle");
                w.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().teleportTo(core.getX()+.5,-60,-2.5));c.waitTicks(5);c.runOnClient(m->m.options.hideGui=false);aim(c,core.getX()+.5,core.getY()+.5,.5);c.waitTicks(4);c.getInput().pressKey(o->o.keyUse);
                if(core.equals(d))c.waitForScreen(DisenchantScreen.class);else c.waitForScreen(ResearchScreen.class);c.waitTicks(5);
                c.runOnClient(m->check(core.equals(d)?m.player.containerMenu instanceof DisenchantMenu:m.player.containerMenu instanceof ResearchMenu,"native formed model interaction opens existing menu"));
                c.takeScreenshot("visual-"+name+"-04-menu");c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);
                // Remove one real formed part, inspect every other original state; no cosmetic substitute blocks remain.
                w.getServer().runOnServer(s->{s.overworld().destroyBlock(core,false);for(int i=0;i<18;i++)if(i!=13)check(s.overworld().getBlockState(WorkstationStructure.position(core,i)).equals(originals.get(i)),"exact state restoration "+name+" "+i);s.overworld().setBlockAndUpdate(core,originals.get(13));});
                c.waitTicks(5);view(c,w,core);photo(c,name+"-05-restored");
                form(c,w,core);view(c,w,core);photo(c,name+"-06-reassembled");
                w.getServer().runOnServer(s->check(WorkstationStructure.controller(s.overworld(),core,kind)!=null,"reassembled after new model removal"));
            }
        }
        try(var w=saveHolder[0].open()){
            w.getServer().runOnServer(s->{check(WorkstationStructure.controller(s.overworld(),r,WorkstationStructure.Kind.RESEARCH)!=null&&WorkstationStructure.controller(s.overworld(),d,WorkstationStructure.Kind.DISENCHANT)!=null,"both visual machines survive disk reload");});
            view(c,w,r);photo(c,"research-07-reloaded");view(c,w,d);photo(c,"disenchanter-07-reloaded");
        }
        c.runOnClient(m->m.options.hideGui=false);
        try{var dir=Path.of("../reports/workstation-visuals");Files.createDirectories(dir);Files.writeString(dir.resolve("native-client.txt"),"Both unchanged material frames: native empty-hand sneak assembly; same-camera before/formed/restored/reassembled screenshots; native right-click opens the existing menus; all 17 remaining original states restore exactly; both formed machines persist through actual world save and reopening.\n1600x1000 raw OpenGL screenshots. No image edits. No new particle or gameplay implementation.\n");}catch(Exception e){throw new RuntimeException(e);}
    }
}
