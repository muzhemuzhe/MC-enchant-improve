package dev.differentenchant.enchanting;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;

public final class EnchantingCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->dispatcher.register(
                Commands.literal("differentenchant").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("tier").then(Commands.argument("part",BlockPosArgument.blockPos())
                                .then(Commands.argument("level",IntegerArgumentType.integer(1,10)).executes(context->{
                                    var source=context.getSource();
                                    var part=BlockPosArgument.getBlockPos(context,"part");
                                    var master=MachineAccess.controller(source.getLevel(),part);
                                    int tier=IntegerArgumentType.getInteger(context,"level");
                                    if(master==null || tier>EnchantingConfig.current().maxTier()) {
                                        source.sendFailure(Component.literal("Loaded complete machine required; tier must be within configured maximum.")); return 0;
                                    }
                                    master.setMachineTier(tier);
                                    source.sendSuccess(()->Component.literal("Machine tier = "+tier),true);
                                    return tier;
                                }))))
                        .then(Commands.literal("reload").executes(context->{
                            try { EnchantingConfig.load(); }
                            catch(Exception error) { context.getSource().sendFailure(Component.literal("Configuration rejected: "+error.getMessage())); return 0; }
                            context.getSource().sendSuccess(()->Component.literal("Differentenchant server configuration reloaded."),true); return 1;
                        }))));
    }
    private EnchantingCommands() {}
}
