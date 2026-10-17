package dev.differentenchant.enchanting;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.differentenchant.enchanting.RandomEnchantPolicy.*;

class RandomEnchantPolicyTest {
    static final List<Candidate> POOL = List.of(new Candidate("fixture:common",10,1,5,Set.of("fixture:rare")),
            new Candidate("fixture:uncommon",5,1,3,Set.of()),new Candidate("fixture:rare",2,1,5,Set.of()),new Candidate("thirdparty:very_rare",1,1,1,Set.of()));
    @Test void fullLegalSupportIsPositiveAnalyticallyAtEveryTierAndNoShelves() {
        var p = Parameters.defaults();
        for (int tier = 1; tier <= 10; tier++) for (int slot = 0; slot < 3; slot++) for (int shelves : List.of(0,1,15,16,32,35,36,37,80)) {
            Context context = new Context(tier,shelves,slot);
            double total = POOL.stream().mapToDouble(c -> RandomEnchantPolicy.typeWeight(c,context,p)).sum();
            for (Candidate candidate : POOL) {
                double typeProbability = RandomEnchantPolicy.typeWeight(candidate,context,p)/total;
                for (double level : RandomEnchantPolicy.levelProbabilities(candidate,context,p)) assertTrue(Double.isFinite(typeProbability*level) && typeProbability*level > 0);
            }
        }
    }
    private static double rareProbability(Context context, Parameters p) {
        return POOL.stream().filter(c -> c.weight()<=2).mapToDouble(c->RandomEnchantPolicy.typeWeight(c,context,p)).sum()
                /POOL.stream().mapToDouble(c->RandomEnchantPolicy.typeWeight(c,context,p)).sum();
    }
    @Test void qualityImprovesMonotonicallyAndBookshelvesAreIndependent() {
        var p=Parameters.defaults();
        for (int tier=1;tier<10;tier++) {
            Context low = new Context(tier,0,0), high = new Context(tier+1,0,0), books=new Context(tier,15,0);
            assertTrue(rareProbability(high,p)>rareProbability(low,p));
            assertTrue(rareProbability(books,p)>rareProbability(low,p));
            for(Candidate c:POOL) if(c.maxLevel()>c.minLevel()) {
                int max=c.maxLevel()-c.minLevel();
                assertTrue(RandomEnchantPolicy.levelProbabilities(c,high,p)[max]>RandomEnchantPolicy.levelProbabilities(c,low,p)[max]);
                assertTrue(RandomEnchantPolicy.levelProbabilities(c,books,p)[max]>RandomEnchantPolicy.levelProbabilities(c,low,p)[max]);
            }
        }
    }
    @Test void oneHundredThousandPerContextAreDeterministicCompatibleAndBounded() {
        for(int tier:List.of(1,3,5)) {
            var policy=RandomEnchantPolicy.prepare(POOL,new Context(tier,15,2),Parameters.defaults());
            Random first=new Random(20261016), second=new Random(20261016);
            int high=0;
            for(int i=0;i<100_000;i++) {
                var roll=policy.roll(first);
                assertEquals(roll,policy.roll(second));
                assertFalse(roll.isEmpty()); assertTrue(roll.size()<=4);
                assertEquals(roll.size(),roll.stream().map(Choice::id).distinct().count());
                assertFalse(roll.stream().anyMatch(c->c.id().equals("fixture:common")) && roll.stream().anyMatch(c->c.id().equals("fixture:rare")));
                for(var choice:roll) {
                    Candidate candidate=POOL.stream().filter(c->c.id().equals(choice.id())).findFirst().orElseThrow();
                    assertTrue(choice.level()>=candidate.minLevel() && choice.level()<=candidate.maxLevel());
                    if(choice.level()==5) high++;
                }
            }
            assertTrue(high>0); // Supplemental empirical check; analytic test establishes strict positivity.
        }
    }
    @Test void probabilityAxesCanBeDisabledSeparately() {
        var p=Parameters.defaults();
        var rarityOnly = new Parameters(p.rarityTier(),p.rarityShelves(),p.raritySlot(),p.levelBase(),0,0,0,0,0,0,0,1,15,.5,Map.of());
        var low=new Context(1,0,0); var high=new Context(5,15,2);
        assertArrayEquals(RandomEnchantPolicy.levelProbabilities(POOL.getFirst(),low,rarityOnly),RandomEnchantPolicy.levelProbabilities(POOL.getFirst(),high,rarityOnly));
        var levelOnly=new Parameters(0,0,0,-4,1,2,.5,0,0,0,0,1,15,.5,Map.of());
        assertEquals(rareProbability(low,levelOnly),rareProbability(high,levelOnly));
    }
    @Test void exclusionsEmptyPoolAndBadWeightsFailSafely() {
        var p=Parameters.defaults();
        var blocked=new Parameters(p.rarityTier(),p.rarityShelves(),p.raritySlot(),p.levelBase(),p.levelTier(),p.levelShelves(),p.levelSlot(),p.extraBase(),p.extraTier(),p.extraShelves(),p.extraSlot(),4,15,.5,Map.of("fixture:common",0.0));
        var prepared=RandomEnchantPolicy.prepare(POOL,new Context(1,0,0),blocked);
        assertTrue(prepared.candidates().stream().noneMatch(c->c.id().equals("fixture:common")));
        assertEquals(List.of(),RandomEnchantPolicy.prepare(List.of(),new Context(1,0,0),p).roll(new Random(1)));
        assertThrows(IllegalArgumentException.class,()->new Candidate("bad",0,1,5,Set.of()));
        assertThrows(IllegalArgumentException.class,()->new Candidate("bad",1,5,1,Set.of()));
        for(String value:List.of("NaN","Infinity","-1")) {
            Properties properties=new Properties(); properties.setProperty("rarity.tier",value);
            assertThrows(IllegalArgumentException.class,()->EnchantingConfig.parse(properties));
        }
        assertThrows(IllegalArgumentException.class,()->RandomEnchantPolicy.prepare(List.of(POOL.getFirst(),POOL.getFirst()),new Context(1,0,0),p));
        var extreme=new Parameters(4,4,4,-12,4,4,4,1,1,1,1,6,1,1,Map.of("fixture:common",100.0));
        for(var context:List.of(new Context(1,0,0),new Context(10,32,2))) for(var candidate:POOL) {
            assertTrue(RandomEnchantPolicy.typeWeight(candidate,context,extreme)>0);
            for(double probability:RandomEnchantPolicy.levelProbabilities(candidate,context,extreme)) assertTrue(probability>0 && Double.isFinite(probability));
        }
    }
    @Test void everyShelfThroughThirtySixChangesActualProbabilitiesAndTailDiminishes() {
        var p=Parameters.defaults();
        double previousStep=Double.POSITIVE_INFINITY;
        for(int b=0;b<=36;b++) {
            var context=new Context(1,b,2);
            double influence=RandomEnchantPolicy.bookshelfInfluence(context,p);
            if(b<=15) assertEquals(b/15.0,influence,"original base curve preserved");
            if(b==0) continue;
            var before=new Context(1,b-1,2);
            assertTrue(rareProbability(context,p)>rareProbability(before,p),"no dead shelf count at "+b);
            for(var candidate:POOL) if(candidate.maxLevel()>candidate.minLevel()) {
                int max=candidate.maxLevel()-candidate.minLevel();
                assertTrue(RandomEnchantPolicy.levelProbabilities(candidate,context,p)[max]>RandomEnchantPolicy.levelProbabilities(candidate,before,p)[max]);
            }
            double step=influence-RandomEnchantPolicy.bookshelfInfluence(before,p);
            if(b>=16) {assertTrue(step>0 && step<previousStep,"positive, diminishing marginal tail");previousStep=step;}
            else previousStep=step;
        }
        var capped=RandomEnchantPolicy.prepare(POOL,new Context(1,36,2),p);
        for(int b:List.of(37,80,Integer.MAX_VALUE)) {
            var excess=RandomEnchantPolicy.prepare(POOL,new Context(1,b,2),p);
            var first=new Random(20261017);var second=new Random(20261017);
            for(int roll=0;roll<1000;roll++) assertEquals(capped.roll(first),excess.roll(second),"identical draws beyond cap");
        }
        assertThrows(IllegalArgumentException.class,()->new Context(1,-1,0));
    }
    @Test void oldConfigGetsTailGainAndScaleSupportsAllThirtySix() {
        var properties=new Properties();properties.setProperty("bookshelf_scale","15");
        var legacy=EnchantingConfig.parse(properties).policy();
        assertEquals(Parameters.defaults(),legacy);
        assertTrue(RandomEnchantPolicy.bookshelfInfluence(new Context(1,36,0),legacy)>1);
        for(int scale:List.of(1,15,32,35,36)) {
            properties.setProperty("bookshelf_scale",Integer.toString(scale));
            var p=EnchantingConfig.parse(properties).policy();
            double previous=-1;
            for(int b=0;b<=36;b++) {
                double value=RandomEnchantPolicy.bookshelfInfluence(new Context(1,b,0),p);
                assertTrue(value>previous);previous=value;
            }
        }
        for(String invalid:List.of("0","37","-1")) {properties.setProperty("bookshelf_scale",invalid);assertThrows(IllegalArgumentException.class,()->EnchantingConfig.parse(properties));}
        properties.setProperty("bookshelf_scale","15");
        for(String invalid:List.of("0","NaN","Infinity","-1","1.01")) {properties.setProperty("bookshelf_tail_gain",invalid);assertThrows(IllegalArgumentException.class,()->EnchantingConfig.parse(properties));}
        properties.setProperty("bookshelf_tail_gain","0.01");
        assertTrue(RandomEnchantPolicy.bookshelfInfluence(new Context(1,36,0),EnchantingConfig.parse(properties).policy())>1);
    }
}
