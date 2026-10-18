package dev.differentenchant.ruins;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

final class LanternCachePolicyTest {
    @Test void boundedDistinctCoveredCandidatesAndStableSeeds() {
        assertEquals(16,new HashSet<>(LanternCachePolicy.CANDIDATES).size());
        var patterns=new HashSet<>();
        for(long seed=0;seed<10000;seed++) {
            var selected=LanternCachePolicy.select(seed);
            assertTrue(selected.size()>=4 && selected.size()<=7);
            assertEquals(selected.size(),new HashSet<>(selected).size());
            assertTrue(LanternCachePolicy.CANDIDATES.containsAll(selected));
            assertEquals(selected,LanternCachePolicy.select(seed));
            final long currentSeed=seed;
            assertEquals(selected.size(),selected.stream().map(p->LanternCachePolicy.lootSeed(currentSeed,p)).distinct().count());
            patterns.add(selected);
        }
        assertTrue(patterns.size()>9000,"different worldgen seeds vary the hidden subset");
        for(var p:LanternCachePolicy.CANDIDATES) {
            assertEquals(1,p.y());assertTrue(Math.abs(p.x()-16)+Math.abs(p.z()-16)<=19);
        }
    }
    @Test void everyCandidateCanBeSelectedAndOmitted() {
        for(var p:LanternCachePolicy.CANDIDATES) {
            boolean present=false,absent=false;
            for(long seed=0;seed<1000;seed++) {boolean found=LanternCachePolicy.select(seed).contains(p);present|=found;absent|=!found;}
            assertTrue(present && absent);
        }
    }
}
