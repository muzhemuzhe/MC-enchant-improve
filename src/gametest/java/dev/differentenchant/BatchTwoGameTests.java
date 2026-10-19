package dev.differentenchant;
import dev.differentenchant.lost.*;
import dev.differentenchant.research.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.arrow.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.*;
import java.util.*;
public final class BatchTwoGameTests {
    static ItemStack gear(GameTestHelper h,Item item,String path,int rank){return LostEffectsGameTests.gear(h,item,path,rank);}
    /** The deprecated helper hardcodes gameMode()=CREATIVE, which makes Warden targeting tests invalid. */
    static net.minecraft.server.level.ServerPlayer player(GameTestHelper h){
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"batch2-player"),false);
        var p=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,p,cookie);p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());p.setGameMode(GameType.SURVIVAL);var pos=h.absolutePos(new BlockPos(3,2,3));p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);return p;
    }
    static void vanilla(GameTestHelper h,ItemStack stack,String id,int rank){stack.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(net.minecraft.resources.Identifier.withDefaultNamespace(id)).orElseThrow(),rank);}
    @GameTest public void borrowedFlameRealMeleeNoCooldownAndImmuneRule(GameTestHelper h){
        var p=player(h);p.setOnGround(true);
        for(int rank=1;rank<=2;rank++){var victim=h.spawn(EntityType.HUSK,new BlockPos(3,2,4));victim.setNoAi(true);p.setItemSlot(EquipmentSlot.MAINHAND,gear(h,Items.DIAMOND_SWORD,"borrowed_flame",rank));p.setRemainingFireTicks(400);p.resetAttackStrengthTicker();p.attack(victim);h.assertTrue(!p.isOnFire()&&victim.getRemainingFireTicks()==rank*100,"actual melee transfers capped existing fire and extinguishes attacker rank "+rank);victim.discard();}
        var target=h.spawn(EntityType.HUSK,new BlockPos(3,2,4));p.clearFire();p.attack(target);h.assertTrue(!target.isOnFire(),"no source fire cannot create flame");
        p.setRemainingFireTicks(160);var blaze=h.spawn(EntityType.BLAZE,new BlockPos(3,2,4));p.attack(blaze);h.assertTrue(p.getRemainingFireTicks()==160,"immune target leaves source burning unchanged");
        var weapon=gear(h,Items.DIAMOND_AXE,"borrowed_flame",2);vanilla(h,weapon,"fire_aspect",2);p.setItemSlot(EquipmentSlot.MAINHAND,weapon);p.setRemainingFireTicks(80);target.invulnerableTime=0;p.attack(target);h.assertTrue(!p.isOnFire()&&target.getRemainingFireTicks()>=80,"native Fire Aspect and transfer coexist without extra damage call");h.succeed();
    }
    @GameTest public void crossbowNativeLoadingConservationAndProjectileKinds(GameTestHelper h)throws Exception {
        var p=player(h);var report=new StringBuilder("level,loads,spared\n");
        for(int rank=0;rank<=3;rank++){int spared=0;for(int i=0;i<600;i++){
            var bow=rank==0?new ItemStack(Items.CROSSBOW):gear(h,Items.CROSSBOW,"returning_feather",rank);p.setItemSlot(EquipmentSlot.MAINHAND,bow);p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.ARROW));
            ((CrossbowItem)Items.CROSSBOW).onUseTick(h.getLevel(),p,bow,72000-CrossbowItem.getChargeDuration(bow,p));
            h.assertTrue(CrossbowItem.isCharged(bow),"native load succeeds with existing arrow");boolean kept=!p.getOffhandItem().isEmpty();if(kept)spared++;
            var ammo=bow.get(DataComponents.CHARGED_PROJECTILES).itemCopies().getFirst();h.assertTrue(ammo.has(DataComponents.INTANGIBLE_PROJECTILE)==kept,"spared projectile is intangible; no recoverable duplicate");
        }double actual=spared/600.0,expected=(double)rank/(rank+1);h.assertTrue(Math.abs(actual-expected)<.07,"real independent loading probability at rank "+rank+": "+actual);report.append(rank).append(',').append(600).append(',').append(spared).append('\n');}
        for(var item:List.of(Items.TIPPED_ARROW,Items.SPECTRAL_ARROW,Items.FIREWORK_ROCKET)){var bow=gear(h,Items.CROSSBOW,"returning_feather",3);p.setItemSlot(EquipmentSlot.MAINHAND,bow);p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(item));((CrossbowItem)Items.CROSSBOW).onUseTick(h.getLevel(),p,bow,71975);h.assertTrue(p.getOffhandItem().isEmpty()&&CrossbowItem.isCharged(bow),"special ammunition always consumed: "+item);}
        p.getInventory().clearContent();var empty=gear(h,Items.CROSSBOW,"returning_feather",3);p.setItemSlot(EquipmentSlot.MAINHAND,empty);h.assertTrue(((CrossbowItem)Items.CROSSBOW).use(h.getLevel(),p,InteractionHand.MAIN_HAND)==InteractionResult.FAIL,"at least one actual projectile required");
        var multi=gear(h,Items.CROSSBOW,"returning_feather",3);vanilla(h,multi,"multishot",1);vanilla(h,multi,"quick_charge",3);p.setItemSlot(EquipmentSlot.MAINHAND,multi);p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.ARROW));((CrossbowItem)Items.CROSSBOW).onUseTick(h.getLevel(),p,multi,72000-CrossbowItem.getChargeDuration(multi,p));h.assertTrue(multi.get(DataComponents.CHARGED_PROJECTILES).itemCopies().size()==3,"multishot native three projectiles from one ammo decision");
        p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));int before=h.getLevel().getEntitiesOfClass(AbstractArrow.class,p.getBoundingBox().inflate(20)).size();((CrossbowItem)Items.CROSSBOW).performShooting(h.getLevel(),p,InteractionHand.MAIN_HAND,multi,3.15f,0,null);h.assertTrue(!CrossbowItem.isCharged(multi)&&h.getLevel().getEntitiesOfClass(AbstractArrow.class,p.getBoundingBox().inflate(20)).size()==before+3,"charged crossbow still shoots its stored projectiles after hand switch");
        var path=java.nio.file.Path.of("../reports/lost-batch-two/ammo.csv");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,report.toString());h.succeed();
    }
    @GameTest public void bowNativeVelocityCriticalThresholdAndCompatibility(GameTestHelper h){
        var p=player(h);for(int rank=0;rank<=3;rank++)for(int delta=-1;delta<=0;delta++){
            var bow=rank==0?new ItemStack(Items.BOW):gear(h,Items.BOW,"swift_draw",rank);vanilla(h,bow,"power",3);vanilla(h,bow,"punch",2);vanilla(h,bow,"flame",1);vanilla(h,bow,"unbreaking",3);vanilla(h,bow,"infinity",1);
            p.setItemSlot(EquipmentSlot.MAINHAND,bow);p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.ARROW));p.startUsingItem(InteractionHand.MAIN_HAND);
            int before=h.getLevel().getEntitiesOfClass(AbstractArrow.class,p.getBoundingBox().inflate(20)).stream().mapToInt(Entity::getId).max().orElse(-1);((BowItem)Items.BOW).releaseUsing(bow,h.getLevel(),p,72000-BatchRules.drawTicks(rank)-delta);
            var arrow=h.getLevel().getEntitiesOfClass(AbstractArrow.class,p.getBoundingBox().inflate(20)).stream().filter(e->e.getId()>before).findFirst().orElseThrow();h.assertTrue(arrow.isCritArrow()==(delta==0),"native critical threshold uses adjusted full draw rank "+rank);h.assertTrue(delta!=0||Math.abs(arrow.getDeltaMovement().length()-3)<.06,"full speed is still vanilla 3");h.assertTrue(!p.getOffhandItem().isEmpty(),"Infinity preserves real arrow");p.stopUsingItem();arrow.discard();
        }h.succeed();
    }
    @GameTest public void paperKiteHorizontalOnlyAndExclusions(GameTestHelper h){
        var p=player(h);p.setOnGround(false);p.setShiftKeyDown(true);double previous=2;
        for(int rank=1;rank<=3;rank++){p.setItemSlot(EquipmentSlot.FEET,gear(h,Items.DIAMOND_BOOTS,"paper_kite",rank));p.setDeltaMovement(.3,-.4,0);BatchEffects.airControl(p,Vec3.ZERO);h.assertTrue(p.getDeltaMovement().y==-.4&&p.getDeltaMovement().x<previous,"higher kite rank brakes more with identical Y");previous=p.getDeltaMovement().x;}
        p.getAbilities().flying=true;p.setDeltaMovement(.3,-.4,0);BatchEffects.airControl(p,Vec3.ZERO);h.assertTrue(p.getDeltaMovement().equals(new Vec3(.3,-.4,0)),"creative flying unaffected");p.getAbilities().flying=false;p.setShiftKeyDown(false);BatchEffects.airControl(p,Vec3.ZERO);h.assertTrue(p.getDeltaMovement().x==.3,"no sneak no brake");p.setShiftKeyDown(true);p.setItemSlot(EquipmentSlot.FEET,ItemStack.EMPTY);BatchEffects.airControl(p,Vec3.ZERO);h.assertTrue(p.getDeltaMovement().x==.3,"no enchantment preserves motion");h.succeed();
    }
    @GameTest public void quietStepRealSensorWardenAndShrieker(GameTestHelper h){
        var p=player(h);p.setItemSlot(EquipmentSlot.FEET,gear(h,Items.DIAMOND_BOOTS,"hush_step",1));p.doTick();p.setOnGround(true);
        var sensorPos=h.absolutePos(new BlockPos(1,2,3));h.getLevel().setBlockAndUpdate(sensorPos,Blocks.SCULK_SENSOR.defaultBlockState());var sensor=(SculkSensorBlockEntity)h.getLevel().getBlockEntity(sensorPos);
        var shriekerPos=h.absolutePos(new BlockPos(1,2,5));h.getLevel().setBlockAndUpdate(shriekerPos,Blocks.SCULK_SHRIEKER.defaultBlockState());var shrieker=(SculkShriekerBlockEntity)h.getLevel().getBlockEntity(shriekerPos);
        var warden=h.spawn(EntityType.WARDEN,new BlockPos(5,2,3));warden.setNoGravity(true);warden.setPose(Pose.STANDING);p.setInvulnerable(false);p.getAbilities().invulnerable=false;p.setShiftKeyDown(false);warden.updateDynamicGameEventListener(net.minecraft.world.level.gameevent.DynamicGameEventListener::add);
        h.assertTrue(warden.canTargetEntity(p),"native warden listener has a valid survival player");
        h.getLevel().gameEvent(GameEvent.STEP,p.position(),GameEvent.Context.of(p));
        h.assertTrue(sensor.getVibrationData().getSelectionStrategy().chosenCandidate(h.getLevel().getGameTime()+1).isEmpty()&&warden.getVibrationData().getSelectionStrategy().chosenCandidate(h.getLevel().getGameTime()+1).isEmpty(),"quiet STEP excluded from actual active sensor and active-AI warden listeners");
        h.assertTrue(!shrieker.getBlockState().getValue(SculkShriekerBlock.SHRIEKING),"no downstream shriek from quiet step");
        p.setSprinting(true);h.getLevel().gameEvent(GameEvent.STEP,p.position(),GameEvent.Context.of(p));
        h.assertTrue(sensor.getVibrationData().getSelectionStrategy().chosenCandidate(h.getLevel().getGameTime()+1).isPresent()&&warden.getVibrationData().getSelectionStrategy().chosenCandidate(h.getLevel().getGameTime()+1).isPresent(),"sprinting STEP reaches both real listeners");
        // Exercise vanilla receiver responses explicitly; client acceptance separately uses their normal scheduled ticking.
        sensor.getVibrationUser().onReceiveVibration(h.getLevel(),p.blockPosition(),GameEvent.STEP,p,null,2);
        warden.getVibrationUser().onReceiveVibration(h.getLevel(),p.blockPosition(),GameEvent.STEP,p,null,2);
        h.assertTrue(shrieker.getVibrationData().getSelectionStrategy().chosenCandidate(h.getLevel().getGameTime()+1).isPresent(),"native sensor tendril event reaches real shrieker listener");
        shrieker.getVibrationUser().onReceiveVibration(h.getLevel(),sensorPos,GameEvent.SCULK_SENSOR_TENDRILS_CLICKING,p,null,2);
        h.assertTrue(shrieker.getBlockState().getValue(SculkShriekerBlock.SHRIEKING)&&warden.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.VIBRATION_COOLDOWN),"native shriek and warden vibration response remain functional");
        h.assertTrue(!BatchEffects.silentStep(GameEvent.HIT_GROUND,GameEvent.Context.of(p))&&!BatchEffects.silentStep(GameEvent.BLOCK_OPEN,GameEvent.Context.of(p)),"landing and container opening are never filtered");h.succeed();
    }
    @GameTest(maxTicks=80) public void tridentRealBlockImpactPullAndExpiry(GameTestHelper h){
        h.onEachTick(()->TideVortices.tick(h.getLevel().getServer()));for(int x=0;x<8;x++)for(int z=0;z<8;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);var p=player(h);var center=h.absolutePos(new BlockPos(3,2,5));h.getLevel().setBlockAndUpdate(center.below(),Blocks.STONE.defaultBlockState());var trident=new ThrownTrident(h.getLevel(),p,gear(h,Items.TRIDENT,"undertow",2));trident.setPos(center.getX()+.5,center.getY()+1.5,center.getZ()+.5);trident.setDeltaMovement(0,-.8,0);h.getLevel().addFreshEntity(trident);var victim=h.spawn(EntityType.ARMOR_STAND,new BlockPos(5,2,5));double oldX=victim.getX();p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
        h.runAfterDelay(10,()->{h.assertTrue(TideVortices.count(p.getUUID())==1,"native trident floor impact creates one field after hand switch");h.assertTrue(victim.getX()<oldX,"actual living entity moved toward vortex: old="+oldX+" new="+victim.position()+" velocity="+victim.getDeltaMovement()+" centers="+TideVortices.centers());});h.runAfterDelay(48,()->{h.assertTrue(TideVortices.count(p.getUUID())==0,"transient field expires without world changes");h.succeed();});
    }
    @GameTest public void parryReusesRealArrowsNearGroundAndRejectsSides(GameTestHelper h){
        var p=player(h);var attacker=player(h);attacker.teleportTo(p.getX()+5,p.getY(),p.getZ());p.setYRot(0);p.setYHeadRot(0);
        for(int type=0;type<3;type++){
            p.stopUsingItem();p.setItemSlot(EquipmentSlot.OFFHAND,gear(h,Items.SHIELD,"parry",2));p.startUsingItem(InteractionHand.OFF_HAND);for(int i=0;i<6;i++)p.doTick();
            var ammo=new ItemStack(type==1?Items.TIPPED_ARROW:Items.ARROW);if(type==1)ammo.set(DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.POISON));
            AbstractArrow arrow=type==2?new SpectralArrow(EntityType.SPECTRAL_ARROW,h.getLevel()):new Arrow(h.getLevel(),attacker,ammo,new ItemStack(Items.BOW));
            arrow.setPos(p.getX(),p.getY()+.2,p.getZ()+1);arrow.setDeltaMovement(0,0,-1.5);arrow.setOwner(attacker);arrow.pickup=type==1?AbstractArrow.Pickup.CREATIVE_ONLY:AbstractArrow.Pickup.ALLOWED;var pickup=arrow.pickup;var originalAmmo=arrow.getPickupItemStackOrigin().copy();h.getLevel().addFreshEntity(arrow);int id=arrow.getId();float health=p.getHealth();arrow.tick();
            h.assertTrue(arrow.isAlive()&&arrow.getId()==id&&arrow.getOwner()==p&&arrow.getY()>p.getY()+1&&arrow.getDeltaMovement().y>0&&arrow.getDeltaMovement().z>0,"native low incoming arrow reused, safely raised and aimed: type "+type);
            h.assertTrue(p.getHealth()==health&&arrow.pickup==pickup&&ItemStack.matches(originalAmmo,arrow.getPickupItemStackOrigin()),"parry neither hurts player nor changes pickup count");arrow.tick();h.assertTrue(arrow.isAlive(),"reflected low arrow does not immediately hit ground");arrow.discard();
        }
        var side=new Arrow(h.getLevel(),attacker,new ItemStack(Items.ARROW),new ItemStack(Items.BOW));side.setPos(p.getX()+1,p.getY()+1,p.getZ());side.setDeltaMovement(-1,0,0);h.assertTrue(!ArrowParry.reflect(side,new EntityHitResult(p)),"side arrows outside shield facing remain native");
        p.stopUsingItem();p.setItemSlot(EquipmentSlot.OFFHAND,gear(h,Items.SHIELD,"parry",1));p.startUsingItem(InteractionHand.OFF_HAND);for(int i=0;i<11;i++)p.doTick();side.setPos(p.getX(),p.getY()+1,p.getZ()+1);side.setDeltaMovement(0,0,-1);h.assertTrue(!ArrowParry.reflect(side,new EntityHitResult(p)),"rank I precise window expired");
        p.stopUsingItem();p.setItemSlot(EquipmentSlot.OFFHAND,gear(h,Items.SHIELD,"parry",2));p.startUsingItem(InteractionHand.OFF_HAND);for(int i=0;i<11;i++)p.doTick();h.assertTrue(ArrowParry.reflect(side,new EntityHitResult(p)),"rank II retains longer precise window");h.succeed();
    }
    @GameTest public void reflectionUnderwaterTagRangeCapAndSurfaceExclusion(GameTestHelper h){
        var p=player(h);p.setItemSlot(EquipmentSlot.HEAD,gear(h,Items.DIAMOND_HELMET,"water_reflection",1));
        var base=h.absolutePos(new BlockPos(3,2,3));for(int x=-1;x<=1;x++)for(int y=0;y<3;y++)for(int z=-1;z<=1;z++)h.getLevel().setBlockAndUpdate(base.offset(x,y,z),Blocks.WATER.defaultBlockState());p.doTick();p.doTick();
        for(int i=0;i<10;i++)h.getLevel().setBlockAndUpdate(base.offset(i%5-2,-1,i/5+2),i%2==0?Blocks.CHEST.defaultBlockState():Blocks.BARREL.defaultBlockState());
        h.getLevel().setBlockAndUpdate(base.offset(10,0,0),Blocks.CHEST.defaultBlockState());h.getLevel().setBlockAndUpdate(base.offset(1,-1,-2),Blocks.DIAMOND_ORE.defaultBlockState());
        var list=ReflectionTargets.scan(p);h.assertTrue(p.isUnderWater()&&list.size()==8&&list.stream().allMatch(pos->pos.distSqr(p.blockPosition())<=36),"real underwater limited nearby container targets capped at eight");
        p.teleportTo(base.getX(),base.getY()+5,base.getZ());p.doTick();p.doTick();h.assertTrue(ReflectionTargets.scan(p).isEmpty(),"surface disables all targets");h.succeed();
    }
    @GameTest public void starBridgeRealOwnerCollisionLifecycleAndPersistentCooldown(GameTestHelper h){
        StarBridges.clear();var p=player(h);var other=player(h);p.setItemSlot(EquipmentSlot.FEET,gear(h,Items.DIAMOND_BOOTS,"star_bridge",1));p.setSprinting(true);var base=h.absolutePos(new BlockPos(3,2,3));h.getLevel().setBlockAndUpdate(base.below(),Blocks.STONE.defaultBlockState());
        p.teleportTo(base.getX()+.8,base.getY(),base.getZ()+.5);p.setOnGround(true);StarBridges.tick(p);
        p.teleportTo(base.getX()+1.5,base.getY()-.02,base.getZ()+.5);p.setOnGround(false);StarBridges.tick(p);
        var steps=h.getLevel().getEntitiesOfClass(StarStep.class,p.getBoundingBox().inflate(5));h.assertTrue(steps.size()==3&&!StarStep.TYPE.canSerialize(),"three actual non-saving foothold entities");
        p.move(MoverType.SELF,new Vec3(0,-1,0));h.assertTrue(p.onGround()&&p.getY()>base.getY()-.2,"native server movement stands on real bridge collision");
        other.teleportTo(base.getX()+1.5,base.getY()-.02,base.getZ()+.5);other.move(MoverType.SELF,new Vec3(0,-1,0));h.assertTrue(other.getY()<p.getY()-.5,"other player cannot use or inherit private collision");
        var data=StarBridgeData.get(h.getLevel().getServer());long now=h.getLevel().getServer().overworld().getGameTime();h.assertTrue(!data.ready(p.getUUID(),now),"cooldown spent after actual creation");var tag=StarBridgeData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,data).getOrThrow();h.assertTrue(!StarBridgeData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,tag).getOrThrow().ready(p.getUUID(),now),"native NBT persists cooldown across reconnect");
        h.assertTrue(steps.stream().noneMatch(Entity::isPickable)&&h.getLevel().getBlockState(base.offset(1,-1,0)).isAir(),"no mining target and no world terrain writes");
        p.setItemSlot(EquipmentSlot.FEET,ItemStack.EMPTY);for(var step:steps)step.tick();h.assertTrue(steps.stream().allMatch(Entity::isRemoved),"unequip immediately clears all own footholds");h.succeed();
    }
    @GameTest public void researchExpansionPreservesPinnedAnswersAndNewPoolCoverage(GameTestHelper h)throws Exception {
        var original=ResearchWorldData.get(h.getLevel().getServer());var json=com.google.gson.JsonParser.parseString(original.exportJson()).getAsJsonObject();json.addProperty("catalogueRevision",0);
        var oldIds=Set.of("trail_stride","prospectors_echo","lantern_sight","living_margin","archivists_reach","warding_focus","gravity_thread","last_page");
        var oldCatalogue=new com.google.gson.JsonArray();for(var value:json.getAsJsonArray("catalogue"))if(oldIds.contains(value.getAsJsonObject().get("id").getAsString().split(":")[1]))oldCatalogue.add(value);json.add("catalogue",oldCatalogue);json.add("outcomes",new com.google.gson.JsonObject());json.add("discoveries",new com.google.gson.JsonObject());
        var legacy=ResearchWorldData.importJson(json.toString());var player=UUID.randomUUID();var books=ResearchGameTests.vanillaBooks(h.getLevel());var pinned=new TreeMap<String,ResearchPolicy.Result>();
        for(int i=0;i<100;i++){var r=new ResearchPolicy.Recipe(books.get(i%books.size()),books.get((i*7+2)%books.size()));var result=legacy.resolve(r);legacy.discover(player,r,result);pinned.put(r.key(),result);}
        var beforeExpansion=ResearchWorldData.importJson(legacy.exportJson());
        h.assertTrue(legacy.migrateBatchTwo(h.getLevel().registryAccess())&&!legacy.migrateBatchTwo(h.getLevel().registryAccess())&&legacy.catalogue().size()==17,"explicit expansion appends nine exactly once");
        for(var entry:pinned.entrySet())h.assertTrue(legacy.knows(player,ResearchPolicy.Recipe.parse(entry.getKey()))&&legacy.resolve(ResearchPolicy.Recipe.parse(entry.getKey())).equals(entry.getValue()),"all legacy discovered answers untouched");
        var restored=ResearchWorldData.importJson(legacy.exportJson());for(var entry:pinned.entrySet())h.assertTrue(restored.resolve(ResearchPolicy.Recipe.parse(entry.getKey())).equals(entry.getValue()),"migration save round trip keeps fixed answers");
        var nbt=ResearchWorldData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,legacy).getOrThrow();var nbtRestored=ResearchWorldData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,nbt).getOrThrow();for(var entry:pinned.entrySet())h.assertTrue(nbtRestored.resolve(ResearchPolicy.Recipe.parse(entry.getKey())).equals(entry.getValue()),"migrated legacy outcomes survive native NBT");
        var otherJson=com.google.gson.JsonParser.parseString(legacy.exportJson()).getAsJsonObject();otherJson.addProperty("seed",otherJson.get("seed").getAsLong()+1);otherJson.add("outcomes",new com.google.gson.JsonObject());otherJson.add("discoveries",new com.google.gson.JsonObject());var otherWorld=ResearchWorldData.importJson(otherJson.toString());var otherCounts=new TreeMap<String,Integer>();int seedChanges=0;
        var coverage=new TreeSet<String>();var counts=new TreeMap<String,Integer>();var oldCounts=new TreeMap<String,Integer>();int changed=0,total=0;for(int i=0;i<books.size();i++)for(int j=i;j<books.size();j++){var r=new ResearchPolicy.Recipe(books.get(i),books.get(j));var result=legacy.resolve(r);var oldResult=beforeExpansion.resolve(r);var other=otherWorld.resolve(r);if(!result.equals(other))seedChanges++;otherCounts.merge(other.id(),1,Integer::sum);h.assertTrue(result.rarity()==oldResult.rarity()&&result.scroll()==oldResult.scroll(),"expansion leaves the grade/pool tier selection and scroll branch unchanged");if(!result.equals(oldResult))changed++;oldCounts.merge(oldResult.id(),1,Integer::sum);h.assertTrue(result.equals(legacy.resolve(new ResearchPolicy.Recipe(r.b(),r.a()))),"unordered input remains stable");if(!result.scroll())coverage.add(result.id());counts.merge(result.id(),1,Integer::sum);total++;}
        h.assertTrue(coverage.size()==17&&seedChanges>total/2,"all seventeen outputs reached and different world seeds retain differing distributions");
        var path=java.nio.file.Path.of("../reports/lost-batch-two/expanded-pools.csv");java.nio.file.Files.createDirectories(path.getParent());var report=new StringBuilder("result,count\n");counts.forEach((id,n)->report.append(id).append(',').append(n).append('\n'));java.nio.file.Files.writeString(path,report.toString());var oldReport=new StringBuilder("result,count\n");oldCounts.forEach((id,n)->oldReport.append(id).append(',').append(n).append('\n'));java.nio.file.Files.writeString(path.resolveSibling("legacy-pools.csv"),oldReport.toString());var secondReport=new StringBuilder("result,count\n");otherCounts.forEach((id,n)->secondReport.append(id).append(',').append(n).append('\n'));java.nio.file.Files.writeString(path.resolveSibling("second-seed-pools.csv"),secondReport.toString());java.nio.file.Files.writeString(path.resolveSibling("migration.txt"),"Pinned "+pinned.size()+" answers preserved; "+changed+" of "+total+" vanilla combinations changed among unpinned space. Same tier/scroll for all "+total+" pairs; "+seedChanges+" results differ in adjacent seed.\n");h.succeed();
    }

    @GameTest public void newBooksNativeAnvilAndRegistryAdaptation(GameTestHelper h){
        var p=player(h);var items=Map.of("hush_step",Items.DIAMOND_BOOTS,"borrowed_flame",Items.DIAMOND_SWORD,"returning_feather",Items.CROSSBOW,"swift_draw",Items.BOW,"paper_kite",Items.DIAMOND_BOOTS,"parry",Items.SHIELD,"undertow",Items.TRIDENT,"water_reflection",Items.DIAMOND_HELMET,"star_bridge",Items.DIAMOND_BOOTS);
        var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());
        for(var entry:items.entrySet()){var holder=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(ResearchContent.id(entry.getKey())).orElseThrow();
            h.assertTrue(holder.value().canEnchant(new ItemStack(entry.getValue()))&&!holder.value().canEnchant(new ItemStack(Items.DIRT)),"data-driven equipment adaptation "+entry.getKey());
            p.experienceLevel=100;var menu=new net.minecraft.world.inventory.AnvilMenu(72,p.getInventory(),net.minecraft.world.inventory.ContainerLevelAccess.create(h.getLevel(),pos));p.containerMenu=menu;
            int max=holder.value().getMaxLevel();var book=ResearchBooks.output(h.getLevel().registryAccess(),new ResearchPolicy.Result(holder.key().identifier().toString(),max,1,false));menu.getSlot(0).set(new ItemStack(entry.getValue()));menu.getSlot(1).set(book);var result=menu.quickMoveStack(p,2);h.assertTrue(net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(holder,result)==max,"real native anvil applies new maximum level "+entry.getKey());menu.removed(p);
        }
        var pool=dev.differentenchant.enchanting.EnchantmentCandidates.forItem(h.getLevel().registryAccess(),new ItemStack(Items.BOOK)).candidates();h.assertTrue(pool.stream().noneMatch(e->e.id().startsWith("differentenchant:")),"all lost enchantments remain excluded from ordinary random machine");h.succeed();
    }

    @GameTest(maxTicks=80) public void undertowLoyaltyRecoveryAndRiptideNoProjectile(GameTestHelper h){
        h.onEachTick(()->TideVortices.tick(h.getLevel().getServer()));var p=player(h);h.onEachTick(p::doTick);var base=h.absolutePos(new BlockPos(3,2,3));for(int x=1;x<=5;x++)for(int z=1;z<=6;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var item=gear(h,Items.TRIDENT,"undertow",1);vanilla(h,item,"loyalty",3);p.setItemSlot(EquipmentSlot.MAINHAND,item);p.setXRot(65);p.setYRot(0);p.startUsingItem(InteractionHand.MAIN_HAND);
        h.assertTrue(((TridentItem)Items.TRIDENT).releaseUsing(item,h.getLevel(),p,71980)&&p.getMainHandItem().isEmpty(),"actual native throw removes one real loyalty trident from hand");p.stopUsingItem();
        h.runAfterDelay(7,()->h.assertTrue(TideVortices.count(p.getUUID())==1,"loyalty throw still starts one impact field"));
        h.runAfterDelay(48,()->{h.assertTrue(p.getInventory().countItem(Items.TRIDENT)==1&&h.getLevel().getEntitiesOfClass(ThrownTrident.class,p.getBoundingBox().inflate(20)).isEmpty(),"native loyalty returns exactly one original enchanted trident: inv="+p.getInventory().countItem(Items.TRIDENT)+" player="+p.position()+" projectiles="+h.getLevel().getEntitiesOfClass(ThrownTrident.class,p.getBoundingBox().inflate(30)).stream().map(e->e.position()+" velocity="+e.getDeltaMovement()+" noPhysics="+e.isNoPhysics()).toList());h.assertTrue(TideVortices.count(p.getUUID())==0,"loyalty field expired independently of recovered weapon");
            p.getInventory().clearContent();var spin=gear(h,Items.TRIDENT,"undertow",2);vanilla(h,spin,"riptide",3);p.setItemSlot(EquipmentSlot.MAINHAND,spin);h.assertTrue(!((TridentItem)Items.TRIDENT).releaseUsing(spin,h.getLevel(),p,71980),"dry riptide retains vanilla refusal");
            for(int x=-1;x<=1;x++)for(int y=0;y<3;y++)for(int z=-1;z<=1;z++)h.getLevel().setBlockAndUpdate(base.offset(x,y,z),Blocks.WATER.defaultBlockState());p.teleportTo(base.getX()+.5,base.getY(),base.getZ()+.5);p.doTick();p.doTick();h.assertTrue(((TridentItem)Items.TRIDENT).releaseUsing(spin,h.getLevel(),p,71980)&&p.isAutoSpinAttack()&&spin.getCount()==1,"native water riptide spins without consuming trident");h.assertTrue(TideVortices.count(p.getUUID())==0&&h.getLevel().getEntitiesOfClass(ThrownTrident.class,p.getBoundingBox().inflate(20)).isEmpty(),"riptide has no impact projectile and never creates free vortex");h.succeed();});
    }
    @GameTest(maxTicks=60) public void starTimeoutAndUnloadedRayGuard(GameTestHelper h){
        var p=player(h);p.setItemSlot(EquipmentSlot.FEET,gear(h,Items.DIAMOND_BOOTS,"star_bridge",1));var step=new StarStep(h.getLevel(),p,p.getX(),p.getY()-.1,p.getZ());h.getLevel().addFreshEntity(step);
        var far=p.position().add(160000,0,160000);int cx=net.minecraft.util.Mth.floor(far.x)>>4,cz=net.minecraft.util.Mth.floor(far.z)>>4;h.assertTrue(h.getLevel().getChunkSource().getChunkNow(cx,cz)==null&&!LoadedArea.ray(h.getLevel(),far,far.add(1,0,1))&&h.getLevel().getChunkSource().getChunkNow(cx,cz)==null,"collision-ray preflight never obtains an unloaded chunk");
        h.runAfterDelay(44,()->{h.assertTrue(step.isRemoved(),"native entity tick clears timed-out collidable display");h.succeed();});
    }

    @GameTest public void diagonalStarBridgeFollowsMotionWithRealCollision(GameTestHelper h){
        var p=player(h);var base=h.absolutePos(new BlockPos(3,2,3));h.getLevel().setBlockAndUpdate(base.below(),Blocks.STONE.defaultBlockState());p.setItemSlot(EquipmentSlot.FEET,gear(h,Items.DIAMOND_BOOTS,"star_bridge",1));p.setSprinting(true);p.teleportTo(base.getX()+.8,base.getY(),base.getZ()+.8);p.setOnGround(true);StarBridges.tick(p);p.teleportTo(base.getX()+1.5,base.getY()-.02,base.getZ()+1.5);p.setOnGround(false);StarBridges.tick(p);
        var list=h.getLevel().getEntitiesOfClass(StarStep.class,p.getBoundingBox().inflate(4));h.assertTrue(list.size()==3,"diagonal running creates exactly three safe steps");p.move(MoverType.SELF,new Vec3(0,-1,0));for(int i=0;i<2;i++){p.move(MoverType.SELF,new Vec3(.7,0,.7));p.move(MoverType.SELF,new Vec3(0,-.1,0));h.assertTrue(p.onGround()&&p.getY()>base.getY()-.2,"native diagonal movement remains supported on following step "+i);}h.succeed();
    }

}
