package dev.betterenchant;

import java.util.*;
import dev.betterenchant.client.MachineEnchantmentScreen;
import dev.betterenchant.enchanting.*;
import dev.betterenchant.structure.StructurePattern;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.inventory.ContainerInput;

/** Actual screen, inventory click packets, mouse button packet, saving and reconnecting. */
public final class EnchantingClientGameTest implements FabricClientGameTest {
    private static final BlockPos ORIGIN=new BlockPos(0,-59,0);
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void open(ClientGameTestContext context) {
        context.getInput().lookAt(ORIGIN); context.waitTicks(3);
        context.getInput().pressKey(options->options.keyUse);
        context.waitForScreen(MachineEnchantmentScreen.class);
    }
    private static void shift(ClientGameTestContext context,Item item) {
        context.runOnClient(client->{
            var menu=client.player.containerMenu;
            for(int index=2;index<menu.slots.size();index++) if(menu.getSlot(index).getItem().is(item)) {
                client.gameMode.handleContainerInput(menu.containerId,index,0,ContainerInput.QUICK_MOVE,client.player); return;
            }
            throw new AssertionError("Missing fixture inventory item "+item);
        });
        context.waitTicks(3);
    }
    private static void mouseOption(ClientGameTestContext context,int option) {
        double[] pixel=context.computeOnClient(client->{
            double scale=client.getWindow().getGuiScale();
            return new double[]{((client.screen.width-MachineGuiLayout.WIDTH)/2.0+MachineGuiLayout.option(option).x()+60)*scale,((client.screen.height-MachineGuiLayout.HEIGHT)/2.0+MachineGuiLayout.option(option).y()+8)*scale};
        });
        context.getInput().setCursorPos(pixel[0],pixel[1]); context.getInput().pressMouse(0); context.waitTicks(4);
    }
    private static int[] preview(ClientGameTestContext context) {
        return context.computeOnClient(client->{var menu=(MachineEnchantmentMenu)client.player.containerMenu;
            return new int[]{menu.enchantClue[0],menu.enchantClue[1],menu.enchantClue[2],menu.levelClue[0],menu.levelClue[1],menu.levelClue[2]};});
    }
    @Override public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280,720);
        TestWorldSave save; int[] preview; long sourceSeed; int vanillaSeed;
        List<EnchantingPreviewPayload.Offer> progressive;
        try(var world=context.worldBuilder().create()) {
            save=world.getWorldSave();
            world.getServer().runOnServer(server->{
                var level=server.overworld();
                for(var cell:StructurePattern.CELLS) level.setBlockAndUpdate(FormationService.position(ORIGIN,cell,0),FormationService.originalBlock(cell.material()).defaultBlockState());
                require(FormationService.form(level,ORIGIN,Direction.NORTH).startsWith("Formed"),"formed fixture");
                var master=(MachineControllerBlockEntity)level.getBlockEntity(ORIGIN); master.setMachineTier(3);
                BookshelfFixtures.playerLayout(level,ORIGIN,15);
                var player=server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL); player.experienceLevel=40;
                player.getInventory().setSelectedSlot(8);
                player.getInventory().setItem(9,new ItemStack(Items.DIAMOND_SWORD));
                player.getInventory().setItem(10,new ItemStack(Items.LAPIS_LAZULI,12));
            });
            world.getServer().runCommand("tp @a 0.5 -58 0.5"); context.waitTicks(5);
            open(context); shift(context,Items.DIAMOND_SWORD); shift(context,Items.LAPIS_LAZULI);
            context.runOnClient(client->{
                var menu=(MachineEnchantmentMenu)client.player.containerMenu;
                require(menu.machineTier()==3 && menu.bookshelfCount()==15,"tier/shelves synchronized to real client");
                require(menu.costs[2]==30 && menu.enchantClue[2]>=0 && menu.preview()!=null,"client has usable priced preview");
            });
            preview=preview(context);
            progressive=context.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).preview().offers());
            sourceSeed=world.getServer().computeOnServer(server->EnchantingSeedData.forPlayer(server.getPlayerList().getPlayers().getFirst(),0));
            vanillaSeed=world.getServer().computeOnServer(server->server.getPlayerList().getPlayers().getFirst().getEnchantmentSeed());
            require(vanillaSeed!=0,"initialize vanilla zero sentinel before first save");
            context.runOnClient(client->client.getToastManager().clear()); context.getInput().setCursorPos(1,1); context.waitTicks(2); context.takeScreenshot("05-random-enchanting-offers");
            context.takeScreenshot("ui-random-00-vanilla-equipment");
            double[] costTooltip=context.computeOnClient(client->{double scale=client.getWindow().getGuiScale();return new double[]{((client.screen.width-MachineGuiLayout.WIDTH)/2.0+140)*scale,((client.screen.height-MachineGuiLayout.HEIGHT)/2.0+86)*scale};});
            context.getInput().setCursorPos(costTooltip[0],costTooltip[1]); context.waitTick();
            context.takeScreenshot("08-enchanting-cost-tooltip");
            context.takeScreenshot("ui-random-06-vanilla-hover");
            context.getInput().pressKey(options->options.keyInventory); context.waitForScreen(null);
        }
        try(var world=save.open()) {
            context.waitTicks(5);
            world.getServer().runOnServer(server->{
                require(((MachineControllerBlockEntity)server.overworld().getBlockEntity(ORIGIN)).machineTier()==3,"tier saved to disk");
                require(server.getPlayerList().getPlayers().getFirst().getEnchantmentSeed()==vanillaSeed,"vanilla seed saved to disk");
                require(EnchantingSeedData.forPlayer(server.getPlayerList().getPlayers().getFirst(),0)==sourceSeed,"private salt/seed survives disk reload");
            });
            open(context); shift(context,Items.DIAMOND_SWORD); shift(context,Items.LAPIS_LAZULI);
            require(Arrays.equals(preview,preview(context)),"disk reopen does not reroll");
            require(progressive.equals(context.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).preview().offers())),"all disclosed entries and order survive disk reconnect");
            int stale=context.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).buttonIdFor(2));
            world.getServer().runCommand("better_enchant tier 0 -59 0 5"); context.waitTicks(4);
            context.runOnClient(client->{
                var menu=(MachineEnchantmentMenu)client.player.containerMenu;
                require(menu.machineTier()==5,"live tier synchronized");
                client.gameMode.handleInventoryButtonClick(menu.containerId,stale);
            });
            context.waitTicks(3);
            world.getServer().runOnServer(server->require(server.getPlayerList().getPlayers().getFirst().experienceLevel==40,"stale network packet rejected"));
            context.takeScreenshot("06-live-machine-tier");
            int committed=context.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).buttonIdFor(2));
            var disclosed=context.computeOnClient(client->((MachineEnchantmentMenu)client.player.containerMenu).preview().offers().get(2));
            mouseOption(context,2);
            context.waitFor(client->client.player.containerMenu.getSlot(0).getItem().isEnchanted());
            world.getServer().runOnServer(server->{
                var player=server.getPlayerList().getPlayers().getFirst();
                require(player.experienceLevel==37,"actual network commit spends three levels");
                require(player.containerMenu.getSlot(1).getItem().getCount()==9,"actual network commit spends three lapis");
                var actual=EnchantmentHelper.getEnchantmentsForCrafting(player.containerMenu.getSlot(0).getItem());
                require(actual.size()==disclosed.revealed().size() && !disclosed.hidden(),"client full preview exact result count");
                for(var entry:disclosed.revealed()) require(actual.getLevel(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(entry.id()).orElseThrow())==entry.level(),"each client preview equals committed server result");
            });
            context.runOnClient(client->client.gameMode.handleInventoryButtonClick(client.player.containerMenu.containerId,committed));
            context.waitTicks(3);
            world.getServer().runOnServer(server->require(server.getPlayerList().getPlayers().getFirst().experienceLevel==37,"network replay cannot double-charge"));
            double[] input=context.computeOnClient(client->{double scale=client.getWindow().getGuiScale();return new double[]{((client.screen.width-MachineGuiLayout.WIDTH)/2.0+33)*scale,((client.screen.height-MachineGuiLayout.HEIGHT)/2.0+86)*scale};});
            context.getInput().setCursorPos(input[0],input[1]); context.waitTick();
            context.takeScreenshot("07-enchanted-item-result");
            context.runOnClient(client->client.gameMode.handleContainerInput(client.player.containerMenu.containerId,0,0,ContainerInput.QUICK_MOVE,client.player));
            context.waitTicks(3); context.getInput().pressKey(options->options.keyInventory); context.waitForScreen(null);
        }
        try(var world=save.open()) {
            context.waitTicks(5);
            world.getServer().runOnServer(server->{
                var player=server.getPlayerList().getPlayers().getFirst();
                require(player.experienceLevel==37 && player.getInventory().countItem(Items.LAPIS_LAZULI)==9,"costs saved to disk");
                int count=0;
                for(int i=0;i<player.getInventory().getContainerSize();i++) {
                    var item=player.getInventory().getItem(i);
                    if(item.is(Items.DIAMOND_SWORD)) {require(!EnchantmentHelper.getEnchantmentsForCrafting(item).isEmpty(),"enchantments saved on actual item");count+=item.getCount();}
                }
                require(count==1,"exactly one sword after reload");
                require(MachineAccess.controller(server.overworld(),ORIGIN)!=null,"machine still valid after enchanting");
            });
        }
    }
}
