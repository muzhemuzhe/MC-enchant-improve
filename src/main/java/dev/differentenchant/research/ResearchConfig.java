package dev.differentenchant.research;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

/** Defaults for newly initialized worlds only. Existing world snapshots deliberately ignore reloads. */
public final class ResearchConfig {
    private static ResearchPolicy.Rules current=ResearchPolicy.Rules.defaults();
    public static ResearchPolicy.Rules current(){return current;}
    public static void load() throws java.io.IOException {
        var path=FabricLoader.getInstance().getConfigDir().resolve("differentenchant-research.properties");
        if(!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path,"# Only new world snapshots use changes. Existing mappings are permanent.\nlevel_weight=0.65\ngrade_thresholds=0.35,0.60,0.80\npool.1=100,0,0,0\npool.2=55,45,0,0\npool.3=30,40,30,0\npool.4=18,24,30,28\nscroll_chance=0.06\noutput_level_bias=2\n");
        }
        var p=new Properties();try(var reader=Files.newBufferedReader(path)){p.load(reader);}
        var defaults=ResearchPolicy.Rules.defaults();var weights=new ArrayList<List<Integer>>();
        for(int i=1;i<=4;i++){final int grade=i;weights.add(Arrays.stream(p.getProperty("pool."+i,defaults.weights().get(grade-1).toString().replaceAll("[\\[\\] ]","")).split(",")).map(String::trim).map(Integer::parseInt).toList());}
        current=new ResearchPolicy.Rules(Double.parseDouble(p.getProperty("level_weight","0.65")),Arrays.stream(p.getProperty("grade_thresholds","0.35,0.60,0.80").split(",")).map(String::trim).map(Double::parseDouble).toList(),weights,Double.parseDouble(p.getProperty("scroll_chance","0.06")),Double.parseDouble(p.getProperty("output_level_bias","2")));
    }
    private ResearchConfig(){}
}
