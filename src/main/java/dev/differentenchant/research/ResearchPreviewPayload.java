package dev.differentenchant.research;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Unknown results are absent. Records contain only the receiving player's discoveries, at most three. */
public record ResearchPreviewPayload(int containerId,long revision,ItemStack a,ItemStack b,ItemStack core,int grade,int status,
        ResearchPolicy.Result known,boolean records,int offset,int total,List<Row> rows) implements CustomPacketPayload {
    public record Row(String recipe,ResearchPolicy.Result result){}
    public ResearchPreviewPayload {a=a.copy();b=b.copy();core=core.copy();rows=List.copyOf(rows);}
    public static final Type<ResearchPreviewPayload> TYPE=new Type<>(ResearchContent.id("research_preview"));
    private static void result(RegistryFriendlyByteBuf b,ResearchPolicy.Result r){b.writeUtf(r.id(),256);b.writeVarInt(r.level());b.writeVarInt(r.rarity());b.writeBoolean(r.scroll());}
    private static ResearchPolicy.Result result(RegistryFriendlyByteBuf b){return new ResearchPolicy.Result(b.readUtf(256),b.readVarInt(),b.readVarInt(),b.readBoolean());}
    public static final StreamCodec<RegistryFriendlyByteBuf,ResearchPreviewPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.containerId);b.writeLong(p.revision);ItemStack.OPTIONAL_STREAM_CODEC.encode(b,p.a);ItemStack.OPTIONAL_STREAM_CODEC.encode(b,p.b);ItemStack.OPTIONAL_STREAM_CODEC.encode(b,p.core);
        b.writeVarInt(p.grade);b.writeVarInt(p.status);b.writeBoolean(p.known!=null);if(p.known!=null)result(b,p.known);
        b.writeBoolean(p.records);b.writeVarInt(p.offset);b.writeVarInt(p.total);b.writeVarInt(p.rows.size());for(var row:p.rows){b.writeUtf(row.recipe,1024);result(b,row.result);}
    },b->{
        int id=b.readVarInt();long revision=b.readLong();var a=ItemStack.OPTIONAL_STREAM_CODEC.decode(b);var book=ItemStack.OPTIONAL_STREAM_CODEC.decode(b);var core=ItemStack.OPTIONAL_STREAM_CODEC.decode(b);
        int grade=b.readVarInt(),status=b.readVarInt();var known=b.readBoolean()?result(b):null;boolean records=b.readBoolean();int offset=b.readVarInt(),total=b.readVarInt(),size=b.readVarInt();
        if(size<0||size>3)throw new IllegalArgumentException("Invalid research record page");var rows=new ArrayList<Row>();for(int i=0;i<size;i++)rows.add(new Row(b.readUtf(1024),result(b)));
        return new ResearchPreviewPayload(id,revision,a,book,core,grade,status,known,records,offset,total,rows);
    });
    @Override public Type<ResearchPreviewPayload> type(){return TYPE;}
}
