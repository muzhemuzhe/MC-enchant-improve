package dev.betterenchant.client;

import dev.betterenchant.enchanting.MachineEnchantmentMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.input.MouseButtonEvent;

public final class MachineEnchantmentScreen extends EnchantmentScreen {
    private final MachineEnchantmentMenu machine;
    public MachineEnchantmentScreen(MachineEnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title); machine=menu;
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font,Component.translatable("container.better_enchant.status",machine.machineTier(),machine.bookshelfCount()),titleLabelX,titleLabelY,0xFF404040);
        graphics.text(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xFF404040);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for(int option=0;option<3;option++) {
            if(event.x()>=leftPos+60 && event.x()<leftPos+168 && event.y()>=topPos+14+19*option && event.y()<topPos+33+19*option) {
                if(event.button()==0 && machine.clickMenuButton(minecraft.player,option))
                    minecraft.gameMode.handleInventoryButtonClick(machine.containerId,machine.buttonIdFor(option));
                return true;
            }
        }
        return super.mouseClicked(event,doubleClick);
    }
}
