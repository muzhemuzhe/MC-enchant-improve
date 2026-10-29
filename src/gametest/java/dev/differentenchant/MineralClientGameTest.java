package dev.differentenchant;

import dev.differentenchant.mineral.*;
import dev.differentenchant.realm.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Natural Realm survey and real native attack packets. No terrain/ore placement. */
public final class MineralClientGameTest implements FabricClientGameTest {
    private static final boolean PHOTO_ONLY="1".equals(System.getenv("DIFFERENTENCHANT_MINERAL_PHOTO_ONLY"));
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private record View(BlockPos pos,Direction face){}
    private static final class Scan {
        int chunks,luminous,silver,crystals,down,side;long signature=1;final List<View> luminousViews=new ArrayList<>();View silverView,downView,sideView;
        final Set<Long> visited=new HashSet<>();
        boolean ready(){return luminousViews.size()>=2&&silverView!=null&&downView!=null&&sideView!=null;}
    }
    private static View exposed(ServerLevel l,BlockPos p){
        for(var d:Direction.values())if(l.getBlockState(p.relative(d)).isAir()&&l.getBlockState(p.relative(d,2)).isAir())return new View(p,d);
        return null;
    }
    private static void scanChunk(ServerLevel l,Scan result,int cx,int cz){
        long key=(cx&0xffffffffL)|((long)cz<<32);if(!result.visited.add(key))return;
        var chunk=l.getChunk(cx,cz);result.chunks++;var sections=chunk.getSections();
        for(int index=0;index<sections.length;index++){
            var section=sections[index];if(!section.maybeHas(s->s.is(MineralContent.LUMINOUS_STONE)||s.is(MineralContent.HARMONIC_SILVER)||s.is(MineralContent.RIFT_CLUSTER)))continue;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                var state=section.getBlockState(x,y,z);var p=new BlockPos(cx*16+x,l.getMinY()+index*16+y,cz*16+z);
                if(state.is(MineralContent.LUMINOUS_STONE)||state.is(MineralContent.HARMONIC_SILVER)||state.is(MineralContent.RIFT_CLUSTER))result.signature=31*result.signature+31L*p.hashCode()+state.toString().hashCode();
                if(state.is(MineralContent.LUMINOUS_STONE)){result.luminous++;if(result.luminousViews.size()<8){var v=exposed(l,p);if(v!=null)result.luminousViews.add(v);}}
                if(state.is(MineralContent.HARMONIC_SILVER)){result.silver++;if(result.silverView==null)result.silverView=exposed(l,p);}
                if(state.is(MineralContent.RIFT_CLUSTER)){
                    result.crystals++;check(state.canSurvive(l,p),"natural cluster has actual sturdy support");var face=state.getValue(AmethystClusterBlock.FACING);
                    if(face==Direction.DOWN){result.down++;if(result.downView==null)result.downView=new View(p,Direction.DOWN);}
                    if(face.getAxis().isHorizontal()){result.side++;if(result.sideView==null)result.sideView=new View(p,face);}
                }
            }
        }
    }
    private static void ring(ServerLevel l,Scan scan,int cx,int cz,int r){
        for(int dz=-r;dz<=r;dz++)for(int dx=-r;dx<=r;dx++)if(Math.max(Math.abs(dx),Math.abs(dz))==r)scanChunk(l,scan,cx+dx,cz+dz);
        System.out.println("MINERAL SCAN chunks="+scan.chunks+" luminous="+scan.luminous+" silver="+scan.silver+" crystals="+scan.crystals+" underside="+scan.down+" sides="+scan.side+" ready="+scan.ready());
    }
    private static void camera(ClientGameTestContext c,TestSingleplayerContext w,View view,double distance,boolean survival){
        var p=view.pos();var d=view.face();double x=p.getX()+.5+d.getStepX()*distance,y=p.getY()+.5+d.getStepY()*distance-1.62,z=p.getZ()+.5+d.getStepZ()*distance;
        w.getServer().runOnServer(s->{var player=s.getPlayerList().getPlayers().getFirst();player.setGameMode(survival?GameType.SURVIVAL:GameType.CREATIVE);player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(s.getLevel(RealmContent.REALM),x,y,z,Set.of(),0,0,true);});
        c.waitFor(m->m.level.dimension().equals(RealmContent.REALM)&&m.player.position().distanceTo(new Vec3(x,y,z))<.6);c.getInput().lookAt(p);
        c.waitFor(m->{var state=m.level.getBlockState(p);return m.level.hasChunkAt(p)&&(state.is(MineralContent.LUMINOUS_STONE)||state.is(MineralContent.HARMONIC_SILVER)||state.is(MineralContent.RIFT_CLUSTER));},1200);
        c.waitTicks(10);c.getInput().lookAt(p);c.waitTicks(4);w.getClientLevel().waitForChunksRender(false);
        c.runOnClient(m->check(m.level.getBlockState(BlockPos.containing(m.player.getEyePosition())).isAir(),"camera in natural air"));
        c.runOnClient(m->check(m.player.getViewVector(1).dot(Vec3.atCenterOf(p).subtract(m.player.getEyePosition()).normalize())>.995,"settled camera really faces target"));
        System.out.println("MINERAL CAMERA target="+p.toShortString()+" face="+d+" feet="+x+","+y+","+z+" survival="+survival);
    }
    private static void shot(ClientGameTestContext c,String name){c.runOnClient(m->{m.gui.getChat().clearMessages(true);m.getToastManager().clear();});c.waitTicks(3);c.takeScreenshot("minerals-"+name);}
    private static void language(ClientGameTestContext c,String code){var future=c.computeOnClient(m->{m.getLanguageManager().setSelected(code);m.options.languageCode=code;return m.reloadResourcePacks();});c.waitFor(m->future.isDone()&&m.getOverlay()==null);c.waitTicks(4);}
    private static int resourceCount(ServerPlayer p){int count=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(MineralGameTests.resource(p.getInventory().getItem(i)))count+=p.getInventory().getItem(i).getCount();return count;}
    private static void mine(ClientGameTestContext c,TestSingleplayerContext w,View view,Item item,String enchantment,Item expected,String label,StringBuilder report){
        w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.level().getEntitiesOfClass(ItemEntity.class,new AABB(view.pos()).inflate(32),e->MineralGameTests.resource(e.getItem())).forEach(Entity::discard);p.getInventory().clearContent();p.getInventory().setSelectedSlot(0);p.setItemSlot(EquipmentSlot.MAINHAND,enchantment==null?new ItemStack(item):MineralGameTests.enchanted(p.level(),item,enchantment,1));p.inventoryMenu.broadcastChanges();});
        camera(c,w,view,2.5,true);c.runOnClient(m->m.options.hideGui=false);
        c.waitFor(m->m.player.getMainHandItem().is(item));c.waitFor(m->m.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(view.pos()));
        long start=w.getServer().computeOnServer(s->s.getTickCount());
        c.getInput().holdKey(o->o.keyAttack);c.waitTicks(12);shot(c,label+"-digging");
        c.waitFor(m->!m.level.getBlockState(view.pos()).is(MineralContent.LUMINOUS_STONE)&&!m.level.getBlockState(view.pos()).is(MineralContent.HARMONIC_SILVER)&&!m.level.getBlockState(view.pos()).is(MineralContent.RIFT_CLUSTER),220);
        c.getInput().releaseKey(o->o.keyAttack);
        boolean committed=false;
        for(int i=0;i<30;i++){committed=w.getServer().computeOnServer(s->{var state=s.getLevel(RealmContent.REALM).getBlockState(view.pos());return !state.is(MineralContent.LUMINOUS_STONE)&&!state.is(MineralContent.HARMONIC_SILVER)&&!state.is(MineralContent.RIFT_CLUSTER);});if(committed)break;c.waitTicks(1);}
        check(committed,"server committed native break, not just client prediction");
        w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();var l=p.level();int count=resourceCount(p)+l.getEntitiesOfClass(ItemEntity.class,new AABB(view.pos()).inflate(10),e->MineralGameTests.resource(e.getItem())).stream().mapToInt(e->e.getItem().getCount()).sum();
            check(count==(expected==null?0:1),"native client break exact resource count "+label+"="+count);
            System.out.println("MINERAL HARVEST "+label+" ticks="+(s.getTickCount()-start)+" count="+count+" main="+p.getMainHandItem());
            report.append("Native attack ").append(label).append(" tool=").append(item).append(" enchantment=").append(enchantment).append(" ticks=").append(s.getTickCount()-start).append(" count=").append(count).append(" target=").append(view.pos()).append('\n');
        });shot(c,label+"-result");
    }
    @Override public void runTest(ClientGameTestContext c){
        if(PHOTO_ONLY)System.out.println("MINERAL PHOTO_ONLY: natural scene retake; mining/persistence not repeated");
        c.getInput().resizeWindow(1600,1000);language(c,"en_us");c.runOnClient(m->{m.options.renderDistance().set(12);m.options.simulationDistance().set(5);});
        var report=new StringBuilder();TestWorldSave saved;BlockPos kept;String keptState;long originalSignature;int originalChunks;
        try(var w=c.worldBuilder().setUseConsistentSettings(false).adjustSettings(ui->{ui.setSeed("2026102001");ui.setAllowCommands(true);}).create()){
            w.getServer().runCommand("difficulty peaceful");w.getServer().runCommand("execute as @a run differentenchant dimension enter");c.waitFor(m->m.level.dimension().equals(RealmContent.REALM));
            // Native night vision for readable underground evidence, not scene lighting edits.
            w.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false)));
            var scan=new Scan();
            for(int r=0;r<=6&&!scan.ready();r++)for(var center:new int[][]{{4,20},{4,36}}){int radius=r;w.getServer().runOnServer(s->ring(s.getLevel(RealmContent.REALM),scan,center[0],center[1],radius));c.waitTicks(2);}
            check(scan.ready(),"all natural exposed ores, underside and wall clusters found");check(scan.luminous>scan.silver,"luminous more common than harmonic silver in surveyed chunks");
            originalSignature=scan.signature;originalChunks=scan.chunks;
            report.append("seed=2026102001 chunks=").append(scan.chunks).append(" luminous=").append(scan.luminous).append(" silver=").append(scan.silver).append(" clusters=").append(scan.crystals).append(" down=").append(scan.down).append(" side=").append(scan.side).append(" signature=").append(scan.signature).append("\n");
            c.runOnClient(m->m.options.hideGui=true);
            camera(c,w,scan.luminousViews.get(0),3.2,false);shot(c,"01-natural-luminous");
            camera(c,w,scan.silverView,3.2,false);shot(c,"02-natural-silver");
            camera(c,w,scan.downView,4.0,false);shot(c,"03-natural-underside-cluster");
            camera(c,w,scan.sideView,3.2,false);shot(c,"04-natural-wall-cluster");
            if(PHOTO_ONLY){try{Files.writeString(Path.of("minerals-photo-report.txt"),report+"Natural scene camera retake only; native night vision, no block writes.\n");}catch(Exception e){throw new RuntimeException(e);}return;}
            kept=scan.sideView.pos();keptState=w.getServer().computeOnServer(s->s.getLevel(RealmContent.REALM).getBlockState(kept).toString());
            mine(c,w,scan.luminousViews.get(0),Items.ENCHANTED_BOOK,"minecraft:protection",MineralContent.LUMINOUS_FRAGMENT,"05-protection-book",report);
            mine(c,w,scan.luminousViews.get(1),Items.NETHERITE_PICKAXE,null,null,"06-wrong-netherite",report);
            mine(c,w,scan.silverView,Items.DIAMOND_SWORD,"minecraft:fire_aspect",MineralContent.RAW_SILVER,"07-fire-sword",report);
            mine(c,w,scan.downView,Items.ENCHANTED_BOOK,"differentenchant:trail_stride",MineralContent.RIFT_CRYSTAL,"08-lost-book",report);
            w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.level().getEntitiesOfClass(ItemEntity.class,new AABB(p.blockPosition()).inflate(32),e->MineralGameTests.resource(e.getItem())).forEach(Entity::discard);p.getInventory().clearContent();var inv=p.getInventory();Item[] items={MineralContent.LUMINOUS_STONE.asItem(),MineralContent.HARMONIC_SILVER.asItem(),MineralContent.RIFT_CLUSTER.asItem(),MineralContent.LUMINOUS_FRAGMENT,MineralContent.RAW_SILVER,MineralContent.RIFT_CRYSTAL};for(int i=0;i<items.length;i++)inv.setItem(9+i,new ItemStack(items[i]));inv.setItem(18,MineralGameTests.enchanted(p.level(),Items.ENCHANTED_BOOK,"minecraft:protection",1));inv.setItem(19,MineralGameTests.enchanted(p.level(),Items.ENCHANTED_BOOK,"minecraft:flame",1));inv.setItem(20,MineralGameTests.enchanted(p.level(),Items.ENCHANTED_BOOK,"differentenchant:trail_stride",1));p.inventoryMenu.broadcastChanges();});
            c.waitTicks(5);c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(InventoryScreen.class);c.getInput().setCursorPos(1,1);shot(c,"09-inventory-en");language(c,"zh_cn");shot(c,"10-inventory-zh");c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);saved=w.getWorldSave();
        }
        try(var w=saved.open()){
            w.getServer().runOnServer(s->{var l=s.getLevel(RealmContent.REALM);l.getChunk(kept.getX()>>4,kept.getZ()>>4);check(l.getBlockState(kept).toString().equals(keptState)&&l.getBlockState(kept).canSurvive(l,kept),"natural crystal orientation/support persisted after disk reopen");check(resourceCount(s.getPlayerList().getPlayers().getFirst())==3,"resource stacks persist without duplication");});
            w.getServer().runCommand("execute as @a run differentenchant dimension return");c.waitFor(m->m.level.dimension().equals(Level.OVERWORLD));
        }
        try(var w=c.worldBuilder().setUseConsistentSettings(false).adjustSettings(ui->{ui.setSeed("2026102001");ui.setAllowCommands(true);}).create()){
            var replay=new Scan();for(int r=0;r<=6&&!replay.ready();r++)for(var center:new int[][]{{4,20},{4,36}}){int radius=r;w.getServer().runOnServer(s->ring(s.getLevel(RealmContent.REALM),replay,center[0],center[1],radius));c.waitTicks(2);}
            check(replay.signature==originalSignature&&replay.chunks==originalChunks,"all surveyed mineral positions/types/orientations replay in an independent same-seed world");report.append("Same-seed mineral position/type/orientation signature replay passed.\n");
        }
        report.append("All photographs are natural generation; only inventory fixtures and native player mining changed. Persisted crystal/support, resource stacks and return passed.\n");
        try{Files.writeString(Path.of("minerals-client-report.txt"),report.toString());}catch(Exception e){throw new RuntimeException(e);}
    }
}
