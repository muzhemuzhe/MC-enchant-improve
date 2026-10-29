package dev.differentenchant.mineral;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.storage.loot.LootParams;

/** Native six-way attachment/waterlogging; original geometry and exclusive harvest. */
public final class RiftClusterBlock extends AmethystClusterBlock {
    public static final MapCodec<AmethystClusterBlock> CODEC=simpleCodec(RiftClusterBlock::new);
    public RiftClusterBlock(BlockBehaviour.Properties properties){super(12,2,properties);}
    @Override public MapCodec<AmethystClusterBlock> codec(){return CODEC;}
    @Override protected float getDestroyProgress(BlockState state,Player player,BlockGetter level,BlockPos pos){return EnchantedHarvest.progress(player,EnchantmentGate.LOST);}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return List.of();}
    @Override public void playerDestroy(Level level,Player player,BlockPos pos,BlockState state,BlockEntity entity,ItemStack tool){
        super.playerDestroy(level,player,pos,state,entity,tool);
        EnchantedHarvest.drop(level,player,pos,tool,EnchantmentGate.LOST,MineralContent.RIFT_CRYSTAL);
    }
}
