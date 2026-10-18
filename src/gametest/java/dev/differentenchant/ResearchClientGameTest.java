package dev.differentenchant;

import com.google.gson.JsonParser;
import dev.differentenchant.client.ResearchScreen;
import dev.differentenchant.research.*;
import dev.differentenchant.resonance.ResonanceItems;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;

/** Native block placement/opening, mouse slots, actual research packets and disk reconnect. */
public final class ResearchClientGameTest implements FabricClientGameTest {
    private static final BlockPos TABLE=new BlockPos(0,-60,0);
    private static void check(boolean v,String message){ResearchGameTests.check(v,message);}
    private static void language(ClientGameTestContext c,String code){var future=c.computeOnClient(client->{client.getLanguageManager().setSelected(code);client.options.languageCode=code;return client.reloadResourcePacks();});c.waitFor(client->future.isDone()&&client.getOverlay()==null);c.waitTicks(3);}
    private static void cursor(ClientGameTestContext c,int x,int y){var p=c.computeOnClient(client->{double s=client.getWindow().getGuiScale();return new double[]{((client.screen.width-ResearchMenu.WIDTH)/2+x)*s,((client.screen.height-ResearchMenu.HEIGHT)/2+y)*s};});c.getInput().setCursorPos(p[0],p[1]);c.waitTick();}
    private static void click(ClientGameTestContext c,int x,int y){cursor(c,x,y);c.getInput().pressMouse(0);c.waitTicks(3);}
    private static void shift(ClientGameTestContext c,int x,int y){cursor(c,x,y);c.runOnClient(client->{var event=new net.minecraft.client.input.MouseButtonEvent((client.screen.width-ResearchMenu.WIDTH)/2+x,(client.screen.height-ResearchMenu.HEIGHT)/2+y,new net.minecraft.client.input.MouseButtonInfo(0,1));client.screen.mouseClicked(event,false);client.screen.mouseReleased(event);});c.waitTicks(3);}
    private static void shot(ClientGameTestContext c,String name){c.runOnClient(client->{client.getToastManager().clear();client.gui.getChat().clearMessages(true);});c.getInput().setCursorPos(1,1);c.waitTicks(2);c.takeScreenshot("research-"+name);}
    private static void ready(ClientGameTestContext c){c.waitTicks(3);c.waitFor(client->client.player.containerMenu instanceof ResearchMenu m&&m.preview()!=null);}
    private static void open(ClientGameTestContext c){c.getInput().lookAt(TABLE);c.waitTicks(3);c.getInput().pressKey(o->o.keyUse);c.waitForScreen(ResearchScreen.class);ready(c);}
    private static void close(ClientGameTestContext c){c.getInput().pressKey(o->o.keyInventory);c.waitForScreen(null);c.waitTicks(3);}
    private static void aim(ClientGameTestContext c,double x,double y,double z){var angles=c.computeOnClient(client->{var eye=client.player.getEyePosition();double dx=x-eye.x,dy=y-eye.y,dz=z-eye.z;return new float[]{(float)(Math.toDegrees(Math.atan2(dz,dx))-90),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)))};});c.getInput().lookAt(angles[0],angles[1]);}
    private static Map<String,Integer> counts(TestSingleplayerContext world){return world.getServer().computeOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();var counts=new TreeMap<String,Integer>();
        var stacks=new ArrayList<ItemStack>();for(int i=0;i<p.getInventory().getContainerSize();i++)stacks.add(p.getInventory().getItem(i));
        if(p.containerMenu instanceof ResearchMenu menu)for(int i=0;i<4;i++)stacks.add(menu.getSlot(i).getItem());stacks.add(p.containerMenu.getCarried());
        for(var item:s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(TABLE).inflate(12)))stacks.add(item.getItem());
        for(var stack:stacks)if(!stack.isEmpty())counts.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum);return counts;});}
    @Override public void runTest(ClientGameTestContext c) {
        c.getInput().resizeWindow(1280,720);language(c,"en_us");TestWorldSave save;String savedData;Map<String,Integer> savedItems;ResearchPolicy.Recipe first,scroll;UUID playerId;
        var report=new StringBuilder();
        try(var world=c.worldBuilder().create()) {
            save=world.getWorldSave();world.getServer().runCommand("time set day");world.getServer().runCommand("weather clear");world.getServer().runCommand("difficulty peaceful");
            first=world.getServer().computeOnServer(s->ResearchGameTests.find(s.overworld(),r->!r.scroll()));scroll=world.getServer().computeOnServer(s->ResearchGameTests.find(s.overworld(),ResearchPolicy.Result::scroll));
            playerId=world.getServer().computeOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.SURVIVAL);p.getInventory().setSelectedSlot(0);p.getInventory().setItem(0,new ItemStack(ResearchContent.TABLE_ITEM));
                p.getInventory().setItem(9,ResearchGameTests.book(s.overworld(),first.a()));p.getInventory().setItem(10,ResearchGameTests.book(s.overworld(),first.b()));p.getInventory().setItem(11,new ItemStack(ResonanceItems.ATTUNED_CORE,8));
                p.getInventory().setItem(12,ResearchGameTests.book(s.overworld(),first.a()));p.getInventory().setItem(13,ResearchGameTests.book(s.overworld(),first.b()));return p.getUUID();});
            world.getServer().runCommand("tp @a 0.5 -60 -2.5");c.waitTicks(5);aim(c,.5,-60.001,.5);c.waitTicks(3);c.getInput().pressKey(o->o.keyUse);c.waitTicks(5);
            world.getServer().runOnServer(s->check(s.overworld().getBlockState(TABLE).is(ResearchContent.TABLE)&&s.getPlayerList().getPlayers().getFirst().getMainHandItem().isEmpty(),"normal survival placement consumes table item; actual "+s.overworld().getBlockState(TABLE)+" held "+s.getPlayerList().getPlayers().getFirst().getMainHandItem()));
            world.getServer().runCommand("tp @a 1.7 -59 -1.5");c.waitTicks(4);c.getInput().lookAt(TABLE);c.waitTicks(4);world.getClientLevel().waitForChunksRender();
            c.runOnClient(client->client.options.hideGui=true);shot(c,"01-table-block");c.runOnClient(client->client.options.hideGui=false);
            world.getServer().runCommand("tp @a 0.5 -60 -2.5");c.waitTicks(4);open(c);
            click(c,70,140);click(c,32,56);click(c,88,140);click(c,64,56);shift(c,106,140);ready(c);
            c.runOnClient(client->{var m=(ResearchMenu)client.player.containerMenu;check(m.preview().known()==null&&m.preview().status()==0,"first GUI shows only unknown result");});shot(c,"02-unknown-recipe");
            cursor(c,230,106);c.takeScreenshot("research-02b-material-rules");
            click(c,230,106);c.waitFor(client->!((ResearchMenu)client.player.containerMenu).getSlot(3).getItem().isEmpty());ready(c);
            world.getServer().runOnServer(s->{var m=(ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu;check(m.getSlot(0).getItem().isEmpty()&&m.getSlot(1).getItem().isEmpty()&&m.getSlot(2).getItem().getCount()==7,"real success paid both books and one existing attuned core");});shot(c,"03-success-result");
            cursor(c,252,56);c.takeScreenshot("research-04-lost-book-tooltip");shift(c,252,56);ready(c);
            // Swapped spare books resolve to the same discovered recipe.
            click(c,142,140);click(c,32,56);click(c,124,140);click(c,64,56);ready(c);
            c.runOnClient(client->check(((ResearchMenu)client.player.containerMenu).preview().known()!=null,"known swapped recipe preview"));shot(c,"05-known-preview");
            click(c,120,24);ready(c);c.waitFor(client->((ResearchMenu)client.player.containerMenu).recordsOpen());shot(c,"06-first-discovery-record");click(c,40,24);ready(c);
            shift(c,32,56);shift(c,64,56);ready(c);
            // Fixture setup only supplies the next input books; the actual GUI button still sends the production packet.
            world.getServer().runOnServer(s->{var m=(ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu;var a=ResearchGameTests.book(s.overworld(),scroll.a());a.set(DataComponents.CUSTOM_NAME,Component.literal("Kept after research"));a.set(DataComponents.REPAIR_COST,17);m.getSlot(0).set(a);m.getSlot(1).set(ResearchGameTests.book(s.overworld(),scroll.b()));m.broadcastChanges();});ready(c);
            var failureA=world.getServer().computeOnServer(s->((ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu).getSlot(0).getItem().copy());
            var failureB=world.getServer().computeOnServer(s->((ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu).getSlot(1).getItem().copy());
            click(c,230,106);c.waitFor(client->((ResearchMenu)client.player.containerMenu).getSlot(3).getItem().is(ResearchContent.LOST_SCROLL));ready(c);
            world.getServer().runOnServer(s->{var m=(ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu;check(ItemStack.matches(failureA,m.getSlot(0).getItem())&&ItemStack.matches(failureB,m.getSlot(1).getItem())&&m.getSlot(2).getItem().getCount()==6,"scroll keeps full input components and consumes one core");});shot(c,"07-scroll-books-preserved");
            shift(c,252,56);ready(c);shot(c,"08-known-scroll-preview");
            // Earn four more distinct records through real commits; no direct discover() test shortcut.
            var fixtures=world.getServer().computeOnServer(s->ResearchWorldData.get(s).catalogue().stream().map(e->ResearchGameTests.find(s.overworld(),r->r.id().equals(e.id()))).filter(r->!r.equals(first)).limit(4).toList());
            for(var recipe:fixtures) {
                shift(c,32,56);shift(c,64,56);ready(c);
                world.getServer().runOnServer(s->{var m=(ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu;m.getSlot(0).set(ResearchGameTests.book(s.overworld(),recipe.a()));m.getSlot(1).set(ResearchGameTests.book(s.overworld(),recipe.b()));m.broadcastChanges();});ready(c);
                click(c,230,106);c.waitFor(client->!((ResearchMenu)client.player.containerMenu).getSlot(3).getItem().isEmpty());shift(c,252,56);ready(c);
            }
            click(c,120,24);ready(c);shot(c,"09-records-english");cursor(c,180,64);c.getInput().scroll(-2);c.waitTicks(5);ready(c);
            c.runOnClient(client->check(((ResearchMenu)client.player.containerMenu).preview().offset()>0,"scroll requests next owned discovery rows"));shot(c,"10-records-scrolled");
            language(c,"zh_cn");ready(c);c.runOnClient(client->check(Component.translatable("block.differentenchant.lost_research_table").getString().equals("失落魔法研究台"),"Chinese loaded"));shot(c,"11-records-chinese");
            click(c,40,24);ready(c);world.getServer().runOnServer(s->{var m=(ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu;m.getSlot(0).set(ResearchGameTests.book(s.overworld(),first.a()));m.getSlot(1).set(ResearchGameTests.book(s.overworld(),first.b()));m.broadcastChanges();});ready(c);shot(c,"12-known-chinese");
            savedData=world.getServer().computeOnServer(s->ResearchWorldData.get(s).exportJson());savedItems=counts(world);
            long worldSeed=world.getServer().computeOnServer(s->s.overworld().getSeed());
            report.append("Survival placed original table; real use opened custom GUI. Mouse pickup, shift-core, Research packets, shift-output and swapped known recipe verified.\n")
                .append("Success: two books + one core. Scroll: preserved both original ItemStacks including name/repair component, one core consumed. Four extra records earned through commits; record scroll and English/Chinese checked.\n")
                .append("World seed ").append(worldSeed).append(", UUID ").append(playerId).append(".\n")
                .append("Success recipe ").append(first.key()).append("; scroll recipe ").append(scroll.key()).append(".\n");
            // Leave this real menu open: disconnect must return both books and the remaining cores once.
        }
        try(var world=save.open()) {
            c.waitTicks(5);check(savedItems.equals(counts(world)),"disconnect returned or dropped all transient inventory exactly once");
            world.getServer().runOnServer(s->{check(s.getPlayerList().getPlayers().getFirst().getUUID().equals(playerId),"same player identity on reconnect");check(JsonParser.parseString(savedData).equals(JsonParser.parseString(ResearchWorldData.get(s).exportJson())),"objective snapshot and personal discoveries survive disk restart");});
            open(c);world.getServer().runOnServer(s->{var m=(ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu;m.getSlot(0).set(ResearchGameTests.book(s.overworld(),first.a()));m.getSlot(1).set(ResearchGameTests.book(s.overworld(),first.b()));m.broadcastChanges();});ready(c);
            c.runOnClient(client->check(((ResearchMenu)client.player.containerMenu).preview().known()!=null,"known result remains known after restart"));shot(c,"13-reconnected-preview");
            close(c);report.append("Disconnected with menu open: inventory plus nearby native item-drop totals unchanged. Disk reopen retains world snapshot, UUID discoveries and known preview.\n");
            // Real equipped player + native fluid state + normal scheduled server event, not a direct effect-body call.
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();var helmet=new ItemStack(Items.DIAMOND_HELMET);
                helmet.enchant(s.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(ResearchContent.id("lantern_sight")).orElseThrow(),1);p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,helmet);
                for(int x=3;x<=5;x++)for(int y=-60;y<=-57;y++)for(int z=3;z<=5;z++)s.overworld().setBlockAndUpdate(new BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());});
            world.getServer().runCommand("tp @a 4.5 -60 4.5");c.waitFor(client->client.player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION));
            shot(c,"14-scheduled-lantern-sight");report.append("Actual equipped underwater client receives Lantern Sight via normal scheduled server callback and effect synchronization.\n");
        }
        try{var path=Path.of("../reports/research/client.txt");Files.createDirectories(path.getParent());Files.writeString(path,report.toString());}catch(Exception e){throw new RuntimeException(e);}
    }
}
