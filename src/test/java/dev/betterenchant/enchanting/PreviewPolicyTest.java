package dev.betterenchant.enchanting;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewPolicyTest {
    @Test void stablePrefixesAtAllTiersAndArbitraryResultSizes() {
        for(int size=0;size<=100;size++) {
            var result=IntStream.range(0,size).boxed().toList();
            for(int tier=1;tier<=10;tier++) {
                int count=Math.min(size,tier>=5?size:tier>=3?2:1);
                assertEquals(result.subList(0,count),PreviewPolicy.reveal(result,tier));
                assertEquals(size,result.size());
            }
        }
        assertThrows(IllegalArgumentException.class,()->PreviewPolicy.revealedCount(1,-1));
    }
    @Test void actualSlotGeometryAndHitRegionsAreDisjointAndFitMinimumCanvas() {
        assertTrue(MachineGuiLayout.WIDTH<=320 && MachineGuiLayout.HEIGHT<=240);
        for(int i=0;i<=MachineGuiLayout.CORE_SLOT;i++) {
            var slot=MachineGuiLayout.slot(i);
            for(int x=slot.x()-1;x<=slot.x()+16;x++) for(int y=slot.y()-1;y<=slot.y()+16;y++) {
                assertFalse(MachineGuiLayout.DIRECTED_LIST.contains(x,y));assertFalse(MachineGuiLayout.ENCHANT_BUTTON.contains(x,y));
            }
            assertTrue(slot.x()>0 && slot.x()+17<MachineGuiLayout.WIDTH && slot.y()>0 && slot.y()+17<MachineGuiLayout.HEIGHT);
            for(int j=0;j<3;j++) for(int x=slot.x()-1;x<=slot.x()+16;x++) for(int y=slot.y()-1;y<=slot.y()+16;y++) assertFalse(MachineGuiLayout.option(j).contains(x,y));
            for(int k=i+1;k<=MachineGuiLayout.CORE_SLOT;k++) {
                var other=MachineGuiLayout.slot(k);
                assertTrue(Math.abs(slot.x()-other.x())>=18 || Math.abs(slot.y()-other.y())>=18);
            }
        }
        for(int i=0;i<3;i++) {
            var rect=MachineGuiLayout.option(i);
            assertTrue(rect.contains(rect.x(),rect.y()));
            assertFalse(rect.contains(rect.x()+rect.width(),rect.y()));
            for(int j=0;j<3;j++) if(i!=j) assertFalse(MachineGuiLayout.option(j).contains(rect.x(),rect.y()));
        }
    }
}
