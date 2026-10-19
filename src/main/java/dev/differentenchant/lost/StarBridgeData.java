package dev.differentenchant.lost;
import java.util.*;
import com.mojang.serialization.Codec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.*;
import dev.differentenchant.research.*;
public final class StarBridgeData extends SavedData {
    private final Map<String,Long> readyAt;
    private StarBridgeData(Map<String,Long> values){readyAt=new HashMap<>(values);}
    public static final Codec<StarBridgeData> CODEC=Codec.unboundedMap(Codec.STRING,Codec.LONG).fieldOf("ready_at").xmap(StarBridgeData::new,d->d.readyAt).codec();
    public static final SavedDataType<StarBridgeData> TYPE=new SavedDataType<>(ResearchContent.id("star_bridge_cooldowns"),()->new StarBridgeData(Map.of()),CODEC,null);
    public static StarBridgeData get(MinecraftServer server){return ResearchPersistence.load(server,TYPE,()->new StarBridgeData(Map.of()));}
    public boolean ready(UUID player,long now){return now>=readyAt.getOrDefault(player.toString(),0L);}
    public void spend(UUID player,long now){readyAt.put(player.toString(),now+400);setDirty();}
}
