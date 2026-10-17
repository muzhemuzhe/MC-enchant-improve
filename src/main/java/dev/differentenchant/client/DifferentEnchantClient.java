package dev.differentenchant.client;

import dev.differentenchant.DifferentEnchant;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class DifferentEnchantClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                dev.differentenchant.enchanting.DirectedPreviewPayload.TYPE,(payload,context)->{
                    if(context.player().containerMenu instanceof dev.differentenchant.enchanting.MachineEnchantmentMenu menu) menu.acceptControls(payload);
                });
        MenuScreens.register(DifferentEnchant.ENCHANTMENT_MENU,MachineEnchantmentScreen::new);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                dev.differentenchant.enchanting.EnchantingPreviewPayload.TYPE,(payload,context)->{
                    if(context.player().containerMenu instanceof dev.differentenchant.enchanting.MachineEnchantmentMenu menu) menu.acceptPreview(payload);
                });
    }
}
