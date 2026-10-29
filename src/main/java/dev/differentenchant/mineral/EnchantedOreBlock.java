package dev.differentenchant.mineral;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.storage.loot.LootParams;

public final class EnchantedOreBlock extends Block {
    private final TagKey<Enchantment> rule;
    private final java.util.function.Supplier<Item> resource;
    public EnchantedOreBlock(BlockBehaviour.Properties properties,TagKey<Enchantment> rule,java.util.function.Supplier<Item> resource){super(properties);this.rule=rule;this.resource=resource;}
    @Override protected float getDestroyProgress(BlockState state,Player player,BlockGetter level,BlockPos pos){return EnchantedHarvest.progress(player,rule);}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return List.of();}
    @Override public void playerDestroy(Level level,Player player,BlockPos pos,BlockState state,BlockEntity entity,ItemStack tool){
        super.playerDestroy(level,player,pos,state,entity,tool);
        EnchantedHarvest.drop(level,player,pos,tool,rule,resource.get());
    }
}
