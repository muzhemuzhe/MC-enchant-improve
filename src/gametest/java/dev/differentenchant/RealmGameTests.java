package dev.differentenchant;

import dev.differentenchant.realm.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public final class RealmGameTests {
    @GameTest public void faultDistanceCodecAndVisitorDoNotReuseUnboundValues(GameTestHelper h) {
        var f=new FaultEdgeDensity(net.minecraft.world.level.levelgen.DensityFunctions.constant(1),64);
        var point=new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(8,150,8);
        h.assertTrue(f.compute(point)==64,"bounded inside field");
        var codec=net.minecraft.world.level.levelgen.DensityFunction.HOLDER_HELPER_CODEC;
        var encoded=codec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,f).getOrThrow();
        var decoded=codec.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow();
        h.assertTrue(decoded.compute(point)==64,"registered native density codec roundtrip");
        var mapped=f.mapAll(input->input instanceof FaultEdgeDensity?input:net.minecraft.world.level.levelgen.DensityFunctions.constant(-1));
        h.assertTrue(mapped.compute(point)==-1&&f.compute(point)==64,"visitor binds a fresh input without stale point cache");
        h.succeed();
    }
    @GameTest public void landingRejectsFluidsHolesAndObstructions(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(2,3,2));
        level.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(p.above(),Blocks.AIR.defaultBlockState());
        h.assertTrue(RealmTravel.safe(level,p),"solid dry floor and two air blocks");
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.LAVA,Blocks.WATER,Blocks.STONE}) {
            level.setBlockAndUpdate(p,block.defaultBlockState());h.assertTrue(!RealmTravel.safe(level,p),"unsafe landing rejected");
        }
        level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(p.below(),Blocks.MAGMA_BLOCK.defaultBlockState());
        h.assertTrue(!RealmTravel.safe(level,p),"hazardous floor rejected");
        level.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(p,Blocks.FIRE.defaultBlockState());
        h.assertTrue(!RealmTravel.safe(level,p),"collision-free fire rejected");h.succeed();
    }
    @GameTest public void nativeReturnCodecPreservesIndependentPlayerAnchors(GameTestHelper h) {
        var data=RealmReturnData.get(h.getLevel().getServer());var a=java.util.UUID.randomUUID();var b=java.util.UUID.randomUUID();
        var anchor=new RealmReturnData.Anchor("minecraft:overworld",1.25,64,2.75,32,-8);data.put(a,anchor);
        var encoded=RealmReturnData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,data).getOrThrow();
        var decoded=RealmReturnData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow();
        h.assertTrue(anchor.equals(decoded.get(a))&&decoded.get(b)==null,"exact positions and UUID separation through real Codec");
        data.remove(a);h.succeed();
    }
}
