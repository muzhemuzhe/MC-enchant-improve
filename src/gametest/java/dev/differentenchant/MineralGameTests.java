package dev.differentenchant;

import dev.differentenchant.mineral.*;
import dev.differentenchant.realm.RealmContent;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.AABB;

public final class MineralGameTests {
    static ItemStack enchanted(ServerLevel l,Item item,String id,int level){var s=new ItemStack(item);s.enchant(l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(id)).orElseThrow(),level);return s;}
    static boolean resource(ItemStack s){return s.is(MineralContent.LUMINOUS_FRAGMENT)||s.is(MineralContent.RAW_SILVER)||s.is(MineralContent.RIFT_CRYSTAL);}
    static List<ItemEntity> drops(ServerLevel l,BlockPos pos){return l.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1.5),e->resource(e.getItem()));}
    static BlockPos fixture(GameTestHelper h,Block b){var p=h.absolutePos(new BlockPos(2,3,2));h.getLevel().setBlockAndUpdate(p.below(),RealmContent.CHALKSTONE.defaultBlockState());h.getLevel().setBlockAndUpdate(p,b.defaultBlockState());return p;}
    static void mine(GameTestHelper h,ServerPlayer player,Block block,ItemStack tool,Item expected){
        var pos=fixture(h,block);player.setItemSlot(EquipmentSlot.MAINHAND,tool);
        h.assertTrue(player.hasCorrectToolForDrops(block.defaultBlockState()),"no tool-class restriction, including books");
        h.assertTrue(player.gameMode.destroyBlock(pos),"native player destruction succeeds");
        var items=drops(h.getLevel(),pos);int count=items.stream().mapToInt(e->e.getItem().getCount()).sum();
        h.assertTrue(count==(expected==null?0:1),"exact one gated resource or none: "+tool+" count="+count);
        if(expected!=null)h.assertTrue(items.getFirst().getItem().is(expected),"correct basic resource, no silk block");
        player.gameMode.destroyBlock(pos);h.assertTrue(drops(h.getLevel(),pos).stream().mapToInt(e->e.getItem().getCount()).sum()==count,"second break of air cannot duplicate");
        items.forEach(Entity::discard);
    }
    @GameTest public void protectionBooksArmorAndFireOverlapActuallyMine(GameTestHelper h){
        var l=h.getLevel();var p=BatchTwoGameTests.player(h);
        for(String id:List.of("protection","fire_protection","blast_protection","projectile_protection","feather_falling"))
            mine(h,p,MineralContent.LUMINOUS_STONE,enchanted(l,Items.ENCHANTED_BOOK,"minecraft:"+id,id.equals("feather_falling")?4:1),MineralContent.LUMINOUS_FRAGMENT);
        var armor=enchanted(l,Items.DIAMOND_CHESTPLATE,"minecraft:fire_protection",1);
        mine(h,p,MineralContent.LUMINOUS_STONE,armor.copy(),MineralContent.LUMINOUS_FRAGMENT);
        mine(h,p,MineralContent.HARMONIC_SILVER,armor.copy(),MineralContent.RAW_SILVER);
        mine(h,p,MineralContent.HARMONIC_SILVER,enchanted(l,Items.ENCHANTED_BOOK,"minecraft:flame",1),MineralContent.RAW_SILVER);
        mine(h,p,MineralContent.HARMONIC_SILVER,enchanted(l,Items.DIAMOND_SWORD,"minecraft:fire_aspect",2),MineralContent.RAW_SILVER);h.succeed();
    }
    @GameTest public void everyRegisteredLostEnchantmentQualifiesWithoutNameList(GameTestHelper h){
        var p=BatchTwoGameTests.player(h);int found=0;
        for(var holder:h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().toList())if(holder.is(EnchantmentGate.LOST)){
            var book=new ItemStack(Items.ENCHANTED_BOOK);book.enchant(holder,1);mine(h,p,MineralContent.RIFT_CLUSTER,book,MineralContent.RIFT_CRYSTAL);found++;
        }
        h.assertTrue(found>=17,"entire current registry tag enumerated, not a hard-coded name set");
        for(String id:List.of("mending","soul_speed","swift_sneak","protection","flame","sharpness"))mine(h,p,MineralContent.RIFT_CLUSTER,enchanted(h.getLevel(),Items.ENCHANTED_BOOK,"minecraft:"+id,1),null);
        h.succeed();
    }
    @GameTest public void bothComponentsModItemsTagExtensionAndNoCosmeticBypass(GameTestHelper h){
        var l=h.getLevel();var p=BatchTwoGameTests.player(h);
        var custom=enchanted(l,CompatibilityTestMod.TAGGED_TOOL,"minecraft:protection",1);mine(h,p,MineralContent.LUMINOUS_STONE,custom,MineralContent.LUMINOUS_FRAGMENT);
        var stored=new ItemStack(CompatibilityTestMod.PREVIEW_TOOL);stored.set(DataComponents.STORED_ENCHANTMENTS,enchanted(l,Items.ENCHANTED_BOOK,"minecraft:flame",1).get(DataComponents.STORED_ENCHANTMENTS));
        mine(h,p,MineralContent.HARMONIC_SILVER,stored,MineralContent.RAW_SILVER);
        mine(h,p,MineralContent.HARMONIC_SILVER,enchanted(l,CompatibilityTestMod.TAGGED_TOOL,"differentenchant_tests:probe",1),MineralContent.RAW_SILVER);
        var fake=new ItemStack(Items.NETHERITE_PICKAXE);fake.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Protection I"));fake.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
        mine(h,p,MineralContent.LUMINOUS_STONE,fake,null);
        p.setItemSlot(EquipmentSlot.CHEST,enchanted(l,Items.DIAMOND_CHESTPLATE,"minecraft:protection",4));p.setItemSlot(EquipmentSlot.OFFHAND,enchanted(l,Items.ENCHANTED_BOOK,"minecraft:protection",1));
        mine(h,p,MineralContent.LUMINOUS_STONE,new ItemStack(Items.DIAMOND_PICKAXE),null);
        mine(h,p,MineralContent.LUMINOUS_STONE,ItemStack.EMPTY,null);h.succeed();
    }
    @GameTest public void uniformTimingIgnoresMaterialAndSilkFortuneCannotBypass(GameTestHelper h){
        var l=h.getLevel();var p=BatchTwoGameTests.player(h);var pos=fixture(h,MineralContent.LUMINOUS_STONE);
        for(Item item:List.of(Items.ENCHANTED_BOOK,Items.WOODEN_PICKAXE,Items.NETHERITE_PICKAXE,Items.DIAMOND_CHESTPLATE,CompatibilityTestMod.TAGGED_TOOL)){
            p.setItemSlot(EquipmentSlot.MAINHAND,enchanted(l,item,"minecraft:protection",1));h.assertTrue(Math.abs(l.getBlockState(pos).getDestroyProgress(p,l,pos)-1f/30)<.000001,"all qualifying item classes mine in 30 ticks");
        }
        for(Item tool:List.of(Items.DIAMOND_PICKAXE,Items.NETHERITE_PICKAXE))for(String id:List.of("silk_touch","fortune"))
            mine(h,p,MineralContent.LUMINOUS_STONE,enchanted(l,tool,"minecraft:"+id,id.equals("fortune")?3:1),null);
        var qualified=enchanted(l,Items.NETHERITE_PICKAXE,"minecraft:protection",1);qualified.enchant(l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.withDefaultNamespace("fortune")).orElseThrow(),3);
        mine(h,p,MineralContent.LUMINOUS_STONE,qualified,MineralContent.LUMINOUS_FRAGMENT);h.succeed();
    }
    @GameTest public void commitUsesActualServerHandAndSurvivesToolBreaking(GameTestHelper h){
        var l=h.getLevel();var p=BatchTwoGameTests.player(h);var pos=fixture(h,MineralContent.HARMONIC_SILVER);
        p.setItemSlot(EquipmentSlot.MAINHAND,enchanted(l,Items.DIAMOND_SWORD,"minecraft:fire_aspect",1));h.assertTrue(l.getBlockState(pos).getDestroyProgress(p,l,pos)>1f/40,"qualified at start");
        p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.NETHERITE_PICKAXE));mine(h,p,MineralContent.HARMONIC_SILVER,p.getMainHandItem(),null);
        var breaking=enchanted(l,Items.WOODEN_PICKAXE,"minecraft:fire_aspect",1);breaking.setDamageValue(breaking.getMaxDamage()-1);
        mine(h,p,MineralContent.HARMONIC_SILVER,breaking,MineralContent.RAW_SILVER);
        h.assertTrue(p.getMainHandItem().isEmpty(),"native tool broke, captured actual tool still authorizes exactly once");
        p.setGameMode(GameType.CREATIVE);mine(h,p,MineralContent.LUMINOUS_STONE,enchanted(l,Items.ENCHANTED_BOOK,"minecraft:protection",1),null);h.succeed();
    }
    @GameTest public void allSixSupportsDetachWithoutCrystalDrops(GameTestHelper h){
        var l=h.getLevel();var center=h.absolutePos(new BlockPos(3,3,3));l.setBlockAndUpdate(center,RealmContent.CHALKSTONE.defaultBlockState());
        for(var d:Direction.values()){var at=center.relative(d);var state=MineralContent.RIFT_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING,d);h.assertTrue(state.canSurvive(l,at),"six-sided solid support "+d);l.setBlockAndUpdate(at,state);}
        l.destroyBlock(center,true);
        for(var d:Direction.values())h.assertTrue(l.getBlockState(center.relative(d)).isAir(),"support loss removes cluster "+d);
        h.assertTrue(drops(l,center).isEmpty(),"no crystal from any lost support");h.succeed();
    }
    @GameTest public void genericLootAndActualExplosionNeverAwardEvenWithQualifiedActor(GameTestHelper h){
        var l=h.getLevel();var p=BatchTwoGameTests.player(h);p.setItemSlot(EquipmentSlot.MAINHAND,enchanted(l,Items.ENCHANTED_BOOK,"minecraft:protection",1));var pos=fixture(h,MineralContent.LUMINOUS_STONE);
        Block.dropResources(l.getBlockState(pos),l,pos,null,p,p.getMainHandItem());h.assertTrue(drops(l,pos).isEmpty(),"even player-shaped generic loot context is not an authorized break");
        l.explode(p,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,4f,Level.ExplosionInteraction.TNT);
        h.assertTrue(l.getBlockState(pos).isAir()&&drops(l,pos).isEmpty(),"actual player-caused explosion removes ore, no resource");h.succeed();
    }
    @GameTest public void pistonDestroysClusterButCannotMoveOres(GameTestHelper h){
        var l=h.getLevel();var pos=h.absolutePos(new BlockPos(2,3,3));l.setBlockAndUpdate(pos.below(),RealmContent.CHALKSTONE.defaultBlockState());l.setBlockAndUpdate(pos,MineralContent.RIFT_CLUSTER.defaultBlockState());
        l.setBlockAndUpdate(pos.west(),Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.EAST));l.setBlockAndUpdate(pos.west().north(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        h.runAfterDelay(4,()->{h.assertTrue(!l.getBlockState(pos).is(MineralContent.RIFT_CLUSTER)&&drops(l,pos).isEmpty(),"actual powered piston breaks cluster without a crystal");
            h.assertTrue(MineralContent.LUMINOUS_STONE.defaultBlockState().getPistonPushReaction()==net.minecraft.world.level.material.PushReaction.BLOCK&&MineralContent.HARMONIC_SILVER.defaultBlockState().getPistonPushReaction()==net.minecraft.world.level.material.PushReaction.BLOCK,"ore cannot be piston-moved");h.succeed();});
    }
    @GameTest public void fluidRemovalNoLootAndWorldgenRejectsOtherDimensions(GameTestHelper h){
        var l=h.getLevel();var pos=fixture(h,MineralContent.RIFT_CLUSTER);l.setBlockAndUpdate(pos,l.getBlockState(pos).setValue(AmethystClusterBlock.WATERLOGGED,true));
        h.assertTrue(l.getFluidState(pos).is(net.minecraft.world.level.material.Fluids.WATER),"native waterlogged cluster");l.destroyBlock(pos,true);h.assertTrue(drops(l,pos).isEmpty(),"environment removal emits no crystal");
        for(String name:List.of("luminous_stone","harmonic_silver_ore","rift_crystals")){
            var feature=l.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(RealmContent.id(name)).orElseThrow().value();
            h.assertTrue(!feature.place(l,l.getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(3),pos),"dimension guard rejects non-Realm "+name);
        }h.succeed();
    }
}
