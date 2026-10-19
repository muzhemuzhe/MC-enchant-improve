package dev.differentenchant.lost;
import dev.differentenchant.research.ResearchContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.core.Holder;
public final class BatchEffects {
    public static int level(Entity entity,ItemStack stack,String path){return entity.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(ResearchContent.id(path)).map(h->EnchantmentHelper.getItemEnchantmentLevel(h,stack)).orElse(0);}
    public static int level(net.minecraft.core.RegistryAccess registry,ItemStack stack,String path){return registry.lookupOrThrow(Registries.ENCHANTMENT).get(ResearchContent.id(path)).map(h->EnchantmentHelper.getItemEnchantmentLevel(h,stack)).orElse(0);}
    public static void borrowFire(Entity victim,DamageSource source,ItemStack weapon) {
        if(!(source.getEntity() instanceof ServerPlayer p)||source.getDirectEntity()!=p||!(victim instanceof LivingEntity)||weapon==null||!p.isOnFire()||victim.fireImmune())return;
        int ticks=BatchRules.transferredFire(p.getRemainingFireTicks(),level(p,weapon,"borrowed_flame"));if(ticks==0)return;
        victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(),ticks));p.clearFire();
    }
    public static boolean silentStep(Holder<GameEvent> event,GameEvent.Context context) {
        return event.is(GameEvent.STEP)&&context.sourceEntity() instanceof ServerPlayer p&&p.onGround()&&!p.isSprinting()&&!p.isSwimming()&&!p.isFallFlying()&&!p.getAbilities().flying&&level(p,p.getItemBySlot(EquipmentSlot.FEET),"hush_step")>0;
    }
    public static void airControl(Player p,Vec3 input) {
        if(!p.isShiftKeyDown()||p.onGround()||p.isSpectator()||p.getAbilities().flying||p.isFallFlying()||p.isInWater()||p.isInLava()||p.isSwimming()||p.isPassenger()||p.onClimbable()||p.isAutoSpinAttack())return;
        int l=level(p,p.getItemBySlot(EquipmentSlot.FEET),"paper_kite");if(l==0)return;
        var old=p.getDeltaMovement();double yaw=Math.toRadians(p.getYRot()),s=Math.sin(yaw),c=Math.cos(yaw);var config=AirControlConfig.current;
        double x=old.x*config.drag(l)+(input.x*c-input.z*s)*config.control(l),z=old.z*config.drag(l)+(input.z*c+input.x*s)*config.control(l);
        // Only brake/steer ordinary motion. Never amplify an external high-speed launch or alter Y.
        double cap=Math.max(.3,old.horizontalDistance()),speed=Math.sqrt(x*x+z*z);if(speed>cap){x*=cap/speed;z*=cap/speed;}p.setDeltaMovement(x,old.y,z);
    }
    public static void initialize() {
        StarStep.initialize();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{for(var p:server.getPlayerList().getPlayers())StarBridges.tick(p);});
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->StarBridges.forget(handler.player));
        AirControlConfig.load();
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(ReflectionTargets.Preview.TYPE,ReflectionTargets.Preview.CODEC);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{for(var p:server.getPlayerList().getPlayers())if(p.tickCount%20==0)ReflectionTargets.tick(p);});
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(AirControlConfig.Settings.TYPE,AirControlConfig.Settings.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->sender.sendPacket(AirControlConfig.current));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(TideVortices::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->{TideVortices.clear();StarBridges.clear();});
    }
    private BatchEffects(){}
}
