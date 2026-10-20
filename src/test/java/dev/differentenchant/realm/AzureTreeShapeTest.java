package dev.differentenchant.realm;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AzureTreeShapeTest {
    @Test void deterministicBoundedConnectedWoodAndSupportedFoliage() {
        var fingerprints=new HashSet<Integer>();
        for(long seed=0;seed<300;seed++) {
            var shape=AzureTreeShape.create(seed);
            assertEquals(shape,AzureTreeShape.create(seed));fingerprints.add(shape.hashCode());
            var wood=new HashSet<AzureTreeShape.Point>();shape.wood().forEach(w->wood.add(w.point()));
            assertTrue(shape.wood().stream().anyMatch(w->w.axis()=='x'));
            assertTrue(shape.wood().stream().anyMatch(w->w.axis()=='z'));
            assertTrue(shape.leaves().size()>70);
            var seen=new HashSet<AzureTreeShape.Point>();var queue=new ArrayDeque<AzureTreeShape.Point>();queue.add(new AzureTreeShape.Point(0,0,0));
            while(!queue.isEmpty()) {var p=queue.remove();if(!seen.add(p))continue;for(var q:wood)if(p.distance(q)==1&&!seen.contains(q))queue.add(q);}
            assertEquals(wood,seen,"wood must remain face connected");
            shape.leaves().forEach((p,d)->{assertTrue(d>=1&&d<=6);assertFalse(wood.contains(p));assertTrue(Math.abs(p.x())<=7&&Math.abs(p.z())<=7&&p.y()<=14);});
        }
        assertEquals(300,fingerprints.size());
    }
}
