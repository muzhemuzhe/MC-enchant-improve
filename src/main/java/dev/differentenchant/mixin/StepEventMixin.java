package dev.differentenchant.mixin;
import dev.differentenchant.lost.BatchEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerLevel.class)
public abstract class StepEventMixin {
    @Inject(method="gameEvent",at=@At("HEAD"),cancellable=true)
    private void hush(Holder<GameEvent> event,Vec3 origin,GameEvent.Context context,CallbackInfo ci){if(BatchEffects.silentStep(event,context))ci.cancel();}
}
