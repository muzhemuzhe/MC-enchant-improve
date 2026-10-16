package dev.betterenchant.client;

import dev.betterenchant.BetterEnchant;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class BetterEnchantClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                dev.betterenchant.enchanting.DirectedPreviewPayload.TYPE,(payload,context)->{
                    if(context.player().containerMenu instanceof dev.betterenchant.enchanting.MachineEnchantmentMenu menu) menu.acceptControls(payload);
                });
        MenuScreens.register(BetterEnchant.ENCHANTMENT_MENU,MachineEnchantmentScreen::new);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                dev.betterenchant.enchanting.EnchantingPreviewPayload.TYPE,(payload,context)->{
                    if(context.player().containerMenu instanceof dev.betterenchant.enchanting.MachineEnchantmentMenu menu) menu.acceptPreview(payload);
                });
    }
}
