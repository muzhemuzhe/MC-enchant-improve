package dev.differentenchant.research;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Version-one objective recipe mapping. No registries, player state, discovery order or global RNG. */
public final class ResearchPolicy {
    public static final int VERSION=1;
    public record Book(String id,int level) {
        public Book {if(id==null||!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||level<1||level>255)throw new IllegalArgumentException("Invalid research book");}
        public String key() {return id+"@"+level;}
        public static Book parse(String key) {int at=key.lastIndexOf('@');return new Book(key.substring(0,at),Integer.parseInt(key.substring(at+1)));}
    }
    public record Recipe(Book a,Book b) {
        public Recipe {Objects.requireNonNull(a);Objects.requireNonNull(b);if(a.key().compareTo(b.key())>0){var temp=a;a=b;b=temp;}}
        public String key() {return a.key()+"|"+b.key();}
        public static Recipe parse(String key) {var parts=key.split("\\|",-1);if(parts.length!=2)throw new IllegalArgumentException("Invalid recipe");return new Recipe(Book.parse(parts[0]),Book.parse(parts[1]));}
    }
    public record Input(int weight,int maxLevel) {
        public Input {if(weight<1||maxLevel<1||maxLevel>255)throw new IllegalArgumentException("Invalid registry input metadata");}
    }
    public record Entry(String id,int rarity,int maxLevel) {
        public Entry {new Book(id,1);if(rarity<1||rarity>4||maxLevel<1||maxLevel>255)throw new IllegalArgumentException("Invalid lost catalogue entry");}
    }
    public record Result(String id,int level,int rarity,boolean scroll) {
        public Result {new Book(id,Math.max(1,level));if(level<1||rarity<1||rarity>4)throw new IllegalArgumentException("Invalid research result");}
    }
    public record Rules(double levelWeight,List<Double> thresholds,List<List<Integer>> weights,double scrollChance,double levelBias) {
        public Rules {
            thresholds=List.copyOf(thresholds);weights=weights.stream().map(List::copyOf).toList();
            if(!Double.isFinite(levelWeight)||levelWeight<=0||levelWeight>=1||thresholds.size()!=3||weights.size()!=4
                ||!Double.isFinite(scrollChance)||scrollChance<0||scrollChance>.25||!Double.isFinite(levelBias)||levelBias<0||levelBias>10)throw new IllegalArgumentException("Invalid research rules");
            double previous=0;for(double t:thresholds){if(!Double.isFinite(t)||t<=previous||t>=1)throw new IllegalArgumentException("Invalid grade thresholds");previous=t;}
            for(int grade=1;grade<=4;grade++) {
                var row=weights.get(grade-1);if(row.size()!=4)throw new IllegalArgumentException("Four rarity weights required");
                for(int tier=1;tier<=4;tier++)if(row.get(tier-1)<0||row.get(tier-1)>1000000||(tier>grade&&row.get(tier-1)!=0)||(tier<=grade&&row.get(tier-1)==0))throw new IllegalArgumentException("Pool weights violate grade support");
            }
        }
        public static Rules defaults() {return new Rules(.65,List.of(.35,.60,.80),List.of(List.of(100,0,0,0),List.of(55,45,0,0),List.of(30,40,30,0),List.of(18,24,30,28)),.06,2);}
    }
    public static double score(Book book,Input input,Rules rules) {
        double level=Math.min(1,(double)book.level()/input.maxLevel());
        double rarity=Math.clamp(StrictMath.log(10.0/input.weight())/StrictMath.log(10),0,1);
        return rules.levelWeight()*level+(1-rules.levelWeight())*rarity;
    }
    public static double score(Recipe recipe,Map<String,Input> inputs,Rules rules) {
        return (score(recipe.a(),Objects.requireNonNull(inputs.get(recipe.a().id())),rules)+score(recipe.b(),Objects.requireNonNull(inputs.get(recipe.b().id())),rules))/2;
    }
    public static int grade(double score,Rules rules) {int grade=1;for(double threshold:rules.thresholds())if(score>=threshold)grade++;return grade;}
    public static Result resolve(long worldSeed,Recipe recipe,Map<String,Input> inputs,List<Entry> catalogue,Rules rules) {
        double score=score(recipe,inputs,rules);int grade=grade(score,rules);
        var random=new Random(hash(worldSeed,recipe.key()));
        if(grade>=2&&random.nextDouble()<rules.scrollChance())return new Result("differentenchant:lost_scroll",1,2,true);
        // Stable sort; the frozen catalogue can contain dozens of unrelated effects without recipe changes.
        var sorted=catalogue.stream().sorted(Comparator.comparing(Entry::id)).toList();
        int total=0;for(int tier=1;tier<=grade;tier++){final int t=tier;if(sorted.stream().anyMatch(e->e.rarity()==t))total+=rules.weights().get(grade-1).get(tier-1);}
        if(total==0)throw new IllegalStateException("No eligible lost enchantments");
        int roll=random.nextInt(total),selected=0;
        for(int tier=1;tier<=grade;tier++) {
            final int t=tier;if(sorted.stream().noneMatch(e->e.rarity()==t))continue;
            roll-=rules.weights().get(grade-1).get(tier-1);if(roll<0){selected=tier;break;}
        }
        final int tier=selected;var pool=sorted.stream().filter(e->e.rarity()==tier).toList();var entry=pool.get(random.nextInt(pool.size()));
        double sum=0;for(int l=1;l<=entry.maxLevel();l++)sum+=levelWeight(l,entry.maxLevel(),score,rules);
        double levelRoll=random.nextDouble()*sum;int level=entry.maxLevel();
        for(int l=1;l<=entry.maxLevel();l++){levelRoll-=levelWeight(l,entry.maxLevel(),score,rules);if(levelRoll<0){level=l;break;}}
        return new Result(entry.id(),level,entry.rarity(),false);
    }
    private static double levelWeight(int level,int max,double score,Rules rules) {return StrictMath.exp(rules.levelBias()*score*(level-1)/Math.max(1,max-1));}
    private static long hash(long seed,String key) {
        try {var digest=MessageDigest.getInstance("SHA-256");digest.update(("differentenchant/research/v1\n"+seed+"\n"+key).getBytes(StandardCharsets.UTF_8));return ByteBuffer.wrap(digest.digest()).getLong();}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private ResearchPolicy() {}
}
