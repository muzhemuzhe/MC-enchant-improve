package dev.betterenchant;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.betterenchant.structure.StructurePattern;
import dev.betterenchant.enchanting.*;

public class EnchantingMenuGameTests {
    static MachineControllerBlockEntity machine(GameTestHelper h) {
        BlockPos origin=h.absolutePos(new BlockPos(3,3,3));
        for(var cell:StructurePattern.CELLS) h.getLevel().setBlockAndUpdate(FormationService.position(origin,cell,0),FormationService.originalBlock(cell.material()).defaultBlockState());
        h.assertTrue(FormationService.form(h.getLevel(),origin,Direction.NORTH).startsWith("Formed"),"fixture formation");
        return (MachineControllerBlockEntity)h.getLevel().getBlockEntity(origin);
    }
    static ServerPlayer player(GameTestHelper h,MachineControllerBlockEntity master) {
        var player=h.makeMockServerPlayerInLevel();
        player.teleportTo(master.getBlockPos().getX()+.5,master.getBlockPos().getY(),master.getBlockPos().getZ()-3);
        player.setGameMode(GameType.SURVIVAL); player.experienceLevel=40;
        h.assertTrue(!player.hasInfiniteMaterials(),"real payment precondition");
        return player;
    }
    static MachineEnchantmentMenu menu(GameTestHelper h,MachineControllerBlockEntity master,ServerPlayer player) {
        var menu=new MachineEnchantmentMenu(10,player.getInventory(),h.getLevel(),master);
        player.containerMenu=menu;
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,12));
        return menu;
    }
    static void assertLegal(GameTestHelper h,ItemStack item) {
        var enchants=EnchantmentHelper.getEnchantmentsForCrafting(item);
        h.assertTrue(!enchants.isEmpty(),"actual enchantments stored on result");
        for(var enchantment:enchants.keySet()) {
            h.assertTrue(item.is(Items.ENCHANTED_BOOK) || enchantment.value().isPrimaryItem(item),"supported item");
            h.assertTrue(enchants.getLevel(enchantment)>=1 && enchants.getLevel(enchantment)<=enchantment.value().getMaxLevel(),"valid rank");
            for(var other:enchants.keySet()) if(!enchantment.equals(other)) h.assertTrue(Enchantment.areCompatible(enchantment,other),"compatible pair");
        }
    }
    @GameTest
    public void exactPaymentsAllRowsAndReplayProtection(GameTestHelper h) {
        var master=machine(h); var player=player(h,master);
        for(int option=0;option<3;option++) {
            var menu=menu(h,master,player); player.experienceLevel=40;
            int code=menu.buttonIdFor(option), seed=player.getEnchantmentSeed();
            int hint=menu.enchantClue[option], rank=menu.levelClue[option];
            h.assertTrue(menu.costs[option]>0,"no bookshelf offers");
            h.assertTrue(menu.clickMenuButton(player,code),"commit row "+option);
            h.assertTrue(player.experienceLevel==40-option-1,"exact level deduction");
            h.assertTrue(menu.getSlot(1).getItem().getCount()==12-option-1,"exact lapis deduction");
            h.assertTrue(player.getEnchantmentSeed()!=seed,"success advances vanilla seed");
            h.assertTrue(menu.getEnchantmentSeed()==player.getEnchantmentSeed(),"cosmetic seed follows successful enchant");
            var result=menu.getSlot(0).getItem(); assertLegal(h,result);
            var holder=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(hint).orElseThrow();
            h.assertTrue(EnchantmentHelper.getEnchantmentsForCrafting(result).getLevel(holder)==rank,"preview matches committed result");
            h.assertTrue(!menu.clickMenuButton(player,code),"repeated packet rejected");
            h.assertTrue(player.experienceLevel==40-option-1,"no double charge");
            menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
            h.assertTrue(!menu.clickMenuButton(player,code),"old quote cannot enchant a newly inserted item");
            menu.removed(player);
        }
        h.succeed();
    }
    @GameTest
    public void insufficientResourcesInvalidItemsAndBooks(GameTestHelper h) {
        var master=machine(h); var player=player(h,master); var menu=menu(h,master,player);
        int seed=player.getEnchantmentSeed();
        player.experienceLevel=0;
        h.assertTrue(!menu.clickMenuButton(player,menu.buttonIdFor(0)),"not enough XP");
        player.experienceLevel=40;
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,2));
        h.assertTrue(!menu.clickMenuButton(player,menu.buttonIdFor(2)),"not enough lapis");
        menu.getSlot(1).set(new ItemStack(Items.DIRT,64));
        h.assertTrue(!menu.clickMenuButton(player,menu.buttonIdFor(0)),"currency type validation");
        h.assertTrue(!menu.clickMenuButton(player,-1) && !menu.clickMenuButton(player,Integer.MAX_VALUE),"invalid requests");
        h.assertTrue(seed==player.getEnchantmentSeed() && player.experienceLevel==40,"failed requests do not charge or reroll");
        menu.getSlot(0).set(new ItemStack(Items.DIRT)); menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,10));
        h.assertTrue(Arrays.stream(menu.costs).allMatch(value->value==0),"invalid input has no offers");
        menu.getSlot(0).set(new ItemStack(Items.BOOK));
        h.assertTrue(menu.clickMenuButton(player,menu.buttonIdFor(1)),"book enchant works");
        h.assertTrue(menu.getSlot(0).getItem().is(Items.ENCHANTED_BOOK),"book converted"); assertLegal(h,menu.getSlot(0).getItem());
        h.assertTrue(!menu.clickMenuButton(player,menu.buttonIdFor(1)),"enchanted book cannot be enchanted again");
        menu.removed(player); h.succeed();
    }
    @GameTest
    public void deterministicReopenTierChangeAndBrokenMachine(GameTestHelper h) {
        var master=machine(h); var player=player(h,master); var first=menu(h,master,player);
        int[] clue=first.enchantClue.clone(),levels=first.levelClue.clone();
        long seed=EnchantingSeedData.forPlayer(player,0);
        first.removed(player);
        var second=menu(h,master,player);
        h.assertTrue(Arrays.equals(clue,second.enchantClue) && Arrays.equals(levels,second.levelClue),"reopen does not reroll");
        second.getSlot(0).set(ItemStack.EMPTY); second.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
        h.assertTrue(Arrays.equals(clue,second.enchantClue) && Arrays.equals(levels,second.levelClue),"remove/reinsert does not reroll");
        int oldPoolCode=second.buttonIdFor(0);
        EnchantmentCandidates.invalidate(); second.broadcastChanges();
        h.assertTrue(!second.clickMenuButton(player,oldPoolCode),"datapack generation invalidates old quote");
        h.assertTrue(Arrays.equals(clue,second.enchantClue) && Arrays.equals(levels,second.levelClue),"identical reloaded data has deterministic offers");
        int oldCode=second.buttonIdFor(2);
        master.setMachineTier(5); second.broadcastChanges();
        h.assertTrue(second.machineTier()==5 && !second.clickMenuButton(player,oldCode),"reject quote issued before level change");
        h.assertTrue(player.experienceLevel==40 && second.getSlot(1).getItem().getCount()==12,"stale quote has no charge");
        h.assertTrue(EnchantingSeedData.forPlayer(player,0)==seed,"tier does not rotate source randomness");
        var saved=master.saveWithFullMetadata(h.getLevel().registryAccess());
        h.getLevel().removeBlockEntity(master.getBlockPos());
        var loaded=(MachineControllerBlockEntity)BlockEntity.loadStatic(master.getBlockPos(),master.getBlockState(),saved,h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(loaded); h.assertTrue(loaded.machineTier()==5,"tier persists");
        h.getLevel().destroyBlock(FormationService.position(master.getBlockPos(),StructurePattern.CELLS.getFirst(),0),false);
        h.assertTrue(!second.stillValid(player) && !second.clickMenuButton(player,second.buttonIdFor(2)),"broken machine invalidates menu");
        int before=player.getInventory().countItem(Items.DIAMOND_SWORD);
        second.removed(player); second.removed(player);
        h.assertTrue(player.getInventory().countItem(Items.DIAMOND_SWORD)==before+1,"close returns input exactly once");
        h.succeed();
    }
    @GameTest
    public void twoPlayersIsolatedInventoriesAndCreativeNoPayment(GameTestHelper h) {
        var master=machine(h); var a=player(h,master); var b=player(h,master);
        var first=menu(h,master,a); var second=menu(h,master,b);
        h.assertTrue(!first.clickMenuButton(b,first.buttonIdFor(0)),"another player cannot commit this menu");
        h.assertTrue(first.clickMenuButton(a,first.buttonIdFor(2)),"first player commits");
        h.assertTrue(b.experienceLevel==40 && !second.getSlot(0).getItem().isEnchanted() && second.getSlot(1).getItem().getCount()==12,"second inventory isolated");
        h.assertTrue(second.clickMenuButton(b,second.buttonIdFor(2)),"second player commits independently");
        first.getSlot(0).set(new ItemStack(Items.BOOK)); a.setGameMode(GameType.CREATIVE);
        int xp=a.experienceLevel,lapis=first.getSlot(1).getItem().getCount();
        h.assertTrue(first.clickMenuButton(a,first.buttonIdFor(2)),"creative enchant works");
        h.assertTrue(a.experienceLevel==xp && first.getSlot(1).getItem().getCount()==lapis,"creative consumes neither XP nor lapis");
        first.removed(a); second.removed(b); h.succeed();
    }
    @GameTest
    public void wholeMachineShelvesAndTierCommand(GameTestHelper h) {
        var master=machine(h); var level=h.getLevel(); var origin=master.getBlockPos();
        h.assertTrue(MachineAccess.shelves(level,master)==0,"zero bookshelves supported");
        level.setBlockAndUpdate(origin.offset(2,-1,0),Blocks.BOOKSHELF.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(3,0,0),Blocks.BOOKSHELF.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(2,0,0),Blocks.STONE.defaultBlockState());
        h.assertTrue(MachineAccess.shelves(level,master)==2,"both actual machine layers, no transmitter/air condition");
        var player=player(h,master); var menu=menu(h,master,player);
        int quote=menu.buttonIdFor(0);
        level.setBlockAndUpdate(origin.offset(2,-1,0),Blocks.AIR.defaultBlockState()); menu.broadcastChanges();
        h.assertTrue(menu.bookshelfCount()==1 && !menu.clickMenuButton(player,quote),"shelf quote invalidation");
        String command="better_enchant tier "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" 3";
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),command);
        h.assertTrue(master.machineTier()==3,"real administrator command sets tier");
        menu.removed(player); h.succeed();
    }
    @GameTest
    public void thirdPartyRegistryItemTagsAndAcquisitionRestrictions(GameTestHelper h) {
        var pool=EnchantmentCandidates.forItem(h.getLevel().registryAccess(),new ItemStack(CompatibilityTestMod.TAGGED_TOOL));
        h.assertTrue(pool.holders().containsKey("better_enchant_tests:probe"),"datapack enchantment discovered automatically");
        h.assertTrue(pool.holders().containsKey("minecraft:sharpness"),"registered mod item obtains vanilla enchantments through tags");
        for(String blocked:List.of("treasure_probe","curse_probe","excluded_probe","unsupported_probe"))
            h.assertTrue(!pool.holders().containsKey("better_enchant_tests:"+blocked),"excluded candidate "+blocked);
        var master=machine(h);var player=player(h,master);var menu=menu(h,master,player);
        menu.getSlot(0).set(new ItemStack(CompatibilityTestMod.TAGGED_TOOL));
        h.assertTrue(menu.clickMenuButton(player,menu.buttonIdFor(2)),"registered mod item can actually be enchanted");
        assertLegal(h,menu.getSlot(0).getItem()); menu.removed(player); h.succeed();
    }
}
