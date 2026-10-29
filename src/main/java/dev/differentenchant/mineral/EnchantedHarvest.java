package dev.differentenchant.mineral;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Shared commit boundary. Generic loot is always empty; only playerDestroy awards. */
public final class EnchantedHarvest {
    public static float progress(Player player,TagKey<Enchantment> rule){return 1f/(EnchantmentGate.allows(player.getMainHandItem(),rule)?30:90);}
    public static void drop(Level level,Player player,BlockPos pos,ItemStack serverToolSnapshot,TagKey<Enchantment> rule,Item resource){
        if(player instanceof ServerPlayer&&!player.preventsBlockDrops()&&!player.isSpectator()&&EnchantmentGate.allows(serverToolSnapshot,rule))
            Block.popResource(level,pos,new ItemStack(resource));
    }
    private EnchantedHarvest(){}
}
