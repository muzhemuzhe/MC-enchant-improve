package dev.differentenchant.enchanting;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Commands contain no targets, costs, complete plans or output items. */
public record DirectedActionPayload(int containerId,long revision,int action,String enchantment) implements CustomPacketPayload {
    public static final int RANDOM=0,DIRECTED=1,ADD=2,REMOVE=3,ENCHANT=4;
    public static final Type<DirectedActionPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("differentenchant","directed_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DirectedActionPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.containerId);b.writeLong(p.revision);b.writeVarInt(p.action);b.writeUtf(p.enchantment,256);
    },b->new DirectedActionPayload(b.readVarInt(),b.readLong(),b.readVarInt(),b.readUtf(256)));
    @Override public Type<DirectedActionPayload> type() { return TYPE; }
}
