package dev.differentenchant.research;

import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.item.enchantment.Enchantment;

/** Objective world mapping + UUID-isolated discoveries. No client access or reload invalidation. */
public final class ResearchWorldData extends SavedData {
    // NBT has byte booleans; DynamicOps converts these to JSON numbers, not JSON boolean tokens.
    private static final Gson JSON=new com.google.gson.GsonBuilder().registerTypeAdapter(boolean.class,
        (com.google.gson.JsonDeserializer<Boolean>)(json,type,context)->{
            if(json.getAsJsonPrimitive().isNumber()) {
                int value=json.getAsInt();if(value!=0&&value!=1)throw new IllegalArgumentException("Invalid saved boolean");return value==1;
            }
            return json.getAsBoolean();
        }).create();
    private static final class State {
        int version=ResearchPolicy.VERSION;
        int catalogueRevision;
        boolean initialized;
        long seed;
        ResearchPolicy.Rules rules;
        List<ResearchPolicy.Entry> catalogue=new ArrayList<>();
        Map<String,ResearchPolicy.Input> inputs=new TreeMap<>();
        Map<String,ResearchPolicy.Result> outcomes=new TreeMap<>();
        Map<String,Set<String>> discoveries=new TreeMap<>();
    }
    private State state=new State();
    private static final Codec<ResearchWorldData> TREE_CODEC=Codec.PASSTHROUGH.xmap(
        dynamic->decode(dynamic.convert(com.mojang.serialization.JsonOps.INSTANCE).getValue().toString()),
        data->new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE,JSON.toJsonTree(data.state))).fieldOf("snapshot").codec();
    public static final Codec<ResearchWorldData> CODEC=Codec.either(TREE_CODEC,Codec.STRING.xmap(ResearchWorldData::decode,ResearchWorldData::encode).fieldOf("snapshot_json").codec())
        .xmap(either->either.map(data->data,data->data),com.mojang.datafixers.util.Either::left);
    public static final SavedDataType<ResearchWorldData> TYPE=new SavedDataType<>(ResearchContent.id("lost_research"),ResearchWorldData::new,CODEC,null);
    private static ResearchWorldData decode(String json) {
        var data=new ResearchWorldData();data.state=Objects.requireNonNull(JSON.fromJson(json,State.class));
        if(data.state.version!=ResearchPolicy.VERSION)throw new IllegalStateException("Unsupported research save version "+data.state.version+"; refusing to reshuffle world");
        if(!data.state.initialized||data.state.rules==null||data.state.catalogue.isEmpty())throw new IllegalStateException("Incomplete research snapshot; refusing to initialize over existing data");
        for(var keys:data.state.discoveries.values())for(var key:keys)if(!data.state.outcomes.containsKey(key))throw new IllegalStateException("Discovery missing its fixed outcome: "+key);
        return data;
    }
    private static String encode(ResearchWorldData data){return JSON.toJson(data.state);}
    public String exportJson(){return encode(this);}
    public static ResearchWorldData importJson(String json){return decode(json);}
    public static ResearchWorldData get(MinecraftServer server) {
        var data=ResearchPersistence.load(server,TYPE,()->{var value=new ResearchWorldData();value.initialize(server);return value;});
        data.migrateBatchTwo(server.registryAccess());return data;
    }
    private void initialize(MinecraftServer server) {
        if(state.initialized)return;
        var registry=server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var catalogue=new TreeMap<String,ResearchPolicy.Entry>();
        for(int tier=1;tier<=4;tier++) {
            var tag=TagKey.create(Registries.ENCHANTMENT,ResearchContent.id("lost_tier_"+tier));
            for(var holder:registry.get(tag).orElseThrow(()->new IllegalStateException("Missing lost rarity tag")).stream().toList()) {
                String id=holder.unwrapKey().orElseThrow().identifier().toString();
                if(catalogue.put(id,new ResearchPolicy.Entry(id,tier,holder.value().getMaxLevel()))!=null)throw new IllegalStateException("Lost enchantment has multiple rarity tiers: "+id);
            }
        }
        state.seed=server.overworld().getSeed();state.rules=ResearchConfig.current();state.catalogue=List.copyOf(catalogue.values());
        registry.listElements().forEach(holder->{var e=holder.value();state.inputs.put(holder.key().identifier().toString(),new ResearchPolicy.Input(e.getWeight(),e.getMaxLevel()));});
        state.initialized=true;state.catalogueRevision=1;setDirty();
    }
    /** One explicit expansion. Pinned outcomes and UUID discoveries are never recomputed or removed. */
    public boolean migrateBatchTwo(net.minecraft.core.RegistryAccess registries) {
        if(state.catalogueRevision>=1)return false;
        var registry=registries.lookupOrThrow(Registries.ENCHANTMENT);
        var additions=registry.get(TagKey.create(Registries.ENCHANTMENT,ResearchContent.id("batch_two")));
        if(additions.isEmpty())return false;
        var merged=new TreeMap<String,ResearchPolicy.Entry>();state.catalogue.forEach(e->merged.put(e.id(),e));
        for(var holder:additions.get()) {
            int tier=0;for(int n=1;n<=4;n++)if(holder.is(TagKey.create(Registries.ENCHANTMENT,ResearchContent.id("lost_tier_"+n)))){if(tier!=0)throw new IllegalStateException("Duplicate expansion tier");tier=n;}
            if(tier==0)throw new IllegalStateException("Expansion result has no research tier");
            String id=holder.unwrapKey().orElseThrow().identifier().toString();merged.putIfAbsent(id,new ResearchPolicy.Entry(id,tier,holder.value().getMaxLevel()));
        }
        state.catalogue=List.copyOf(merged.values());state.catalogueRevision=1;setDirty();return true;
    }
    /** Newly installed input enchantments can be appended; frozen output pools and old input metadata stay fixed. */
    public void ensureInput(String id,Enchantment enchantment) {
        if(!state.inputs.containsKey(id)){state.inputs.put(id,new ResearchPolicy.Input(enchantment.getWeight(),enchantment.getMaxLevel()));setDirty();}
    }
    public void observeNewInputs(net.minecraft.core.RegistryAccess registries) {
        registries.lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(holder->ensureInput(holder.key().identifier().toString(),holder.value()));
    }
    public int grade(ResearchPolicy.Recipe recipe){return ResearchPolicy.grade(ResearchPolicy.score(recipe,state.inputs,state.rules),state.rules);}
    public ResearchPolicy.Result resolve(ResearchPolicy.Recipe recipe) {
        var pinned=state.outcomes.get(recipe.key());return pinned!=null?pinned:ResearchPolicy.resolve(state.seed,recipe,state.inputs,state.catalogue,state.rules);
    }
    public boolean knows(UUID player,ResearchPolicy.Recipe recipe){return state.discoveries.getOrDefault(player.toString(),Set.of()).contains(recipe.key());}
    public void discover(UUID player,ResearchPolicy.Recipe recipe,ResearchPolicy.Result result) {
        var previous=state.outcomes.putIfAbsent(recipe.key(),result);
        if(previous!=null&&!previous.equals(result))throw new IllegalStateException("Pinned objective recipe changed");
        state.discoveries.computeIfAbsent(player.toString(),key->new TreeSet<>()).add(recipe.key());setDirty();
    }
    public List<String> records(UUID player){return state.discoveries.getOrDefault(player.toString(),Set.of()).stream().sorted().toList();}
    public ResearchPolicy.Result recorded(String key){return state.outcomes.get(key);}
    public List<ResearchPolicy.Entry> catalogue(){return List.copyOf(state.catalogue);}
    /** Future Lv.3 integration boundary: identity only; performs no machine mutation or upgrade. */
    public boolean isLostResult(String enchantmentId){return state.catalogue.stream().anyMatch(e->e.id().equals(enchantmentId));}
}
