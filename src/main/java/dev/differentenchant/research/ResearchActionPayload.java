package dev.differentenchant.research;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Only an intent and a menu revision; never an output, recipe, cost or seed. */
public record ResearchActionPayload(int containerId,long revision,int action,int offset) implements CustomPacketPayload {
    public static final Type<ResearchActionPayload> TYPE=new Type<>(ResearchContent.id("research_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ResearchActionPayload> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.containerId);b.writeLong(p.revision);b.writeVarInt(p.action);b.writeVarInt(p.offset);},b->new ResearchActionPayload(b.readVarInt(),b.readLong(),b.readVarInt(),b.readVarInt()));
    @Override public Type<ResearchActionPayload> type(){return TYPE;}
}
