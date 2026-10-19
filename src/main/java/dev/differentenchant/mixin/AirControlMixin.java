package dev.differentenchant.mixin;
import dev.differentenchant.lost.BatchEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Player.class)
public abstract class AirControlMixin {
    @Inject(method="travel",at=@At("TAIL"))
    private void kite(Vec3 input,CallbackInfo ci){BatchEffects.airControl((Player)(Object)this,input);}
}
