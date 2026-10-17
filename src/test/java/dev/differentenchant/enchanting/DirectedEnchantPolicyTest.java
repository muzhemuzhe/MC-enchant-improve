package dev.differentenchant.enchanting;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.differentenchant.enchanting.DirectedEnchantPolicy.*;

class DirectedEnchantPolicyTest {
    static final Candidate A=new Candidate("fixture:a",10,1,5,Set.of("fixture:b"));
    static final Candidate B=new Candidate("fixture:b",1,1,5,Set.of("fixture:a"));
    static final Candidate C=new Candidate("thirdparty:c",2,1,3,Set.of());
    static final Map<String,Candidate> POOL=Map.of(A.id(),A,B.id(),B,C.id(),C);
    static Plan plan(Map<String,Integer> existing,List<String> steps,int stones) {return evaluate(POOL,existing,steps,stones,1,Parameters.defaults());}
    @Test void oneStoneIsExactlyOneUpgradeAndFiveAreExactlyFive() {
        assertEquals(Map.of(A.id(),1),plan(Map.of(),List.of(A.id()),1).targets());
        assertThrows(IllegalArgumentException.class,()->plan(Map.of(),List.of(A.id(),C.id()),1));
        var five=Collections.nCopies(5,A.id());assertEquals(5,plan(Map.of(),five,5).upgrades());
        var six=new ArrayList<>(five);six.add(C.id());assertThrows(IllegalArgumentException.class,()->plan(Map.of(),six,5));
    }
    @Test void additionsRemovalsPreserveOtherAllocationsAndFreeTheBudget() {
        var steps=List.of(A.id(),C.id(),A.id(),C.id(),A.id());
        var reduced=removeLast(steps,A.id());assertEquals(List.of(A.id(),C.id(),A.id(),C.id()),reduced);
        assertEquals(Map.of(A.id(),2,C.id(),2),plan(Map.of(),reduced,5).targets());
        var again=new ArrayList<>(reduced);again.add(C.id());assertEquals(3,plan(Map.of(),again,5).targets().get(C.id()));
        assertEquals(steps,removeLast(steps,"missing"));assertEquals(5,steps.size());
    }
    @Test void existingIIIUpgradesStepwiseToVAndNeverBeyondMaximum() {
        var existing=Map.of(A.id(),3,C.id(),1);var result=plan(existing,List.of(A.id(),A.id()),2);
        assertEquals(Map.of(A.id(),5),result.targets());assertEquals(Map.of(A.id(),3,C.id(),1),existing);
        assertThrows(IllegalArgumentException.class,()->plan(existing,List.of(A.id(),A.id(),A.id()),3));
        assertThrows(IllegalArgumentException.class,()->plan(Map.of(),List.of("unknown"),1));
    }
    @Test void pairwiseConflictsCoverBothExistingAndPlannedEnchantments() {
        assertThrows(IllegalArgumentException.class,()->plan(Map.of(A.id(),1),List.of(B.id()),1));
        assertThrows(IllegalArgumentException.class,()->plan(Map.of(),List.of(A.id(),B.id()),2));
        assertEquals(2,plan(Map.of(),List.of(A.id(),C.id()),2).upgrades());
        var asymmetric=Map.of(A.id(),new Candidate(A.id(),10,1,5,Set.of()),B.id(),B);
        assertThrows(IllegalArgumentException.class,()->evaluate(asymmetric,Map.of(B.id(),1),List.of(A.id()),1,1,Parameters.defaults()));
    }
    @Test void eachStepCostsFiveToEightAndTierNeverRaisesComparableCosts() {
        for(var c:POOL.values()) for(int rank=1;rank<=c.max();rank++) {
            int previous=8;
            for(int tier=1;tier<=10;tier++) {int cost=stepCost(c,rank,tier,Parameters.defaults());assertTrue(cost>=5 && cost<=8 && cost<=previous);previous=cost;}
        }
        assertTrue(stepCost(B,5,1,Parameters.defaults())>stepCost(B,5,5,Parameters.defaults()));
        int expected=stepCost(A,4,1,Parameters.defaults())+stepCost(A,5,1,Parameters.defaults())+stepCost(C,1,1,Parameters.defaults());
        assertEquals(expected,plan(Map.of(A.id(),3),List.of(A.id(),A.id(),C.id()),3).levels());
        assertEquals(30,REQUIRED_LEVEL);
    }
    @Test void invalidConfigRejectedAndExtremeValidParametersStillBounded() {
        assertThrows(IllegalArgumentException.class,()->new Parameters(Double.NaN,0,0,0,false,false));
        assertThrows(IllegalArgumentException.class,()->new Parameters(5,-1,0,0,false,false));
        assertEquals(5,stepCost(A,1,1,new Parameters(0,0,0,0,false,false)));
        assertEquals(8,stepCost(B,5,1,new Parameters(8,8,8,0,false,false)));
        var p=new Properties();p.setProperty("directed.base","6");assertEquals(6,EnchantingConfig.parse(p).directed().base());
        p.setProperty("directed.allow_treasure","yes");assertThrows(IllegalArgumentException.class,()->EnchantingConfig.parse(p));
    }
}
