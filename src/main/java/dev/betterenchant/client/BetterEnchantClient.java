package dev.betterenchant.client;

import dev.betterenchant.BetterEnchant;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class BetterEnchantClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        MenuScreens.<net.minecraft.world.inventory.EnchantmentMenu, MachineEnchantmentScreen>register(BetterEnchant.ENCHANTMENT_MENU,
                (menu,inventory,title)->new MachineEnchantmentScreen((dev.betterenchant.enchanting.MachineEnchantmentMenu)menu,inventory,title));
    }
}
