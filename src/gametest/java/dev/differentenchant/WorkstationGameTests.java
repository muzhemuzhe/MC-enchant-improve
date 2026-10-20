package dev.differentenchant;

import java.util.*;
import dev.differentenchant.workstation.*;
import dev.differentenchant.disenchant.*;
import dev.differentenchant.research.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;

public final class WorkstationGameTests {
    static final WorkstationStructure.Kind D=WorkstationStructure.Kind.DISENCHANT,R=WorkstationStructure.Kind.RESEARCH;
    static BlockPos build(GameTestHelper h,WorkstationStructure.Kind kind){return WorkstationFixtures.build(h.getLevel(),h.absolutePos(new BlockPos(3,3,3)),kind);}
    static List<BlockState> states(ServerLevel l,BlockPos core){var result=new ArrayList<BlockState>();for(int i=0;i<18;i++)result.add(l.getBlockState(WorkstationStructure.position(core,i)));return result;}
    static int items(ServerLevel l,BlockPos core,Item item){return l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(core).inflate(4)).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum();}
    static void restored(GameTestHelper h,BlockPos core,WorkstationStructure.Kind kind,int missing){for(int i=0;i<18;i++)h.assertTrue(h.getLevel().getBlockState(WorkstationStructure.position(core,i)).is(i==missing?Blocks.AIR:WorkstationStructure.original(kind,i)),"restored original "+kind+" "+i);}
    @GameTest(maxTicks=60) public void bothPatternsEveryMissingCellRejectsWithoutMutation(GameTestHelper h) {
        for(var kind:WorkstationStructure.Kind.values())for(int missing=0;missing<18;missing++){
            var core=build(h,kind);var p=WorkstationStructure.position(core,missing);h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);var before=states(h.getLevel(),core);
            h.assertTrue(!WorkstationStructure.form(h.getLevel(),core,kind).equals("formed")&&before.equals(states(h.getLevel(),core)),"missing cell rejects atomically "+kind+missing);
        }h.succeed();
    }
    @GameTest public void matureUpClustersOnlyWaterStateAndRealSmallCollision(GameTestHelper h) {
        var l=h.getLevel();for(var block:List.of(Blocks.SMALL_AMETHYST_BUD,Blocks.MEDIUM_AMETHYST_BUD,Blocks.LARGE_AMETHYST_BUD)) {
            var core=build(h,D);l.setBlockAndUpdate(WorkstationStructure.position(core,9),block.defaultBlockState().setValue(BlockStateProperties.FACING,Direction.UP));h.assertTrue(!WorkstationStructure.form(l,core,D).equals("formed"),"buds never satisfy mature cluster");
        }
        for(var facing:Direction.values())if(facing!=Direction.UP){var core=build(h,D);l.setBlock(WorkstationStructure.position(core,9),Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(BlockStateProperties.FACING,facing),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);h.assertTrue(!WorkstationStructure.form(l,core,D).equals("formed"),"wrong mature direction rejected "+facing);}
        var core=build(h,D);var p=WorkstationStructure.position(core,9);var wet=l.getBlockState(p).setValue(BlockStateProperties.WATERLOGGED,true);l.setBlockAndUpdate(p,wet);var before=states(l,core);h.assertTrue(WorkstationStructure.form(l,core,D).equals("formed"),"waterlogged upward cluster accepted");
        var actual=l.getBlockState(p).getCollisionShape(l,p).bounds();var expected=wet.getCollisionShape(l,p).bounds();h.assertTrue(actual.equals(expected)&&actual.maxY<1&&actual.getXsize()<1,"formed cluster retains vanilla narrow collision, not full cube");h.assertTrue(l.getFluidState(p).is(net.minecraft.world.level.material.Fluids.WATER),"water remains present while formed");l.destroyBlock(core,false);for(int i=0;i<18;i++)if(i!=13)h.assertTrue(l.getBlockState(WorkstationStructure.position(core,i)).equals(before.get(i)),"full state restored support-first");h.assertTrue(items(l,core,Items.AMETHYST_SHARD)==0&&items(l,core,Items.AMETHYST_CLUSTER)==0,"no support-triggered phantom drops");h.succeed();
    }
    @GameTest(maxTicks=60) public void repeatedAssemblyRestoresFourClustersWithoutFreeMaterials(GameTestHelper h) {
        var l=h.getLevel();var core=build(h,D);for(int cycle=0;cycle<30;cycle++){
            l.setBlockAndUpdate(core,DisenchantContent.CORE.defaultBlockState());h.assertTrue(WorkstationStructure.form(l,core,D).equals("formed"),"reform cycle");l.destroyBlock(core,false);restored(h,core,D,13);
            for(int i:new int[]{9,11,15,17}){var p=WorkstationStructure.position(core,i);h.assertTrue(l.getBlockState(p).getValue(BlockStateProperties.FACING)==Direction.UP&&l.getBlockState(p).canSurvive(l,p),"all restored clusters supported/up");}
        }h.assertTrue(items(l,core,Items.AMETHYST_SHARD)==0&&items(l,core,Items.AMETHYST_CLUSTER)==0,"30 assemble/dismantle cycles produce no crystals or shards");h.succeed();
    }
    @GameTest(maxTicks=80) public void lecternFacingAllRotationsAndEmptyBookEntityRoundTrip(GameTestHelper h) {
        var l=h.getLevel();for(var rotation:Rotation.values()) {
            var core=build(h,R);for(int i=9;i<18;i++){var o=dev.differentenchant.structure.StructurePattern.CELLS.get(i).offset();var target=core.offset(new BlockPos(o.x(),o.y(),o.z()).rotate(rotation));l.setBlockAndUpdate(target,WorkstationStructure.template(R,i).rotate(rotation));}
            h.assertTrue(WorkstationStructure.form(l,core,R).equals("formed"),"structure can rotate "+rotation);var id=WorkstationStructure.controller(l,core,R).machineId();
            for(int i=0;i<18;i++){var p=WorkstationStructure.position(core,i);var saved=l.getBlockEntity(p).saveWithFullMetadata(l.registryAccess());l.removeBlockEntity(p);l.setBlockEntity(BlockEntity.loadStatic(p,l.getBlockState(p),saved,l.registryAccess()));}
            h.assertTrue(id.equals(WorkstationStructure.controller(l,core,R).machineId()),"identity and original block entities survive native NBT");l.destroyBlock(core,false);
            for(int i:new int[]{10,12,14,16}){var p=WorkstationStructure.position(core,i);h.assertTrue(l.getBlockState(p).getValue(LecternBlock.FACING)==WorkstationStructure.inward(i)&&l.getBlockEntity(p) instanceof LecternBlockEntity lectern&&lectern.getBook().isEmpty(),"restored lectern correct facing and empty original BE");}
        }
        for(int bad:new int[]{10,12,14,16}){var core=build(h,R);var p=WorkstationStructure.position(core,bad);l.setBlockAndUpdate(p,l.getBlockState(p).setValue(LecternBlock.FACING,WorkstationStructure.inward(bad).getOpposite()));var before=states(l,core);h.assertTrue(!WorkstationStructure.form(l,core,R).equals("formed")&&before.equals(states(l,core)),"outward lectern rejects without mutation "+bad);l.setBlockAndUpdate(p,l.getBlockState(p).setValue(LecternBlock.FACING,WorkstationStructure.inward(bad)));h.assertTrue(WorkstationStructure.form(l,core,R).equals("formed"),"correcting direction permits assembly");}
        h.succeed();
    }
    @GameTest public void lecternBookAndSpoofedHasBookCannotBeDestroyedByAssembly(GameTestHelper h) {
        var l=h.getLevel();for(boolean flag:List.of(false,true)){
            var core=build(h,R);var p=WorkstationStructure.position(core,10);var be=(LecternBlockEntity)l.getBlockEntity(p);var book=new ItemStack(Items.WRITABLE_BOOK);book.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Do not remove"));be.setBook(book);l.setBlock(p,l.getBlockState(p).setValue(LecternBlock.HAS_BOOK,flag),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);var saved=be.saveWithFullMetadata(l.registryAccess());var before=states(l,core);
            h.assertTrue(WorkstationStructure.form(l,core,R).equals("lectern_book"),"actual Book or HAS_BOOK prevents assembly");h.assertTrue(before.equals(states(l,core))&&saved.equals(l.getBlockEntity(p).saveWithFullMetadata(l.registryAccess()))&&items(l,core,Items.WRITABLE_BOOK)==0,"rejecting never changes/drops/copies lectern book");be.clearContent();l.setBlockAndUpdate(p,l.getBlockState(p).setValue(LecternBlock.HAS_BOOK,false));h.assertTrue(WorkstationStructure.form(l,core,R).equals("formed"),"cleared lectern accepted");l.destroyBlock(core,false);
        }h.succeed();
    }
    @GameTest public void controllerWithoutFrameAndRebuiltIdentityCannotResearch(GameTestHelper h) {
        var l=h.getLevel();var p=BatchTwoGameTests.player(h);var core=h.absolutePos(new BlockPos(3,3,3));l.setBlockAndUpdate(core,ResearchContent.TABLE.defaultBlockState());p.teleportTo(core.getX()+.5,core.getY(),core.getZ()-2);var m=new ResearchMenu(91,p.getInventory(),l,core);p.containerMenu=m;var recipe=ResearchGameTests.find(l,r->!r.scroll());ResearchGameTests.fill(m,l,recipe,2);
        h.assertTrue(!m.stillValid(p)&&m.getSlot(3).getItem().isEmpty(),"standalone table has no preview/authority");m.clicked(3,0,net.minecraft.world.inventory.ContainerInput.PICKUP,p);h.assertTrue(m.getSlot(2).getItem().getCount()==2,"standalone take never spends core");m.removed(p);
        WorkstationFixtures.build(l,core,R);WorkstationStructure.form(l,core,R);var real=new ResearchMenu(92,p.getInventory(),l,core);p.containerMenu=real;ResearchGameTests.fill(real,l,recipe,2);h.assertTrue(real.stillValid(p)&&real.getSlot(3).hasItem(),"assembled table produces existing research preview");h.assertTrue(WorkstationStructure.controller(l,core,D)==null,"machine kinds cannot cross recognize");l.destroyBlock(WorkstationStructure.position(core,10),false);WorkstationFixtures.build(l,core,R);WorkstationStructure.form(l,core,R);real.clicked(3,0,net.minecraft.world.inventory.ContainerInput.PICKUP,p);h.assertTrue(!real.stillValid(p)&&real.getCarried().isEmpty()&&real.getSlot(2).getItem().getCount()==2,"same-position rebuild cannot validate an old pending transaction");real.removed(p);h.succeed();
    }
    @GameTest(maxTicks=60) public void legacyDisenchanterReturnsOldMaterialsInsteadOfConverting(GameTestHelper h) {
        var l=h.getLevel();var core=build(h,D);WorkstationStructure.form(l,core,D);
        for(int i=0;i<18;i++){var p=WorkstationStructure.position(core,i);var state=l.getBlockState(p);var tag=l.getBlockEntity(p).saveWithFullMetadata(l.registryAccess());var original=i<9?Blocks.DEEPSLATE_BRICKS:i==13?DisenchantContent.CORE:WorkstationStructure.corner(i)?Blocks.CHISELED_DEEPSLATE:Blocks.CUT_COPPER;tag.put("original",BlockState.CODEC.encodeStart(NbtOps.INSTANCE,original.defaultBlockState()).getOrThrow());l.removeBlockEntity(p);l.setBlockEntity(BlockEntity.loadStatic(p,state,tag,l.registryAccess()));}
        h.assertTrue(WorkstationStructure.controller(l,core,D)==null,"legacy materials cannot power new pattern");h.runAfterDelay(23,()->{for(int i=0;i<18;i++){var expected=i<9?Blocks.DEEPSLATE_BRICKS:i==13?DisenchantContent.CORE:WorkstationStructure.corner(i)?Blocks.CHISELED_DEEPSLATE:Blocks.CUT_COPPER;h.assertTrue(l.getBlockState(WorkstationStructure.position(core,i)).is(expected),"legacy originals restored with no new material conversion");}h.succeed();});
    }
    @GameTest(maxTicks=100) public void nativeScrollExplorationSourcesNoCircularCrafting(GameTestHelper h)throws Exception {
        var l=h.getLevel();var params=new LootParams.Builder(l).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO))).create(LootContextParamSets.CHEST);var random=net.minecraft.util.RandomSource.create(20261019);var counts=new LinkedHashMap<String,Integer>();int vanilla=0;
        for(var path:List.of("minecraft:chests/stronghold_library","differentenchant:chests/study","differentenchant:chests/small","differentenchant:chests/research")) {
            var table=l.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,Identifier.parse(path)));int found=0;
            for(int t=0;t<2048;t++)for(var item:table.getRandomItems(params,random)){if(item.is(ResearchContent.LOST_SCROLL))found+=item.getCount();if(path.startsWith("minecraft:")&&item.is(Items.BOOK))vanilla++;}counts.put(path,found);
        }
        h.assertTrue(counts.get("minecraft:chests/stronghold_library")>500&&counts.get("minecraft:chests/stronghold_library")<850&&counts.get("differentenchant:chests/study")>100&&counts.get("differentenchant:chests/study")<250,"primary library and lower study chance produce real scrolls without research");h.assertTrue(counts.get("differentenchant:chests/small")==0&&counts.get("differentenchant:chests/research")==0&&vanilla>0,"small/auxiliary sources unchanged, vanilla library loot retained");
        var chestPos=h.absolutePos(new BlockPos(2,2,2));l.setBlockAndUpdate(chestPos,Blocks.CHEST.defaultBlockState());var chest=(ChestBlockEntity)l.getBlockEntity(chestPos);boolean produced=false;for(long seed=1;seed<=32&&!produced;seed++){chest.clearContent();chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,Identifier.withDefaultNamespace("chests/stronghold_library")),seed);chest.unpackLootTable(null);for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(ResearchContent.LOST_SCROLL))produced=true;}h.assertTrue(produced,"native chest opening can give first scroll before owning research table");
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("../reports/workstation-structures"));java.nio.file.Files.writeString(java.nio.file.Path.of("../reports/workstation-structures/scroll-loot.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(counts));h.succeed();
    }
    @GameTest(maxTicks=80) public void researchEveryPartBreakRestoresOriginalsAndNoLecternBookDrops(GameTestHelper h) {
        var l=h.getLevel();for(int broken=0;broken<18;broken++){
            var core=build(h,R);h.assertTrue(WorkstationStructure.form(l,core,R).equals("formed"),"research formation");
            var target=WorkstationStructure.position(core,broken);var original=((WorkstationPartEntity)l.getBlockEntity(target)).originalState();var tool=DisenchantGameTests.add(l,new ItemStack(Items.DIAMOND_AXE),"minecraft:silk_touch",1);
            var expected=Block.getDrops(original,l,target,null,null,tool);var actual=Block.getDrops(l.getBlockState(target),l,target,l.getBlockEntity(target),null,tool);h.assertTrue(expected.size()==actual.size(),"original loot count");for(int i=0;i<expected.size();i++)h.assertTrue(ItemStack.matches(expected.get(i),actual.get(i)),"original native loot is forwarded once");
            l.destroyBlock(target,false);restored(h,core,R,broken);h.assertTrue(items(l,core,Items.WRITABLE_BOOK)==0&&items(l,core,Items.WRITTEN_BOOK)==0,"empty lectern snapshot cannot create book");
        }h.succeed();
    }
    @GameTest public void removingClusterSupportDropsOnlyOneNativeClusterWorth(GameTestHelper h) {
        var l=h.getLevel();var core=build(h,D);WorkstationStructure.form(l,core,D);var support=WorkstationStructure.position(core,0);l.destroyBlock(support,false);
        h.assertTrue(l.getBlockState(WorkstationStructure.position(core,9)).isAir(),"cluster cannot float after its support is mined");h.assertTrue(items(l,core,Items.AMETHYST_SHARD)==2&&items(l,core,Items.AMETHYST_CLUSTER)==0,"native toolless cluster yields two shards, never a duplicate restored cluster");
        for(int i:new int[]{11,15,17})h.assertTrue(l.getBlockState(WorkstationStructure.position(core,i)).is(Blocks.AMETHYST_CLUSTER),"other three clusters restored supported");h.succeed();
    }
    @GameTest(maxTicks=60) public void changedFormedLecternFacingRejectsPendingTradeAndRecovers(GameTestHelper h) {
        var l=h.getLevel();var core=build(h,R);WorkstationStructure.form(l,core,R);var p=BatchTwoGameTests.player(h);p.teleportTo(core.getX()+.5,core.getY(),core.getZ()-2);var menu=new ResearchMenu(93,p.getInventory(),l,core);p.containerMenu=menu;ResearchGameTests.fill(menu,l,ResearchGameTests.find(l,r->!r.scroll()),1);
        var lectern=WorkstationStructure.position(core,10);l.setBlock(lectern,l.getBlockState(lectern).setValue(BlockStateProperties.HORIZONTAL_FACING,net.minecraft.core.Direction.NORTH),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        menu.clicked(3,0,net.minecraft.world.inventory.ContainerInput.PICKUP,p);h.assertTrue(!menu.stillValid(p)&&menu.getCarried().isEmpty()&&menu.getSlot(2).getItem().getCount()==1,"formed-facing tampering invalidates original quote immediately");menu.removed(p);
        h.runAfterDelay(23,()->{for(int i=0;i<18;i++){var target=WorkstationStructure.position(core,i);var entity=l.getBlockEntity(target);h.assertTrue(entity instanceof WorkstationPartEntity part?part.originalState().is(WorkstationStructure.original(R,i)):l.getBlockState(target).is(WorkstationStructure.original(R,i)),"invalid projection restores originals and valid full frame may automatically reassemble");}h.succeed();});
    }
    @GameTest(maxTicks=850) public void actualChunkUnloadReloadKeepsBothWholeMachinesAndNeverAcquiresMissingChunks(GameTestHelper h) {
        var l=h.getLevel();var cores=List.of(new BlockPos(8191,-58,8191),new BlockPos(12287,-58,12287));var ids=new ArrayList<UUID>();var chunks=new LinkedHashSet<net.minecraft.world.level.ChunkPos>();
        for(var core:cores)for(int i=0;i<18;i++)chunks.add(new net.minecraft.world.level.ChunkPos(WorkstationStructure.position(core,i).getX()>>4,WorkstationStructure.position(core,i).getZ()>>4));
        for(var chunk:chunks)l.setChunkForced(chunk.x(),chunk.z(),true);
        for(int j=0;j<2;j++){var kind=WorkstationStructure.Kind.values()[j];var core=cores.get(j);WorkstationFixtures.build(l,core,kind);h.assertTrue(WorkstationStructure.form(l,core,kind).equals("formed"),"real far chunk-boundary assembly");ids.add(WorkstationStructure.controller(l,core,kind).machineId());}
        for(var chunk:chunks)l.setChunkForced(chunk.x(),chunk.z(),false);int[] phase={0};long[] ready={0};
        h.runAtTickTime(849,()->{for(var chunk:chunks)l.setChunkForced(chunk.x(),chunk.z(),false);});
        h.succeedWhen(()->{
            if(phase[0]==0){for(var chunk:chunks)h.assertTrue(l.getChunkSource().getChunkNow(chunk.x(),chunk.z())==null,"wait for genuine native chunk unload");for(int j=0;j<2;j++)h.assertTrue(WorkstationStructure.controller(l,cores.get(j),WorkstationStructure.Kind.values()[j])==null,"unloaded machines inaccessible");for(var chunk:chunks)h.assertTrue(l.getChunkSource().getChunkNow(chunk.x(),chunk.z())==null,"validation did not acquire unloaded chunks");for(var chunk:chunks)l.setChunkForced(chunk.x(),chunk.z(),true);phase[0]=1;ready[0]=l.getGameTime()+23;}
            h.assertTrue(l.getGameTime()>=ready[0],"wait for native reload integrity ticks");
            for(int j=0;j<2;j++){var core=cores.get(j);var kind=WorkstationStructure.Kind.values()[j];var master=WorkstationStructure.controller(l,core,kind);h.assertTrue(master!=null&&ids.get(j).equals(master.machineId()),"saved original states and UUID survive actual chunk unload/reload");l.destroyBlock(core,false);restored(h,core,kind,13);}
            for(var chunk:chunks)l.setChunkForced(chunk.x(),chunk.z(),false);
        });
    }
}
