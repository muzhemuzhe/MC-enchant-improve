package dev.differentenchant.mixin;

import dev.differentenchant.advancement.ModAdvancements;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AdvancementCursorMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleContainerClick",at=@At("RETURN"))
    private void differentenchant$deliveredCursor(ServerboundContainerClickPacket packet,CallbackInfo ci){
        ModAdvancements.obtained(player,player.containerMenu.getCarried());
    }
}
