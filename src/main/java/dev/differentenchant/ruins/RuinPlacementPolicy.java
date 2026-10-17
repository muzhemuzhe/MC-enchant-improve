package dev.differentenchant.ruins;

import java.util.Arrays;
import java.util.OptionalInt;

/** Terrain decisions use noise-column samples only, never acquiring live chunks. */
public final class RuinPlacementPolicy {
    public static OptionalInt floor(int[] heights, boolean buried, int seaLevel, int minY, int burialDepth) {
        if(heights.length!=9 || burialDepth<14 || burialDepth>32) throw new IllegalArgumentException("Invalid terrain samples");
        int low=Arrays.stream(heights).min().orElseThrow(), high=Arrays.stream(heights).max().orElseThrow();
        if(low<seaLevel || high-low>(buried?12:4)) return OptionalInt.empty();
        int floor=buried?low-burialDepth:high;
        return floor>minY+8?OptionalInt.of(floor):OptionalInt.empty();
    }
    private RuinPlacementPolicy() {}
}
