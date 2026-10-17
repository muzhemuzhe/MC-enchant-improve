package dev.differentenchant.structure;

import java.util.ArrayList;
import java.util.List;

/** Pure gameplay coordinates: controller is (0, 0, 0), lower floor is y=-1. */
public final class StructurePattern {
    public enum Material { FLOOR, CORNER, EDGE, CONTROLLER }
    public record Offset(int x, int y, int z) {
        public Offset rotate(int quarterTurns) {
            return switch (Math.floorMod(quarterTurns, 4)) {
                case 0 -> this;
                case 1 -> new Offset(-z, y, x);
                case 2 -> new Offset(-x, y, -z);
                default -> new Offset(z, y, -x);
            };
        }
        public Offset add(Offset other) { return new Offset(x + other.x, y + other.y, z + other.z); }
    }
    public record Cell(int index, Offset offset, Material material) {}
    public static final List<Cell> CELLS;
    static {
        var cells = new ArrayList<Cell>();
        for (int y = -1; y <= 0; y++) {
            for (int z = -1; z <= 1; z++) {
                for (int x = -1; x <= 1; x++) {
                    Material material = y == -1 ? Material.FLOOR
                            : x == 0 && z == 0 ? Material.CONTROLLER
                            : x != 0 && z != 0 ? Material.CORNER : Material.EDGE;
                    cells.add(new Cell(cells.size(), new Offset(x, y, z), material));
                }
            }
        }
        CELLS = List.copyOf(cells);
    }
    private StructurePattern() {}
}
