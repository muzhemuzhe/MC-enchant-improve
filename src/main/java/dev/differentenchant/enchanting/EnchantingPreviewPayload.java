package dev.differentenchant.enchanting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Only disclosed enchantments cross the wire; the complete offers stay in the server menu. */
public record EnchantingPreviewPayload(int containerId,int quote,ItemStack input,List<Offer> offers) implements CustomPacketPayload {
    public record Entry(Identifier id,int level) {}
    public record Offer(List<Entry> revealed,boolean hidden) {
        public Offer { revealed=List.copyOf(revealed); }
    }
    public EnchantingPreviewPayload {
        input=input.copy(); offers=List.copyOf(offers);
        if(offers.size()!=3 || offers.stream().anyMatch(o->o.revealed().size()>1024)) throw new IllegalArgumentException("Invalid preview offers");
    }
    public static final Type<EnchantingPreviewPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("differentenchant","preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EnchantingPreviewPayload> CODEC=StreamCodec.of((buf,value)->{
        buf.writeVarInt(value.containerId); buf.writeVarInt(value.quote);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf,value.input);
        for(var offer:value.offers) {
            buf.writeBoolean(offer.hidden); buf.writeVarInt(offer.revealed.size());
            for(var entry:offer.revealed) { buf.writeIdentifier(entry.id); buf.writeVarInt(entry.level); }
        }
    },buf->{
        int id=buf.readVarInt(), quote=buf.readVarInt();
        ItemStack input=ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        var offers=new ArrayList<Offer>();
        for(int i=0;i<3;i++) {
            boolean hidden=buf.readBoolean(); int size=buf.readVarInt();
            // Generous protocol bound, independent of the current policy's small result cap.
            if(size<0 || size>1024) throw new IllegalArgumentException("Invalid preview size");
            var entries=new ArrayList<Entry>();
            for(int j=0;j<size;j++) entries.add(new Entry(buf.readIdentifier(),buf.readVarInt()));
            offers.add(new Offer(entries,hidden));
        }
        return new EnchantingPreviewPayload(id,quote,input,offers);
    });
    @Override public Type<EnchantingPreviewPayload> type() { return TYPE; }
}
