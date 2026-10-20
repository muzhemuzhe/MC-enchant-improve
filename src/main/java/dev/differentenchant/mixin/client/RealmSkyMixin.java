package dev.differentenchant.mixin.client;

import dev.differentenchant.realm.RealmContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep native sky gradient, clouds and fog; suppress celestial quads only in the Realm. */
@Mixin(SkyRenderer.class)
public abstract class RealmSkyMixin {
    @Inject(method="renderSunMoonAndStars",at=@At("HEAD"),cancellable=true)
    private void differentenchant$hideCelestials(CallbackInfo ci) {
        var level=Minecraft.getInstance().level;
        if(level!=null && level.dimension().equals(RealmContent.REALM)) ci.cancel();
    }
}
