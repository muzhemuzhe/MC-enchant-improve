package dev.betterenchant.enchanting;

import java.util.*;
import java.util.random.RandomGenerator;

/** Pure probability policy. The registry adapter supplies ONLY legal candidates. */
public final class RandomEnchantPolicy {
    public record Candidate(String id, int weight, int minLevel, int maxLevel, Set<String> incompatible) {
        public Candidate {
            Objects.requireNonNull(id);
            incompatible = Set.copyOf(incompatible);
            if (id.isBlank() || weight < 1 || weight > 1_000_000 || minLevel < 1 || maxLevel < minLevel || maxLevel > 255)
                throw new IllegalArgumentException("Invalid enchantment candidate: " + id);
        }
    }
    public record Choice(String id, int level) {}
    public record Context(int tier, int shelves, int slot) {
        public Context {
            if (tier < 1 || tier > 10 || shelves < 0 || slot < 0 || slot > 2)
                throw new IllegalArgumentException("Invalid enchanting context");
            shelves=Math.min(shelves,BookshelfScan.MAX_EFFECTIVE);
        }
    }
    public record Parameters(double rarityTier, double rarityShelves, double raritySlot,
                             double levelBase, double levelTier, double levelShelves, double levelSlot,
                             double extraBase, double extraTier, double extraShelves, double extraSlot,
                             int maxEnchantments, int shelfScale, double shelfTailGain, Map<String, Double> typeMultipliers) {
        public Parameters {
            for (double value : new double[]{rarityTier, rarityShelves, raritySlot, levelTier, levelShelves, levelSlot})
                finiteRange(value, 0, 4);
            finiteRange(levelBase, -12, 12);
            for (double value : new double[]{extraBase, extraTier, extraShelves, extraSlot}) finiteRange(value, 0, 1);
            finiteRange(shelfTailGain,.01,1);
            if (maxEnchantments < 1 || maxEnchantments > 6 || shelfScale < 1 || shelfScale > BookshelfScan.MAX_EFFECTIVE)
                throw new IllegalArgumentException("Invalid multi-enchantment/shelf limit");
            typeMultipliers = Map.copyOf(typeMultipliers);
            typeMultipliers.forEach((id, value) -> {
                if (id.isBlank() || value == null) throw new IllegalArgumentException("Invalid multiplier");
                if (value != 0) finiteRange(value, .01, 100);
            });
        }
        public static Parameters defaults() {
            return new Parameters(.35, .8, .2, -4, .8, 2, .6, .15, .04, .15, .1, 4, 15, .5, Map.of());
        }
    }
    private static void finiteRange(double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid probability parameter: " + value);
    }
    public static double rarityExponent(Context c, Parameters p) {
        return 1 / (1 + p.rarityTier * (c.tier - 1) + p.rarityShelves * bookshelfInfluence(c, p) + p.raritySlot * c.slot);
    }
    public static double levelTilt(Context c, Parameters p) {
        return Math.clamp(p.levelBase + p.levelTier * (c.tier - 1) + p.levelShelves * bookshelfInfluence(c, p) + p.levelSlot * c.slot, -12, 12);
    }
    public static double bookshelfInfluence(Context c, Parameters p) {
        double base=Math.min(c.shelves,p.shelfScale)/(double)p.shelfScale;
        return base+p.shelfTailGain*Math.log1p(Math.max(0,c.shelves-p.shelfScale)/(double)p.shelfScale);
    }
    public static double typeWeight(Candidate candidate, Context c, Parameters p) {
        return p.typeMultipliers.getOrDefault(candidate.id, 1.0) * Math.pow(candidate.weight, rarityExponent(c, p));
    }
    public static double[] levelProbabilities(Candidate candidate, Context c, Parameters p) {
        int count = candidate.maxLevel - candidate.minLevel + 1;
        double[] probabilities = new double[count];
        double total = 0;
        for (int i = 0; i < count; i++) {
            probabilities[i] = Math.exp(levelTilt(c, p) * (count == 1 ? 0 : i / (double)(count - 1)));
            total += probabilities[i];
        }
        for (int i = 0; i < count; i++) probabilities[i] /= total;
        return probabilities;
    }
    public static Prepared prepare(List<Candidate> source, Context context, Parameters parameters) {
        return new Prepared(source, context, parameters);
    }
    public static final class Prepared {
        private final List<Candidate> pool;
        private final double[] weights;
        private final double[][] levels;
        private final int limit;
        private final double continuation;
        private Prepared(List<Candidate> source, Context c, Parameters p) {
            Set<String> ids = new HashSet<>();
            for (Candidate candidate : source) if (!ids.add(candidate.id)) throw new IllegalArgumentException("Duplicate candidate " + candidate.id);
            pool = source.stream().filter(candidate -> typeWeight(candidate, c, p) > 0).toList();
            weights = new double[pool.size()];
            levels = new double[pool.size()][];
            for (int i = 0; i < pool.size(); i++) {
                weights[i] = typeWeight(pool.get(i), c, p);
                levels[i] = levelProbabilities(pool.get(i), c, p);
            }
            limit = p.maxEnchantments;
            continuation = Math.clamp(p.extraBase + p.extraTier * (c.tier - 1) + p.extraShelves * bookshelfInfluence(c, p) + p.extraSlot * c.slot, 0, .8);
        }
        public List<Choice> roll(RandomGenerator random) {
            var available = new ArrayList<Integer>();
            for (int i = 0; i < pool.size(); i++) available.add(i);
            var result = new ArrayList<Choice>();
            while (!available.isEmpty() && result.size() < limit) {
                double total = 0;
                for (int index : available) total += weights[index];
                double target = random.nextDouble() * total;
                int selected = available.getLast();
                for (int index : available) { target -= weights[index]; if (target < 0) { selected = index; break; } }
                Candidate chosen = pool.get(selected);
                double levelRoll = random.nextDouble();
                int rank = levels[selected].length - 1;
                for (int i = 0; i < levels[selected].length; i++) { levelRoll -= levels[selected][i]; if (levelRoll < 0) { rank = i; break; } }
                result.add(new Choice(chosen.id, chosen.minLevel + rank));
                available.removeIf(index -> {
                    Candidate other = pool.get(index);
                    return chosen.id.equals(other.id) || chosen.incompatible.contains(other.id) || other.incompatible.contains(chosen.id);
                });
                if (random.nextDouble() >= continuation / result.size()) break;
            }
            return List.copyOf(result);
        }
        public List<Candidate> candidates() { return pool; }
    }
    private RandomEnchantPolicy() {}
}
