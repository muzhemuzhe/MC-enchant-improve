package dev.differentenchant.enchanter;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.stats.Stats;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public final class ExperienceCrystalItem extends Item {
    public ExperienceCrystalItem(Properties properties){super(properties);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(!stack.is(this))return InteractionResult.PASS;
        if(level instanceof ServerLevel server){
            player.giveExperiencePoints(EnchanterBalance.CRYSTAL_XP);
            if(!player.getAbilities().instabuild)stack.shrink(1);
            player.awardStat(Stats.ITEM_USED.get(this));
            server.playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.EXPERIENCE_ORB_PICKUP,SoundSource.PLAYERS,.45f,1.1f);
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER,player.getX(),player.getY()+1,player.getZ(),5,.25,.3,.25,.01);
        }
        return InteractionResult.SUCCESS;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag){
        lines.accept(Component.translatable("item.differentenchant.condensed_experience_crystal.help",EnchanterBalance.CRYSTAL_XP).withStyle(ChatFormatting.GRAY));
    }
}
