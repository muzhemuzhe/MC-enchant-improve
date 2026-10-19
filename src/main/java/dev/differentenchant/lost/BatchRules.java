package dev.differentenchant.lost;
/** Stateless experimental balance; never changes a vanilla maximum draw or vertical velocity. */
public final class BatchRules {
    public static int drawTicks(int level){return 20-2*Math.clamp(level,0,3);}
    public static float bowPower(int ticks,int level){float p=(float)ticks/drawTicks(level);return Math.min(1,(p*p+2*p)/3);}
    public static int transferredFire(int remaining,int level){return Math.min(Math.max(remaining,0),100*Math.clamp(level,0,2));}
    public static boolean spareArrow(int level,int roll){return level>0&&roll>0;}
    private BatchRules(){}
}
