package dev.differentenchant.workstation;

import dev.differentenchant.research.ResearchContent;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/** Additive vanilla loot hook; datapack replacements retain control of their own tables. */
public final class ScrollExploration {
    public static void initialize(){LootTableEvents.MODIFY.register((key,table,source,registries)->{
        if(source.isBuiltin()&&key.identifier().equals(Identifier.withDefaultNamespace("chests/stronghold_library")))table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
            .when(LootItemRandomChanceCondition.randomChance(1f/3f)).add(LootItem.lootTableItem(ResearchContent.LOST_SCROLL)));
    });}
    private ScrollExploration(){}
}
