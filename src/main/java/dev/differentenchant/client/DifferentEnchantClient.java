package dev.differentenchant.client;

import dev.differentenchant.DifferentEnchant;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class DifferentEnchantClient implements ClientModInitializer {
    private static void appendLostDescription(java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.network.chat.Component description) {
        var client=net.minecraft.client.Minecraft.getInstance();int wrap=Math.min(220,Math.max(80,client.getWindow().getGuiScaledWidth()-24));
        for(var part:client.font.split(description,wrap)) {
            var text=new StringBuilder();part.accept((index,style,codepoint)->{text.appendCodePoint(codepoint);return true;});
            lines.add(net.minecraft.network.chat.Component.literal(text.toString()).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
    @Override public void onInitializeClient() {
        MenuScreens.register(dev.differentenchant.disenchant.DisenchantContent.MENU,DisenchantScreen::new);
        WaterReflectionClient.initialize();
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(dev.differentenchant.lost.StarStep.TYPE,StarStepRenderer::new);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(dev.differentenchant.lost.AirControlConfig.Settings.TYPE,(payload,context)->dev.differentenchant.lost.AirControlConfig.current=payload);
        MenuScreens.register(dev.differentenchant.research.ResearchContent.MENU,ResearchScreen::new);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack,context,flag,lines)->{
            if(stack.is(dev.differentenchant.research.ResearchContent.LOST_SCROLL))appendLostDescription(lines,net.minecraft.network.chat.Component.translatable("item.differentenchant.lost_scroll.help"));
            var lost=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,dev.differentenchant.research.ResearchContent.id("lost_enchantments"));
            for(var holder:net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet())if(holder.is(lost)) {
                var id=holder.unwrapKey().orElseThrow().identifier();var description=net.minecraft.network.chat.Component.translatable("enchantment."+id.getNamespace()+"."+id.getPath()+".desc");
                // Unknown third-party descriptions are left to their own provider, without showing a raw key.
                if(net.minecraft.client.resources.language.I18n.exists("enchantment."+id.getNamespace()+"."+id.getPath()+".desc"))appendLostDescription(lines,description);
            }
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(dev.differentenchant.research.ResearchPreviewPayload.TYPE,(payload,context)->{
            if(context.player().containerMenu instanceof dev.differentenchant.research.ResearchMenu menu)menu.acceptPreview(payload);
        });
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
