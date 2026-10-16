package dev.betterenchant.enchanting;

import java.util.*;

/** Deterministic planning and level costs. No game, GUI, networking or randomness dependencies. */
public final class DirectedEnchantPolicy {
    private DirectedEnchantPolicy() {}
    public static final int REQUIRED_LEVEL=30;
    public record Parameters(double base,double rarity,double target,double tierDiscount,boolean allowTreasure,boolean allowCurses) {
        public Parameters {
            for(double value:new double[]{base,rarity,target,tierDiscount})
                if(!Double.isFinite(value) || value<0 || value>8) throw new IllegalArgumentException("Invalid directed cost parameter");
        }
        public static Parameters defaults() { return new Parameters(5,1.5,1.5,.5,false,false); }
    }
    public record Candidate(String id,int weight,int min,int max,Set<String> excluded) {
        public Candidate {
            Objects.requireNonNull(id);excluded=Set.copyOf(excluded);
            if(weight<1 || min<1 || max<min) throw new IllegalArgumentException("Invalid directed candidate");
        }
    }
    public record Plan(Map<String,Integer> targets,int upgrades,int levels) {
        public Plan { targets=Map.copyOf(targets); }
    }
    public static int stepCost(Candidate c,int target,int tier,Parameters p) {
        if(target<c.min || target>c.max || tier<1) throw new IllegalArgumentException("Invalid target/tier");
        double normalized=c.max==1?0:(target-1.0)/(c.max-1.0);
        return (int)Math.clamp((long)Math.ceil(p.base+p.rarity/c.weight+p.target*normalized-p.tierDiscount*(tier-1)),5,8);
    }
    public static Plan evaluate(Map<String,Candidate> pool,Map<String,Integer> existing,List<String> steps,int capacity,int tier,Parameters p) {
        if(capacity<0 || steps.size()>capacity || steps.size()>1024) throw new IllegalArgumentException("Insufficient stones");
        var current=new HashMap<>(existing);var targets=new TreeMap<String,Integer>();int cost=0;
        for(String id:steps) {
            Candidate candidate=pool.get(id);
            if(candidate==null) throw new IllegalArgumentException("Unsupported enchantment");
            int target=Math.addExact(current.getOrDefault(id,0),1);
            if(target<candidate.min || target>candidate.max) throw new IllegalArgumentException("Maximum or nonstandard minimum level");
            for(String other:current.keySet()) if(!id.equals(other) && (candidate.excluded.contains(other)
                    || (pool.containsKey(other) && pool.get(other).excluded.contains(id)))) throw new IllegalArgumentException("Incompatible enchantment");
            cost=Math.addExact(cost,stepCost(candidate,target,tier,p));current.put(id,target);targets.put(id,target);
        }
        return new Plan(targets,steps.size(),cost);
    }
    public static List<String> removeLast(List<String> steps,String id) {
        var copy=new ArrayList<>(steps);int index=copy.lastIndexOf(id);
        if(index>=0) copy.remove(index);
        return List.copyOf(copy);
    }
}
