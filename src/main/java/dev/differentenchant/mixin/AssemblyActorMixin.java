package dev.differentenchant.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.differentenchant.automation.AssemblyActor;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayerGameMode.class)
public abstract class AssemblyActorMixin {
    @WrapMethod(method="useItemOn")
    private InteractionResult differentenchant$withActor(ServerPlayer player,Level level,ItemStack stack,InteractionHand hand,BlockHitResult hit,Operation<InteractionResult> original){
        var previous=AssemblyActor.enter(player);
        try{return original.call(player,level,stack,hand,hit);}finally{AssemblyActor.restore(previous);}
    }
}
