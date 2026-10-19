package dev.differentenchant.lost;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.differentenchant.research.ResearchContent;
public final class ReflectionTargets {
    private static final TagKey<Block> TARGETS=TagKey.create(Registries.BLOCK,ResearchContent.id("reflection_targets"));
    private static final List<BlockPos> OFFSETS;
    static {var list=new ArrayList<BlockPos>();for(int x=-6;x<=6;x++)for(int y=-6;y<=6;y++)for(int z=-6;z<=6;z++)if(x*x+y*y+z*z<=36)list.add(new BlockPos(x,y,z));list.sort(Comparator.comparingDouble(p->p.distSqr(BlockPos.ZERO)));OFFSETS=List.copyOf(list);}
    public static List<BlockPos> scan(ServerPlayer p){
        if(!p.isUnderWater()||p.isSpectator()||BatchEffects.level(p,p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD),"water_reflection")==0)return List.of();
        var level=p.level();var found=new ArrayList<BlockPos>();for(var delta:OFFSETS){var pos=p.blockPosition().offset(delta);if(level.hasChunkAt(pos)&&level.getBlockState(pos).is(TARGETS)){found.add(pos);if(found.size()==8)break;}}return List.copyOf(found);
    }
    public record Preview(Identifier dimension,List<BlockPos> positions) implements CustomPacketPayload {
        public Preview{positions=List.copyOf(positions);if(positions.size()>8)throw new IllegalArgumentException("Too many reflection targets");}
        public static final Type<Preview> TYPE=new Type<>(ResearchContent.id("reflection_targets"));
        public static final StreamCodec<FriendlyByteBuf,Preview> CODEC=StreamCodec.of((b,p)->{b.writeIdentifier(p.dimension);b.writeVarInt(p.positions.size());for(var pos:p.positions)b.writeBlockPos(pos);},b->{var dimension=b.readIdentifier();int n=b.readVarInt();if(n<0||n>8)throw new IllegalArgumentException("Reflection target bound");var list=new ArrayList<BlockPos>();for(int i=0;i<n;i++)list.add(b.readBlockPos());return new Preview(dimension,list);});
        @Override public Type<Preview> type(){return TYPE;}
    }
    public static void tick(ServerPlayer p){net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,new Preview(p.level().dimension().identifier(),scan(p)));}
    private ReflectionTargets(){}
}
