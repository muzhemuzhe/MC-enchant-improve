package dev.differentenchant;

import java.util.*;
import dev.differentenchant.enchanting.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class EnchantingPreviewGameTests {
    @GameTest public void snapshotCodecAndClientContextGate(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h);var player=EnchantingMenuGameTests.player(h,master);
        var serverMenu=EnchantingMenuGameTests.menu(h,master,player);
        var snapshot=serverMenu.previewSnapshot();
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            EnchantingPreviewPayload.CODEC.encode(buffer,snapshot);
            var decoded=EnchantingPreviewPayload.CODEC.decode(buffer);
            h.assertTrue(snapshot.offers().equals(decoded.offers()) && ItemStack.matches(snapshot.input(),decoded.input()),"actual registry item codec round trip");
            var client=new MachineEnchantmentMenu(snapshot.containerId(),player.getInventory());
            client.getSlot(0).set(snapshot.input().copy());client.acceptPreview(decoded);
            h.assertTrue(client.preview()==null,"do not display before matching data-slot quote");
            int quote=snapshot.quote()>>>2;
            client.setData(12,quote & 0xFFFF);client.setData(13,quote>>>16);
            h.assertTrue(client.preview()!=null,"matching version and input displays preview");
            client.acceptPreview(new EnchantingPreviewPayload(snapshot.containerId()+1,snapshot.quote(),snapshot.input(),snapshot.offers()));
            h.assertTrue(client.preview()!=null,"unrelated container payload ignored");
            client.getSlot(0).set(new ItemStack(Items.BOOK));
            h.assertTrue(client.preview()==null,"replacement input immediately hides stale preview");
            client.getSlot(0).set(snapshot.input().copy());client.setData(12,(quote+1)&0xFFFF);
            h.assertTrue(client.preview()==null,"new quote hides stale preview even with same input");
            // The protocol and presentation policy are independent of the current four-result default.
            var many=java.util.stream.IntStream.range(0,50).mapToObj(i->snapshot.offers().getFirst().revealed().getFirst()).toList();
            var large=new EnchantingPreviewPayload(10,0,snapshot.input(),Collections.nCopies(3,new EnchantingPreviewPayload.Offer(many,false)));
            buffer.clear();EnchantingPreviewPayload.CODEC.encode(buffer,large);
            h.assertTrue(EnchantingPreviewPayload.CODEC.decode(buffer).offers().getFirst().revealed().size()==50,"not limited to three results");
        } finally { buffer.release(); }
        serverMenu.removed(player);h.succeed();
    }
    @GameTest public void revealedPrefixesMatchEveryCommittedResultAndReopen(GameTestHelper h) {
        var master=EnchantingMenuGameTests.machine(h); var player=EnchantingMenuGameTests.player(h,master);
        for(int tier=1;tier<=5;tier++) for(int option=0;option<3;option++) {
            master.setMachineTier(tier); player.experienceLevel=40;
            var menu=EnchantingMenuGameTests.menu(h,master,player);
            var before=menu.previewSnapshot();
            // Removing and reinserting the very same item does not change revealed order or contents.
            menu.getSlot(0).set(ItemStack.EMPTY); menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
            h.assertTrue(before.offers().equals(menu.previewSnapshot().offers()),"reinsert stable tier "+tier);
            menu.removed(player);
            menu=EnchantingMenuGameTests.menu(h,master,player);
            h.assertTrue(before.offers().equals(menu.previewSnapshot().offers()),"reopen stable tier "+tier);
            var visible=menu.previewSnapshot().offers().get(option);
            h.assertTrue(menu.clickMenuButton(player,menu.buttonIdFor(option)),"commit tier/row "+tier+"/"+option);
            var actual=EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem());
            h.assertTrue(visible.revealed().size()==PreviewPolicy.revealedCount(tier,actual.size()),"precise reveal count");
            h.assertTrue(visible.hidden()==(visible.revealed().size()<actual.size()),"only real hidden entries");
            for(var entry:visible.revealed()) h.assertTrue(actual.getLevel(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(entry.id()).orElseThrow())==entry.level(),"revealed entry matches actual output");
            if(tier==5) h.assertTrue(!visible.hidden() && actual.size()==visible.revealed().size(),"all and only actual results");
            h.assertTrue(menu.previewSnapshot().offers().stream().allMatch(o->o.revealed().isEmpty() && !o.hidden()),"enchanted output has no new random offers");
            menu.removed(player);
        }
        h.succeed();
    }
}
