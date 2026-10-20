package dev.differentenchant.advancement;

import dev.differentenchant.research.ResearchContent;
import dev.differentenchant.resonance.ResonanceItems;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Native progress and persistence; callers supply only committed server events. */
public final class ModAdvancements {
    public static void committed(ServerPlayer player,String path) {
        var holder=player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("differentenchant",path));
        if(holder!=null)player.getAdvancements().award(holder,"committed");
    }
    /** Native inventory predicates also see real cursor deliveries, never menu preview slots. */
    public static void obtained(ServerPlayer player,ItemStack stack) {
        if(stack.is(ResonanceItems.ATTUNED_CORE)||stack.is(ResearchContent.LOST_SCROLL))
            CriteriaTriggers.INVENTORY_CHANGED.trigger(player,player.getInventory(),stack);
    }
    private ModAdvancements(){}
}
