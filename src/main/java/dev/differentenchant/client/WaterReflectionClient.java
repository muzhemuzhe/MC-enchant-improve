package dev.differentenchant.client;
import java.util.*;
import dev.differentenchant.lost.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
/** Client-only, non-colliding glowing display outlines; no world block changes or server actors. */
public final class WaterReflectionClient {
    private static final List<Display.BlockDisplay> MARKERS=new ArrayList<>();
    private static java.util.List<net.minecraft.core.BlockPos> positions=List.of();
    private static net.minecraft.resources.Identifier dimension;
    private static long expires;
    private static int nextId=-2000000000;
    public static void accept(ReflectionTargets.Preview payload){
        var client=net.minecraft.client.Minecraft.getInstance();if(client.level==null||!client.level.dimension().identifier().equals(payload.dimension()))return;
        expires=client.level.getGameTime()+30;if(payload.positions().equals(positions)&&Objects.equals(dimension,payload.dimension()))return;
        clear();positions=payload.positions();dimension=payload.dimension();
        for(var pos:positions){var marker=new Display.BlockDisplay(EntityType.BLOCK_DISPLAY,client.level){@Override public boolean isCurrentlyGlowing(){return true;}};
            marker.setId(nextId++);marker.setPos(pos.getX()+.08,pos.getY()+.08,pos.getZ()+.08);marker.setBlockState(Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());marker.setGlowColorOverride(0x75c5d4);
            marker.setTransformation(new com.mojang.math.Transformation(new org.joml.Vector3f(),new org.joml.Quaternionf(),new org.joml.Vector3f(.84f),new org.joml.Quaternionf()));client.level.addEntity(marker);MARKERS.add(marker);}
    }
    public static int count(){return MARKERS.size();}
    public static void tick(net.minecraft.client.Minecraft client){if(client.level==null||client.player==null||!client.player.isUnderWater()||client.level.getGameTime()>expires||!client.level.dimension().identifier().equals(dimension)||BatchEffects.level(client.player,client.player.getItemBySlot(EquipmentSlot.HEAD),"water_reflection")==0)clear();}
    private static void clear(){for(var e:MARKERS)e.discard();MARKERS.clear();positions=List.of();dimension=null;}
    public static void initialize(){net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(ReflectionTargets.Preview.TYPE,(payload,context)->accept(payload));net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(WaterReflectionClient::tick);}
    private WaterReflectionClient(){}
}
