package dev.differentenchant;

import java.util.*;
import dev.differentenchant.enchanting.*;
import dev.differentenchant.resonance.*;
import dev.differentenchant.structure.StructurePattern;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/** Production menus, native crafting, persistent components, shared inventory and teardown. */
public final class ResonanceGameTests {
    static ItemStack core(int energy) {var stack=new ItemStack(ResonanceItems.CORE);stack.set(ResonanceItems.ENERGY,energy);return stack;}
    private static void energy(GameTestHelper h,MachineControllerBlockEntity master,int expected) {
        h.assertTrue(ResonanceItems.energy(master.coreInventory().getItem(0))==expected,"core energy "+expected);
    }
    @GameTest public void nativeRecipeEveryBookMixtureAndActualMaterialConsumption(GameTestHelper h) {
        var level=h.getLevel();
        for(int mask=0;mask<16;mask++) {
            var grid=new ArrayList<ItemStack>();
            int book=0;
            for(int i=0;i<9;i++) {
                ItemStack stack=new ItemStack(i==4?Items.LAPIS_BLOCK:(i%2==0?Items.IRON_INGOT:Items.BOOK));
                if(i%2==1 && (mask & (1<<book++))!=0) {
                    stack=new ItemStack(Items.ENCHANTED_BOOK);
                    stack.enchant(DirectedGameTests.holder(h,"minecraft:sharpness"),5);
                    stack.enchant(DirectedGameTests.holder(h,"minecraft:unbreaking"),3);
                }
                grid.add(stack);
            }
            var input=CraftingInput.of(3,3,grid);
            var recipe=level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow();
            var output=recipe.value().assemble(input);
            h.assertTrue(output.is(ResonanceItems.CORE) && output.getCount()==1 && output.getMaxStackSize()==1 && ResonanceItems.energy(output)==0,"native recipe mixed books mask "+mask);
            h.assertTrue(!output.isEnchanted(),"book enchantments are consumed, not transferred");
            var wrong=new ArrayList<>(grid);wrong.set(1,new ItemStack(Items.PAPER));
            h.assertTrue(!recipe.value().matches(CraftingInput.of(3,3,wrong),level),"paper cannot substitute a book");
            wrong=new ArrayList<>(grid);wrong.set(0,new ItemStack(Items.GOLD_INGOT));
            h.assertTrue(!recipe.value().matches(CraftingInput.of(3,3,wrong),level),"iron required");
            // Native ResultSlot.onTake, not a custom crafting implementation.
            var player=h.makeMockServerPlayerInLevel();
            var pos=h.absolutePos(new BlockPos(1,2,1));level.setBlockAndUpdate(pos,Blocks.CRAFTING_TABLE.defaultBlockState());
            var menu=new CraftingMenu(11,player.getInventory(),ContainerLevelAccess.create(level,pos));player.containerMenu=menu;
            for(int i=0;i<9;i++) menu.getSlot(i+1).set(grid.get(i).copy());
            h.assertTrue(menu.getSlot(0).getItem().is(ResonanceItems.CORE),"real crafting result");
            h.assertTrue(menu.quickMoveStack(player,0).is(ResonanceItems.CORE),"craft through native result slot");
            for(int i=1;i<=9;i++) h.assertTrue(menu.getSlot(i).getItem().isEmpty(),"each ingredient consumed exactly once");
            h.assertTrue(player.getInventory().countItem(ResonanceItems.CORE)==1,"one fresh core delivered");menu.removed(player);
        }
        h.assertTrue(new ItemStack(ResonanceItems.ATTUNED_CORE).getMaxStackSize()==64,"stateless attuned cores stack normally");h.succeed();
    }
    @GameTest public void randomActualPaymentsElevenFullThenRepeatProductionWithoutTierThree(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var player=EnchantingMenuGameTests.player(h,master);
        master.coreInventory().setItem(0,core(0));var menu=EnchantingMenuGameTests.menu(h,master,player);
        for(int option:new int[]{0,1,2}) {
            int before=ResonanceItems.energy(master.coreInventory().getItem(0));player.experienceLevel=60;
            menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
            int quote=menu.buttonIdFor(option);
            h.assertTrue(menu.clickMenuButton(player,quote),"real random option "+option);
            energy(h,master,before+option+1);
            h.assertTrue(!menu.clickMenuButton(player,quote),"replay cannot charge energy twice");energy(h,master,before+option+1);
        }
        for(int option:new int[]{1,2}) {
            player.experienceLevel=60;menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,3));
            h.assertTrue(menu.clickMenuButton(player,menu.buttonIdFor(option)),"toward eleven");
        }
        energy(h,master,11);h.assertTrue(master.machineTier()==1,"11/12 is not full");
        player.experienceLevel=60;menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,3));
        int quote=menu.buttonIdFor(2);h.assertTrue(menu.clickMenuButton(player,quote),"successful final charge");energy(h,master,12);
        h.assertTrue(master.machineTier()==2 && menu.machineTier()==2 && master.coreInventory().getItem(0).is(ResonanceItems.ATTUNED_CORE)
                && master.coreInventory().getItem(0).getCount()==1 && !master.coreInventory().getItem(0).has(ResonanceItems.ENERGY),"one stateless attuned core retained and live tier synchronized");
        master.coreInventory().setItem(0,core(11));menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
        h.assertTrue(!menu.clickMenuButton(player,quote),"prior conversion request cannot charge replacement core");energy(h,master,11);
        player.experienceLevel=60;menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,3));
        h.assertTrue(menu.clickMenuButton(player,menu.buttonIdFor(2)),"repeat production at Lv.2");
        h.assertTrue(master.machineTier()==2 && master.coreInventory().getItem(0).is(ResonanceItems.ATTUNED_CORE),"second full core never upgrades to Lv.3");
        menu.removed(player);h.succeed();
    }
    @GameTest public void directedBatchChargesActualLevelsOnlyAfterSuccessAndIgnoresCreative(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var player=EnchantingMenuGameTests.player(h,master);
        master.coreInventory().setItem(0,core(0));var menu=DirectedGameTests.directed(h,master,player,3);
        DirectedGameTests.action(menu,player,DirectedActionPayload.ADD,DirectedGameTests.SHARP);energy(h,master,0);
        DirectedGameTests.action(menu,player,DirectedActionPayload.REMOVE,DirectedGameTests.SHARP);energy(h,master,0);
        DirectedGameTests.action(menu,player,DirectedActionPayload.ADD,DirectedGameTests.SHARP);
        player.experienceLevel=29;
        h.assertTrue(!DirectedGameTests.action(menu,player,DirectedActionPayload.ENCHANT,""),"30 gate unchanged");energy(h,master,0);
        player.experienceLevel=60;
        int cost=menu.directedSnapshot().levels();h.assertTrue(cost>=5 && cost<=8,"single actual fee");
        h.assertTrue(DirectedGameTests.action(menu,player,DirectedActionPayload.ENCHANT,""),"single success");energy(h,master,cost);
        h.assertTrue(player.experienceLevel==60-cost,"no extra XP charged for resonance");
        DirectedGameTests.action(menu,player,DirectedActionPayload.ADD,DirectedGameTests.SHARP);
        DirectedGameTests.action(menu,player,DirectedActionPayload.ADD,DirectedGameTests.UNBREAK);
        var state=menu.directedSnapshot();var request=new DirectedActionPayload(menu.containerId,state.revision(),DirectedActionPayload.ENCHANT,"");
        h.assertTrue(state.levels()>=10 && menu.handleDirected(player,request),"batch pays each planned step");
        energy(h,master,12);h.assertTrue(master.machineTier()==2 && player.experienceLevel==60-cost-state.levels(),"batch excess discarded, tier only two");
        master.coreInventory().setItem(0,core(0));h.assertTrue(!menu.handleDirected(player,request),"batch replay rejected");energy(h,master,0);
        player.setGameMode(GameType.CREATIVE);player.experienceLevel=60;menu.getSlot(1).set(new ItemStack(DifferentEnchant.RUNIC_STONE));
        DirectedGameTests.action(menu,player,DirectedActionPayload.ADD,DirectedGameTests.SHARP);
        h.assertTrue(DirectedGameTests.action(menu,player,DirectedActionPayload.ENCHANT,""),"creative directed still works");energy(h,master,0);
        menu.removed(player);h.succeed();
    }
    @GameTest public void failedRandomCreativeNoCoreAndNoRetroactiveEnergy(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var player=EnchantingMenuGameTests.player(h,master);var menu=EnchantingMenuGameTests.menu(h,master,player);
        h.assertTrue(menu.clickMenuButton(player,menu.buttonIdFor(2)),"works without any core");
        master.coreInventory().setItem(0,core(0));energy(h,master,0);
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));player.experienceLevel=0;
        h.assertTrue(!menu.clickMenuButton(player,menu.buttonIdFor(2)),"failed XP");energy(h,master,0);
        player.experienceLevel=60;menu.getSlot(1).set(ItemStack.EMPTY);
        h.assertTrue(!menu.clickMenuButton(player,menu.buttonIdFor(2)),"failed lapis");energy(h,master,0);
        player.setGameMode(GameType.CREATIVE);
        h.assertTrue(menu.clickMenuButton(player,menu.buttonIdFor(2)),"creative free enchanting still works");energy(h,master,0);
        h.assertTrue(master.machineTier()==1,"free operation cannot upgrade machine");menu.removed(player);h.succeed();
    }
    @GameTest public void transferComponentNetworkAndSaveRoundTripAndLegacyEmpty(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);master.coreInventory().setItem(0,core(7));
        var player=EnchantingMenuGameTests.player(h,master);var menu=EnchantingMenuGameTests.menu(h,master,player);
        menu.removed(player);menu.removed(player);energy(h,master,7);
        h.assertTrue(player.getInventory().countItem(ResonanceItems.CORE)==0,"closing never returns installed core");
        var tag=master.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored=(MachineControllerBlockEntity)BlockEntity.loadStatic(master.getBlockPos(),master.getBlockState(),tag,h.getLevel().registryAccess());
        h.assertTrue(restored!=null && restored.machineTier()==1,"persistent controller");energy(h,restored,7);
        h.getLevel().removeBlockEntity(master.getBlockPos());h.getLevel().setBlockEntity(restored);
        var stack=restored.coreInventory().removeItemNoUpdate(0);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {ItemStack.STREAM_CODEC.encode(buffer,stack);var decoded=ItemStack.STREAM_CODEC.decode(buffer);h.assertTrue(ItemStack.matches(stack,decoded),"charge is native network-synchronized item data");} finally {buffer.release();}
        // A distinct controller position / UUID, not a second menu for the same machine.
        var pos=master.getBlockPos().offset(12,0,0);
        for(var cell:StructurePattern.CELLS) h.getLevel().setBlockAndUpdate(FormationService.position(pos,cell,0),FormationService.originalBlock(cell.material()).defaultBlockState());
        FormationService.form(h.getLevel(),pos,net.minecraft.core.Direction.NORTH);
        var other=(MachineControllerBlockEntity)h.getLevel().getBlockEntity(pos);other.coreInventory().setItem(0,stack);
        other.chargeCore(2,false);energy(h,other,9);h.assertTrue(restored.coreInventory().isEmpty(),"source is empty after transfer");
        tag.remove("resonance_core");var legacy=(MachineControllerBlockEntity)BlockEntity.loadStatic(master.getBlockPos(),master.getBlockState(),tag,h.getLevel().registryAccess());
        h.assertTrue(legacy!=null && legacy.coreInventory().isEmpty(),"pre-core saves read an empty slot");
        for(int malformed:new int[]{-5,Integer.MAX_VALUE}) {other.coreInventory().setItem(0,core(malformed));other.chargeCore(1,false);energy(h,other,malformed<0?1:12);}
        for(int tier=2;tier<=5;tier++) {
            other.setMachineTier(tier);var named=core(11);named.set(DataComponents.CUSTOM_NAME,Component.literal("Traveling core"));
            other.coreInventory().setItem(0,named);other.chargeCore(8,false);
            h.assertTrue(other.machineTier()==tier && other.coreInventory().getItem(0).is(ResonanceItems.ATTUNED_CORE)
                    && other.coreInventory().getItem(0).get(DataComponents.CUSTOM_NAME).equals(named.get(DataComponents.CUSTOM_NAME)),"tier "+tier+" produces material without upgrading; unrelated data retained");
        }
        h.succeed();
    }
    @GameTest public void sharedMenusShiftClicksClosingAndRemovalCannotDuplicate(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var a=EnchantingMenuGameTests.player(h,master);var b=EnchantingMenuGameTests.player(h,master);
        var first=EnchantingMenuGameTests.menu(h,master,a);var second=EnchantingMenuGameTests.menu(h,master,b);
        a.getInventory().setItem(9,core(7));
        h.assertTrue(first.quickMoveStack(a,2).is(ResonanceItems.CORE),"shift insert");
        h.assertTrue(first.getSlot(38).getItem()==second.getSlot(38).getItem(),"one shared live server stack");energy(h,master,7);
        h.assertTrue(second.quickMoveStack(b,38).is(ResonanceItems.CORE),"other viewer takes once");
        h.assertTrue(first.quickMoveStack(a,38).isEmpty() && master.coreInventory().isEmpty(),"cannot take again from stale viewer");
        h.assertTrue(b.getInventory().countItem(ResonanceItems.CORE)==1 && a.getInventory().countItem(ResonanceItems.CORE)==0,"one owner after transfer");
        h.assertTrue(!first.getSlot(38).mayPlace(new ItemStack(Items.DIRT)),"core slot rejects other items");
        master.coreInventory().setItem(0,new ItemStack(ResonanceItems.ATTUNED_CORE));
        h.assertTrue(second.quickMoveStack(b,38).is(ResonanceItems.ATTUNED_CORE),"shift result out");
        b.getInventory().setItem(10,new ItemStack(ResonanceItems.ATTUNED_CORE,5));
        h.assertTrue(second.quickMoveStack(b,3).is(ResonanceItems.ATTUNED_CORE) && master.coreInventory().getItem(0).getCount()==1 && b.getInventory().getItem(10).getCount()==4,"one core per installed slot even from a stack");
        var request=new DirectedActionPayload(first.containerId,first.directedSnapshot().revision(),DirectedActionPayload.DIRECTED,"");
        first.removed(a);h.assertTrue(!first.handleDirected(a,request),"closed session cannot operate");
        second.removed(b);h.assertTrue(master.coreInventory().getItem(0).getCount()==1,"both closes keep core installed");
        first=EnchantingMenuGameTests.menu(h,master,a);second=EnchantingMenuGameTests.menu(h,master,b);
        master.coreInventory().setItem(0,core(11));int stale=second.buttonIdFor(0);
        h.assertTrue(first.clickMenuButton(a,first.buttonIdFor(0)) && master.machineTier()==2,"one player completes shared core");
        h.assertTrue(!second.clickMenuButton(b,stale) && b.experienceLevel==40,"other viewer cannot use pre-upgrade quotation");
        second.broadcastChanges();h.assertTrue(second.machineTier()==2 && second.getSlot(38).getItem().is(ResonanceItems.ATTUNED_CORE),"other viewer refreshes tier and same finished core");
        first.removed(a);second.removed(b);h.succeed();
    }
    @GameTest public void allPartsDropInstalledCoreExactlyOnceAndInvalidateOldSlot(GameTestHelper h) {
        for(int broken=0;broken<18;broken++) {
            var master=EnchantingMenuGameTests.machine(h);var player=EnchantingMenuGameTests.player(h,master);var menu=EnchantingMenuGameTests.menu(h,master,player);
            master.coreInventory().setItem(0,core(7));var pos=FormationService.position(master.getBlockPos(),StructurePattern.CELLS.get(broken),0);
            h.getLevel().destroyBlock(pos,false);DisassemblyService.disassemble(h.getLevel(),master,pos);
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(master.getBlockPos()).inflate(4));
            var cores=drops.stream().filter(e->e.getItem().is(ResonanceItems.CORE)).toList();
            h.assertTrue(cores.stream().mapToInt(e->e.getItem().getCount()).sum()==1 && ResonanceItems.energy(cores.getFirst().getItem())==7,"exactly one persistent core from broken part "+broken);
            h.assertTrue(!menu.getSlot(38).mayPickup(player) && !menu.getSlot(38).mayPlace(core(0)) && menu.quickMoveStack(player,38).isEmpty(),"stale menu cannot recover drained core");
            h.assertTrue(master.coreInventory().isEmpty(),"removed controller drained before replacement");
            drops.forEach(ItemEntity::discard);menu.removed(player);
        }
        h.succeed();
    }
    @GameTest(padding=16) public void creativeBreakAndExplosionKeepOneInstalledCore(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var player=EnchantingMenuGameTests.player(h,master);
        player.setGameMode(GameType.CREATIVE);master.coreInventory().setItem(0,new ItemStack(ResonanceItems.ATTUNED_CORE));
        h.assertTrue(player.gameMode.destroyBlock(master.getBlockPos()),"creative breaks controller");
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(master.getBlockPos()).inflate(4));
        h.assertTrue(drops.stream().filter(e->e.getItem().is(ResonanceItems.ATTUNED_CORE)).mapToInt(e->e.getItem().getCount()).sum()==1,"creative keeps installed finished core");drops.forEach(ItemEntity::discard);
        master=EnchantingMenuGameTests.machine(h);master.coreInventory().setItem(0,core(11));var pos=master.getBlockPos();
        h.getLevel().explode(null,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,6,net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(12));
        h.assertTrue(drops.stream().filter(e->e.getItem().is(ResonanceItems.CORE) && ResonanceItems.energy(e.getItem())==11).mapToInt(e->e.getItem().getCount()).sum()==1,"actual explosion keeps installed charged core once");
        h.succeed();
    }
}
