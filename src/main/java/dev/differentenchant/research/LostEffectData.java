package dev.differentenchant.research;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.*;

/** Last Page cooldown survives equipment swaps, reconnects, dimensions and world restarts. */
public final class LostEffectData extends SavedData {
    private final Map<String,Long> cooldowns;
    private LostEffectData(Map<String,Long> values){cooldowns=new HashMap<>(values);}
    public static final Codec<LostEffectData> CODEC=Codec.unboundedMap(Codec.STRING,Codec.LONG).fieldOf("ready_at").xmap(LostEffectData::new,d->d.cooldowns).codec();
    public static final SavedDataType<LostEffectData> TYPE=new SavedDataType<>(ResearchContent.id("lost_effect_cooldowns"),()->new LostEffectData(Map.of()),CODEC,null);
    public static LostEffectData get(MinecraftServer server){return ResearchPersistence.load(server,TYPE,()->new LostEffectData(Map.of()));}
    public boolean ready(UUID player,long now){return now>=cooldowns.getOrDefault(player.toString(),0L);}
    public void spend(UUID player,long now){cooldowns.put(player.toString(),now+24000L);setDirty();}
}
