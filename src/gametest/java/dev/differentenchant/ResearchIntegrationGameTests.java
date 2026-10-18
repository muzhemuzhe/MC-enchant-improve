package dev.differentenchant;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Opt-in once before delivery: reuse three relevant existing acceptance cases, not the historical suite. */
public final class ResearchIntegrationGameTests {
    @GameTest public void existingPaidRandomCoreProductionAndTierTwo(GameTestHelper h) {
        new ResonanceGameTests().randomActualPaymentsElevenFullThenRepeatProductionWithoutTierThree(h);
    }
    @GameTest public void existingDirectedBatchAndThirtyLevelThreshold(GameTestHelper h) {
        new DirectedGameTests().batchIIIToVPreservesComponentsAndPaysOnceBelowThreshold(h);
    }
    @GameTest public void existingFormationDisassemblyAndDisabledVanillaTable(GameTestHelper h) {
        new VanillaTableGameTests().emptyHandAssemblyAndDisassemblyRestorationStillWork(h);
    }
}
