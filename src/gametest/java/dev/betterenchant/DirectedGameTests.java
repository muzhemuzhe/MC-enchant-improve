package dev.betterenchant;

import java.util.*;
import dev.betterenchant.enchanting.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.network.chat.Component;

public final class DirectedGameTests {
    static final String SHARP="minecraft:sharpness",UNBREAK="minecraft:unbreaking",SMITE="minecraft:smite";
    static boolean action(MachineEnchantmentMenu menu,ServerPlayer player,int action,String id) {
        return menu.handleDirected(player,new DirectedActionPayload(menu.containerId,menu.directedSnapshot().revision(),action,id));
    }
    static MachineEnchantmentMenu directed(GameTestHelper h,MachineControllerBlockEntity master,ServerPlayer player,int count) {
        var menu=EnchantingMenuGameTests.menu(h,master,player);menu.getSlot(1).set(new ItemStack(BetterEnchant.RUNIC_STONE,count));
        h.assertTrue(action(menu,player,DirectedActionPayload.DIRECTED,""),"switch to directed");return menu;
    }
    static DirectedPreviewPayload.Row row(MachineEnchantmentMenu menu,String id) {
        return menu.directedSnapshot().rows().stream().filter(r->r.id().toString().equals(id)).findFirst().orElseThrow();
    }
    static net.minecraft.core.Holder.Reference<Enchantment> holder(GameTestHelper h,String id) {return h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(id)).orElseThrow();}
    @GameTest public void oneAndFiveStonesAllocationCancellationAndConflicts(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var player=EnchantingMenuGameTests.player(h,master);var menu=directed(h,master,player,1);
        var addition=new DirectedActionPayload(menu.containerId,menu.directedSnapshot().revision(),DirectedActionPayload.ADD,SHARP);
        h.assertTrue(menu.handleDirected(player,addition),"one stone one upgrade");
        h.assertTrue(!menu.handleDirected(player,addition) && menu.directedSnapshot().upgrades()==1,"replayed addition does not allocate again");
        h.assertTrue(!action(menu,player,DirectedActionPayload.ADD,UNBREAK),"cannot allocate second stone");
        h.assertTrue(menu.directedSnapshot().upgrades()==1 && !row(menu,UNBREAK).plus(),"budget zero disables all other additions");
        menu.getSlot(1).set(new ItemStack(BetterEnchant.RUNIC_STONE,5));
        h.assertTrue(action(menu,player,DirectedActionPayload.ADD,SHARP) && action(menu,player,DirectedActionPayload.ADD,SHARP),"successive new I II III");
        h.assertTrue(action(menu,player,DirectedActionPayload.ADD,UNBREAK) && action(menu,player,DirectedActionPayload.ADD,UNBREAK),"multiple compatible kinds");
        h.assertTrue(menu.directedSnapshot().upgrades()==5 && row(menu,SHARP).target()==3 && row(menu,UNBREAK).target()==2,"five allocated upgrades");
        h.assertTrue(!action(menu,player,DirectedActionPayload.ADD,SHARP),"sixth upgrade rejected");
        h.assertTrue(action(menu,player,DirectedActionPayload.REMOVE,UNBREAK) && row(menu,SHARP).plus(),"minus restores budget");
        h.assertTrue(!action(menu,player,DirectedActionPayload.ADD,SMITE),"planned incompatibility");
        h.assertTrue(action(menu,player,DirectedActionPayload.ADD,SHARP),"reallocate restored capacity");
        h.assertTrue(!menu.getSlot(0).getItem().isEnchanted() && player.experienceLevel==40 && menu.getSlot(1).getItem().getCount()==5,"planning never mutates or charges");
        menu.removed(player);h.succeed();
    }
    @GameTest public void batchIIIToVPreservesComponentsAndPaysOnceBelowThreshold(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var player=EnchantingMenuGameTests.player(h,master);var menu=directed(h,master,player,2);
        var input=new ItemStack(Items.DIAMOND_SWORD);input.enchant(holder(h,SHARP),3);input.enchant(holder(h,UNBREAK),2);
        input.set(DataComponents.CUSTOM_NAME,Component.literal("Keep this component"));input.setDamageValue(7);
        var tag=new net.minecraft.nbt.CompoundTag();tag.putString("thirdparty_marker","preserved");
        input.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));menu.getSlot(0).set(input);
        player.experienceLevel=30;int seed=player.getEnchantmentSeed();
        h.assertTrue(action(menu,player,DirectedActionPayload.ADD,SHARP) && action(menu,player,DirectedActionPayload.ADD,SHARP),"III to IV to V");
        h.assertTrue(!row(menu,SHARP).plus() && !action(menu,player,DirectedActionPayload.ADD,SHARP),"normal maximum cannot be exceeded");
        var quote=menu.directedSnapshot();int cost=quote.levels();
        h.assertTrue(cost>=10 && cost<=16 && quote.canEnchant(),"30 is threshold, not fee");
        var request=new DirectedActionPayload(menu.containerId,quote.revision(),DirectedActionPayload.ENCHANT,"");
        h.assertTrue(menu.handleDirected(player,request),"atomic commit");var output=menu.getSlot(0).getItem();
        var enchantments=EnchantmentHelper.getEnchantmentsForCrafting(output);
        h.assertTrue(enchantments.getLevel(holder(h,SHARP))==5 && enchantments.getLevel(holder(h,UNBREAK))==2,"upgraded selected and retained other enchantment");
        h.assertTrue(output.getDamageValue()==7 && output.get(DataComponents.CUSTOM_NAME).equals(input.get(DataComponents.CUSTOM_NAME)) && output.get(DataComponents.CUSTOM_DATA).equals(input.get(DataComponents.CUSTOM_DATA)),"unrelated components unchanged");
        h.assertTrue(player.experienceLevel==30-cost && menu.getSlot(1).getItem().isEmpty() && player.getEnchantmentSeed()==seed,"exact costs without rerolling random seed");
        h.assertTrue(!menu.handleDirected(player,request) && player.experienceLevel==30-cost,"replay no second commit");
        menu.removed(player);h.succeed();
    }
    @GameTest public void thresholdsResourceChangesAndStaleRequestsAreAtomic(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var player=EnchantingMenuGameTests.player(h,master);var menu=directed(h,master,player,5);
        for(int i=0;i<5;i++) h.assertTrue(action(menu,player,DirectedActionPayload.ADD,SHARP),"five legal levels");
        player.experienceLevel=30;
        h.assertTrue(menu.directedSnapshot().levels()>30 && !action(menu,player,DirectedActionPayload.ENCHANT,""),"threshold alone does not pay a large batch");
        h.assertTrue(player.experienceLevel==30 && menu.getSlot(1).getItem().getCount()==5 && !menu.getSlot(0).getItem().isEnchanted(),"failure changes nothing");
        var stale=new DirectedActionPayload(menu.containerId,menu.directedSnapshot().revision(),DirectedActionPayload.ENCHANT,"");
        menu.getSlot(1).set(new ItemStack(BetterEnchant.RUNIC_STONE,1));
        h.assertTrue(menu.directedSnapshot().upgrades()==1 && row(menu,SHARP).target()==1,"reduced count removes newest excess allocations");
        h.assertTrue(!menu.handleDirected(player,stale),"resource change rejects stale commit");player.experienceLevel=29;
        h.assertTrue(!action(menu,player,DirectedActionPayload.ENCHANT,""),"below30 rejected even if actual fee affordable");
        player.experienceLevel=40;menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,64));
        h.assertTrue(menu.directedSnapshot().upgrades()==0 && !action(menu,player,DirectedActionPayload.ADD,SHARP),"lapis is not a stone");
        menu.getSlot(1).set(new ItemStack(BetterEnchant.RUNIC_STONE,1));menu.getSlot(0).set(new ItemStack(Items.DIRT));
        h.assertTrue(!action(menu,player,DirectedActionPayload.ADD,SHARP) && !action(menu,player,DirectedActionPayload.ENCHANT,""),"unsupported item and fabricated operation rejected");
        h.assertTrue(player.experienceLevel==40 && menu.getSlot(1).getItem().getCount()==1,"no resources taken by failures");
        menu.removed(player);h.succeed();
    }
    @GameTest public void identityModesTierReloadClosingAndPlayersInvalidatePlans(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var a=EnchantingMenuGameTests.player(h,master);var b=EnchantingMenuGameTests.player(h,master);
        var menu=directed(h,master,a,5);var other=directed(h,master,b,1);
        action(menu,a,DirectedActionPayload.ADD,SHARP);
        var request=new DirectedActionPayload(menu.containerId,menu.directedSnapshot().revision(),DirectedActionPayload.ENCHANT,"");
        h.assertTrue(!menu.handleDirected(b,request) && other.directedSnapshot().upgrades()==0,"separate owner and plan");
        int low=menu.directedSnapshot().levels();master.setMachineTier(5);int high=menu.directedSnapshot().levels();
        h.assertTrue(high<=low && !menu.handleDirected(a,request),"tier recosts and invalidates previous revision");
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
        h.assertTrue(menu.directedSnapshot().upgrades()==0,"identical replacement item has different slot identity");
        action(menu,a,DirectedActionPayload.ADD,SHARP);EnchantmentCandidates.invalidate();
        h.assertTrue(menu.directedSnapshot().upgrades()==0,"reload clears invalidated planning context");
        action(menu,a,DirectedActionPayload.ADD,SHARP);request=new DirectedActionPayload(menu.containerId,menu.directedSnapshot().revision(),DirectedActionPayload.ENCHANT,"");
        h.assertTrue(action(menu,a,DirectedActionPayload.RANDOM,"") && !menu.handleDirected(a,request),"mode switch rejects pending directed commit");
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,3));
        h.assertTrue(menu.clickMenuButton(a,menu.buttonIdFor(0)),"random mode still works after directed planning");
        h.assertTrue(action(menu,a,DirectedActionPayload.DIRECTED,"") && !menu.clickMenuButton(a,menu.buttonIdFor(1)),"random packets cannot commit in directed mode");
        int before=a.getInventory().countItem(Items.DIAMOND_SWORD);menu.removed(a);menu.removed(a);
        h.assertTrue(a.getInventory().countItem(Items.DIAMOND_SWORD)==before+1 && !menu.handleDirected(a,request),"closing returns one item and invalidates all requests");
        var reopened=directed(h,master,a,1);h.assertTrue(reopened.directedSnapshot().upgrades()==0 && !reopened.handleDirected(a,request),"reused menu id has a fresh session revision");
        h.getLevel().destroyBlock(master.getBlockPos(),false);
        h.assertTrue(!action(reopened,a,DirectedActionPayload.ENCHANT,""),"broken machine cannot commit");
        reopened.removed(a);other.removed(b);h.succeed();
    }
    @GameTest public void nonTableEquipmentAndCreativeStillPayAndIllegalBaseRejected(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var player=EnchantingMenuGameTests.player(h,master);var menu=directed(h,master,player,2);
        menu.getSlot(0).set(new ItemStack(Items.SHIELD));
        h.assertTrue(!menu.getSlot(0).getItem().isEnchantable() && row(menu,UNBREAK).plus(),"shield supported by directed without random ENCHANTABLE eligibility");
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.experienceLevel=30;
        h.assertTrue(action(menu,player,DirectedActionPayload.ADD,UNBREAK),"shield plan");int cost=menu.directedSnapshot().levels();
        h.assertTrue(action(menu,player,DirectedActionPayload.ENCHANT,"") && player.experienceLevel==30-cost && menu.getSlot(1).getItem().getCount()==1,"even creative spends one stone and actual levels");
        var invalid=new ItemStack(Items.DIAMOND_SWORD);invalid.enchant(holder(h,SHARP),1);invalid.enchant(holder(h,SMITE),1);menu.getSlot(0).set(invalid);
        h.assertTrue(!action(menu,player,DirectedActionPayload.ADD,UNBREAK) && !menu.directedSnapshot().canEnchant(),"pre-existing illegal combination cannot be used to bypass compatibility");
        var removed=new ItemStack(Items.DIAMOND_SWORD);removed.remove(DataComponents.ENCHANTMENTS);menu.getSlot(0).set(removed);
        h.assertTrue(menu.directedSnapshot().rows().isEmpty(),"honor a removed enchantment storage component");
        menu.removed(player);h.succeed();
    }
    @GameTest public void fullRegistryThirdPartyTagsAndExistingCursesArePreserved(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var player=EnchantingMenuGameTests.player(h,master);var menu=directed(h,master,player,2);
        var input=new ItemStack(CompatibilityTestMod.TAGGED_TOOL);input.enchant(holder(h,"better_enchant_tests:curse_probe"),1);menu.getSlot(0).set(input);
        var ids=menu.directedSnapshot().rows().stream().map(r->r.id().toString()).toList();
        h.assertTrue(ids.contains("better_enchant_tests:directed_probe") && ids.contains("better_enchant_tests:probe"),"all supported registry entries, including non-random acquisition");
        for(String blocked:List.of("treasure_probe","curse_probe","unsupported_probe","excluded_probe")) h.assertTrue(!ids.contains("better_enchant_tests:"+blocked),"directed exclusions "+blocked);
        h.assertTrue(action(menu,player,DirectedActionPayload.ADD,"better_enchant_tests:probe"),"test mod enchantment planned");
        h.assertTrue(!action(menu,player,DirectedActionPayload.ADD,"better_enchant_tests:directed_probe"),"modded exclusive_set respected");
        h.assertTrue(action(menu,player,DirectedActionPayload.REMOVE,"better_enchant_tests:probe") && action(menu,player,DirectedActionPayload.ADD,"better_enchant_tests:directed_probe"),"cancel permits alternative");
        h.assertTrue(action(menu,player,DirectedActionPayload.ENCHANT,""),"actual third party item commit");
        var result=EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem());
        h.assertTrue(result.getLevel(holder(h,"better_enchant_tests:directed_probe"))==1 && result.getLevel(holder(h,"better_enchant_tests:curse_probe"))==1,"new begins at I, existing curse retained");
        // Registry-aware snapshot and action codec round trips.
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {var snapshot=menu.directedSnapshot();DirectedPreviewPayload.CODEC.encode(buffer,snapshot);var decoded=DirectedPreviewPayload.CODEC.decode(buffer);
            h.assertTrue(decoded.rows().equals(snapshot.rows()) && decoded.revision()==snapshot.revision() && ItemStack.matches(decoded.input(),snapshot.input()),"snapshot round trip");
            var action=new DirectedActionPayload(menu.containerId,snapshot.revision(),DirectedActionPayload.ADD,"thirdparty:unknown");
            buffer.clear();DirectedActionPayload.CODEC.encode(buffer,action);h.assertTrue(action.equals(DirectedActionPayload.CODEC.decode(buffer)),"action round trip");
            for(int bad:new int[]{-1,5,Integer.MAX_VALUE}) h.assertTrue(!action(menu,player,bad,"thirdparty:unknown"),"invalid action refused");
        } finally {buffer.release();}
        menu.removed(player);h.succeed();
    }
}
