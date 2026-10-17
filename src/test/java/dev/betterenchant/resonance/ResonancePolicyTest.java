package dev.betterenchant.resonance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResonancePolicyTest {
    @Test void boundedEnergyAndOnlyFirstTierUpgrades() {
        for(int tier=1;tier<=10;tier++) for(int stored=-2;stored<=15;stored++) for(int spent=0;spent<=40;spent++) {
            var result=ResonancePolicy.charge(stored,spent,false,tier);
            int energy=Math.min(12,ResonancePolicy.clamp(stored)+spent);
            assertEquals(energy,result.energy());
            assertEquals(spent>0 && energy==12,result.completed());
            assertEquals(result.completed() && tier==1?2:tier,result.tier());
        }
        assertEquals(12,ResonancePolicy.charge(11,Integer.MAX_VALUE,false,1).energy());
    }
    @Test void previewsFailuresAndCreativeHaveNoEnergy() {
        for(int tier=1;tier<=5;tier++) for(int stored=0;stored<=12;stored++) {
            assertEquals(new ResonancePolicy.Charge(stored,tier,false),ResonancePolicy.charge(stored,0,false,tier));
            assertEquals(new ResonancePolicy.Charge(stored,tier,false),ResonancePolicy.charge(stored,18,true,tier));
        }
        assertThrows(IllegalArgumentException.class,()->ResonancePolicy.charge(0,-1,false,1));
        assertThrows(IllegalArgumentException.class,()->ResonancePolicy.charge(0,1,false,0));
    }
    @Test void randomOneTwoThreeAndDirectedActualLevels() {
        for(int spent:new int[]{1,2,3,5,8,18}) assertEquals(Math.min(12,spent),ResonancePolicy.charge(0,spent,false,1).energy());
        int energy=0;
        for(int i=0;i<4;i++) energy=ResonancePolicy.charge(energy,3,false,1).energy();
        assertEquals(12,energy);
        assertEquals(11,ResonancePolicy.charge(8,3,false,1).energy());
    }
}
