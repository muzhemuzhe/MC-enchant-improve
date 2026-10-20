package dev.differentenchant.realm;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AzureTreeShapeTest {
    @Test void allFourProfilesAreReproducibleConnectedAndHaveActualLeafSupportPaths() {
        for(var kind:AzureTreeShape.Kind.values())for(long seed=0;seed<100;seed++) {
            var shape=AzureTreeShape.create(seed,kind);assertEquals(shape,AzureTreeShape.create(seed,kind));
            var wood=new HashSet<AzureTreeShape.Point>();shape.wood().forEach(w->wood.add(w.point()));
            var seen=new HashSet<AzureTreeShape.Point>();var queue=new ArrayDeque<AzureTreeShape.Point>();queue.add(new AzureTreeShape.Point(0,0,0));
            while(!queue.isEmpty()){var p=queue.remove();if(!seen.add(p))continue;for(var q:wood)if(p.distance(q)==1&&!seen.contains(q))queue.add(q);}
            assertEquals(wood,seen,"wood face connectivity, "+kind+"/"+seed);
            assertTrue(shape.leaves().size()>55,kind+"/"+seed);
            shape.leaves().forEach((p,d)->{
                assertTrue(Math.abs(p.x())<=11&&Math.abs(p.z())<=11&&p.y()<=21&&p.y()>=0,"native neighboring-chunk envelope");
                assertFalse(wood.contains(p));assertTrue(d>=1&&d<=6);
                assertTrue(d==1?wood.stream().anyMatch(w->p.distance(w)==1):shape.leaves().entrySet().stream().anyMatch(e->e.getValue()==d-1&&p.distance(e.getKey())==1),"leaf has a real path toward wood");
            });
            if(kind==AzureTreeShape.Kind.ANCIENT)for(int x=0;x<2;x++)for(int z=0;z<2;z++)assertTrue(wood.contains(new AzureTreeShape.Point(x,0,z)));
            int min=shape.leaves().keySet().stream().mapToInt(AzureTreeShape.Point::y).min().orElseThrow();
            int max=shape.leaves().keySet().stream().mapToInt(AzureTreeShape.Point::y).max().orElseThrow();
            assertTrue(max-min>=5,"volumetric crown, never a two-block umbrella");
        }
    }
    @Test void mixedForestIncludesAllSilhouettesAndOldTreesStayRare() {
        var counts=new EnumMap<AzureTreeShape.Kind,Integer>(AzureTreeShape.Kind.class);
        for(long seed=0;seed<2000;seed++)counts.merge(AzureTreeShape.create(seed).kind(),1,Integer::sum);
        assertEquals(4,counts.size());assertTrue(counts.get(AzureTreeShape.Kind.ANCIENT)>=40&&counts.get(AzureTreeShape.Kind.ANCIENT)<=180,counts.toString());
    }
}
