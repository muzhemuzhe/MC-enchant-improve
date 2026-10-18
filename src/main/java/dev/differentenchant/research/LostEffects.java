package dev.differentenchant.research;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Only effects needing state/events live here; the recipe generator knows none of these names. */
public final class LostEffects {
    public static int level(ServerPlayer player,ItemStack stack,String path) {
        return player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(ResearchContent.id(path))
            .map(holder->EnchantmentHelper.getItemEnchantmentLevel(holder,stack)).orElse(0);
    }
    public static void tick(ServerPlayer player) {
        if(!player.isAlive()||player.isSpectator())return;
        int sight=level(player,player.getItemBySlot(EquipmentSlot.HEAD),"lantern_sight");
        if(sight>0&&player.isUnderWater())player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,240,0,true,false));
        int margin=level(player,player.getItemBySlot(EquipmentSlot.CHEST),"living_margin");
        if(margin>0&&player.tickCount%200==0&&player.getFoodData().getFoodLevel()>=18&&player.getHealth()<player.getMaxHealth()) {
            player.heal(margin);player.getFoodData().addExhaustion(margin*.5f);
        }
        int ward=level(player,player.getUseItem(),"warding_focus");
        if(ward>0&&player.isBlocking())player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,40,ward-1,true,false));
    }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server->{for(var player:server.getPlayerList().getPlayers())if(player.tickCount%20==0)tick(player);});
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity,source,amount)->{
            if(!(entity instanceof ServerPlayer player)||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                ||level(player,player.getItemBySlot(EquipmentSlot.CHEST),"last_page")==0)return true;
            var server=player.level().getServer();var cooldown=LostEffectData.get(server);long now=server.overworld().getGameTime();
            if(!cooldown.ready(player.getUUID(),now))return true;
            cooldown.spend(player.getUUID(),now);player.setHealth(6);player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,0));
            player.getItemBySlot(EquipmentSlot.CHEST).hurtAndBreak(32,player,EquipmentSlot.CHEST);
            player.level().sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,player.getX(),player.getY()+1,player.getZ(),24,.5,.5,.5,.1);
            return false;
        });
    }
    private LostEffects(){}
}
