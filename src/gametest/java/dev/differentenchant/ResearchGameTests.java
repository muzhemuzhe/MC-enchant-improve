package dev.differentenchant;

import dev.differentenchant.research.*;
import dev.differentenchant.resonance.ResonanceItems;
import java.util.*;
import java.util.function.Predicate;
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
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public final class ResearchGameTests {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static ItemStack book(ServerLevel level,ResearchPolicy.Book input) {
        var holder=level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(input.id())).orElseThrow();
        var stack=new ItemStack(Items.ENCHANTED_BOOK);EnchantmentHelper.updateEnchantments(stack,mutable->mutable.set(holder,input.level()));return stack;
    }
    static List<ResearchPolicy.Book> vanillaBooks(ServerLevel level) {
        var result=new ArrayList<ResearchPolicy.Book>();
        level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().filter(h->h.key().identifier().getNamespace().equals("minecraft")).sorted(Comparator.comparing(h->h.key().identifier().toString()))
            .forEach(h->{for(int l=1;l<=h.value().getMaxLevel();l++)result.add(new ResearchPolicy.Book(h.key().identifier().toString(),l));});return result;
    }
    static ResearchPolicy.Recipe find(ServerLevel level,Predicate<ResearchPolicy.Result> predicate) {
        var inputs=vanillaBooks(level);var data=ResearchWorldData.get(level.getServer());
        for(int i=0;i<inputs.size();i++)for(int j=i;j<inputs.size();j++){var recipe=new ResearchPolicy.Recipe(inputs.get(i),inputs.get(j));if(predicate.test(data.resolve(recipe)))return recipe;}
        throw new AssertionError("No fixture recipe");
    }
    static ResearchMenu menu(ServerLevel level,BlockPos pos,ServerPlayer player) {
        level.setBlockAndUpdate(pos,ResearchContent.TABLE.defaultBlockState());player.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()-2);player.setGameMode(GameType.SURVIVAL);
        var menu=new ResearchMenu(51,player.getInventory(),level,pos);player.containerMenu=menu;return menu;
    }
    static void fill(ResearchMenu menu,ServerLevel level,ResearchPolicy.Recipe recipe,int cores) {
        menu.getSlot(0).set(book(level,recipe.a()));menu.getSlot(1).set(book(level,recipe.b()));menu.getSlot(2).set(new ItemStack(ResonanceItems.ATTUNED_CORE,cores));menu.broadcastChanges();
    }
    static ResearchActionPayload action(ResearchMenu menu){menu.broadcastChanges();return new ResearchActionPayload(menu.containerId,menu.snapshot().revision(),0,0);}
    @GameTest public void previewWireContainsOnlyPersonalPresentation(GameTestHelper h) {
        var level=h.getLevel();var p=h.makeMockServerPlayerInLevel();var m=menu(level,h.absolutePos(new BlockPos(1,2,1)),p);
        var recipe=find(level,ResearchPolicy.Result::scroll);fill(m,level,recipe,2);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        try {
            ResearchPreviewPayload.CODEC.encode(buffer,m.snapshot());var decoded=ResearchPreviewPayload.CODEC.decode(buffer);
            h.assertTrue(decoded.known()==null&&decoded.rows().isEmpty(),"unknown wire payload contains no result, scroll flag or record");
            h.assertTrue(m.getSlot(3).getItem().is(Items.BOOK)&&m.getSlot(3).getItem().get(DataComponents.STORED_ENCHANTMENTS)==null,"native slot sync contains only harmless unknown book");
            var client=new ResearchMenu(m.containerId,p.getInventory());for(int i=0;i<3;i++)client.getSlot(i).set(m.getSlot(i).getItem().copy());client.acceptPreview(decoded);
            h.assertTrue(client.preview()!=null,"matching native inputs accept preview");client.getSlot(1).set(ItemStack.EMPTY);h.assertTrue(client.preview()==null,"replacing native input immediately suppresses stale preview");
            m.clicked(3,0,ContainerInput.PICKUP,p);m.handle(p,new ResearchActionPayload(m.containerId,m.snapshot().revision(),1,0));
            buffer.clear();ResearchPreviewPayload.CODEC.encode(buffer,m.snapshot());decoded=ResearchPreviewPayload.CODEC.decode(buffer);
            h.assertTrue(decoded.known().scroll()&&decoded.rows().size()==1&&decoded.rows().getFirst().result().scroll(),"own known output and record codec round trip");
            var fields=Arrays.stream(ResearchPreviewPayload.DisplayResult.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
            h.assertTrue(fields.equals(List.of("id","level","scroll")),"display protocol excludes rarity, score, weight and probability fields");
        } finally {buffer.release();}
        m.removed(p);h.succeed();
    }
    @GameTest public void nativeCraftAndEightAnvilApplications(GameTestHelper h) {
        var level=h.getLevel();var player=h.makeMockServerPlayerInLevel();player.setGameMode(GameType.SURVIVAL);
        var grid=List.of(new ItemStack(Items.AMETHYST_SHARD),new ItemStack(Items.BOOKSHELF),new ItemStack(Items.AMETHYST_SHARD),new ItemStack(Items.QUARTZ),new ItemStack(Items.LECTERN),new ItemStack(Items.QUARTZ),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.IRON_INGOT));
        var nativeRecipe=level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,CraftingInput.of(3,3,grid),level).orElseThrow();
        h.assertTrue(nativeRecipe.value().assemble(CraftingInput.of(3,3,grid)).is(ResearchContent.TABLE_ITEM),"survival recipe uses existing vanilla materials");
        var craftingPos=h.absolutePos(new BlockPos(1,2,1));level.setBlockAndUpdate(craftingPos,Blocks.CRAFTING_TABLE.defaultBlockState());
        var crafting=new CraftingMenu(60,player.getInventory(),ContainerLevelAccess.create(level,craftingPos));player.containerMenu=crafting;
        for(int i=0;i<9;i++)crafting.getSlot(i+1).set(grid.get(i).copy());
        h.assertTrue(crafting.quickMoveStack(player,0).is(ResearchContent.TABLE_ITEM),"real crafting output");
        for(int i=1;i<=9;i++)h.assertTrue(crafting.getSlot(i).getItem().isEmpty(),"craft consumes each material");crafting.removed(player);
        var items=Map.<String,Item>of("trail_stride",Items.DIAMOND_BOOTS,"prospectors_echo",Items.DIAMOND_PICKAXE,"lantern_sight",Items.DIAMOND_HELMET,"living_margin",Items.DIAMOND_CHESTPLATE,"archivists_reach",Items.DIAMOND_PICKAXE,"warding_focus",Items.SHIELD,"gravity_thread",Items.BOW,"last_page",Items.DIAMOND_CHESTPLATE);
        var data=ResearchWorldData.get(level.getServer());h.assertTrue(data.catalogue().size()>=8,"extensible registry-tag catalogue");
        var pos=h.absolutePos(new BlockPos(1,2,1));
        for(var entry:data.catalogue()) {
            var id=Identifier.parse(entry.id());var holder=level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(id).orElseThrow();
            var book=ResearchBooks.output(level.registryAccess(),new ResearchPolicy.Result(entry.id(),entry.maxLevel(),entry.rarity(),false));
            h.assertTrue(ResearchBooks.read(book)!=null&&ResearchBooks.read(book).level()==entry.maxLevel(),"real stored enchantments, independent rarity/max level");
            player.experienceLevel=100;level.setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());
            var anvil=new AnvilMenu(61,player.getInventory(),ContainerLevelAccess.create(level,pos));player.containerMenu=anvil;
            anvil.getSlot(0).set(new ItemStack(items.get(id.getPath())));anvil.getSlot(1).set(book);
            h.assertTrue(!anvil.getSlot(2).getItem().isEmpty()&&anvil.getCost()>0,"native anvil accepts supported lost book "+entry.id());
            var applied=anvil.quickMoveStack(player,2);h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(holder,applied)==entry.maxLevel(),"native result actually applied "+entry.id());
            h.assertTrue(anvil.getSlot(0).getItem().isEmpty()&&anvil.getSlot(1).getItem().isEmpty()&&player.experienceLevel<100,"one book/tool consumed and XP paid");anvil.removed(player);
        }
        var multi=book(level,new ResearchPolicy.Book("minecraft:sharpness",1));multi.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.withDefaultNamespace("unbreaking")).orElseThrow(),1);
        h.assertTrue(ResearchBooks.read(multi)==null&&ResearchBooks.read(new ItemStack(Items.BOOK))==null,"multi and plain books rejected explicitly");
        h.assertTrue(dev.differentenchant.enchanting.EnchantmentCandidates.forItem(level.registryAccess(),new ItemStack(Items.BOOK)).candidates().stream().noneMatch(c->c.id().startsWith("differentenchant:")),"lost books do not leak into random table");
        h.succeed();
    }
    @GameTest public void objectiveDiscoveryFailureReplayAndPersistence(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,2,1));var p=h.makeMockServerPlayerInLevel();var menu=menu(level,pos,p);
        var data=ResearchWorldData.get(level.getServer());var scroll=find(level,ResearchPolicy.Result::scroll);fill(menu,level,scroll,4);
        menu.getSlot(0).getItem().set(DataComponents.CUSTOM_NAME,Component.literal("Original A"));menu.getSlot(0).getItem().set(DataComponents.REPAIR_COST,17);menu.broadcastChanges();
        var a=menu.getSlot(0).getItem().copy();var b=menu.getSlot(1).getItem().copy();
        h.assertTrue(menu.snapshot().known()==null&&menu.getSlot(3).getItem().is(Items.BOOK),"unknown scroll displays only a plain book, not its real output");
        var request=action(menu);menu.handle(p,request);h.assertTrue(menu.getSlot(2).getItem().getCount()==4,"old research button packet does nothing");
        menu.clicked(3,0,ContainerInput.PICKUP,p);
        h.assertTrue(menu.getCarried().is(ResearchContent.LOST_SCROLL)&&menu.getSlot(2).getItem().getCount()==3,"direct pickup delivers real scroll and charges exactly one core");
        h.assertTrue(ItemStack.matches(a,menu.getSlot(0).getItem())&&ItemStack.matches(b,menu.getSlot(1).getItem()),"scroll preserves all book components");
        h.assertTrue(menu.snapshot().known().scroll()&&menu.getSlot(3).getItem().is(ResearchContent.LOST_SCROLL),"personal discovery immediately unlocks real preview");
        menu.clicked(3,1,ContainerInput.PICKUP,p);
        h.assertTrue(menu.getCarried().getCount()==2&&menu.getSlot(2).getItem().getCount()==2,"right pickup repeats fixed recipe once, merging real scrolls");
        menu.clicked(3,0,ContainerInput.QUICK_MOVE,p);
        h.assertTrue(p.getInventory().countItem(ResearchContent.LOST_SCROLL)==1&&menu.getSlot(2).getItem().getCount()==1,"one shift gesture produces one real scroll");
        menu.handle(p,request);h.assertTrue(menu.getSlot(2).getItem().getCount()==1,"legacy/replayed action cannot charge materials");
        var restored=ResearchWorldData.importJson(data.exportJson());h.assertTrue(restored.knows(p.getUUID(),scroll)&&restored.resolve(new ResearchPolicy.Recipe(scroll.b(),scroll.a())).equals(data.resolve(scroll)),"world answer and UUID discovery survive round trip");
        h.assertTrue(!restored.knows(UUID.randomUUID(),scroll),"discoveries remain personal");
        menu.removed(p);menu.removed(p);h.assertTrue(p.getInventory().countItem(ResearchContent.LOST_SCROLL)==3&&p.getInventory().countItem(Items.BOOK)==0,"close returns real cursor once and never the preview");
        var invalid=menu(level,pos,p);fill(invalid,level,scroll,1);var extra=level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.withDefaultNamespace("luck_of_the_sea")).orElseThrow();
        invalid.getSlot(0).getItem().enchant(extra,1);invalid.broadcastChanges();invalid.clicked(3,0,ContainerInput.PICKUP,p);
        h.assertTrue(invalid.snapshot().status()==1&&invalid.getSlot(2).getItem().getCount()==1&&invalid.getSlot(3).getItem().isEmpty(),"multi book cannot produce or charge");invalid.removed(p);h.succeed();
    }
    @GameTest public void successTwoPlayersShiftCloseAndDestroyedWorkstation(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,2,1));var p=h.makeMockServerPlayerInLevel();var other=h.makeMockServerPlayerInLevel();
        var a=menu(level,pos,p);var b=menu(level,pos,other);var recipe=find(level,r->!r.scroll());fill(a,level,recipe,2);fill(b,level,new ResearchPolicy.Recipe(recipe.b(),recipe.a()),2);
        var unknown=a.getSlot(3).getItem().copy();a.clicked(3,0,ContainerInput.PICKUP,other);
        h.assertTrue(a.getCarried().isEmpty()&&a.getSlot(2).getItem().getCount()==2,"foreign player cannot claim result");
        a.clicked(3,0,ContainerInput.PICKUP,p);var result=a.getCarried().copy();
        for(int i=0;i<20;i++)a.clicked(3,0,ContainerInput.PICKUP,p);
        h.assertTrue(result.is(Items.ENCHANTED_BOOK)&&a.getCarried().getCount()==1&&a.getSlot(0).getItem().isEmpty()&&a.getSlot(1).getItem().isEmpty()&&a.getSlot(2).getItem().getCount()==1,"rapid pickup after success cannot duplicate or charge twice");
        h.assertTrue(b.snapshot().known()==null&&ItemStack.matches(unknown,b.getSlot(3).getItem()),"other player still sees the same secret-free book");
        var shifted=b.quickMoveStack(other,3);h.assertTrue(ItemStack.matches(result,shifted),"swapped input order has identical real output");
        a.removed(p);a.removed(p);h.assertTrue(p.getInventory().countItem(Items.ENCHANTED_BOOK)==1&&p.getInventory().countItem(ResonanceItems.ATTUNED_CORE)==1,"close returns cursor/core exactly once");
        fill(b,level,recipe,1);level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        b.clicked(3,0,ContainerInput.QUICK_MOVE,other);h.assertTrue(b.getSlot(2).getItem().getCount()==1,"destroyed workstation refuses commit");b.removed(other);b.removed(other);
        h.assertTrue(other.getInventory().countItem(Items.ENCHANTED_BOOK)==3&&other.getInventory().countItem(ResonanceItems.ATTUNED_CORE)==1&&other.getInventory().countItem(Items.BOOK)==0,"break returns inputs once; preview never returned");h.succeed();
    }
    @GameTest public void capacityHotbarDoubleClickCloneAndPageSafety(GameTestHelper h) {
        var level=h.getLevel();var p=h.makeMockServerPlayerInLevel();var m=menu(level,h.absolutePos(new BlockPos(1,2,1)),p);var recipe=find(level,ResearchPolicy.Result::scroll);fill(m,level,recipe,6);
        var before=m.getSlot(3).getItem().copy();
        for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        m.clicked(3,0,ContainerInput.QUICK_MOVE,p);m.clicked(3,0,ContainerInput.SWAP,p);m.setCarried(new ItemStack(Items.DIAMOND));m.clicked(3,0,ContainerInput.PICKUP,p);
        h.assertTrue(m.getSlot(2).getItem().getCount()==6&&!ResearchWorldData.get(level.getServer()).knows(p.getUUID(),recipe),"full inventory, occupied hotbar and incompatible cursor refuse charge/discovery");
        m.setCarried(ItemStack.EMPTY);m.clicked(3,0,ContainerInput.THROW,p);p.setGameMode(GameType.CREATIVE);m.clicked(3,0,ContainerInput.CLONE,p);p.setGameMode(GameType.SURVIVAL);
        h.assertTrue(m.getCarried().isEmpty()&&ItemStack.matches(before,m.getSlot(3).getItem()),"throw and creative clone cannot extract display stack");
        m.setCarried(before.copy());m.clicked(4,0,ContainerInput.PICKUP_ALL,p);h.assertTrue(m.getCarried().getCount()==1&&m.getSlot(2).getItem().getCount()==6,"double click elsewhere excludes result preview");m.setCarried(ItemStack.EMPTY);
        p.getInventory().setItem(0,ItemStack.EMPTY);m.clicked(3,0,ContainerInput.SWAP,p);
        h.assertTrue(p.getInventory().getItem(0).is(ResearchContent.LOST_SCROLL)&&m.getSlot(2).getItem().getCount()==5,"empty hotbar receives real hidden scroll");
        p.getInventory().setItem(0,new ItemStack(ResearchContent.LOST_SCROLL,64));m.clicked(3,0,ContainerInput.SWAP,p);h.assertTrue(m.getSlot(2).getItem().getCount()==5,"full same-item hotbar cannot charge");
        p.getInventory().setItem(0,new ItemStack(ResearchContent.LOST_SCROLL,63));m.clicked(3,0,ContainerInput.QUICK_MOVE,p);h.assertTrue(p.getInventory().getItem(0).getCount()==64&&m.getSlot(2).getItem().getCount()==4,"shift merges exactly one into last available stack space");
        m.handle(p,new ResearchActionPayload(m.containerId,m.snapshot().revision(),1,0));m.clicked(3,0,ContainerInput.PICKUP,p);h.assertTrue(m.recordsOpen()&&m.getSlot(3).getItem().isEmpty()&&m.getSlot(2).getItem().getCount()==4,"record page cannot transact hidden slots");
        m.handle(p,new ResearchActionPayload(m.containerId,m.snapshot().revision(),2,0));m.clicked(3,40,ContainerInput.SWAP,p);h.assertTrue(p.getInventory().getItem(40).is(ResearchContent.LOST_SCROLL)&&m.getSlot(2).getItem().getCount()==3,"offhand gets one real output");
        p.getInventory().clearContent();m.removed(p);m.removed(p);h.assertTrue(p.getInventory().countItem(Items.ENCHANTED_BOOK)==2&&p.getInventory().countItem(ResonanceItems.ATTUNED_CORE)==3&&p.getInventory().countItem(ResearchContent.LOST_SCROLL)==0,"closing unclaimed preview returns only materials");h.succeed();
    }
    @GameTest public void largeNativeSnapshotFrozenAnswersAndThirdPartyInputs(GameTestHelper h) throws Exception {
        var level=h.getLevel();var data=ResearchWorldData.importJson(ResearchWorldData.get(level.getServer()).exportJson());var player=UUID.randomUUID();
        var books=vanillaBooks(level);int discovered=0;ResearchPolicy.Recipe pinned=null;
        outer:for(int i=0;i<books.size();i++)for(int j=i;j<books.size();j++) {
            var recipe=new ResearchPolicy.Recipe(books.get(i),books.get(j));data.discover(player,recipe,data.resolve(recipe));pinned=recipe;if(++discovered==2000)break outer;
        }
        h.assertTrue(discovered==2000&&data.exportJson().length()>65535,"large discovery snapshot exceeds a single NBT string");
        var tag=(net.minecraft.nbt.CompoundTag)ResearchWorldData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,data).getOrThrow();
        h.assertTrue(tag.contains("snapshot")&&!tag.contains("snapshot_json"),"native compound/list snapshot, no monolithic string");
        var bytes=new java.io.ByteArrayOutputStream();try(var output=new java.io.DataOutputStream(bytes)){net.minecraft.nbt.NbtIo.write(tag,output);}
        net.minecraft.nbt.CompoundTag disk;try(var input=new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))){disk=net.minecraft.nbt.NbtIo.read(input,net.minecraft.nbt.NbtAccounter.unlimitedHeap());}
        var restored=ResearchWorldData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,disk).getOrThrow();
        h.assertTrue(restored.records(player).size()==2000&&restored.resolve(pinned).equals(data.resolve(pinned)),"2000 discoveries survive binary NBT round-trip");
        var undiscovered=new ResearchPolicy.Recipe(books.getLast(),books.getLast());
        h.assertTrue(restored.resolve(undiscovered).equals(data.resolve(undiscovered)),"unvisited mapping also retains the frozen seed/rules across NBT conversion");
        var json=com.google.gson.JsonParser.parseString(data.exportJson()).getAsJsonObject();
        json.getAsJsonArray("catalogue").remove(0);var changed=ResearchWorldData.importJson(json.toString());
        h.assertTrue(changed.resolve(pinned).equals(data.resolve(pinned)),"saved discovered answer stays pinned despite catalogue changes");
        json.addProperty("version",999);boolean refused=false;try{ResearchWorldData.importJson(json.toString());}catch(IllegalStateException expected){refused=true;}
        h.assertTrue(refused,"unsupported version refuses silent reshuffle");
        var third=book(level,new ResearchPolicy.Book("differentenchant_tests:directed_probe",1));
        h.assertTrue(ResearchBooks.read(third)!=null,"third-party registry book accepted without a name whitelist");
        var pos=h.absolutePos(new BlockPos(1,2,1));var p=h.makeMockServerPlayerInLevel();var menu=menu(level,pos,p);var recipe=find(level,r->!r.scroll());fill(menu,level,recipe,1);
        var stale=action(menu);menu.getSlot(0).getItem().set(DataComponents.CUSTOM_NAME,Component.literal("changed in place"));menu.handle(p,stale);
        h.assertTrue(menu.getSlot(3).getItem().is(Items.BOOK)&&menu.getSlot(2).getItem().getCount()==1,"in-place component mutation refreshes preview without committing stale action");
        menu.getSlot(2).set(ItemStack.EMPTY);menu.broadcastChanges();menu.clicked(3,0,ContainerInput.PICKUP,p);h.assertTrue(!menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(3).getItem().isEmpty(),"missing core charges nothing");menu.removed(p);
        // Exhaust all actual vanilla single-book levels, separately from the 100k controlled-grade unit fixtures.
        var metadata=new TreeMap<String,ResearchPolicy.Input>();level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(holder->metadata.put(holder.key().identifier().toString(),new ResearchPolicy.Input(holder.value().getWeight(),holder.value().getMaxLevel())));
        long[][] counts=new long[4][7];var coverage=new ArrayList<Set<String>>();for(int i=0;i<4;i++)coverage.add(new TreeSet<>());
        for(int i=0;i<books.size();i++)for(int j=i;j<books.size();j++) {
            var pair=new ResearchPolicy.Recipe(books.get(i),books.get(j));int grade=ResearchPolicy.grade(ResearchPolicy.score(pair,metadata,ResearchPolicy.Rules.defaults()),ResearchPolicy.Rules.defaults());
            var answer=ResearchPolicy.resolve(12345,pair,metadata,data.catalogue(),ResearchPolicy.Rules.defaults());var different=ResearchPolicy.resolve(98765,pair,metadata,data.catalogue(),ResearchPolicy.Rules.defaults());
            counts[grade-1][0]++;counts[grade-1][answer.rarity()]++;if(answer.scroll())counts[grade-1][5]++;if(!answer.equals(different))counts[grade-1][6]++;coverage.get(grade-1).add(answer.id()+"@"+answer.level());
        }
        var report=new StringBuilder("grade,vanilla_recipe_pairs,tier_I,tier_II,tier_III,tier_IV,scrolls,changed_other_seed,unique_results\n");
        for(int grade=1;grade<=4;grade++){report.append(grade);for(long value:counts[grade-1])report.append(',').append(value);report.append(',').append(coverage.get(grade-1).size()).append('\n');}
        var path=java.nio.file.Path.of("../reports/research/vanilla-recipes.csv");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,report.toString());h.succeed();
    }
}
