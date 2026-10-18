package dev.differentenchant.research;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import dev.differentenchant.research.ResearchPolicy.*;

final class ResearchPolicyTest {
    private static final List<Entry> POOL=List.of(new Entry("differentenchant:trail_stride",1,2),new Entry("differentenchant:prospectors_echo",1,4),new Entry("differentenchant:lantern_sight",1,1),new Entry("differentenchant:living_margin",2,3),new Entry("differentenchant:archivists_reach",2,2),new Entry("differentenchant:warding_focus",3,2),new Entry("differentenchant:gravity_thread",3,2),new Entry("differentenchant:last_page",4,1));
    @Test void unorderedLevelsStableCatalogueOrderAndSupport() {
        var rules=Rules.defaults();var inputs=Map.of("test:a",new Input(10,5),"test:b",new Input(1,3));
        var recipe=new Recipe(new Book("test:a",3),new Book("test:b",2));var reversed=new Recipe(recipe.b(),recipe.a());
        assertEquals(recipe,reversed);assertEquals(recipe.key(),reversed.key());assertNotEquals(recipe.key(),new Recipe(new Book("test:a",4),recipe.b()).key());
        var shuffled=new ArrayList<>(POOL);Collections.shuffle(shuffled,new Random(7));
        for(long seed=0;seed<1000;seed++) {
            var result=ResearchPolicy.resolve(seed,recipe,inputs,POOL,rules);
            assertEquals(result,ResearchPolicy.resolve(seed,reversed,inputs,shuffled,rules));
            assertTrue(result.rarity()<=ResearchPolicy.grade(ResearchPolicy.score(recipe,inputs,rules),rules));
        }
        assertThrows(IllegalArgumentException.class,()->new Rules(.65,List.of(.35,.6,.8),List.of(List.of(1,1,0,0),List.of(1,1,0,0),List.of(1,1,1,0),List.of(1,1,1,1)),.06,2));
        assertThrows(IllegalArgumentException.class,()->new Rules(Double.NaN,List.of(.35,.6,.8),rules.weights(),.06,2));
        assertThrows(IllegalArgumentException.class,()->new Book("test:a",0));
    }
    @Test void fixedSeedsHundredThousandDistinctRecipesPerGrade() throws Exception {
        var rules=Rules.defaults();var csv=new StringBuilder("grade,recipes,scrolls,tier_I,tier_II,tier_III,tier_IV,unique_enchantments,unique_results,duplicate_fraction,pair_collision,changed_other_seed\n");
        var detail=new StringBuilder("grade,result,count\n");double previous=-1;
        for(int grade=1;grade<=4;grade++) {
            var metadata=switch(grade){case 1,2->new Input(10,10);case 3->new Input(2,5);default->new Input(1,3);};
            int level=switch(grade){case 1->1;case 2->7;case 3->4;default->3;};
            var inputs=new HashMap<String,Input>();for(int i=0;i<1024;i++)inputs.put("test:input_"+i,metadata);
            var frequencies=new TreeMap<String,Integer>();var names=new HashSet<String>();var rarities=new int[4];int scrolls=0,changed=0;double mean=0;
            for(int i=0;i<100000;i++) {
                var recipe=new Recipe(new Book("test:input_"+(i%512),level),new Book("test:input_"+(512+i/512),level));
                assertEquals(grade,ResearchPolicy.grade(ResearchPolicy.score(recipe,inputs,rules),rules));
                var result=ResearchPolicy.resolve(12345,recipe,inputs,POOL,rules);assertTrue(result.rarity()<=grade);
                if(!result.equals(ResearchPolicy.resolve(98765,recipe,inputs,POOL,rules)))changed++;
                frequencies.merge(result.id()+"@"+result.level(),1,Integer::sum);rarities[result.rarity()-1]++;mean+=result.rarity();
                if(result.scroll())scrolls++;else names.add(result.id());
                if(i<100)assertEquals(result,ResearchPolicy.resolve(12345,recipe,inputs,POOL,rules));
            }
            final int maximum=grade;assertEquals(POOL.stream().filter(e->e.rarity()<=maximum).count(),names.size(),"full eligible-enchantment coverage");
            assertTrue(changed>50000,"world seeds change a majority of objective recipe outcomes");
            if(grade==1)assertEquals(0,scrolls);else assertTrue(scrolls>5000&&scrolls<7000,"low fixed scroll mapping rate");
            mean/=100000;assertTrue(mean>previous);previous=mean;
            double collision=frequencies.values().stream().mapToDouble(n->Math.pow(n/100000.0,2)).sum();
            csv.append(String.format(Locale.ROOT,"%d,100000,%d,%d,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f%n",grade,scrolls,rarities[0],rarities[1],rarities[2],rarities[3],names.size(),frequencies.size(),1-frequencies.size()/100000.0,collision,changed/100000.0));
            for(var entry:frequencies.entrySet())detail.append(grade).append(',').append(entry.getKey()).append(',').append(entry.getValue()).append('\n');
        }
        var path=Path.of("build/reports/research");Files.createDirectories(path);Files.writeString(path.resolve("distribution.csv"),csv);Files.writeString(path.resolve("coverage.csv"),detail);
    }
}
