package dev.differentenchant;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** One delivery-time research acceptance case; no historical probability/ruins suite. */
public final class DisenchantIntegrationGameTests {
    @GameTest public void researchDirectPickupOwnershipAndDestroyStillWork(GameTestHelper h){new ResearchGameTests().successTwoPlayersShiftCloseAndDestroyedWorkstation(h);}
}
