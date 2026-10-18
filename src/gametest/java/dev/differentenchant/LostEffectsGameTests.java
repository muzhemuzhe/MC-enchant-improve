package dev.differentenchant;

import dev.differentenchant.research.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Executes native effects and the production event bodies, not just JSON presence assertions. */
public final class LostEffectsGameTests {
    static ItemStack gear(GameTestHelper h,Item item,String path,int level) {
        var stack=new ItemStack(item);var holder=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(ResearchContent.id(path)).orElseThrow();stack.enchant(holder,level);return stack;
    }
    @GameTest public void nativeAttributesMiningExperienceAndProjectileLevitation(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();p.setGameMode(GameType.SURVIVAL);p.teleportTo(h.absolutePos(new BlockPos(2,3,2)).getX(),h.absolutePos(new BlockPos(2,3,2)).getY(),h.absolutePos(new BlockPos(2,3,2)).getZ());
        double step=p.getAttributeValue(Attributes.STEP_HEIGHT),block=p.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE),entity=p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        p.setItemSlot(EquipmentSlot.FEET,gear(h,Items.DIAMOND_BOOTS,"trail_stride",2));p.setItemSlot(EquipmentSlot.MAINHAND,gear(h,Items.DIAMOND_PICKAXE,"archivists_reach",2));p.doTick();
        h.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT)-step-1)<.001,"Trail Stride II increases actual step height by 1");
        h.assertTrue(Math.abs(p.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)-block-1)<.001&&Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)-entity-1)<.001,"Archivist II modifies both real interaction attributes");
        p.setItemSlot(EquipmentSlot.FEET,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);p.doTick();
        h.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT)-step)<.001&&Math.abs(p.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)-block)<.001,"attributes removed on unequip");
        var pick=gear(h,Items.DIAMOND_PICKAXE,"prospectors_echo",4);
        h.assertTrue(EnchantmentHelper.processBlockExperience(h.getLevel(),pick,5)==9,"native mining XP 5 becomes 9");
        var silk=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(net.minecraft.resources.Identifier.withDefaultNamespace("silk_touch")).orElseThrow();
        var echo=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(ResearchContent.id("prospectors_echo")).orElseThrow();h.assertTrue(!net.minecraft.world.item.enchantment.Enchantment.areCompatible(silk,echo),"Silk Touch incompatibility enforced by registry");
        var bow=gear(h,Items.BOW,"gravity_thread",2);p.setItemSlot(EquipmentSlot.MAINHAND,bow);var arrow=new Arrow(h.getLevel(),p,new ItemStack(Items.ARROW),bow);var victim=h.spawn(EntityType.ZOMBIE,new BlockPos(3,2,3));
        EnchantmentHelper.doPostAttackEffectsWithItemSource(h.getLevel(),victim,h.getLevel().damageSources().arrow(arrow,p),bow);
        h.assertTrue(victim.hasEffect(MobEffects.LEVITATION)&&victim.getEffect(MobEffects.LEVITATION).getDuration()==40,"Gravity Thread II executes actual 40-tick levitation");
        victim.removeEffect(MobEffects.LEVITATION);EnchantmentHelper.doPostAttackEffectsWithItemSource(h.getLevel(),victim,h.getLevel().damageSources().playerAttack(p),bow);
        h.assertTrue(!victim.hasEffect(MobEffects.LEVITATION),"bow melee does not trigger projectile-only effect");h.succeed();
    }
    @GameTest public void submergedVisionHealingAndActiveShield(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();p.setGameMode(GameType.SURVIVAL);var pos=h.absolutePos(new BlockPos(2,2,2));
        for(int x=-1;x<=1;x++)for(int y=0;y<=3;y++)for(int z=-1;z<=1;z++)h.getLevel().setBlockAndUpdate(pos.offset(x,y,z),Blocks.WATER.defaultBlockState());
        p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);p.setItemSlot(EquipmentSlot.HEAD,gear(h,Items.DIAMOND_HELMET,"lantern_sight",1));p.doTick();p.doTick();
        h.assertTrue(p.isUnderWater(),"actual fluid state submerged");LostEffects.tick(p);h.assertTrue(p.hasEffect(MobEffects.NIGHT_VISION),"Lantern Sight applies real night vision underwater");
        p.removeEffect(MobEffects.NIGHT_VISION);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);LostEffects.tick(p);h.assertTrue(!p.hasEffect(MobEffects.NIGHT_VISION),"no effect without the helmet");
        p.setItemSlot(EquipmentSlot.CHEST,gear(h,Items.DIAMOND_CHESTPLATE,"living_margin",3));p.setHealth(10);p.getFoodData().setFoodLevel(20);p.tickCount=200;LostEffects.tick(p);h.assertTrue(p.getHealth()==13,"Living Margin III heals 3 actual health");
        p.getFoodData().setFoodLevel(10);p.tickCount=400;LostEffects.tick(p);h.assertTrue(p.getHealth()==13,"food prerequisite prevents free low-food healing");
        p.setItemSlot(EquipmentSlot.OFFHAND,gear(h,Items.SHIELD,"warding_focus",2));p.startUsingItem(InteractionHand.OFF_HAND);
        for(int i=0;i<6;i++)p.doTick();h.assertTrue(p.isBlocking(),"native shield active after raise delay");LostEffects.tick(p);
        h.assertTrue(p.hasEffect(MobEffects.RESISTANCE)&&p.getEffect(MobEffects.RESISTANCE).getAmplifier()==1,"Warding Focus II grants actual Resistance II");
        p.stopUsingItem();p.removeEffect(MobEffects.RESISTANCE);LostEffects.tick(p);h.assertTrue(!p.hasEffect(MobEffects.RESISTANCE),"passive held shield does not grant ward");h.succeed();
    }
    @GameTest public void lastPageRescuesOnceAndCooldownPersists(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=false;p.setItemSlot(EquipmentSlot.CHEST,gear(h,Items.DIAMOND_CHESTPLATE,"last_page",1));p.setHealth(1);
        var armor=p.getItemBySlot(EquipmentSlot.CHEST);p.hurtServer(h.getLevel(),h.getLevel().damageSources().generic(),1000);
        h.assertTrue(p.isAlive()&&p.getHealth()==6&&p.hasEffect(MobEffects.ABSORPTION),"actual fatal hit cancelled and health restored");
        h.assertTrue(armor.getDamageValue()==32,"rescue charges armor durability");
        var data=LostEffectData.get(h.getLevel().getServer());h.assertTrue(!data.ready(p.getUUID(),h.getLevel().getGameTime()),"persistent player cooldown blocks another rescue");
        var tag=LostEffectData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,data).getOrThrow();
        h.assertTrue(!LostEffectData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,tag).getOrThrow().ready(p.getUUID(),h.getLevel().getGameTime()),"rescue cooldown survives native saved-data encoding");
        p.invulnerableTime=0;p.removeEffect(MobEffects.ABSORPTION);p.hurtServer(h.getLevel(),h.getLevel().damageSources().generic(),1000);
        h.assertTrue(p.isDeadOrDying(),"second fatal hit is not cancelled");h.succeed();
    }
}
