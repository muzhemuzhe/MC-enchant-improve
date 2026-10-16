package dev.betterenchant.structure;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.betterenchant.structure.StructurePattern.*;

class StructureValidatorTest {
    private static final Offset ORIGIN = new Offset(15, 80, -16);
    private static Map<Offset, Material> pattern(int rotation) {
        Map<Offset, Material> map = new HashMap<>();
        for (Cell cell : StructurePattern.CELLS) map.put(ORIGIN.add(cell.offset().rotate(rotation)), cell.material());
        return map;
    }
    private static StructureValidator.View view(Map<Offset, Material> map) {
        return new StructureValidator.View() {
            public boolean isLoaded(Offset p) { return true; }
            public boolean matches(Offset p, Material m) { return map.get(p) == m; }
        };
    }
    @Test void exactlyEighteenUniquePositionsInEveryOrientation() {
        for (int rotation = 0; rotation < 4; rotation++) {
            var map = pattern(rotation);
            assertEquals(18, map.size());
            var result = StructureValidator.validate(view(map), ORIGIN, rotation);
            assertTrue(result.valid());
            assertEquals(18, result.placements().size());
            assertEquals(9, map.values().stream().filter(m -> m == Material.FLOOR).count());
        }
    }
    @Test void everySingleMissingOrWrongCellRejectsWithoutPartialPlan() {
        for (int rotation = 0; rotation < 4; rotation++) {
            for (Offset missing : pattern(rotation).keySet()) {
                var map = pattern(rotation);
                map.remove(missing);
                var result = StructureValidator.validate(view(map), ORIGIN, rotation);
                assertFalse(result.valid());
                assertTrue(result.placements().isEmpty());
                map.put(missing, Material.CONTROLLER == pattern(rotation).get(missing) ? Material.EDGE : Material.CONTROLLER);
                assertFalse(StructureValidator.validate(view(map), ORIGIN, rotation).valid());
            }
        }
    }
    @Test void unloadedChunkNeverReadsWorldStates() {
        var result = StructureValidator.validate(new StructureValidator.View() {
            public boolean isLoaded(Offset p) { return p.x() < 16; }
            public boolean matches(Offset p, Material m) { fail("state read before chunk preflight"); return false; }
        }, ORIGIN, 0);
        assertEquals("unloaded", result.failure());
        assertTrue(result.placements().isEmpty());
    }
    @Test void rotationPreservesVerticalCoordinateAndHasCorrectHandedness() {
        Offset point = new Offset(1, -1, 0);
        assertEquals(new Offset(0, -1, 1), point.rotate(1));
        assertEquals(new Offset(-1, -1, 0), point.rotate(2));
        assertEquals(new Offset(0, -1, -1), point.rotate(3));
        assertEquals(point, point.rotate(4));
    }
}
