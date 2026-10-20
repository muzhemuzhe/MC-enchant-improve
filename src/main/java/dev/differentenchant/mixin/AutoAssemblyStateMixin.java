package dev.differentenchant.mixin;

import dev.differentenchant.automation.AutoAssembly;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class AutoAssemblyStateMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("RETURN"))
    private void differentenchant$afterStateSettles(BlockPos pos,BlockState state,int flags,int limit,CallbackInfoReturnable<Boolean> cir){
        if(cir.getReturnValue()&&(Object)this instanceof ServerLevel server)AutoAssembly.changed(server,pos,server.getBlockState(pos));
    }
}
