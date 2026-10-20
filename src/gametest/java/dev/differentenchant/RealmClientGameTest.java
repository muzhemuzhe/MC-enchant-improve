package dev.differentenchant;

import dev.differentenchant.realm.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.*;

/** Natural fault slopes and tapered plate review. No test block writes or placed features. */
public final class RealmClientGameTest implements FabricClientGameTest {
    private static final boolean PROBE="1".equals(System.getenv("DIFFERENTENCHANT_EDGE_PROBE"));
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private record Review(BlockPos core,BlockPos mainland,BlockPos opposite,BlockPos islandLip,int dx,int dz,int width,int depth) {}
    private static double moatWidth(BlockPos a,BlockPos b){return Math.hypot(a.getX()-b.getX(),a.getZ()-b.getZ());}
    private static double density(DensityFunction f,int x,int y,int z){return f.compute(new DensityFunction.SinglePointContext(x,y,z));}
    private static int top(DensityFunction f,int x,int z){for(int y=270;y>=70;y--)if(density(f,x,y,z)>0)return y+1;return -64;}
    private static BlockPos floor(ServerLevel l,int x,int z,int minimum,int maximum){
        l.getChunk(x>>4,z>>4);int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        for(int down=0;down<24;down++){var p=new BlockPos(x,y-down,z);if(p.getY()>=minimum&&p.getY()<=maximum&&RealmTravel.safe(l,p)&&!l.getBlockState(p.below()).is(RealmContent.LOG))return p;}
        return null;
    }
    private static Review survey(ServerLevel l,StringBuilder report){
        var router=l.getChunkSource().randomState().router();var f=router.finalDensity();int n=65,step=32,ground=0,raised=0,voids=0;int[][] tops=new int[n][n];
        for(int z=0;z<n;z++)for(int x=0;x<n;x++){
            int xx=(x-n/2)*step,zz=(z-n/2)*step;
            if(density(f,xx,-62,zz)>0){ground++;tops[x][z]=120;}else{
                tops[x][z]=top(f,xx,zz);if(tops[x][z]>170)raised++;else voids++;
            }
        }
        System.out.println("EDGE GRID seed="+l.getSeed()+" grounded@-62="+ground+" raised="+raised+" abyss="+voids);
        check(ground>n*n*.65,"most of the world is grounded mainland, not globally floating");check(raised>0&&voids>0,"both uplift cores and surrounding abyss exist");
        var components=new ArrayList<List<BlockPos>>();boolean[][] seen=new boolean[n][n];int[][] dirs={{1,0},{0,1},{-1,0},{0,-1}};
        for(int z=0;z<n;z++)for(int x=0;x<n;x++)if(tops[x][z]>170&&!seen[x][z]){
            var queue=new ArrayDeque<int[]>();var points=new ArrayList<BlockPos>();queue.add(new int[]{x,z});seen[x][z]=true;
            while(!queue.isEmpty()) {var p=queue.remove();points.add(new BlockPos((p[0]-n/2)*step,tops[p[0]][p[1]],(p[1]-n/2)*step));
                for(var d:dirs){int a=p[0]+d[0],b=p[1]+d[1];if(a>=0&&b>=0&&a<n&&b<n&&!seen[a][b]&&tops[a][b]>170){seen[a][b]=true;queue.add(new int[]{a,b});}}}
            if(points.size()>=20)components.add(points);
        }
        check(!components.isEmpty(),"large lifted island component, not decorative rubble");
        components.sort(Comparator.comparingDouble(points->points.stream().mapToDouble(p->p.getX()*p.getX()+p.getZ()*p.getZ()).min().orElseThrow()));
        Review result=null;
        for(var points:components){
            int minX=points.stream().mapToInt(BlockPos::getX).min().orElseThrow(),maxX=points.stream().mapToInt(BlockPos::getX).max().orElseThrow();
            int minZ=points.stream().mapToInt(BlockPos::getZ).min().orElseThrow(),maxZ=points.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
            if(maxX-minX<128||maxZ-minZ<128)continue;
            var core=points.stream().min(Comparator.comparingDouble(p->density(router.continents(),p.getX(),0,p.getZ()))).orElseThrow();
            for(var d:dirs){
                BlockPos shore=null,opposite=null,lip=null;int gap=0;
                for(int sign:new int[]{1,-1}){
                    BlockPos last=null;
                    for(int distance=4;distance<=640;distance+=4){int x=core.getX()+d[0]*distance*sign,z=core.getZ()+d[1]*distance*sign;
                        if(density(f,x,-56,z)>0){var at=floor(l,x+d[0]*sign*4,z+d[1]*sign*4,86,169);if(at!=null){if(sign==1)shore=at;else opposite=at;break;}}
                        if(sign==1&&top(f,x,z)>170)last=new BlockPos(x,top(f,x,z),z);
                        else if(sign==1&&last!=null)gap++;
                    }
                    if(sign==1&&last!=null){
                        for(int refine=1;refine<=8;refine++){int x=last.getX()+d[0],z=last.getZ()+d[1];int yy=top(f,x,z);if(yy<=170)break;last=new BlockPos(x,yy,z);}
                        for(int back=0;back<12&&lip==null;back++)lip=floor(l,last.getX()-d[0]*back,last.getZ()-d[1]*back,170,270);
                    }
                }
                if(shore!=null&&opposite!=null&&lip!=null&&gap>=4&&shore.distSqr(opposite)<900*900){
                    if(result==null||Math.abs(moatWidth(shore,lip)-80)<Math.abs(moatWidth(result.mainland(),result.islandLip())-80))
                        result=new Review(core,shore,opposite,lip,d[0],d[1],maxX-minX+step,maxZ-minZ+step);
                }
            }
            if(result!=null)break;
        }
        check(result!=null,"photographable mainland / moat / large island / mainland transect");
        var core=result.core();var shore=result.mainland();var other=result.opposite();
        for(var p:new BlockPos[]{shore,other}){
            var col=l.getChunkSource().getGenerator().getBaseColumn(p.getX(),p.getZ(),l,l.getChunkSource().randomState());
            check(!col.getBlock(-56).isAir()&&!col.getBlock(-64).isAir(),"actual mainland has a deep supporting column");
        }
        var col=l.getChunkSource().getGenerator().getBaseColumn(core.getX(),core.getZ(),l,l.getChunkSource().randomState());
        for(int y=-64;y<=30;y++)check(col.getBlock(y).isAir(),"lifted source column is cut to void, no hidden normal floor");
        check(!col.getBlock(core.getY()-4).isAir(),"real raised rock exists");
        int trees=0;
        for(int z=(core.getZ()>>4)-3;z<=(core.getZ()>>4)+3;z++)for(int x=(core.getX()>>4)-3;x<=(core.getX()>>4)+3;x++){
            l.getChunk(x,z);for(int xx=0;xx<16;xx++)for(int zz=0;zz<16;zz++){
                int wx=x*16+xx,wz=z*16+zz,y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,wx,wz);
                for(int down=0;down<24&&y-down>170;down++){var at=new BlockPos(wx,y-down,wz);if(l.getBlockState(at).is(RealmContent.LOG)&&l.getBlockState(at.below()).is(RealmContent.TURF))trees++;}
            }
        }
        check(trees>0,"natural trees grow on the lifted plate");
        report.append("seed=").append(l.getSeed()).append(" grid=65x65/32m; grounded@-62=").append(ground).append("/4225; raised=").append(raised).append("; abyss=").append(voids)
            .append("; selected lifted component sample bounds=").append(result.width()).append('x').append(result.depth()).append("m; core=").append(core)
            .append("; mainland=").append(shore).append("; opposite=").append(other).append("; islandLip=").append(result.islandLip()).append("; trees=").append(trees).append("\n");
        profile(l,result,report);System.out.println("EDGE SURVEY "+report);return result;
    }
    private static BlockPos air(ServerLevel l,BlockPos origin,int dx,int dz,int outward,int tangent,int y){
        for(int extra:new int[]{0,-20,-40,-60,-80,20})for(int side:new int[]{tangent,-tangent,0}){
            int x=origin.getX()+dx*(outward+extra)-dz*side,z=origin.getZ()+dz*(outward+extra)+dx*side;
            l.getChunk(x>>4,z>>4);var p=new BlockPos(x,y,z);
            if(l.getBlockState(p).isAir()&&l.getBlockState(p.above()).isAir())return p;
        }throw new AssertionError("natural air camera");
    }
    private static void profile(ServerLevel l,Review r,StringBuilder report){
        var f=l.getChunkSource().randomState().router().finalDensity();var mask=l.getChunkSource().randomState().router().continents();
        int hi=-1,low=-1,maxDrop=0,previous=-999;var points=new ArrayList<String>();
        BlockPos start=null;
        for(int back=0;back<=180;back+=4){int x=r.mainland().getX()+r.dx()*back,z=r.mainland().getZ()+r.dz()*back;if(density(mask,x,0,z)>0&&top(f,x,z)>=95){start=new BlockPos(x,0,z);break;}}
        check(start!=null,"intact mainland shoulder before slope");
        for(int t=0;t<=400;t+=2){int x=start.getX()-r.dx()*t,z=start.getZ()-r.dz()*t;
            if(density(mask,x,0,z)<0)break;int height=top(f,x,z);
            if(height==-64)for(int y=69;y>=-64;y--)if(density(f,x,y,z)>0){height=y+1;break;}
            points.add(t+":"+height);
            if(height>=90)hi=-1;else if(hi<0)hi=t;
            if(hi>=0&&height<=0)low=t;
            if(previous!=-999)maxDrop=Math.max(maxDrop,previous-height);previous=height;
            if(low>=0)break;
        }
        System.out.println("EDGE PROFILE seed="+l.getSeed()+" width="+(low-hi)+" maxDrop="+maxDrop+" values="+points);
        check(hi>=0&&low>hi,"mainland profile crosses 90 to 0");if(!PROBE){check(low-hi>=32,"90-block descent has real horizontal slope width");check(maxDrop<64,"no giant vertical wall in sampled mainland profile");}
        int seams=0,seamDelta=0;
        for(int t=0;t<=low;t+=2){int x=start.getX()-r.dx()*t,z=start.getZ()-r.dz()*t;
            if((r.dx()!=0&&(x&15)!=0)||(r.dz()!=0&&(z&15)!=0))continue;
            int a=l.getChunkSource().getGenerator().getFirstOccupiedHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,l,l.getChunkSource().randomState());
            int b=l.getChunkSource().getGenerator().getFirstOccupiedHeight(x+r.dx(),z+r.dz(),Heightmap.Types.OCEAN_FLOOR_WG,l,l.getChunkSource().randomState());
            seamDelta=Math.max(seamDelta,Math.abs(a-b));seams++;
        }
        check(seams>0&&seamDelta<48,"native adjacent columns across chunk borders remain continuous");
        int thin=-1,thick=-1;var island=new ArrayList<String>();
        for(int t=0;t<=220;t+=2){int x=r.islandLip().getX()-r.dx()*t,z=r.islandLip().getZ()-r.dz()*t;int height=top(f,x,z);if(height<=170)continue;
            int bottom=height-1;while(bottom>120&&density(f,x,bottom,z)>0)bottom--;int depth=height-1-bottom;
            island.add(t+":"+height+":"+bottom);if(thin<0&&depth>=5)thin=t;if(thin>=0&&thick<0&&depth>=30)thick=t;
        }
        System.out.println("EDGE ISLAND seed="+l.getSeed()+" run="+(thick-thin)+" values="+island);
        check(thin>=0&&thick>thin&&thick-thin>=16,"island mass gains thickness across a broad rim");
        report.append("Mainland 90->0 width=").append(low-hi).append("m; max 2m descent=").append(maxDrop).append("; native seams=").append(seams).append(" max adjacent delta=").append(seamDelta).append("; profile t:surface=").append(points).append("\n")
            .append("Island 5->30m thickness run=").append(thick-thin).append("m; profile t:surface:bottom=").append(island).append("\n");
    }
    private static void camera(ClientGameTestContext c,TestSingleplayerContext w,double x,double y,double z,BlockPos target,String name,boolean standing){
        w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=!standing;p.onUpdateAbilities();p.teleportTo(s.getLevel(RealmContent.REALM),x,y,z,Set.of(),0,0,true);});
        c.waitFor(m->m.level.dimension().equals(RealmContent.REALM)&&Math.abs(m.player.getX()-x)<.8&&Math.abs(m.player.getY()-y)<1&&Math.abs(m.player.getZ()-z)<.8);
        c.waitTicks(2);c.getInput().lookAt(target);
        c.waitFor(m->m.level.hasChunkAt(target));
        c.waitFor(m->{int cx=m.player.blockPosition().getX()>>4,cz=m.player.blockPosition().getZ()>>4;
            for(int dz=-17;dz<=17;dz++)for(int dx=-17;dx<=17;dx++)if(dx*dx+dz*dz<=17*17&&m.level.getChunkSource().getChunk(cx+dx,cz+dz,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)==null)return false;
            return true;},1200);
        c.waitTicks(40);w.getClientLevel().waitForChunksRender(false);
        c.runOnClient(m->{check(m.level.getBlockState(BlockPos.containing(m.player.getEyePosition())).isAir(),"unobstructed camera "+name);if(standing)check(m.player.onGround()&&!m.player.getAbilities().flying,"standing on natural floor");m.options.hideGui=true;m.gui.getChat().clearMessages(true);m.getToastManager().clear();});
        System.out.println("EDGE CAMERA "+name+" camera="+x+","+y+","+z+" target="+target.toShortString());c.waitTicks(5);c.takeScreenshot("edge-"+name);
    }
    private static long fingerprint(ServerLevel l,BlockPos p){
        l.getChunk(p.getX()>>4,p.getZ()>>4);long hash=1;
        for(int z=0;z<8;z++)for(int x=0;x<8;x++)for(int y=-64;y<270;y++)hash=hash*31+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(new BlockPos(p.getX()+x,y,p.getZ()+z)).getBlock()).toString().hashCode();
        return hash;
    }
    @Override public void runTest(ClientGameTestContext c){
        if(PROBE)System.out.println("EDGE PROBE diagnostic only: photographs/persistence and strict mainland width gate skipped");
        c.getInput().resizeWindow(1600,1000);c.runOnClient(m->{m.options.renderDistance().set(20);m.options.simulationDistance().set(5);});
        var report=new StringBuilder();TestWorldSave saved=null;BlockPos persisted=null;long hash=0;String anchor=null;
        for(long seed:new long[]{2026102001L,87L,20261022L})try(var w=c.worldBuilder().setUseConsistentSettings(false).adjustSettings(ui->{ui.setSeed(Long.toString(seed));ui.setAllowCommands(true);}).create()){
            w.getServer().runCommand("difficulty peaceful");c.waitFor(m->m.player.onGround());c.waitTicks(10);
            w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);var l=p.level();int cx=p.blockPosition().getX()>>4,cz=p.blockPosition().getZ()>>4;
                for(int z=0;z<16;z++)for(int x=0;x<16;x++){var at=floor(l,cx*16+x,cz*16+z,0,300);if(at!=null){p.teleportTo(l,at.getX()+.5,at.getY(),at.getZ()+.5,Set.of(),0,0,true);p.setOnGround(true);return;}}throw new AssertionError("natural source floor");});
            c.waitTicks(10);var old=w.getServer().computeOnServer(s->s.getPlayerList().getPlayers().getFirst().position());
            w.getServer().runCommand("execute as @a run differentenchant dimension enter");c.waitFor(m->m.level.dimension().equals(RealmContent.REALM));
            w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(RealmTravel.safe(p.level(),p.blockPosition())&&p.getY()<170,"entry lands on grounded mainland");});
            var r=w.getServer().computeOnServer(s->survey(s.getLevel(RealmContent.REALM),report));var main=r.mainland();var core=r.core();var lip=r.islandLip();int dx=r.dx(),dz=r.dz();String label="seed-"+seed;
            if(PROBE)continue;
            var side=w.getServer().computeOnServer(s->air(s.getLevel(RealmContent.REALM),main,dx,dz,-90,110,78));
            camera(c,w,side.getX()+.5,side.getY(),side.getZ()+.5,main.offset(dx*8,-34,dz*8),label+"-01-mainland-slope",false);
            double mx=(main.getX()+r.opposite().getX())*.5,mz=(main.getZ()+r.opposite().getZ())*.5;
            camera(c,w,mx-dz*230+dx*80,260,mz+dx*230+dz*80,new BlockPos((int)mx,145,(int)mz),label+"-02-whole-relationship",false);
            var high=w.getServer().computeOnServer(s->air(s.getLevel(RealmContent.REALM),main,dx,dz,-28,75,215));
            camera(c,w,high.getX()+.5,high.getY(),high.getZ()+.5,main.offset(-dx*25,-55,-dz*25),label+"-03-terraces-overhead",false);
            var deep=w.getServer().computeOnServer(s->air(s.getLevel(RealmContent.REALM),main,dx,dz,-105,40,0));
            camera(c,w,deep.getX()+.5,deep.getY(),deep.getZ()+.5,main.offset(-dx*20,-28,-dz*20),label+"-04-abyss-up",false);
            var underside=w.getServer().computeOnServer(s->air(s.getLevel(RealmContent.REALM),main,dx,dz,0,90,main.getY()+52));
            camera(c,w,underside.getX()+.5,underside.getY(),underside.getZ()+.5,lip.offset(-dx*32,-20,-dz*32),label+"-05-island-taper",false);
            if(seed==2026102001L){saved=w.getWorldSave();persisted=new BlockPos(((main.getX()-dx*24)>>4)<<4,main.getY(),((main.getZ()-dz*24)>>4)<<4);var sample=persisted;hash=w.getServer().computeOnServer(s->fingerprint(s.getLevel(RealmContent.REALM),sample));anchor=w.getServer().computeOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();return RealmReturnData.get(s).get(p.getUUID()).toString();});}
            else {w.getServer().runCommand("execute as @a run differentenchant dimension return");c.waitFor(m->m.level.dimension().equals(Level.OVERWORLD));w.getServer().runOnServer(s->check(s.getPlayerList().getPlayers().getFirst().position().distanceTo(old)<.1,"exact source return"));}
        }
        if(PROBE){try{Files.writeString(Path.of("edge-probe-report.txt"),report.toString());}catch(Exception ex){throw new RuntimeException(ex);}return;}
        final var at=persisted;final long expected=hash;final String expectedAnchor=anchor;
        try(var w=saved.open()){
            c.waitFor(m->m.level.dimension().equals(RealmContent.REALM));w.getServer().runOnServer(s->{check(fingerprint(s.getLevel(RealmContent.REALM),at)==expected,"material persistence after disk reopen");var p=s.getPlayerList().getPlayers().getFirst();check(RealmReturnData.get(s).get(p.getUUID()).toString().equals(expectedAnchor),"return anchor persists");});
            w.getServer().runCommand("execute as @a run differentenchant dimension return");c.waitFor(m->m.level.dimension().equals(Level.OVERWORLD));
        }
        try(var w=c.worldBuilder().setUseConsistentSettings(false).adjustSettings(ui->{ui.setSeed("2026102001");ui.setAllowCommands(true);}).create()){
            w.getServer().runOnServer(s->check(fingerprint(s.getLevel(RealmContent.REALM),at)==expected,"same-seed fresh world material replay"));
        }
        report.append("Actual safe mainland entry/return, grounded mainland columns, void under raised source, large natural wooded islands, real floor photography, disk persistence and same-seed replay passed. Wide fault slope and tapered island checks passed. No test block writes.\n");
        try{Files.writeString(Path.of("edge-client-report.txt"),report.toString());}catch(Exception ex){throw new RuntimeException(ex);}
    }
}
