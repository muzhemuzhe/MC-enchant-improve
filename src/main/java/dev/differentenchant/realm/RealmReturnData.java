package dev.differentenchant.realm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.*;

/** World-scoped, per-player return anchors survive disconnect and full server restart. */
public final class RealmReturnData extends SavedData {
    public record Anchor(String dimension,double x,double y,double z,float yaw,float pitch) {
        public static final Codec<Anchor> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Anchor::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(Anchor::x),Codec.DOUBLE.fieldOf("y").forGetter(Anchor::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Anchor::z),Codec.FLOAT.fieldOf("yaw").forGetter(Anchor::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(Anchor::pitch)).apply(i,Anchor::new));
    }
    private final Map<String,Anchor> anchors;
    private RealmReturnData(Map<String,Anchor> anchors) {this.anchors=new HashMap<>(anchors);}
    public static final Codec<RealmReturnData> CODEC=Codec.unboundedMap(Codec.STRING,Anchor.CODEC)
            .xmap(RealmReturnData::new,d->Map.copyOf(d.anchors)).fieldOf("anchors").codec();
    public static final SavedDataType<RealmReturnData> TYPE=new SavedDataType<>(RealmContent.id("realm_return"),()->new RealmReturnData(Map.of()),CODEC,null);
    public static RealmReturnData get(MinecraftServer server) {
        return dev.differentenchant.research.ResearchPersistence.load(server,TYPE,()->new RealmReturnData(Map.of()));
    }
    public Anchor get(UUID player) {return anchors.get(player.toString());}
    public void put(UUID player,Anchor anchor) {anchors.put(player.toString(),anchor);setDirty();}
    public void remove(UUID player) {anchors.remove(player.toString());setDirty();}
}
