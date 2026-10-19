package dev.differentenchant.client;

import java.util.*;
import dev.differentenchant.workstation.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

/** Four sparse native glyphs per second. No particle packets, custom textures or server actors. */
public final class ConvergingMagic {
    public record Mote(int corner,Particle particle) {}
    private static final Map<BlockPos,List<Mote>> ACTIVE=new HashMap<>();
    private static ClientLevel world;
    public static List<Mote> snapshot(BlockPos core){return List.copyOf(ACTIVE.getOrDefault(core,List.of()));}
    private static void clear(List<Mote> motes){motes.forEach(m->m.particle().remove());motes.clear();}
    private static boolean visible(Minecraft client,BlockPos pos){return client.level!=null&&client.player!=null&&client.player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<1024&&WorkstationStructure.controller(client.level,pos,WorkstationStructure.Kind.DISENCHANT)!=null;}
    private static void partTick(WorkstationPartEntity part) {
        if(part.kind()!=WorkstationStructure.Kind.DISENCHANT)return;var client=Minecraft.getInstance();
        if(part.getLevel()!=client.level||!visible(client,part.getBlockPos())||client.screen!=null)return;
        var motes=ACTIVE.computeIfAbsent(part.getBlockPos().immutable(),p->new ArrayList<>());motes.removeIf(m->!m.particle().isAlive());
        if(Math.floorMod(client.level.getGameTime(),20)!=0)return;
        double x=part.getBlockPos().getX()+.5,y=part.getBlockPos().getY()+1.9,z=part.getBlockPos().getZ()+.5;
        for(int index:new int[]{9,11,15,17}) {
            var corner=WorkstationStructure.position(part.getBlockPos(),index);double sx=corner.getX()+.5,sy=corner.getY()+.65,sz=corner.getZ()+.5;
            var particle=client.particleEngine.createParticle(ParticleTypes.ENCHANT,x,y,z,sx-x,sy-y,sz-z);
            if(particle!=null){particle.setLifetime(40);particle.scale(.8f);if(particle instanceof SingleQuadParticle quad)quad.setColor(.58f,.23f,.82f);motes.add(new Mote(index,particle));}
        }
    }
    private static void tick(Minecraft client) {
        if(world!=client.level){ACTIVE.values().forEach(ConvergingMagic::clear);ACTIVE.clear();world=client.level;}
        ACTIVE.entrySet().removeIf(entry->{if(!visible(client,entry.getKey())||client.screen!=null){clear(entry.getValue());return true;}entry.getValue().removeIf(m->!m.particle().isAlive());return false;});
    }
    public static void initialize(){WorkstationPartEntity.clientTick=ConvergingMagic::partTick;net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(ConvergingMagic::tick);}
    private ConvergingMagic(){}
}
