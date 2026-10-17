package dev.betterenchant;

import java.util.*;
import java.nio.file.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.tags.EnchantmentTags;
import dev.betterenchant.enchanting.*;
import dev.betterenchant.enchanting.RandomEnchantPolicy.*;

public class EnchantingProbabilityGameTests {
    @GameTest(maxTicks=200)
    public void actualRegistrySwordSimulation(GameTestHelper helper) throws Exception {
        var pool=EnchantmentCandidates.forItem(helper.getLevel().registryAccess(),new ItemStack(Items.DIAMOND_SWORD));
        helper.assertTrue(!pool.candidates().isEmpty(),"actual sword pool populated");
        for(var holder:pool.holders().values()) {
            helper.assertTrue(!holder.is(EnchantmentTags.TREASURE) && !holder.is(EnchantmentTags.CURSE),"default acquisition restrictions");
            helper.assertTrue(holder.value().isPrimaryItem(new ItemStack(Items.DIAMOND_SWORD)),"item eligibility");
        }
        var report=new StringBuilder("# Actual registry simulation · diamond sword\n\nSeed=20261016; 100,000 rolls per row; option=3; first means first selected enchantment.\n\n");
        report.append("Pool (id / native weight / levels): ").append(pool.candidates().stream().map(c->c.id()+" / "+c.weight()+" / "+c.minLevel()+".."+c.maxLevel()).toList()).append("\n\n");
        report.append("|Tier|Physical shelves|Effective|Influence|First weight<=2 %|First multi-level max %|First rare AND max %|Mean count|First levels I/II/III/IV/V (counts)|All-selection rarity counts (10/5/2/1)|\n|---|---|---|---|---|---|---|---|---|---|\n");
        var plateau=new HashMap<Integer,String>();
        for(int shelves:List.of(0,1,15,16,32,35,36,37,80)) {
            double previousRare=-1, previousMax=-1;
            for(int tier:List.of(1,3,5)) {
                var context=new Context(tier,shelves,2);
                var policy=RandomEnchantPolicy.prepare(pool.candidates(),context,Parameters.defaults());
                for(var candidate:policy.candidates()) {
                    helper.assertTrue(RandomEnchantPolicy.typeWeight(candidate,context,Parameters.defaults())>0,"positive type mass");
                    for(double probability:RandomEnchantPolicy.levelProbabilities(candidate,context,Parameters.defaults())) helper.assertTrue(probability>0 && Double.isFinite(probability),"positive mass at every legal level");
                }
                Random random=new Random(20261016);
                int rare=0,max=0,joint=0,total=0;
                int[] firstLevels=new int[5];
                Map<Integer,Integer> rarityCounts=new TreeMap<>();
                Map<String,Integer> everyKind=new TreeMap<>();
                for(int sample=0;sample<100_000;sample++) {
                    var result=policy.roll(random);
                    total+=result.size();
                    var first=result.getFirst();
                    var enchantment=pool.holders().get(first.id()).value();
                    boolean isRare=enchantment.getWeight()<=2;
                    boolean isMax=enchantment.getMaxLevel()>enchantment.getMinLevel() && first.level()==enchantment.getMaxLevel();
                    if(isRare) rare++;
                    if(isMax) max++;
                    if(isRare && isMax) joint++;
                    firstLevels[first.level()-1]++;
                    for(var choice:result) {
                        var holder=pool.holders().get(choice.id());
                        helper.assertTrue(holder.value().isPrimaryItem(new ItemStack(Items.DIAMOND_SWORD)),"legal item");
                        helper.assertTrue(choice.level()>=holder.value().getMinLevel() && choice.level()<=holder.value().getMaxLevel(),"legal rank");
                        rarityCounts.merge(holder.value().getWeight(),1,Integer::sum);
                        everyKind.merge(choice.id()+"@"+choice.level(),1,Integer::sum);
                        for(var other:result) if(other!=choice) helper.assertTrue(Enchantment.areCompatible(holder,pool.holders().get(other.id())),"no illegal combinations");
                    }
                }
                helper.assertTrue(rare>previousRare && max>previousMax,"observed quality improves in fixed pool");
                previousRare=rare; previousMax=max;
                report.append(String.format(Locale.ROOT,"|%d|%d|%d|%.6f|%.3f|%.3f|%.3f|%.5f|%s|%s|%n",tier,shelves,context.shelves(),RandomEnchantPolicy.bookshelfInfluence(context,Parameters.defaults()),rare/1000.0,max/1000.0,joint/1000.0,total/100000.0,Arrays.toString(firstLevels),rarityCounts));
                String fullResult=everyKind+" / "+Arrays.toString(firstLevels)+" / "+rarityCounts+" / "+total;
                if(shelves==36) plateau.put(tier,fullResult);
                if(shelves>36) helper.assertTrue(plateau.get(tier).equals(fullResult),"identical 100,000-roll statistics after cap at tier "+tier);
                Path details=Path.of("../reports/enchanting/sword-tier"+tier+"-shelves"+shelves+".txt");
                Files.createDirectories(details.getParent());
                Files.writeString(details,everyKind.toString()+"\n");
            }
        }
        Path output=Path.of("../reports/enchanting/actual-registry-simulation.md");
        Files.writeString(output,report.toString());
        helper.assertTrue(EnchantmentCandidates.forItem(helper.getLevel().registryAccess(),new ItemStack(Items.DIRT)).candidates().isEmpty(),"non-enchantable items excluded");
        helper.succeed();
    }
}
