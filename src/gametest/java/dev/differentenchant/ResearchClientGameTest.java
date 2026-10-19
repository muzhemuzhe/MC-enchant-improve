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

/** Native block placement/opening, mouse slots, actual result-slot packets and disk reconnect. */
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
        if(p.containerMenu instanceof ResearchMenu menu)for(int i=0;i<3;i++)stacks.add(menu.getSlot(i).getItem());stacks.add(p.containerMenu.getCarried());
        for(var item:s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(TABLE).inflate(12)))stacks.add(item.getItem());
        for(var stack:stacks)if(!stack.isEmpty())counts.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum);return counts;});}
    private static void supply(TestSingleplayerContext world,ResearchPolicy.Recipe recipe) {
        world.getServer().runOnServer(s->{var m=(ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu;m.getSlot(0).set(ResearchGameTests.book(s.overworld(),recipe.a()));m.getSlot(1).set(ResearchGameTests.book(s.overworld(),recipe.b()));m.broadcastChanges();});
    }
    private static void hoverShot(ClientGameTestContext c,String name){cursor(c,188,66);c.waitTicks(3);c.takeScreenshot("research-gui-"+name);}
    @Override public void runTest(ClientGameTestContext c) {
        c.getInput().resizeWindow(1280,720);language(c,"en_us");TestWorldSave save;String savedData;Map<String,Integer> savedItems;ResearchPolicy.Recipe first,scroll;UUID playerId;
        var report=new StringBuilder();
        try(var world=c.worldBuilder().create()) {
            save=world.getWorldSave();world.getServer().runCommand("time set day");world.getServer().runCommand("weather clear");world.getServer().runCommand("difficulty peaceful");
            first=world.getServer().computeOnServer(s->ResearchGameTests.find(s.overworld(),r->!r.scroll()));scroll=world.getServer().computeOnServer(s->ResearchGameTests.find(s.overworld(),ResearchPolicy.Result::scroll));
            playerId=world.getServer().computeOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.SURVIVAL);p.getInventory().setSelectedSlot(0);p.getInventory().setItem(0,new ItemStack(ResearchContent.TABLE_ITEM));
                p.getInventory().setItem(9,ResearchGameTests.book(s.overworld(),first.a()));p.getInventory().setItem(10,ResearchGameTests.book(s.overworld(),first.b()));p.getInventory().setItem(11,new ItemStack(ResonanceItems.ATTUNED_CORE,12));
                p.getInventory().setItem(12,ResearchGameTests.book(s.overworld(),first.a()));p.getInventory().setItem(13,ResearchGameTests.book(s.overworld(),first.b()));return p.getUUID();});
            world.getServer().runCommand("tp @a 0.5 -60 -2.5");c.waitTicks(5);aim(c,.5,-60.001,.5);c.waitTicks(3);c.getInput().pressKey(o->o.keyUse);c.waitTicks(5);
            world.getServer().runOnServer(s->check(s.overworld().getBlockState(TABLE).is(ResearchContent.TABLE),"normal survival placed actual table"));open(c);
            click(c,42,140);click(c,32,66);click(c,60,140);click(c,64,66);shift(c,78,140);ready(c);
            c.runOnClient(client->{var m=(ResearchMenu)client.player.containerMenu;check(m.preview().known()==null&&m.preview().status()==0&&m.getSlot(3).getItem().is(Items.BOOK),"native synced unknown output contains no hidden enchantment or scroll");check(m.getSlot(3).getItem().get(DataComponents.STORED_ENCHANTMENTS)==null,"unknown has no stored result enchantment");});shot(c,"gui-01-unknown");hoverShot(c,"02-unknown-tooltip-en");
            c.runOnClient(client->client.options.advancedItemTooltips=true);hoverShot(c,"02b-unknown-advanced-tooltip-en");c.runOnClient(client->client.options.advancedItemTooltips=false);
            click(c,188,66);c.waitFor(client->client.player.containerMenu.getCarried().is(Items.ENCHANTED_BOOK));ready(c);
            world.getServer().runOnServer(s->{var m=(ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu;check(m.getSlot(0).getItem().isEmpty()&&m.getSlot(1).getItem().isEmpty()&&m.getSlot(2).getItem().getCount()==11&&m.getSlot(3).getItem().isEmpty(),"direct result mouse pickup charges once and delivers real book to cursor");});
            cursor(c,188,86);c.takeScreenshot("research-gui-03-direct-pickup");click(c,42,198);ready(c);
            click(c,114,140);click(c,32,66);click(c,96,140);click(c,64,66);ready(c);
            c.runOnClient(client->check(((ResearchMenu)client.player.containerMenu).preview().known()!=null&&client.player.containerMenu.getSlot(3).getItem().is(Items.ENCHANTED_BOOK),"known swapped recipe has actual book preview"));shot(c,"gui-04-known-preview-en");hoverShot(c,"05-known-tooltip-en");
            shift(c,188,66);ready(c);world.getServer().runOnServer(s->check(((ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu).getSlot(2).getItem().getCount()==10,"known repeat shift click charges one core"));
            click(c,120,24);ready(c);shot(c,"gui-06-first-record");click(c,40,24);ready(c);
            supply(world,scroll);world.getServer().runOnServer(s->{var m=(ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu;m.getSlot(0).getItem().set(DataComponents.CUSTOM_NAME,Component.literal("Preserved original book"));m.getSlot(0).getItem().set(DataComponents.REPAIR_COST,17);m.broadcastChanges();});ready(c);
            var failureA=world.getServer().computeOnServer(s->((ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu).getSlot(0).getItem().copy());var failureB=world.getServer().computeOnServer(s->((ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu).getSlot(1).getItem().copy());
            c.runOnClient(client->{var m=(ResearchMenu)client.player.containerMenu;check(m.preview().known()==null&&m.getSlot(3).getItem().is(Items.BOOK),"unknown scroll still uses same book icon");});shot(c,"gui-07-secret-scroll");
            language(c,"zh_cn");ready(c);hoverShot(c,"08-unknown-tooltip-zh");
            shift(c,188,66);ready(c);world.getServer().runOnServer(s->{var m=(ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu;check(ItemStack.matches(failureA,m.getSlot(0).getItem())&&ItemStack.matches(failureB,m.getSlot(1).getItem())&&m.getSlot(2).getItem().getCount()==9,"scroll shift keeps complete book components and consumes one core");});shot(c,"gui-09-scroll-preserved");hoverShot(c,"10-known-scroll-tooltip-zh");
            shift(c,188,66);ready(c);world.getServer().runOnServer(s->check(((ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu).getSlot(2).getItem().getCount()==8,"repeat scroll takes one further core"));
            shift(c,32,66);shift(c,64,66);ready(c);
            var fixtures=world.getServer().computeOnServer(s->ResearchWorldData.get(s).catalogue().stream().map(e->ResearchGameTests.find(s.overworld(),r->r.id().equals(e.id()))).filter(r->!r.equals(first)).limit(4).toList());
            for(var recipe:fixtures){supply(world,recipe);ready(c);shift(c,188,66);ready(c);}
            click(c,120,24);ready(c);shot(c,"gui-11-records-zh");cursor(c,150,64);c.getInput().scroll(-2);c.waitTicks(5);ready(c);
            c.runOnClient(client->check(((ResearchMenu)client.player.containerMenu).preview().offset()>0,"discovery list really scrolls"));shot(c,"gui-12-records-scrolled-zh");
            language(c,"en_us");ready(c);shot(c,"gui-13-records-en");click(c,40,24);ready(c);supply(world,first);ready(c);language(c,"zh_cn");ready(c);shot(c,"gui-14-known-preview-zh");hoverShot(c,"15-known-tooltip-zh");
            savedData=world.getServer().computeOnServer(s->ResearchWorldData.get(s).exportJson());savedItems=counts(world);
            report.append("Minecraft 26.1.2 native OpenGL client. Survival placement/use; mouse book inputs and shift-core, unknown synced BOOK without hidden stored enchantments; direct output pickup delivers real book to cursor.\n")
                .append("Success and known repeat consume both books and one core each. Unknown scroll uses identical BOOK, shift delivers true scroll; full named/repaired books unchanged. Known scroll repeats with one core per click.\n")
                .append("English/Chinese unknown and known tooltips, unknown F3+H tooltip, real book enchantment levels, scrolling six discoveries and compact layout screenshots. No Research action button.\n")
                .append("Seed ").append((long)world.getServer().computeOnServer(s->s.overworld().getSeed())).append("; UUID ").append(playerId).append("; success ").append(first.key()).append("; scroll ").append(scroll.key()).append(".\n");
            // Disconnect with a known preview, two real books and cores in the still-open menu.
        }
        try(var world=save.open()) {
            c.waitTicks(5);check(savedItems.equals(counts(world)),"disconnect returns each real input/cursor once without spawning preview");
            world.getServer().runOnServer(s->{check(s.getPlayerList().getPlayers().getFirst().getUUID().equals(playerId),"same UUID on reconnect");check(JsonParser.parseString(savedData).equals(JsonParser.parseString(ResearchWorldData.get(s).exportJson())),"world answer/discoveries persist unchanged");});
            // Native disconnect scatters menu inputs around the player. Move next to the persisted core entity,
            // then let vanilla pickup (including its delay) recover it; do not inject replacement materials.
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();
                if(p.getInventory().countItem(ResonanceItems.ATTUNED_CORE)==0){var core=s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(TABLE).inflate(12)).stream().filter(e->e.getItem().is(ResonanceItems.ATTUNED_CORE)).findFirst().orElseThrow();p.teleportTo(core.getX(),core.getY(),core.getZ());}});
            c.waitFor(client->client.player.getInventory().countItem(ResonanceItems.ATTUNED_CORE)>0);
            world.getServer().runCommand("tp @a 0.5 -60 -2.5");c.waitTicks(3);
            open(c);supply(world,first);world.getServer().runOnServer(s->{var m=(ResearchMenu)s.getPlayerList().getPlayers().getFirst().containerMenu;for(int i=0;i<36;i++){var stack=s.getPlayerList().getPlayers().getFirst().getInventory().getItem(i);if(stack.is(ResonanceItems.ATTUNED_CORE)){m.getSlot(2).set(stack.copy());s.getPlayerList().getPlayers().getFirst().getInventory().setItem(i,ItemStack.EMPTY);break;}}m.broadcastChanges();});ready(c);
            c.runOnClient(client->check(((ResearchMenu)client.player.containerMenu).preview().known()!=null&&client.player.containerMenu.getSlot(3).getItem().is(Items.ENCHANTED_BOOK),"known actual result preview survives disk restart"));shot(c,"gui-16-reconnected");close(c);
            report.append("Disconnected with preview and real materials in open menu: total inventory + native drops unchanged after disk reload; saved world snapshot, fixed answers and UUID discoveries identical.\n");
        }
        try{var path=Path.of("../reports/research-gui/client.txt");Files.createDirectories(path.getParent());Files.writeString(path,report.toString());}catch(Exception e){throw new RuntimeException(e);}
    }
}
