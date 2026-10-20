package dev.differentenchant;

import dev.differentenchant.realm.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;

/** Screenshots are live native rendering of unedited, seed-generated Realm chunks. */
public final class RealmClientGameTest implements FabricClientGameTest {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static String biome(ServerLevel l,BlockPos p){return l.getBiome(p).unwrapKey().orElseThrow().identifier().getPath();}
    private static void fallingCommand(TestSingleplayerContext w,String action) {
        w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.fallDistance=40;
            try {check(s.getCommands().getDispatcher().execute("differentenchant dimension "+action,p.createCommandSourceStack().withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS))==1,"admin command succeeds while falling");}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new RuntimeException(e);}
            check(p.fallDistance==0&&p.onGround(),"teleport clears incoming accumulated fall distance");
        });
    }
    private static long fingerprint(ServerLevel l) {
        long hash=0xcbf29ce484222325L;
        for(int z=30;z<=31;z++)for(int x=30;x<=31;x++)l.getChunk(x,z);
        for(int z=480;z<512;z++)for(int x=480;x<512;x++)for(int y=50;y<224;y++) {
            hash^=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(l.getBlockState(new BlockPos(x,y,z)).getBlock());hash*=0x100000001b3L;
        }
        return hash;
    }
    private static void camera(ClientGameTestContext c,TestSingleplayerContext w,double x,double y,double z,BlockPos target,String name) {
        w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(s.getLevel(RealmContent.REALM),x,y,z,Set.of(),0,0,true);});
        c.waitFor(m->m.level.dimension().equals(RealmContent.REALM)&&Math.abs(m.player.getX()-x)<1);
        c.getInput().lookAt(target);c.waitTicks(80);w.getClientLevel().waitForChunksRender(false);
        c.runOnClient(m->{check(m.level.getBlockState(BlockPos.containing(m.player.getEyePosition())).isAir(),"camera has clear air at "+name);m.options.hideGui=true;m.gui.getChat().clearMessages(true);m.getToastManager().clear();});
        System.out.println("REALM CAMERA "+name+" camera="+x+","+y+","+z+" target="+target.getX()+","+target.getY()+","+target.getZ());
        c.waitTicks(5);c.takeScreenshot("realm-"+name);
    }
    @Override public void runTest(ClientGameTestContext c) {
        c.getInput().resizeWindow(1600,900);
        c.runOnClient(m->{m.options.renderDistance().set(8);m.options.simulationDistance().set(5);});
        var report=new StringBuilder();TestWorldSave save;BlockPos[] views;String[] sample;long generatedHash;
        try(var w=c.worldBuilder().setUseConsistentSettings(false).adjustSettings(ui->{ui.setSeed("2026102001");ui.setAllowCommands(true);}).create()) {
            save=w.getWorldSave();
            w.getServer().runCommand("difficulty peaceful");
            w.getServer().runOnServer(s->{check(s.getLevel(RealmContent.REALM)!=null,"real dynamic dimension registered");var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);
                var denied=p.createCommandSourceStack().withPermission(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS);
                check(!s.getCommands().getDispatcher().getRoot().getChild("differentenchant").canUse(denied),"non-admin cannot access developer commands");
                check(s.getLevel(Level.NETHER)!=null&&s.getLevel(Level.END)!=null,"all three vanilla dimensions remain registered");
                for(var key:new net.minecraft.resources.ResourceKey[]{Level.OVERWORLD,Level.NETHER,Level.END}) {
                    @SuppressWarnings("unchecked") var l=s.getLevel((net.minecraft.resources.ResourceKey<Level>)key);
                    var generator=(net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator)l.getChunkSource().getGenerator();
                    check(generator.generatorSettings().unwrapKey().orElseThrow().identifier().getNamespace().equals("minecraft"),"vanilla dimension retains native noise settings");
                }
            });
            var old=w.getServer().computeOnServer(s->s.getPlayerList().getPlayers().getFirst().position());
            fallingCommand(w,"enter");
            c.waitFor(m->m.level.dimension().equals(RealmContent.REALM));c.waitTicks(30);
            w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(RealmTravel.safe(p.level(),p.blockPosition()),"command landed on dry solid ground");});
            var firstAnchor=w.getServer().computeOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();return RealmReturnData.get(s).get(p.getUUID());});
            w.getServer().runCommand("execute as @a run differentenchant dimension enter");
            w.getServer().runOnServer(s->check(firstAnchor.equals(RealmReturnData.get(s).get(s.getPlayerList().getPlayers().getFirst().getUUID())),"repeated enter cannot overwrite return anchor"));
            c.getInput().lookAt(0,0);var walk=c.computeOnClient(m->m.player.position());c.getInput().holdKey(o->o.keyUp);c.getInput().holdKey(o->o.keyJump);c.waitTicks(40);c.getInput().releaseKey(o->o.keyUp);c.getInput().releaseKey(o->o.keyJump);
            check(c.computeOnClient(m->m.player.position().distanceTo(walk))>1,"real movement packets in Realm, started "+walk+" ended "+c.computeOnClient(m->m.player.position()));
            fallingCommand(w,"return");c.waitFor(m->m.level.dimension().equals(Level.OVERWORLD));
            w.getServer().runOnServer(s->check(s.getPlayerList().getPlayers().getFirst().position().distanceTo(old)<.1,"exact original return position"));
            w.getServer().runCommand("execute as @a run differentenchant dimension enter");c.waitFor(m->m.level.dimension().equals(RealmContent.REALM));
            views=w.getServer().computeOnServer(s->{
                var l=s.getLevel(RealmContent.REALM);BlockPos field=null,forest=null,tree=null,hill=null,island=null,water=null;int highest=0,lowest=320,wet=0;
                var fields=new ArrayList<BlockPos>();var trees=new ArrayList<BlockPos>();int maxInterior=0,maxSeam=0,islandColumns=0;
                // Bounded validation survey only; never part of production gameplay.
                for(int cz=-12;cz<=12;cz++) for(int cx=-12;cx<=12;cx++) {
                    var chunk=l.getChunk(cx,cz);int x=cx*16+8,z=cz*16+8;int top=l.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);int ground=top;
                    for(int y=top;y>63;y--) if(l.getBlockState(new BlockPos(x,y-1,z)).is(RealmContent.TURF)){ground=y;break;}
                    var p=new BlockPos(x,ground,z);String b=biome(l,p);highest=Math.max(highest,ground);lowest=Math.min(lowest,ground);
                    if(b.equals("mint_fields")&&ground>64&&ground<120)fields.add(p);
                    if(forest==null&&b.equals("azure_woodland")&&ground>64&&ground<140)forest=p;
                    if(hill==null||ground>hill.getY()&&ground<165)hill=p;
                    if(l.getBlockState(new BlockPos(x,62,z)).is(Blocks.WATER)){wet++;if(water==null||x*x+z*z<water.getX()*water.getX()+water.getZ()*water.getZ())water=new BlockPos(x,63,z);}
                    for(int y=168;y<226;y++) if(l.getBlockState(new BlockPos(x,y,z)).is(RealmContent.TURF)&&l.getBlockState(new BlockPos(x,y-32,z)).isAir()){island=new BlockPos(x,y,z);islandColumns++;}
                    for(int dx=1;dx<16;dx++) {
                        int xx=cx*16+dx;
                        int a=l.getChunkSource().getGenerator().getFirstOccupiedHeight(xx-1,z,Heightmap.Types.OCEAN_FLOOR_WG,l,l.getChunkSource().randomState());
                        int bHeight=l.getChunkSource().getGenerator().getFirstOccupiedHeight(xx,z,Heightmap.Types.OCEAN_FLOOR_WG,l,l.getChunkSource().randomState());
                        if(a<165&&bHeight<165)maxInterior=Math.max(maxInterior,Math.abs(a-bHeight));
                    }
                    int a=l.getChunkSource().getGenerator().getFirstOccupiedHeight(cx*16+15,z,Heightmap.Types.OCEAN_FLOOR_WG,l,l.getChunkSource().randomState());
                    int bHeight=l.getChunkSource().getGenerator().getFirstOccupiedHeight(cx*16+16,z,Heightmap.Types.OCEAN_FLOOR_WG,l,l.getChunkSource().randomState());
                    if(a<165&&bHeight<165)maxSeam=Math.max(maxSeam,Math.abs(a-bHeight));
                    for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){
                        int xx=cx*16+dx,zz=cz*16+dz;int yy=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,xx,zz);
                        for(int y=yy;y>yy-15&&y>63;y--){var at=new BlockPos(xx,y,zz);if(l.getBlockState(at).is(RealmContent.LOG)&&l.getBlockState(at.below()).is(RealmContent.TURF))trees.add(at);}
                    }
                }
                int best=-99999;
                for(var p:fields) {
                    int score=0;
                    for(int dx:new int[]{-32,0,32})for(int dz:new int[]{-32,0,32}) if(biome(l,p.offset(dx,0,dz)).equals("mint_fields"))score+=20;
                    score-=Math.abs(p.getY()-85);
                    if(score>best){best=score;field=p;}
                }
                for(var p:trees) if(tree==null||p.distSqr(field)<tree.distSqr(field)) tree=p;
                report.append("seed=").append(l.getSeed()).append(" survey=625 chunks, ground range=").append(lowest).append("..").append(highest).append(" water columns=").append(wet).append("\n");
                report.append("Mainland max adjacent height step interior=").append(maxInterior).append(" chunk seam=").append(maxSeam).append("; rare island sample columns=").append(islandColumns).append("/625\n");
                report.append("field=").append(field).append(" forest=").append(forest).append(" tree=").append(tree).append(" hill=").append(hill).append(" island=").append(island).append("\n");
                System.out.println("REALM SURVEY "+report);
                check(field!=null&&forest!=null,"both natural biomes");check(tree!=null,"custom trees actually generated");check(highest-lowest>25,"real rolling terrain");check(wet>0,"natural rivers/lakes");
                check(maxSeam<=Math.max(8,maxInterior+2),"chunk borders have no special height discontinuity");
                return new BlockPos[]{field,forest,tree,hill,island,water};
            });
            var f=views[0];camera(c,w,f.getX()+36,f.getY()+32,f.getZ()-48,f,"01-mint-fields");
            var forest=views[1];camera(c,w,forest.getX()+45,forest.getY()+30,forest.getZ()-48,forest.above(5),"02-azure-woodland");
            var tree=views[2];
            var treeCamera=w.getServer().computeOnServer(s->{var l=s.getLevel(RealmContent.REALM);BlockPos best=null;int lowest=999;
                for(var offset:new int[][]{{13,-15},{-13,15},{15,13},{-15,-13},{0,18},{18,0},{-18,0},{0,-18}}) {
                    int x=tree.getX()+offset[0],z=tree.getZ()+offset[1];int ground=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                    int y=Math.max(tree.getY()+6,ground+3);var p=new BlockPos(x,y,z);
                    if(ground<lowest&&l.getBlockState(p.above()).isAir()&&l.getBlockState(p.above(2)).isAir()){lowest=ground;best=p;}
                }
                check(best!=null,"unobstructed natural tree camera");return best;});
            camera(c,w,treeCamera.getX()+.5,treeCamera.getY(),treeCamera.getZ()+.5,tree.above(6),"03-azure-tree");
            var h=views[3];camera(c,w,h.getX()+64,h.getY()+40,h.getZ()-60,h,"04-hills");
            check(views[4]!=null,"rare floating island naturally present in survey");
            {var i=views[4];camera(c,w,i.getX()+36,i.getY()+12,i.getZ()-44,i.below(9),"05-floating-island");}
            camera(c,w,f.getX(),f.getY()+24,f.getZ(),f.offset(0,40,80),"06-sky-day");
            var sky=c.computeOnClient(m->m.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_COLOR,m.player.position()));
            w.getServer().runCommand("time set midnight");c.waitTicks(30);
            check(c.computeOnClient(m->m.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_COLOR,m.player.position())).equals(sky),"Realm sky stays constant while overworld clock advances");
            c.takeScreenshot("realm-07-sky-midnight");
            w.getServer().runOnServer(s->{var attribute=net.minecraft.world.attribute.EnvironmentAttributes.SKY_LIGHT_LEVEL;
                check(s.overworld().environmentAttributes().getValue(attribute,new BlockPos(8,80,8))<6,"overworld still has a real night");
                check(s.getLevel(RealmContent.REALM).environmentAttributes().getValue(attribute,new BlockPos(8,80,8))==15,"Realm remains full daylight");});
            var river=views[5];camera(c,w,river.getX()+30,river.getY()+28,river.getZ()-36,river,"08-river-valley");
            sample=w.getServer().computeOnServer(s->{var l=s.getLevel(RealmContent.REALM);var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);var feet=new BlockPos(8,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,8,8),8);p.teleportTo(l,8.5,feet.getY(),8.5,Set.of(),0,0,true);return new String[]{l.getBlockState(f.below()).toString(),RealmReturnData.get(s).get(p.getUUID()).toString()};});
            generatedHash=w.getServer().computeOnServer(s->fingerprint(s.getLevel(RealmContent.REALM)));
            report.append("Admin enter/return, actual movement, natural biomes/trees, constant sky verified.\n");
        }
        try(var w=save.open()) {
            c.waitFor(m->m.level.dimension().equals(RealmContent.REALM));
            w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(s.getLevel(RealmContent.REALM).getBlockState(views[0].below()).toString().equals(sample[0]),"natural terrain persisted");check(RealmReturnData.get(s).get(p.getUUID()).toString().equals(sample[1]),"return anchor survived full shutdown");});
            w.getServer().runCommand("execute as @a run differentenchant dimension return");c.waitFor(m->m.level.dimension().equals(Level.OVERWORLD));
            report.append("Full save, shutdown and disk reopen inside Realm; terrain and return anchor persisted, return succeeded.\n");
            // Return anchors carry the real source dimension, including the Nether.
            w.getServer().runOnServer(s->{var l=s.getLevel(Level.NETHER);l.getChunk(0,0);var p=s.getPlayerList().getPlayers().getFirst();p.teleportTo(l,8.5,128,8.5,Set.of(),14,-8,true);});
            c.waitFor(m->m.level.dimension().equals(Level.NETHER));
            w.getServer().runCommand("execute as @a run differentenchant dimension enter");c.waitFor(m->m.level.dimension().equals(RealmContent.REALM));
            w.getServer().runCommand("execute as @a run differentenchant dimension return");c.waitFor(m->m.level.dimension().equals(Level.NETHER));
            w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(p.position().distanceTo(new net.minecraft.world.phys.Vec3(8.5,128,8.5))<.1,"return preserves source Nether coordinates");});
            report.append("Nether source dimension and coordinates restored; no portal created.\n");
        }
        try(var w=c.worldBuilder().setUseConsistentSettings(false).adjustSettings(ui->{ui.setSeed("2026102001");ui.setAllowCommands(true);}).create()) {
            var replay=w.getServer().computeOnServer(s->fingerprint(s.getLevel(RealmContent.REALM)));
            check(replay==generatedHash,"fresh independent world replays identical terrain/vegetation material fingerprint");
            report.append("Independent world same seed, 178176 block materials at chunks (30..31,30..31): ").append(Long.toUnsignedString(generatedHash,16)).append(" == ").append(Long.toUnsignedString(replay,16)).append("\n");
        }
        try{Files.writeString(Path.of("realm-client-report.txt"),report.toString());}catch(Exception e){throw new RuntimeException(e);}
    }
}
