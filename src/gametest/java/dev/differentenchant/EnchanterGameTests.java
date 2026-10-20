package dev.differentenchant;

import java.util.*;
import dev.differentenchant.enchanter.*;
import dev.differentenchant.resonance.ResonanceItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;

public final class EnchanterGameTests {
    static Villager villager(GameTestHelper h,int rank){
        var v=h.spawn(EntityType.VILLAGER,new BlockPos(3,2,3));v.setNoAi(true);v.setVillagerData(v.getVillagerData().withProfession(h.getLevel().registryAccess(),EnchanterContent.PROFESSION_KEY).withLevel(rank));v.refreshBrain(h.getLevel());return v;
    }
    static MerchantMenu menu(ServerPlayer p,Villager v){v.setTradingPlayer(p);var m=new MerchantMenu(99,p.getInventory(),v);p.containerMenu=m;m.setSelectionHint(0);return m;}
    static ItemStack book(ServerLevel l,String id,int rank){return DisenchantGameTests.add(l,new ItemStack(Items.ENCHANTED_BOOK),id,rank);}
    @GameTest public void nativeTradeSetsAndExactInitialBalance(GameTestHelper h){
        var a=villager(h,1);var b=villager(h,2);var c=villager(h,3);var x=a.getOffers().getFirst();var y=b.getOffers().getFirst();var z=c.getOffers().getFirst();
        h.assertTrue(a.getOffers().size()==1&&x.getCostA().is(Items.EMERALD)&&x.getCostA().getCount()==3&&x.getResult().is(Items.EXPERIENCE_BOTTLE)&&x.getResult().getCount()==2&&x.getMaxUses()==16&&x.getXp()==2,"native novice registry set and balance");
        h.assertTrue(BookRecycling.isOffer(y)&&y.getCostB().isEmpty()&&y.getMaxUses()==32&&y.getXp()==5,"native apprentice exchange uses one book only with 32 stock");
        h.assertTrue(z.getCostA().getCount()==12&&z.getResult().is(EnchanterContent.CRYSTAL)&&z.getMaxUses()==12&&z.getXp()==10,"native journeyman registry set");
        h.assertTrue(EnchanterContent.PROFESSION.getTrades(4)==null&&EnchanterContent.PROFESSION.getTrades(5)==null,"no invented future trades");
        h.assertTrue(PoiTypes.forState(Blocks.ENCHANTING_TABLE.defaultBlockState()).orElseThrow().is(EnchanterContent.POI_KEY)&&PoiTypes.forState(DifferentEnchant.PART.defaultBlockState()).isEmpty(),"only original standalone table is a job POI");
        h.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.LIBRARIAN).value().getTrades(1).identifier().getNamespace().equals("minecraft"),"other profession trade sets unchanged");h.succeed();
    }
    @GameTest public void arbitraryBooksNativeCodecsAndUnrelatedOffers(GameTestHelper h){
        var l=h.getLevel();var offer=villager(h,2).getOffers().getFirst();
        for(var id:List.of("minecraft:sharpness","minecraft:efficiency","minecraft:mending","minecraft:vanishing_curse","differentenchant:prospectors_echo","differentenchant_tests:probe"))for(int rank:List.of(1,2,5)){
            var input=book(l,id,rank);h.assertTrue(BookRecycling.validBook(input)&&offer.satisfiedBy(input,ItemStack.EMPTY),"any registered book id/rank "+id+rank);var before=input.copy();h.assertTrue(offer.take(input,ItemStack.EMPTY)&&input.isEmpty(),"native take consumes exactly the real book");h.assertTrue(!before.isEmpty(),"fixture remains distinct copy");
        }
        var multiple=book(l,"minecraft:sharpness",5);DisenchantGameTests.add(l,multiple,"minecraft:vanishing_curse",1);h.assertTrue(offer.satisfiedBy(multiple,ItemStack.EMPTY)&&offer.assemble().getCount()==1,"multi curse book still only one core");
        h.assertTrue(!offer.satisfiedBy(new ItemStack(Items.BOOK),ItemStack.EMPTY)&&!offer.satisfiedBy(new ItemStack(Items.ENCHANTED_BOOK),ItemStack.EMPTY)&&!offer.satisfiedBy(multiple,new ItemStack(Items.EMERALD)),"ordinary/empty/extra cost rejected");
        var ops=RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,l.registryAccess());var nbt=MerchantOffer.CODEC.encodeStart(ops,offer).getOrThrow();var reloaded=MerchantOffer.CODEC.parse(ops,nbt).getOrThrow();
        var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),l.registryAccess());MerchantOffer.STREAM_CODEC.encode(buf,reloaded);var wire=MerchantOffer.STREAM_CODEC.decode(buf);buf.release();
        h.assertTrue(BookRecycling.isOffer(wire)&&wire.satisfiedBy(multiple,ItemStack.EMPTY)&&!wire.satisfiedBy(new ItemStack(Items.ENCHANTED_BOOK),ItemStack.EMPTY),"native save/copy/wire preserve dynamic matching with no protocol change");
        var fixed=new MerchantOffer(new ItemCost(Items.ENCHANTED_BOOK).withComponents(b->b.expect(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,multiple.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS))),new ItemStack(Items.EMERALD),12,1,.05f);
        h.assertTrue(fixed.satisfiedBy(multiple,ItemStack.EMPTY)&&!fixed.satisfiedBy(book(l,"minecraft:mending",1),ItemStack.EMPTY),"unrelated exact-component merchant offer not relaxed");h.succeed();
    }
    @GameTest public void nativeAutofillShiftCapacityRevalidationAndClose(GameTestHelper h){
        var p=BatchTwoGameTests.player(h);var v=villager(h,2);var m=menu(p,v);p.getInventory().clearContent();
        p.getInventory().setItem(9,new ItemStack(Items.ENCHANTED_BOOK));p.getInventory().setItem(10,book(h.getLevel(),"minecraft:vanishing_curse",1));
        m.tryMoveItems(0);h.assertTrue(BookRecycling.validBook(m.getSlot(0).getItem())&&p.getInventory().getItem(9).is(Items.ENCHANTED_BOOK),"autofill skips empty book and moves real nonempty book");
        h.assertTrue(m.quickMoveStack(p,2).is(ResonanceItems.ATTUNED_CORE)&&v.getOffers().getFirst().getUses()==1&&v.getVillagerXp()==5,"native shift consumes one book, issues one core and five career xp");
        h.assertTrue(m.quickMoveStack(p,2).isEmpty(),"rapid second take cannot repeat without another book");
        m.getSlot(0).set(book(h.getLevel(),"minecraft:sharpness",5));for(int i=0;i<p.getInventory().getContainerSize();i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        h.assertTrue(m.quickMoveStack(p,2).isEmpty()&&m.getSlot(0).hasItem()&&v.getOffers().getFirst().getUses()==1,"full inventory consumes nothing");
        p.getInventory().clearContent();
        // Deliberate stale server display: mutating a component in place must still fail mayPickup and Shift.
        m.getSlot(0).getItem().set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        h.assertTrue(!m.getSlot(2).mayPickup(p)&&m.quickMoveStack(p,2).isEmpty(),"take revalidates real stored enchantments even with stale preview");
        var pending=book(h.getLevel(),"differentenchant:trail_stride",1);m.getSlot(0).set(pending);m.removed(p);p.containerMenu=p.inventoryMenu;
        h.assertTrue(p.getInventory().countItem(Items.ENCHANTED_BOOK)==1&&p.getInventory().countItem(ResonanceItems.ATTUNED_CORE)==0,"close returns only unpaid book, never preview");h.succeed();
    }
    @GameTest(maxTicks=600) public void unemployedNativeBrainClaimsTableAndKeepsOtherCareer(GameTestHelper h){
        var l=h.getLevel();l.getServer().getCommands().performPrefixedCommand(l.getServer().createCommandSourceStack(),"time set day");
        var base=h.absolutePos(BlockPos.ZERO).offset(2048,0,2048);var chunks=new HashSet<ChunkPos>();for(int x=0;x<8;x++)for(int z=0;z<8;z++)chunks.add(new ChunkPos((base.getX()+x)>>4,(base.getZ()+z)>>4));for(var c:chunks)l.setChunkForced(c.x(),c.z(),true);
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)l.setBlockAndUpdate(base.offset(x,1,z),Blocks.STONE.defaultBlockState());
        var table=base.offset(4,2,3);l.setBlockAndUpdate(table,Blocks.ENCHANTING_TABLE.defaultBlockState());
        var v=EntityType.VILLAGER.create(l,EntitySpawnReason.COMMAND);v.setPos(base.getX()+3.5,base.getY()+2,base.getZ()+3.5);v.setPersistenceRequired();l.addFreshEntity(v);
        var employed=EntityType.VILLAGER.create(l,EntitySpawnReason.COMMAND);employed.setPos(base.getX()+2.5,base.getY()+2,base.getZ()+3.5);employed.setVillagerData(employed.getVillagerData().withProfession(l.registryAccess(),VillagerProfession.FARMER));employed.setVillagerXp(1);employed.refreshBrain(l);l.addFreshEntity(employed);
        h.runAtTickTime(599,()->{for(var c:chunks)l.setChunkForced(c.x(),c.z(),false);});
        h.succeedWhen(()->{h.assertTrue(v.getVillagerData().profession().is(EnchanterContent.PROFESSION_KEY),"native isolated villager acquires table; actual="+v.getVillagerData().profession()+" job="+v.getBrain().getMemory(MemoryModuleType.JOB_SITE));h.assertTrue(v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(p->p.pos().equals(table)).orElse(false),"real job-site memory");h.assertTrue(employed.getVillagerData().profession().is(VillagerProfession.FARMER),"established other profession unchanged");for(var c:chunks)l.setChunkForced(c.x(),c.z(),false);});
    }
    @GameTest(maxTicks=250) public void nativeCareerAdvancesToMasterWithoutExtraFutureTrades(GameTestHelper h){
        var p=BatchTwoGameTests.player(h);var v=villager(h,1);v.setNoAi(false);v.setVillagerXp(0);var m=menu(p,v);m.getSlot(0).set(new ItemStack(Items.EMERALD,15));for(int i=0;i<5;i++)m.quickMoveStack(p,2);m.removed(p);p.containerMenu=p.inventoryMenu;
        h.runAfterDelay(45,()->{h.assertTrue(v.getVillagerData().level()==2&&v.getOffers().size()==2,"native level-up unlocks apprentice while keeping novice");
            var m2=menu(p,v);m2.setSelectionHint(1);for(int i=0;i<12;i++){m2.getSlot(0).set(book(h.getLevel(),"minecraft:mending",1));m2.quickMoveStack(p,2);}m2.removed(p);p.containerMenu=p.inventoryMenu;
            h.runAfterDelay(45,()->{h.assertTrue(v.getVillagerData().level()==3&&v.getOffers().size()==3,"book recycling awards native career xp and unlocks crystal");
                var m3=menu(p,v);m3.setSelectionHint(2);for(int i=0;i<8;i++){m3.getSlot(0).set(new ItemStack(Items.EMERALD,12));m3.quickMoveStack(p,2);}m3.removed(p);p.containerMenu=p.inventoryMenu;
                h.runAfterDelay(45,()->{h.assertTrue(v.getVillagerData().level()==4&&v.getOffers().size()==3,"expert retains existing three offers only");v.restock();var m4=menu(p,v);m4.setSelectionHint(2);for(int i=0;i<10;i++){m4.getSlot(0).set(v.getOffers().get(2).getCostA().copy());m4.quickMoveStack(p,2);}m4.removed(p);p.containerMenu=p.inventoryMenu;
                    h.runAfterDelay(45,()->{h.assertTrue(v.getVillagerData().level()==5&&v.getOffers().size()==3,"master keeps original trades without invented unlocks");h.succeed();});
                });
            });
        });
    }
    @GameTest(maxTicks=650) public void nativeWorkAtPoiRestocksBookExchange(GameTestHelper h){
        h.setBlock(new BlockPos(3,1,3),Blocks.STONE);h.setBlock(new BlockPos(3,1,4),Blocks.STONE);var v=villager(h,2);var l=h.getLevel();var table=h.absolutePos(new BlockPos(3,2,4));l.setBlockAndUpdate(table,Blocks.ENCHANTING_TABLE.defaultBlockState());v.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(l.dimension(),table));var offer=v.getOffers().getFirst();for(int i=0;i<32;i++)offer.increaseUses();h.assertTrue(offer.isOutOfStock(),"32-use stock limit");
        var work=new WorkAtPoi();h.onEachTick(()->work.tryStart(l,v,l.getGameTime()));
        h.succeedWhen(()->h.assertTrue(offer.getUses()==0&&!offer.isOutOfStock(),"work uses="+offer.getUses()+" pos="+v.position()+" target="+table+" memory="+v.getBrain().getMemory(MemoryModuleType.JOB_SITE)));
    }
    @GameTest public void crystalGivesPointsConsumesOnceNoOrbsAndCreativeAdventure(GameTestHelper h){
        var p=BatchTwoGameTests.player(h);var l=h.getLevel();int orbs=l.getEntitiesOfClass(ExperienceOrb.class,new AABB(p.blockPosition()).inflate(4)).size();
        for(int initial:List.of(0,7,5000)){
            p.experienceLevel=0;p.totalExperience=0;p.experienceProgress=0;p.giveExperiencePoints(initial);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(EnchanterContent.CRYSTAL,2));
            for(int i=0;i<5;i++)EnchanterContent.CRYSTAL.use(l,p,InteractionHand.MAIN_HAND);
            h.assertTrue(p.totalExperience==initial+200&&p.getMainHandItem().isEmpty(),"exact 100 points each at low/high levels, no empty-hand replay");
        }
        p.setGameMode(GameType.ADVENTURE);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(EnchanterContent.CRYSTAL,2));int before=p.totalExperience;EnchanterContent.CRYSTAL.use(l,p,InteractionHand.MAIN_HAND);h.assertTrue(p.totalExperience==before+100&&p.getMainHandItem().getCount()==1,"adventure consumes one");
        p.setGameMode(GameType.CREATIVE);before=p.totalExperience;EnchanterContent.CRYSTAL.use(l,p,InteractionHand.MAIN_HAND);h.assertTrue(p.totalExperience==before+100&&p.getMainHandItem().getCount()==1,"creative grants points without consumption");
        h.assertTrue(l.getEntitiesOfClass(ExperienceOrb.class,new AABB(p.blockPosition()).inflate(4)).size()==orbs&&EnchanterContent.CRYSTAL.getDefaultMaxStackSize()==64,"no experience orb entity, stacks 64");h.succeed();
    }
    @GameTest public void nativePickupHotbarDoubleClickAndThirtyTwoActualTrades(GameTestHelper h){
        var p=BatchTwoGameTests.player(h);p.getInventory().clearContent();var v=villager(h,2);var m=menu(p,v);var offer=v.getOffers().getFirst();
        m.getSlot(0).set(book(h.getLevel(),"minecraft:binding_curse",1));m.clicked(2,4,ContainerInput.SWAP,p);h.assertTrue(p.getInventory().getItem(4).is(ResonanceItems.ATTUNED_CORE)&&offer.getUses()==1&&m.getSlot(0).getItem().isEmpty(),"native number-key swap consumes real curse book once");
        m.getSlot(0).set(book(h.getLevel(),"minecraft:efficiency",2));m.clicked(2,0,ContainerInput.PICKUP,p);m.clicked(2,0,ContainerInput.PICKUP,p);h.assertTrue(m.getCarried().is(ResonanceItems.ATTUNED_CORE)&&m.getCarried().getCount()==1&&offer.getUses()==2,"rapid normal pickup cannot mint a second core");p.getInventory().add(m.getCarried());m.setCarried(ItemStack.EMPTY);
        m.getSlot(0).set(book(h.getLevel(),"differentenchant:prospectors_echo",3));m.clicked(2,0,ContainerInput.PICKUP_ALL,p);h.assertTrue(m.getSlot(0).hasItem()&&offer.getUses()==2,"double-click does not collect preview");m.quickMoveStack(p,2);
        for(int i=3;i<32;i++){m.getSlot(0).set(book(h.getLevel(),"minecraft:mending",1));h.assertTrue(m.quickMoveStack(p,2).is(ResonanceItems.ATTUNED_CORE),"native bulk purchase "+i);}m.getSlot(0).set(book(h.getLevel(),"minecraft:sharpness",5));h.assertTrue(offer.getUses()==32&&offer.isOutOfStock()&&m.getSlot(2).getItem().isEmpty()&&m.getSlot(0).hasItem()&&p.getInventory().countItem(ResonanceItems.ATTUNED_CORE)==32,"32 books produce exactly 32 cores; 33rd book retained");
        v.restock();m.setSelectionHint(0);h.assertTrue(offer.getUses()==0&&offer.getCostA().getCount()==1&&m.quickMoveStack(p,2).is(ResonanceItems.ATTUNED_CORE),"native restock preserves one-book price and enables another actual trade");m.removed(p);p.containerMenu=p.inventoryMenu;h.succeed();
    }
}
