package dev.differentenchant.realm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FaultEdgeDistanceTest {
    @Test void planeDistanceHasPhysicalUnitsAndIgnoresFieldAmplitude(){
        for(int angle=0;angle<32;angle++)for(int offset:new int[]{2,8,24,56,110,180}){
            double a=angle*Math.PI/16,dx=Math.cos(a),dz=Math.sin(a);
            FaultEdgeDistance.Field f=(x,z)->offset+x*dx+z*dz;
            double d=FaultEdgeDistance.measure(0,0,256,f);
            assertTrue(d>=offset-1.5&&d<=offset*1.09+1.5,"plane normal distance "+offset+" / "+d);
            // Integer block probes and normal-order ties have sub-block rounding.
            assertEquals(d,FaultEdgeDistance.measure(0,0,256,(x,z)->f.at(x,z)*.000001),1);
            assertEquals(d,FaultEdgeDistance.measure(0,0,256,(x,z)->f.at(x,z)*1000000),1);
        }
    }
    @Test void curvedCoastOutsideAndBudgetAreBounded(){
        for(int x:new int[]{0,40,120,180}){
            double d=FaultEdgeDistance.measure(x,0,256,(xx,z)->220-Math.hypot(xx,z));
            assertTrue(d>=220-x-1.5&&d<=(220-x)*1.09+1.5);
        }
        assertEquals(-1,FaultEdgeDistance.measure(221,0,256,(x,z)->220-Math.hypot(x,z)));
        int[] calls={0};assertEquals(256,FaultEdgeDistance.measure(0,0,256,(x,z)->{calls[0]++;return 1;}));
        assertTrue(calls[0]<=140,"bounded scalar samples, no world scans");
    }
}
