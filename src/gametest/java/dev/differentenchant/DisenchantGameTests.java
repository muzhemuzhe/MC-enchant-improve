package dev.differentenchant;

import dev.differentenchant.disenchant.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

public final class DisenchantGameTests {
    static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    static ItemStack add(ServerLevel l,ItemStack s,String id,int n){s.enchant(l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(id)).orElseThrow(),n);return s;}
    static ItemStack fixture(ServerLevel l){var s=add(l,new ItemStack(Items.DIAMOND_SWORD),"minecraft:sharpness",5);add(l,s,"minecraft:unbreaking",3);add(l,s,"minecraft:mending",1);add(l,s,"minecraft:vanishing_curse",1);s.setDamageValue(137);s.set(DataComponents.CUSTOM_NAME,Component.literal("Echo Archive"));s.set(DataComponents.REPAIR_COST,19);var foreign=new net.minecraft.nbt.CompoundTag();foreign.putString("third_party_note","Keep this foreign value");foreign.putInt("counter",42);s.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(foreign));s.set(DataComponents.LORE,new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("Research archive specimen"))));return s;}
    static BlockPos build(ServerLevel l,BlockPos core){for(int i=0;i<18;i++)l.setBlockAndUpdate(DisenchantStructure.position(core,i),DisenchantStructure.original(i).defaultBlockState());return core;}
    static DisenchantMenu menu(GameTestHelper h,ServerPlayer p){var l=h.getLevel();var pos=build(l,h.absolutePos(new BlockPos(3,3,3)));check(DisenchantStructure.form(l,pos),"formed fixture");p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()-3);var m=new DisenchantMenu(83,p.getInventory(),l,pos);p.containerMenu=m;p.experienceLevel=40;return m;}
    static void fill(DisenchantMenu m,ItemStack gear,int books){m.getSlot(0).set(gear);m.getSlot(1).set(new ItemStack(Items.BOOK,books));m.broadcastChanges();}
    static void select(DisenchantMenu m,ServerPlayer p,String id){var choices=m.choices();for(int i=0;i<choices.size();i++)if(choices.get(i).id().equals(id)){check(m.clickMenuButton(p,i),"server selection");return;}throw new AssertionError("No choice "+id);}
    static int rank(ItemStack s,String id){return EnchantmentHelper.getEnchantmentsForCrafting(s).entrySet().stream().filter(e->e.getKey().unwrapKey().orElseThrow().identifier().toString().equals(id)).mapToInt(e->e.getIntValue()).findFirst().orElse(0);}
    @GameTest public void servicePreservesComponentsAndRejectsAllCurseSources(GameTestHelper h) {
        var l=h.getLevel();var gear=fixture(l);var before=gear.copy();var choices=DisenchantingService.choices(gear);
        h.assertTrue(choices.size()==3&&choices.stream().noneMatch(c->c.id().contains("curse")),"only three normal choices, curse hidden");
        var result=DisenchantingService.prepare(gear,new ItemStack(Items.BOOK),"minecraft:sharpness");var expected=gear.copy();EnchantmentHelper.updateEnchantments(expected,m->m.set(l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.withDefaultNamespace("sharpness")).orElseThrow(),0));
        h.assertTrue(ItemStack.matches(before,gear)&&ItemStack.matches(expected,result.equipment()),"preparation does not mutate source; all components except selected enchantment identical");
        h.assertTrue(rank(result.book(),"minecraft:sharpness")==4&&result.cost()==6,"V becomes IV, costs six levels");
        h.assertTrue(DisenchantingService.prepare(gear,new ItemStack(Items.BOOK),"minecraft:vanishing_curse")==null,"vanilla curse server preparation denied");
        var mod=add(l,new ItemStack(CompatibilityTestMod.TAGGED_TOOL),"differentenchant_tests:probe",4);add(l,mod,"differentenchant_tests:curse_probe",2);
        h.assertTrue(DisenchantingService.choices(mod).size()==1&&DisenchantingService.prepare(mod,new ItemStack(Items.BOOK),"differentenchant_tests:curse_probe")==null,"foreign namespace normal works; foreign tagged curse denied");
        var extracted=DisenchantingService.prepare(mod,new ItemStack(Items.BOOK),"differentenchant_tests:probe");h.assertTrue(rank(extracted.book(),"differentenchant_tests:probe")==3&&rank(extracted.equipment(),"differentenchant_tests:curse_probe")==2,"third-party level and curse preserved");
        var mending=DisenchantingService.prepare(gear,new ItemStack(Items.BOOK),"minecraft:mending");h.assertTrue(rank(mending.book(),"minecraft:mending")==1&&mending.cost()==3,"I stays I");
        for(var holder:l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().filter(e->e.key().identifier().getNamespace().equals("differentenchant")).toList()) {
            var item=holder.value().getSupportedItems().iterator().next().value();var s=new ItemStack(item);s.enchant(holder,holder.value().getMaxLevel());var r=DisenchantingService.prepare(s,new ItemStack(Items.BOOK),holder.key().identifier().toString());h.assertTrue(r!=null&&EnchantmentHelper.getItemEnchantmentLevel(holder,r.equipment())==0&&rank(r.book(),holder.key().identifier().toString())==Math.max(1,holder.value().getMaxLevel()-1),"lost enchant extractable "+holder.key().identifier());
        }
        h.assertTrue(DisenchantingService.prepare(gear,new ItemStack(Items.WRITABLE_BOOK),"minecraft:sharpness")==null&&DisenchantingService.choices(new ItemStack(Items.ENCHANTED_BOOK)).isEmpty(),"ordinary book only and no enchanted-book stripping");h.succeed();
    }
    @GameTest public void nativeRecipeCostsExactlyFourEchoShards(GameTestHelper h) {
        var grid=List.of(new ItemStack(Items.ECHO_SHARD),new ItemStack(Items.AMETHYST_SHARD),new ItemStack(Items.ECHO_SHARD),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.BOOK),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.ECHO_SHARD),new ItemStack(Items.AMETHYST_SHARD),new ItemStack(Items.ECHO_SHARD));
        var input=CraftingInput.of(3,3,grid);var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();h.assertTrue(recipe.value().assemble(input).is(DisenchantContent.CORE_ITEM),"native survival crafting recipe");
        var p=BatchTwoGameTests.player(h);var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlockAndUpdate(pos,Blocks.CRAFTING_TABLE.defaultBlockState());var m=new CraftingMenu(84,p.getInventory(),ContainerLevelAccess.create(h.getLevel(),pos));p.containerMenu=m;for(int i=0;i<9;i++)m.getSlot(i+1).set(grid.get(i).copy());
        h.assertTrue(m.quickMoveStack(p,0).is(DisenchantContent.CORE_ITEM),"native result transfer");for(int i=1;i<10;i++)h.assertTrue(m.getSlot(i).getItem().isEmpty(),"each of four shards and other inputs consumed exactly once");m.removed(p);h.succeed();
    }
    @GameTest public void actualPickupSequentialExtractionAndXp(GameTestHelper h) {
        var p=BatchTwoGameTests.player(h);var m=menu(h,p);fill(m,fixture(h.getLevel()),3);h.assertTrue(m.getSlot(2).getItem().isEmpty(),"no selection no output");select(m,p,"minecraft:sharpness");var gear=m.getSlot(0).getItem().copy();
        h.assertTrue(ItemStack.matches(gear,m.getSlot(0).getItem())&&p.experienceLevel==40&&m.getSlot(1).getItem().getCount()==3,"selection never charges");m.clicked(2,0,ContainerInput.PICKUP,p);
        for(int i=0;i<25;i++)m.clicked(2,0,ContainerInput.PICKUP,p);
        h.assertTrue(rank(m.getCarried(),"minecraft:sharpness")==4&&m.getCarried().getCount()==1&&rank(m.getSlot(0).getItem(),"minecraft:sharpness")==0&&rank(m.getSlot(0).getItem(),"minecraft:unbreaking")==3&&p.experienceLevel==34&&m.getSlot(1).getItem().getCount()==2,"rapid mouse pickup pays exactly once and removes only selection");
        h.assertTrue(m.getSlot(0).getItem().getDamageValue()==137&&m.getSlot(0).getItem().getHoverName().getString().equals("Echo Archive"),"original durability and name survive actual commit");m.setCarried(ItemStack.EMPTY);select(m,p,"minecraft:unbreaking");m.clicked(2,0,ContainerInput.QUICK_MOVE,p);h.assertTrue(p.getInventory().countItem(Items.ENCHANTED_BOOK)==1&&p.experienceLevel==30&&m.getSlot(1).getItem().getCount()==1,"next enchant selected and shift taken once");select(m,p,"minecraft:mending");m.clicked(2,40,ContainerInput.SWAP,p);h.assertTrue(rank(p.getInventory().getItem(40),"minecraft:mending")==1&&p.experienceLevel==27&&m.choices().isEmpty()&&rank(m.getSlot(0).getItem(),"minecraft:vanishing_curse")==1,"I-level offhand delivery and curse-only remainder");m.removed(p);h.succeed();
    }
    @GameTest public void capacityXpMalformedSelectionAndPreviewCannotDuplicate(GameTestHelper h) {
        var p=BatchTwoGameTests.player(h);var m=menu(h,p);fill(m,fixture(h.getLevel()),5);select(m,p,"minecraft:sharpness");var original=m.getSlot(0).getItem().copy();
        h.assertTrue(!m.clickMenuButton(p,-1)&&!m.clickMenuButton(p,999999),"malformed selection packet refused");for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        m.clicked(2,0,ContainerInput.QUICK_MOVE,p);m.clicked(2,0,ContainerInput.SWAP,p);m.setCarried(new ItemStack(Items.DIAMOND));m.clicked(2,1,ContainerInput.PICKUP,p);m.setCarried(ItemStack.EMPTY);
        h.assertTrue(ItemStack.matches(original,m.getSlot(0).getItem())&&m.getSlot(1).getItem().getCount()==5&&p.experienceLevel==40,"full inventory/hotbar and incompatible cursor do not charge");p.experienceLevel=5;m.clicked(2,0,ContainerInput.PICKUP,p);h.assertTrue(m.getCarried().isEmpty()&&ItemStack.matches(original,m.getSlot(0).getItem()),"insufficient xp no output or loss");p.experienceLevel=40;
        m.clicked(2,0,ContainerInput.THROW,p);p.getAbilities().instabuild=true;m.clicked(2,0,ContainerInput.CLONE,p);p.getAbilities().instabuild=false;var display=m.getSlot(2).getItem().copy();m.setCarried(display.copy());m.clicked(3,0,ContainerInput.PICKUP_ALL,p);h.assertTrue(m.getCarried().getCount()==1&&m.getSlot(1).getItem().getCount()==5,"double click excludes result");m.setCarried(ItemStack.EMPTY);
        m.getSlot(0).getItem().set(DataComponents.CUSTOM_NAME,Component.literal("changed after selection"));m.clicked(2,0,ContainerInput.PICKUP,p);h.assertTrue(m.getSlot(2).getItem().isEmpty()&&m.getSlot(1).getItem().getCount()==5,"in-place component mutation invalidates quoted selection");select(m,p,"minecraft:sharpness");p.getInventory().setItem(0,ItemStack.EMPTY);m.clicked(2,0,ContainerInput.SWAP,p);h.assertTrue(rank(p.getInventory().getItem(0),"minecraft:sharpness")==4&&p.experienceLevel==34,"empty number key slot delivers one real book");
        p.getInventory().clearContent();m.removed(p);m.removed(p);h.assertTrue(p.getInventory().countItem(Items.DIAMOND_SWORD)==1&&p.getInventory().countItem(Items.BOOK)==4&&p.getInventory().countItem(Items.ENCHANTED_BOOK)==0,"close returns real equipment and books once, no preview");h.succeed();
    }
    @GameTest public void twoPlayersForeignClicksDestructionAndNewIdentity(GameTestHelper h) {
        var a=BatchTwoGameTests.player(h);var b=BatchTwoGameTests.player(h);var m=menu(h,a);var pos=h.absolutePos(new BlockPos(3,3,3));b.teleportTo(a.getX(),a.getY(),a.getZ());var n=new DisenchantMenu(85,b.getInventory(),h.getLevel(),pos);b.containerMenu=n;b.experienceLevel=40;
        fill(m,fixture(h.getLevel()),2);fill(n,fixture(h.getLevel()),2);select(m,a,"minecraft:sharpness");select(n,b,"minecraft:unbreaking");m.clicked(2,0,ContainerInput.PICKUP,b);h.assertTrue(m.getCarried().isEmpty()&&!m.clickMenuButton(b,0),"foreign owner cannot select/take");n.clicked(2,1,ContainerInput.PICKUP,b);h.assertTrue(rank(n.getCarried(),"minecraft:unbreaking")==2&&rank(m.getSlot(0).getItem(),"minecraft:unbreaking")==3,"independent player inputs/selections");
        h.getLevel().destroyBlock(DisenchantStructure.position(pos,9),false);m.clicked(2,0,ContainerInput.PICKUP,a);h.assertTrue(m.getCarried().isEmpty()&&m.getSlot(1).getItem().getCount()==2,"broken machine refuses pending result");build(h.getLevel(),pos);DisenchantStructure.form(h.getLevel(),pos);h.assertTrue(!m.stillValid(a),"rebuilt same position has different identity, old menu stays invalid");m.removed(a);n.removed(b);m.removed(a);n.removed(b);h.assertTrue(a.getInventory().countItem(Items.DIAMOND_SWORD)==1&&a.getInventory().countItem(Items.BOOK)==2&&b.getInventory().countItem(Items.DIAMOND_SWORD)==1&&b.getInventory().countItem(Items.ENCHANTED_BOOK)==1,"broken menus return equipment/material/cursor exactly once");h.succeed();
    }
    @GameTest(maxTicks=60) public void everyPartOriginalLootPersistenceAndRecovery(GameTestHelper h) {
        var l=h.getLevel();var pos=h.absolutePos(new BlockPos(3,3,3));for(int broken=0;broken<18;broken++) {
            build(l,pos);h.assertTrue(DisenchantStructure.form(l,pos),"formation");h.assertTrue(!DisenchantStructure.form(l,pos),"repeated activation inert");var id=DisenchantStructure.controller(l,pos).machineId();
            for(int i=0;i<18;i++){var target=DisenchantStructure.position(pos,i);var old=l.getBlockEntity(target);var drops=Block.getDrops(l.getBlockState(target),l,target,old,null,new ItemStack(Items.DIAMOND_PICKAXE));h.assertTrue(drops.size()==1&&drops.getFirst().is(DisenchantStructure.original(i).asItem())&&drops.getFirst().getCount()==1,"original loot exactly once "+i);var tag=old.saveWithFullMetadata(l.registryAccess());l.removeBlockEntity(target);l.setBlockEntity(BlockEntity.loadStatic(target,l.getBlockState(target),tag,l.registryAccess()));}
            h.assertTrue(id.equals(DisenchantStructure.controller(l,pos).machineId()),"all18 part NBT roundtrip");l.destroyBlock(DisenchantStructure.position(pos,broken),false);for(int i=0;i<18;i++)h.assertTrue(l.getBlockState(DisenchantStructure.position(pos,i)).is(i==broken?Blocks.AIR:DisenchantStructure.original(i)),"all other original blocks restored");
        }
        build(l,pos);l.setBlockAndUpdate(DisenchantStructure.position(pos,17),Blocks.AIR.defaultBlockState());h.assertTrue(!DisenchantStructure.form(l,pos)&&l.getBlockState(pos).is(DisenchantContent.CORE),"incomplete never changes blocks");var far=new BlockPos(30000000-100,100,30000000-100);h.assertTrue(!DisenchantStructure.form(l,far)&&l.getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"unloaded formation rejects without chunk acquisition");h.succeed();
    }
    @GameTest public void realPlayerMiningToolAndCreativeDrops(GameTestHelper h) {
        var l=h.getLevel();var p=BatchTwoGameTests.player(h);var pos=build(l,h.absolutePos(new BlockPos(3,3,3)));DisenchantStructure.form(l,pos);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(p.gameMode.destroyBlock(pos),"actual survival controller mining");var nearby=new AABB(pos).inflate(5);h.assertTrue(l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,nearby).stream().filter(e->e.getItem().is(DisenchantContent.CORE_ITEM)).mapToInt(e->e.getItem().getCount()).sum()==1,"one reusable core, never extra shards");
        build(l,pos);DisenchantStructure.form(l,pos);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);var target=DisenchantStructure.position(pos,10);h.assertTrue(p.gameMode.destroyBlock(target),"bare hand can dismantle");h.assertTrue(l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,nearby).stream().noneMatch(e->e.getItem().is(Items.CUT_COPPER)),"wrong tool no extra original copper drop");
        build(l,pos);DisenchantStructure.form(l,pos);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);h.assertTrue(p.gameMode.destroyBlock(pos),"creative dismantling");h.assertTrue(l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,nearby).stream().filter(e->e.getItem().is(DisenchantContent.CORE_ITEM)).mapToInt(e->e.getItem().getCount()).sum()==1,"creative adds no second core");h.succeed();
    }
    @GameTest(maxTicks=60) public void suppressedRemovalRecoversLoadedOrphans(GameTestHelper h) {
        var l=h.getLevel();var pos=build(l,h.absolutePos(new BlockPos(3,3,3)));DisenchantStructure.form(l,pos);var target=DisenchantStructure.position(pos,17);l.removeBlockEntity(target);l.setBlock(target,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        h.runAfterDelay(23,()->{for(int i=0;i<18;i++)h.assertTrue(l.getBlockState(DisenchantStructure.position(pos,i)).is(i==17?Blocks.AIR:DisenchantStructure.original(i)),"integrity recovers without removed part callback");h.succeed();});
    }
    @GameTest(maxTicks=60) public void lateLoadedSavedOrphanRestoresWithoutTouchingNeighbors(GameTestHelper h) {
        var l=h.getLevel();var pos=build(l,h.absolutePos(new BlockPos(3,3,3)));DisenchantStructure.form(l,pos);var target=DisenchantStructure.position(pos,17);var saved=l.getBlockEntity(target).saveWithFullMetadata(l.registryAccess());var state=l.getBlockState(target);
        l.destroyBlock(DisenchantStructure.position(pos,9),false);
        // Reinsert a serialized stale part as if its chunk returned after the loaded half disassembled.
        l.setBlock(target,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);l.setBlockEntity(BlockEntity.loadStatic(target,state,saved,l.registryAccess()));
        h.runAfterDelay(23,()->{for(int i=0;i<18;i++)h.assertTrue(l.getBlockState(DisenchantStructure.position(pos,i)).is(i==9?Blocks.AIR:DisenchantStructure.original(i)),"late loaded orphan restores only matching saved ownership");h.succeed();});
    }
    @GameTest(padding=16) public void explosionMaterialConservation(GameTestHelper h) {
        var l=h.getLevel();var pos=build(l,h.absolutePos(new BlockPos(3,3,3)));DisenchantStructure.form(l,pos);l.explode(null,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,6,net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        var entities=l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(12));for(var entry:Map.<Item,Integer>of(Items.DEEPSLATE_BRICKS,9,Items.CHISELED_DEEPSLATE,4,Items.CUT_COPPER,4,DisenchantContent.CORE_ITEM,1).entrySet()) {
            int count=entities.stream().filter(e->e.getItem().is(entry.getKey())).mapToInt(e->e.getItem().getCount()).sum();for(int i=0;i<18;i++){var s=l.getBlockState(DisenchantStructure.position(pos,i));h.assertTrue(!s.is(DisenchantContent.PART),"no formed orphan");if(s.getBlock().asItem()==entry.getKey())count++;}h.assertTrue(count<=entry.getValue(),"explosion material conservation "+entry.getKey());}h.succeed();
    }
}
