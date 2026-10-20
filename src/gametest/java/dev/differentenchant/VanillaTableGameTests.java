package dev.differentenchant;

import java.util.List;
import dev.differentenchant.enchanting.*;
import dev.differentenchant.structure.StructurePattern;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/** Real server interaction entrypoint, including Fabric's production callback injection. */
public final class VanillaTableGameTests {
    private static ServerPlayer player(GameTestHelper h,BlockPos pos,GameType mode) {
        var p=h.makeMockServerPlayerInLevel();p.setGameMode(mode);p.experienceLevel=40;
        p.teleportTo(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        return p;
    }
    private static InteractionResult use(ServerPlayer p,ServerLevel level,BlockPos pos,InteractionHand hand) {
        return p.gameMode.useItemOn(p,level,p.getItemInHand(hand),hand,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false));
    }
    private static void closed(GameTestHelper h,ServerPlayer p) {h.assertTrue(p.containerMenu==p.inventoryMenu,"no standalone enchanting menu exists on server");}
    @GameTest public void ordinaryUseIsBlockedForBothHandsAndGameModes(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,3,3));
        level.setBlockAndUpdate(pos,Blocks.ENCHANTING_TABLE.defaultBlockState());
        // Native GameTest mock players fix gameMode() to CREATIVE; use a real client for spectators.
        for(var mode:List.of(GameType.SURVIVAL,GameType.CREATIVE,GameType.ADVENTURE)) {
            var p=player(h,pos,mode);
            for(int held=0;held<4;held++) for(var hand:InteractionHand.values()) {
                p.setItemInHand(InteractionHand.MAIN_HAND,(held&1)==0?ItemStack.EMPTY:new ItemStack(Items.DIAMOND_SWORD));
                p.setItemInHand(InteractionHand.OFF_HAND,(held&2)==0?ItemStack.EMPTY:new ItemStack(Items.LAPIS_LAZULI,6));
                h.assertTrue(use(p,level,pos,hand)==InteractionResult.SUCCESS,"server consumes standalone use: "+mode+" "+hand);
                closed(h,p);h.assertTrue(p.experienceLevel==40,"no vanilla enchanting charge");
                h.assertTrue(!p.inventoryMenu.clickMenuButton(p,2),"button cannot enchant without a valid enchanting menu");
            }
        }
        h.assertTrue(level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE) && level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.EnchantingTableBlockEntity,"native block and entity retained");
        h.succeed();
    }
    @GameTest public void emptyHandAssemblyAndDisassemblyRestorationStillWork(GameTestHelper h) { automaticMode(h,0); }
    private static void automaticMode(GameTestHelper h,int index){
        if(index==2){h.succeed();return;}var level=h.getLevel();var origin=h.absolutePos(new BlockPos(3,3,3));var mode=index==0?GameType.SURVIVAL:GameType.CREATIVE;
        for(var cell:StructurePattern.CELLS)level.setBlockAndUpdate(FormationService.position(origin,cell,0),FormationService.originalBlock(cell.material()).defaultBlockState());
        var missing=FormationService.position(origin,StructurePattern.CELLS.getFirst(),0);level.setBlockAndUpdate(missing,Blocks.AIR.defaultBlockState());var p=BatchTwoGameTests.player(h);p.setGameMode(mode);p.teleportTo(origin.getX()+.5,origin.getY()+1,origin.getZ()-2);
        p.setShiftKeyDown(true);use(p,level,origin,InteractionHand.MAIN_HAND);closed(h,p);h.assertTrue(level.getBlockState(origin).is(Blocks.ENCHANTING_TABLE),"incomplete table remains disabled");
        p.setShiftKeyDown(false);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOOK));p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.LAPIS_LAZULI));level.setBlockAndUpdate(missing,Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        h.runAfterDelay(2,()->{var master=MachineAccess.controller(level,origin);h.assertTrue(master!=null,"server automatically forms complete structure without empty hands or crouching: "+mode);var id=master.machineId();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.setShiftKeyDown(true);use(p,level,origin,InteractionHand.MAIN_HAND);h.assertTrue(id.equals(MachineAccess.controller(level,origin).machineId()),"legacy shift interaction is a harmless query");
            p.setShiftKeyDown(false);use(p,level,origin,InteractionHand.MAIN_HAND);h.assertTrue(p.containerMenu instanceof MachineEnchantmentMenu,"existing custom GUI opens");p.closeContainer();level.destroyBlock(missing,false);h.assertTrue(level.getBlockState(origin).is(Blocks.ENCHANTING_TABLE),"disassembly restores original controller");use(p,level,origin,InteractionHand.MAIN_HAND);closed(h,p);automaticMode(h,index+1);
        });
    }
    @GameTest public void vanillaCraftingPlacementSneakPlacementAndDropsAreUnchanged(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(3,3,3));
        var input=CraftingInput.of(3,3,List.of(ItemStack.EMPTY,new ItemStack(Items.BOOK),ItemStack.EMPTY,
                new ItemStack(Items.DIAMOND),new ItemStack(Items.OBSIDIAN),new ItemStack(Items.DIAMOND),
                new ItemStack(Items.OBSIDIAN),new ItemStack(Items.OBSIDIAN),new ItemStack(Items.OBSIDIAN)));
        var recipe=level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow();
        var output=recipe.value().assemble(input);h.assertTrue(output.is(Items.ENCHANTING_TABLE) && output.getCount()==1,"original recipe still assembles table");
        var p=player(h,origin,GameType.SURVIVAL);level.setBlockAndUpdate(origin.below(),Blocks.STONE.defaultBlockState());
        p.setItemInHand(InteractionHand.MAIN_HAND,output);
        h.assertTrue(use(p,level,origin.below(),InteractionHand.MAIN_HAND).consumesAction() && level.getBlockState(origin).is(Blocks.ENCHANTING_TABLE) && p.getMainHandItem().isEmpty(),"normal survival placement consumes one table");
        p.setShiftKeyDown(true);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.ENCHANTING_TABLE));
        var side=new BlockHitResult(origin.getCenter().add(.5,0,0),Direction.EAST,origin,false);
        h.assertTrue(p.gameMode.useItemOn(p,level,p.getMainHandItem(),InteractionHand.MAIN_HAND,side).consumesAction() && level.getBlockState(origin.east()).is(Blocks.ENCHANTING_TABLE),"sneak-held-item placement against table is preserved");closed(h,p);
        p.setShiftKeyDown(false);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));
        h.assertTrue(p.gameMode.destroyBlock(origin) && p.gameMode.destroyBlock(origin.east()),"survival pickaxe breaks native tables");
        var bounds=new AABB(origin).expandTowards(1,0,0).inflate(1);
        var drops=level.getEntitiesOfClass(ItemEntity.class,bounds);
        h.assertTrue(drops.stream().filter(e->e.getItem().is(Items.ENCHANTING_TABLE)).mapToInt(e->e.getItem().getCount()).sum()==2,"one normal drop per survival table");
        drops.forEach(ItemEntity::discard);p.setGameMode(GameType.CREATIVE);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.ENCHANTING_TABLE,2));
        use(p,level,origin.below(),InteractionHand.MAIN_HAND);
        h.assertTrue(level.getBlockState(origin).is(Blocks.ENCHANTING_TABLE) && p.getMainHandItem().getCount()==2,"creative placement retains item count");
        h.assertTrue(p.gameMode.destroyBlock(origin) && level.getEntitiesOfClass(ItemEntity.class,bounds).isEmpty(),"creative break creates no extra drops");h.succeed();
    }
    @GameTest public void otherBlockMenusRemainUsable(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,3,3));var p=player(h,pos,GameType.SURVIVAL);
        level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        for(var block:List.of(Blocks.ANVIL,Blocks.GRINDSTONE)) {
            level.setBlockAndUpdate(pos,block.defaultBlockState());
            h.assertTrue(UseBlockCallback.EVENT.invoker().interact(p,level,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false))==InteractionResult.PASS,"other workstation passes through callback");
            use(p,level,pos,InteractionHand.MAIN_HAND);
            h.assertTrue(block==Blocks.ANVIL?p.containerMenu instanceof AnvilMenu:p.containerMenu instanceof GrindstoneMenu,"real native workstation menu still opens");p.closeContainer();
        }
        h.succeed();
    }
}
