package dev.differentenchant.disenchant;

import java.util.List;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class DisenchantPartBlock extends BaseEntityBlock {
    public static final IntegerProperty PART=IntegerProperty.create("part",0,17);
    public static final VoxelShape CORE_SHAPE=Shapes.or(Block.box(1,0,1,15,4,15),Block.box(2,4,2,14,8,14),Block.box(3,8,3,13,10,13),Block.box(6,10,6,10,14,10));
    public DisenchantPartBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(PART,0));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(DisenchantPartBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(PART);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new DisenchantPartEntity(p,s);}
    @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){return l.isClientSide()?null:createTickerHelper(type,DisenchantContent.ENTITY,(world,p,state,e)->e.tick());}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){int i=s.getValue(PART);if(i<9)return Shapes.block();if(i==13)return CORE_SHAPE;var o=dev.differentenchant.structure.StructurePattern.CELLS.get(i).offset();return o.x()!=0&&o.z()!=0?Shapes.or(Block.box(2,0,2,14,3,14),Block.box(4,3,4,12,14,12),Block.box(2,13,2,14,16,14)):Shapes.or(Block.box(0,0,0,16,3,16),Block.box(2,3,2,14,5,14));}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit) {
        if(l instanceof ServerLevel server&&!player.isSpectator()){var master=DisenchantStructure.controller(server,p);if(master!=null)player.openMenu(new SimpleMenuProvider((id,inv,owner)->new DisenchantMenu(id,inv,server,master.getBlockPos()),Component.translatable("container.differentenchant.disenchanting")));}return InteractionResult.SUCCESS;
    }
    @Override protected List<ItemStack> getDrops(BlockState s,LootParams.Builder b){if(b.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof DisenchantPartEntity part&&part.machine!=null){b.withParameter(LootContextParams.BLOCK_STATE,part.original);return part.original.getDrops(b);}return List.of();}
}
