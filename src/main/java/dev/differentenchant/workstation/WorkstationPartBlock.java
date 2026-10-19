package dev.differentenchant.workstation;

import java.util.List;
import dev.differentenchant.disenchant.*;
import dev.differentenchant.research.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public abstract class WorkstationPartBlock extends BaseEntityBlock {
    public static final IntegerProperty PART=IntegerProperty.create("part",0,17);
    public static final VoxelShape CORE_SHAPE=Shapes.or(Block.box(1,0,1,15,4,15),Block.box(2,4,2,14,8,14),Block.box(3,8,3,13,10,13),Block.box(6,10,6,10,14,10));
    private final WorkstationStructure.Kind kind;
    protected WorkstationPartBlock(WorkstationStructure.Kind kind,Properties p){super(p);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(PART,0).setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH).setValue(BlockStateProperties.WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(PART,BlockStateProperties.HORIZONTAL_FACING,BlockStateProperties.WATERLOGGED);}
    @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){return kind==WorkstationStructure.Kind.DISENCHANT?createTickerHelper(type,DisenchantContent.ENTITY,(world,p,state,e)->e.tick()):createTickerHelper(type,ResearchContent.PART_ENTITY,(world,p,state,e)->e.tick());}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) {
        int i=s.getValue(PART);
        if(kind==WorkstationStructure.Kind.DISENCHANT&&WorkstationStructure.corner(i))
            return Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(BlockStateProperties.FACING,Direction.UP).getShape(l,p,c);
        return WorkstationVisualShapes.get(kind,i,s.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }
    @Override protected FluidState getFluidState(BlockState s){return s.getValue(BlockStateProperties.WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(s);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit) {
        if(l instanceof ServerLevel server&&!player.isSpectator()){var master=WorkstationStructure.controller(server,p,kind);if(master!=null)player.openMenu(new SimpleMenuProvider((id,inv,owner)->kind==WorkstationStructure.Kind.DISENCHANT?new DisenchantMenu(id,inv,server,master.getBlockPos()):new ResearchMenu(id,inv,server,master.getBlockPos()),Component.translatable(kind==WorkstationStructure.Kind.DISENCHANT?"container.differentenchant.disenchanting":"block.differentenchant.lost_research_table")));}return InteractionResult.SUCCESS;
    }
    @Override protected List<ItemStack> getDrops(BlockState s,LootParams.Builder b) {
        if(b.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof WorkstationPartEntity part&&part.machine!=null) {
            var entity=b.getOptionalParameter(LootContextParams.THIS_ENTITY);
            if(entity instanceof Player player&&b.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS)==null&&!player.hasCorrectToolForDrops(part.original))return List.of();
            b.withParameter(LootContextParams.BLOCK_STATE,part.original);
            if(part.originalEntity!=null){var old=BlockEntity.loadStatic(part.getBlockPos(),part.original,part.originalEntity,b.getLevel().registryAccess());if(old!=null)b.withParameter(LootContextParams.BLOCK_ENTITY,old);}
            return part.original.getDrops(b);
        }return List.of();
    }
}
