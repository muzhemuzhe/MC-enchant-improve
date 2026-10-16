package dev.betterenchant.enchanting;

import java.util.List;

/** Information only: never consumes randomness or changes an offer. */
public final class PreviewPolicy {
    private PreviewPolicy() {}
    public static int revealedCount(int tier, int total) {
        if (total < 0) throw new IllegalArgumentException("Negative result size");
        return Math.min(total, tier >= 5 ? total : tier >= 3 ? 2 : 1);
    }
    public static <T> List<T> reveal(List<T> result, int tier) {
        return List.copyOf(result.subList(0, revealedCount(tier, result.size())));
    }
}
