package dev.differentenchant.structure;

import java.util.List;
import java.util.ArrayList;
import dev.differentenchant.structure.StructurePattern.*;

/** Read-only validation. A view must not acquire chunks in isLoaded. */
public final class StructureValidator {
    public interface View {
        boolean isLoaded(Offset position);
        boolean matches(Offset position, Material expected);
    }
    public record Placement(Cell cell, Offset position) {}
    public record Result(List<Placement> placements, String failure) {
        public boolean valid() { return failure.isEmpty(); }
    }
    public static Result validate(View view, Offset origin, int quarterTurns) {
        var plan = new ArrayList<Placement>();
        // Never even inspect a state until every chunk is confirmed available.
        for (Cell cell : StructurePattern.CELLS) {
            Offset position = origin.add(cell.offset().rotate(quarterTurns));
            if (!view.isLoaded(position)) return new Result(List.of(), "unloaded");
            plan.add(new Placement(cell, position));
        }
        for (Placement placement : plan) {
            if (!view.matches(placement.position(), placement.cell().material())) {
                return new Result(List.of(), "mismatch:" + placement.cell().index());
            }
        }
        return new Result(List.copyOf(plan), "");
    }
    private StructureValidator() {}
}
