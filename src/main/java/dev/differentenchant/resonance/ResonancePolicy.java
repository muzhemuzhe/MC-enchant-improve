package dev.differentenchant.resonance;

/** Paid experience levels are energy; no banking, overflow, or upgrades beyond tier two. */
public final class ResonancePolicy {
    public static final int CAPACITY = 12;
    public static int clamp(int energy) { return Math.clamp(energy, 0, CAPACITY); }
    public record Charge(int energy, int tier, boolean completed) {}
    public static Charge charge(int stored, int paidLevels, boolean creative, int tier) {
        if (paidLevels < 0 || tier < 1) throw new IllegalArgumentException("Invalid committed enchantment");
        int next = creative || paidLevels == 0 ? clamp(stored) : (int)Math.min(CAPACITY, (long)clamp(stored) + paidLevels);
        boolean completed = !creative && paidLevels > 0 && next == CAPACITY;
        return new Charge(next, completed && tier == 1 ? 2 : tier, completed);
    }
    private ResonancePolicy() {}
}
