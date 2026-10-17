package dev.differentenchant.enchanting;

import java.util.*;
import dev.differentenchant.DifferentEnchant;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.resources.Identifier;

/** A menu-local server plan, never shared between players or saved as a second inventory. */
final class DirectedSession {
    private static final java.util.concurrent.atomic.AtomicLong VERSIONS=new java.util.concurrent.atomic.AtomicLong();
    private boolean mode,dirty=true,valid,legalBase=true;
    private long revision;
    private List<String> steps=List.of();
    private DirectedCandidates.Pool pool;
    private ItemStack reference,input=ItemStack.EMPTY,reagent=ItemStack.EMPTY;
    private int tier,quote,xp,poolRevision=-1;
    private EnchantingConfig.Settings config;
    boolean mode() {return mode;}
    long revision() {return revision;}
    void setMode(boolean value) {mode=value;steps=List.of();pool=null;dirty=true;}
    void clear() {steps=List.of();dirty=true;}
    private int capacity() {return reagent.is(DifferentEnchant.RUNIC_STONE)?Math.min(1024,reagent.getCount()):0;}
    boolean refresh(ServerLevel level,Player player,ItemStack now,ItemStack material,int machineTier,int randomQuote,boolean machineValid) {
        boolean replaced=reference!=now || !ItemStack.matches(input,now);
        boolean registryChanged=config!=EnchantingConfig.current() || poolRevision!=EnchantmentCandidates.revision();
        if(!dirty && !replaced && !registryChanged && ItemStack.matches(reagent,material) && tier==machineTier && quote==randomQuote && xp==player.experienceLevel && valid==machineValid) return false;
        if(replaced || registryChanged || !machineValid) steps=List.of();
        reference=now;input=now.copy();reagent=material.copy();tier=machineTier;quote=randomQuote;xp=player.experienceLevel;valid=machineValid;
        legalBase=DirectedCandidates.compatible(now);
        config=EnchantingConfig.current();poolRevision=EnchantmentCandidates.revision();
        if(mode && (pool==null || replaced || registryChanged)) pool=DirectedCandidates.forItem(level.registryAccess(),now,config.directed());
        if(steps.size()>capacity()) steps=List.copyOf(steps.subList(0,capacity()));
        if(mode) try {plan();} catch(IllegalArgumentException error) {steps=List.of();}
        revision=VERSIONS.incrementAndGet();dirty=false;return true;
    }
    DirectedEnchantPolicy.Plan plan() {return DirectedEnchantPolicy.evaluate(pool.candidates(),pool.existing(),steps,capacity(),Math.max(1,tier),config.directed());}
    boolean edit(String id,boolean add) {
        if(!mode || !valid || !legalBase || pool==null || !pool.candidates().containsKey(id)) return false;
        List<String> next;
        if(add) {var copy=new ArrayList<>(steps);copy.add(id);next=copy;}
        else {next=DirectedEnchantPolicy.removeLast(steps,id);if(next.equals(steps)) return false;}
        try {DirectedEnchantPolicy.evaluate(pool.candidates(),pool.existing(),next,capacity(),tier,config.directed());}
        catch(IllegalArgumentException error) {return false;}
        steps=List.copyOf(next);dirty=true;return true;
    }
    ItemStack result(ServerLevel level,ItemStack actual,ItemStack actualReagent) {
        if(!mode || !valid || reference!=actual || !ItemStack.matches(input,actual) || !ItemStack.matches(reagent,actualReagent)) return ItemStack.EMPTY;
        var fresh=DirectedCandidates.forItem(level.registryAccess(),actual,config.directed());
        DirectedEnchantPolicy.Plan plan=DirectedEnchantPolicy.evaluate(fresh.candidates(),fresh.existing(),steps,capacity(),tier,config.directed());
        if(plan.upgrades()==0) return ItemStack.EMPTY;
        var output=actual.copy();
        EnchantmentHelper.updateEnchantments(output,mutable->plan.targets().forEach((id,target)->mutable.set(fresh.holders().get(id),target)));
        var enchantments=EnchantmentHelper.getEnchantmentsForCrafting(output);
        for(var a:enchantments.keySet()) for(var b:enchantments.keySet()) if(!a.equals(b) && !Enchantment.areCompatible(a,b)) return ItemStack.EMPTY;
        return output;
    }
    DirectedPreviewPayload snapshot(int containerId) {
        var rows=new ArrayList<DirectedPreviewPayload.Row>();int count=0,cost=0;
        if(mode && pool!=null) {
            var plan=plan();count=plan.upgrades();cost=plan.levels();
            for(var c:pool.candidates().values()) {
                int current=pool.existing().getOrDefault(c.id(),0),target=plan.targets().getOrDefault(c.id(),current);
                int blocked=!valid || !legalBase || target+1<c.min()?4:target>=c.max()?1:count>=capacity()?3:0;
                int next=target<c.max() && target+1>=c.min()?DirectedEnchantPolicy.stepCost(c,target+1,Math.max(1,tier),config.directed()):0;
                if(blocked==0) {
                    var test=new ArrayList<>(steps);test.add(c.id());
                    try {DirectedEnchantPolicy.evaluate(pool.candidates(),pool.existing(),test,capacity(),tier,config.directed());}
                    catch(IllegalArgumentException error) {blocked=2;}
                }
                rows.add(new DirectedPreviewPayload.Row(Identifier.parse(c.id()),current,target,c.max(),next,blocked==0,target>current,blocked));
            }
        }
        return new DirectedPreviewPayload(containerId,quote,revision,mode,input,reagent,count,cost,valid && legalBase && count>0 && xp>=DirectedEnchantPolicy.REQUIRED_LEVEL && xp>=cost,rows);
    }
}
