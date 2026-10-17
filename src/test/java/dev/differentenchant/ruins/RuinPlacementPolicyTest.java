package dev.differentenchant.ruins;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuinPlacementPolicyTest {
    @Test void surfaceRejectsOceanAndSteepGround() {
        int[] heights={64,65,66,67,68,66,66,65,64};
        assertEquals(68,RuinPlacementPolicy.floor(heights,false,63,-64,14).orElseThrow());
        heights[8]=69;assertTrue(RuinPlacementPolicy.floor(heights,false,63,-64,14).isEmpty());
        heights[8]=62;assertTrue(RuinPlacementPolicy.floor(heights,false,63,-64,14).isEmpty());
    }
    @Test void burialStaysFullyCoveredAndAboveBottomAtEveryAllowedDepth() {
        int[] heights=new int[9];Arrays.fill(heights,70);
        for(int depth=14;depth<=32;depth++) {
            int y=RuinPlacementPolicy.floor(heights,true,63,-64,depth).orElseThrow();
            assertTrue(y+6<=70-8);assertTrue(y>-56);
        }
        assertThrows(IllegalArgumentException.class,()->RuinPlacementPolicy.floor(new int[8],true,63,-64,14));
        assertThrows(IllegalArgumentException.class,()->RuinPlacementPolicy.floor(heights,true,63,-64,13));
    }
}
