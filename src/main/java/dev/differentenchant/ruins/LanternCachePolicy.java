package dev.differentenchant.ruins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Piece-local choices: fixed worldgen seed, independent of chunk order and rotation. */
public final class LanternCachePolicy {
    public record Position(int x,int y,int z) {}
    public static final String TEMPLATE = "ruins/lantern_sanctum";
    public static final String LOOT = "chests/lantern_cache";
    public static final List<Position> CANDIDATES;
    static {
        var positions = new ArrayList<Position>();
        for(int x:new int[]{9,13,19,23}) for(int z:new int[]{9,13,19,23}) positions.add(new Position(x,1,z));
        CANDIDATES = List.copyOf(positions);
    }
    private LanternCachePolicy() {}
    public static List<Position> select(long seed) {
        var random = new Random(seed);
        var positions = new ArrayList<>(CANDIDATES);
        int count = 4 + random.nextInt(4);
        Collections.shuffle(positions,random);
        return List.copyOf(positions.subList(0,count));
    }
    public static long lootSeed(long seed,Position p) {
        // Coordinate-derived loot seeds remain stable if chunk generation order changes.
        return seed ^ (p.x()*341873128712L) ^ (p.z()*132897987541L);
    }
}
