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
        h.assertTrue(menu.snapshot().known()==null,"unseen real result never disclosed");var request=action(menu);menu.handle(p,request);menu.handle(p,request);
        h.assertTrue(menu.getSlot(3).getItem().is(ResearchContent.LOST_SCROLL)&&menu.getSlot(2).getItem().getCount()==3,"fixed scroll and exactly one core on replay");
        h.assertTrue(ItemStack.matches(a,menu.getSlot(0).getItem())&&ItemStack.matches(b,menu.getSlot(1).getItem()),"failure preserves full book components");
        h.assertTrue(menu.snapshot().known()!=null&&menu.snapshot().known().scroll(),"own discovered failure is now previewed");
        var blocked=action(menu);menu.handle(p,blocked);h.assertTrue(menu.getSlot(2).getItem().getCount()==3,"occupied output blocks all charges");
        menu.quickMoveStack(p,3);menu.handle(p,request);h.assertTrue(menu.getSlot(3).getItem().isEmpty()&&menu.getSlot(2).getItem().getCount()==3,"old packet stays stale after output collection");
        menu.handle(p,action(menu));h.assertTrue(menu.getSlot(3).getItem().is(ResearchContent.LOST_SCROLL)&&menu.getSlot(2).getItem().getCount()==2,"repeat same fixed failure costs another core");
        var restored=ResearchWorldData.importJson(data.exportJson());h.assertTrue(restored.knows(p.getUUID(),scroll)&&restored.resolve(new ResearchPolicy.Recipe(scroll.b(),scroll.a())).equals(data.resolve(scroll)),"world rules/answer/player discovery round-trip");
        h.assertTrue(!restored.knows(UUID.randomUUID(),scroll),"discoveries remain UUID isolated");
        h.assertTrue(data.resolve(scroll).equals(restored.resolve(scroll)),"research order and save never reassign");
        menu.removed(p);menu.removed(p);h.assertTrue(p.getInventory().countItem(ResearchContent.LOST_SCROLL)==2,"close returns second output once");
        var invalid=menu(level,pos,p);fill(invalid,level,scroll,1);var extra=level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.withDefaultNamespace("luck_of_the_sea")).orElseThrow();
        invalid.getSlot(0).getItem().enchant(extra,1);invalid.broadcastChanges();invalid.handle(p,action(invalid));
        h.assertTrue(invalid.snapshot().status()==1&&invalid.getSlot(2).getItem().getCount()==1&&invalid.getSlot(3).getItem().isEmpty(),"multi-book no hidden selection or charge");invalid.removed(p);h.succeed();
    }
    @GameTest public void successTwoPlayersShiftCloseAndDestroyedWorkstation(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,2,1));var p=h.makeMockServerPlayerInLevel();var other=h.makeMockServerPlayerInLevel();
        h.assertTrue(!p.getUUID().equals(other.getUUID()),"independent player identities");var a=menu(level,pos,p);var b=menu(level,pos,other);var recipe=find(level,r->!r.scroll());
        fill(a,level,recipe,2);fill(b,level,new ResearchPolicy.Recipe(recipe.b(),recipe.a()),2);
        a.handle(p,action(a));var result=a.getSlot(3).getItem().copy();
        h.assertTrue(a.snapshot().known()!=null,"completed visible output remains named after inputs are consumed");
        h.assertTrue(a.getSlot(0).getItem().isEmpty()&&a.getSlot(1).getItem().isEmpty()&&a.getSlot(2).getItem().getCount()==1,"success consumes both books plus one core");
        h.assertTrue(b.snapshot().known()==null&&!b.getSlot(0).getItem().isEmpty(),"other player's materials/discovery untouched");b.handle(other,action(b));
        h.assertTrue(ItemStack.matches(result,b.getSlot(3).getItem()),"same objective result for both players and swapped inputs");
        var forged=new ResearchActionPayload(a.containerId,a.snapshot().revision(),0,0);a.handle(other,forged);h.assertTrue(a.getSlot(2).getItem().getCount()==1,"foreign player action refused");
        a.quickMoveStack(p,3);a.removed(p);a.removed(p);h.assertTrue(p.getInventory().countItem(Items.ENCHANTED_BOOK)==1&&p.getInventory().countItem(ResonanceItems.ATTUNED_CORE)==1,"shift output and close do not double-return");
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());h.assertTrue(!b.stillValid(other),"destroyed block invalidates menu");
        b.handle(other,new ResearchActionPayload(b.containerId,b.snapshot().revision(),0,0));b.removed(other);b.removed(other);
        h.assertTrue(other.getInventory().countItem(Items.ENCHANTED_BOOK)==1&&other.getInventory().countItem(ResonanceItems.ATTUNED_CORE)==1,"break returns each remaining item once; block has no duplicate inventory");
        var transfer=menu(level,pos,p);int before=p.getInventory().countItem(ResonanceItems.ATTUNED_CORE);
        for(int i=4;i<40;i++)if(transfer.getSlot(i).getItem().is(ResonanceItems.ATTUNED_CORE)){transfer.quickMoveStack(p,i);break;}
        h.assertTrue(transfer.getSlot(2).getItem().getCount()==before,"shift core enters correct reagent slot");transfer.removed(p);h.succeed();
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
        h.assertTrue(menu.getSlot(3).getItem().isEmpty()&&menu.getSlot(2).getItem().getCount()==1,"in-place component mutation rejects stale revision");
        menu.getSlot(2).set(ItemStack.EMPTY);menu.handle(p,action(menu));h.assertTrue(!menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(3).getItem().isEmpty(),"missing core charges nothing");menu.removed(p);
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
