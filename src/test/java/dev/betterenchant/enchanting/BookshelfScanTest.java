package dev.betterenchant.enchanting;

import java.util.*;
import org.junit.jupiter.api.Test;
import dev.betterenchant.structure.StructurePattern.Offset;
import static org.junit.jupiter.api.Assertions.*;

class BookshelfScanTest {
    @Test void wholeFootprintHasEightyUniquePositionsInBothLayersAndRings() {
        var positions=new HashSet<>(BookshelfScan.OFFSETS);
        assertEquals(80,positions.size());assertEquals(80,BookshelfScan.OFFSETS.size());
        for(int y:List.of(-1,0)) {
            assertEquals(40,positions.stream().filter(p->p.y()==y).count());
            for(int x=-3;x<=3;x++) for(int z=-3;z<=3;z++)
                assertEquals(Math.abs(x)>1 || Math.abs(z)>1,positions.contains(new Offset(x,y,z)));
        }
        assertTrue(positions.stream().noneMatch(p->p.y()==1 || p.y()==-2));
        assertFalse(positions.contains(new Offset(4,0,0)));
        for(int rotation=0;rotation<4;rotation++) {
            var rotated=new HashSet<Offset>();for(var position:positions) rotated.add(position.rotate(rotation));
            assertEquals(positions,rotated,"four machine directions have identical surroundings");
        }
    }
    private static int count(Set<Offset> providers) {
        var reads=new HashSet<Offset>();
        return BookshelfScan.count(new BookshelfScan.View() {
            public boolean isLoaded(Offset p) {return true;}
            public boolean isProvider(Offset p) {assertTrue(reads.add(p),"read each physical location once");return providers.contains(p);}
        });
    }
    @Test void countsAllBoundaryQuantitiesAndCapsEffectiveCountNotGeometry() {
        for(int shelves:List.of(0,1,15,16,32,35,36,37,60,80)) {
            var providers=new HashSet<>(BookshelfScan.OFFSETS.subList(0,shelves));
            assertEquals(Math.min(shelves,36),count(providers));
        }
    }
    @Test void unloadedPositionsAreNeverReadAndDoNotAcquireAnything() {
        var available=new HashSet<>(BookshelfScan.OFFSETS.subList(0,17));
        var probes=new HashSet<Offset>();var reads=new HashSet<Offset>();
        int result=BookshelfScan.count(new BookshelfScan.View() {
            public boolean isLoaded(Offset p) {assertTrue(probes.add(p));return available.contains(p);}
            public boolean isProvider(Offset p) {assertTrue(available.contains(p),"unloaded block access forbidden");assertTrue(reads.add(p));return true;}
        });
        assertEquals(17,result);assertEquals(80,probes.size());assertEquals(available,reads);
    }
}
