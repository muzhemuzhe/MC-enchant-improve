package dev.differentenchant.enchanting;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record DirectedPreviewPayload(int containerId,int quote,long revision,boolean directed,ItemStack input,ItemStack reagent,
        int upgrades,int levels,boolean canEnchant,List<Row> rows) implements CustomPacketPayload {
    public record Row(Identifier id,int current,int target,int max,int nextCost,boolean plus,boolean minus,int blocked) {}
    public DirectedPreviewPayload { input=input.copy();reagent=reagent.copy();rows=List.copyOf(rows); }
    public static final Type<DirectedPreviewPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("differentenchant","directed_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DirectedPreviewPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.containerId);b.writeVarInt(p.quote);b.writeLong(p.revision);b.writeBoolean(p.directed);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(b,p.input);ItemStack.OPTIONAL_STREAM_CODEC.encode(b,p.reagent);
        b.writeVarInt(p.upgrades);b.writeVarInt(p.levels);b.writeBoolean(p.canEnchant);b.writeVarInt(p.rows.size());
        for(var r:p.rows) {b.writeIdentifier(r.id);b.writeVarInt(r.current);b.writeVarInt(r.target);b.writeVarInt(r.max);b.writeVarInt(r.nextCost);b.writeBoolean(r.plus);b.writeBoolean(r.minus);b.writeVarInt(r.blocked);}
    },b->{
        int id=b.readVarInt(),quote=b.readVarInt();long revision=b.readLong();boolean mode=b.readBoolean();
        ItemStack input=ItemStack.OPTIONAL_STREAM_CODEC.decode(b),reagent=ItemStack.OPTIONAL_STREAM_CODEC.decode(b);
        int upgrades=b.readVarInt(),levels=b.readVarInt();boolean can=b.readBoolean();int size=b.readVarInt();
        if(size<0 || size>16384) throw new IllegalArgumentException("Invalid directed list size");
        var rows=new ArrayList<Row>();for(int i=0;i<size;i++) rows.add(new Row(b.readIdentifier(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readVarInt()));
        return new DirectedPreviewPayload(id,quote,revision,mode,input,reagent,upgrades,levels,can,rows);
    });
    @Override public Type<DirectedPreviewPayload> type() {return TYPE;}
}
