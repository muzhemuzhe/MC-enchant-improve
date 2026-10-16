package dev.betterenchant.enchanting;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.betterenchant.enchanting.RandomEnchantPolicy.*;

class RandomEnchantPolicyTest {
    static final List<Candidate> POOL = List.of(new Candidate("fixture:common",10,1,5,Set.of("fixture:rare")),
            new Candidate("fixture:uncommon",5,1,3,Set.of()),new Candidate("fixture:rare",2,1,5,Set.of()),new Candidate("thirdparty:very_rare",1,1,1,Set.of()));
    @Test void fullLegalSupportIsPositiveAnalyticallyAtEveryTierAndNoShelves() {
        var p = Parameters.defaults();
        for (int tier = 1; tier <= 10; tier++) for (int slot = 0; slot < 3; slot++) for (int shelves : List.of(0,15,32)) {
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
        var rarityOnly = new Parameters(p.rarityTier(),p.rarityShelves(),p.raritySlot(),p.levelBase(),0,0,0,0,0,0,0,1,15,Map.of());
        var low=new Context(1,0,0); var high=new Context(5,15,2);
        assertArrayEquals(RandomEnchantPolicy.levelProbabilities(POOL.getFirst(),low,rarityOnly),RandomEnchantPolicy.levelProbabilities(POOL.getFirst(),high,rarityOnly));
        var levelOnly=new Parameters(0,0,0,-4,1,2,.5,0,0,0,0,1,15,Map.of());
        assertEquals(rareProbability(low,levelOnly),rareProbability(high,levelOnly));
    }
    @Test void exclusionsEmptyPoolAndBadWeightsFailSafely() {
        var p=Parameters.defaults();
        var blocked=new Parameters(p.rarityTier(),p.rarityShelves(),p.raritySlot(),p.levelBase(),p.levelTier(),p.levelShelves(),p.levelSlot(),p.extraBase(),p.extraTier(),p.extraShelves(),p.extraSlot(),4,15,Map.of("fixture:common",0.0));
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
        var extreme=new Parameters(4,4,4,-12,4,4,4,1,1,1,1,6,1,Map.of("fixture:common",100.0));
        for(var context:List.of(new Context(1,0,0),new Context(10,32,2))) for(var candidate:POOL) {
            assertTrue(RandomEnchantPolicy.typeWeight(candidate,context,extreme)>0);
            for(double probability:RandomEnchantPolicy.levelProbabilities(candidate,context,extreme)) assertTrue(probability>0 && Double.isFinite(probability));
        }
    }
}
