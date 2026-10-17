package dev.betterenchant.enchanting;

/** Shared by the actual menu slots, drawing, hit testing and client tests. */
public final class MachineGuiLayout {
    private MachineGuiLayout() {}
    public static final int WIDTH=260, HEIGHT=208;
    public static final int CORE_SLOT=38;
    public static final Rect CORE_PROGRESS=new Rect(10,151,32,5);
    public record Rect(int x,int y,int width,int height) {
        public boolean contains(double px,double py) { return px>=x && px<x+width && py>=y && py<y+height; }
    }
    public record Point(int x,int y) {}
    public static Rect option(int index) {
        if(index<0 || index>2) throw new IllegalArgumentException("Invalid option");
        return new Rect(90,34+22*index,162,19);
    }
    public static final Rect RANDOM_TAB=new Rect(90,18,64,12), DIRECTED_TAB=new Rect(158,18,94,12);
    public static final Rect DIRECTED_LIST=new Rect(90,33,162,60), ENCHANT_BUTTON=new Rect(194,98,58,16);
    public static Rect directedRow(int visible) {return new Rect(90,33+20*visible,162,20);}
    public static Rect plus(int visible) {return new Rect(238,37+20*visible,12,12);}
    public static Rect minus(int visible) {return new Rect(223,37+20*visible,12,12);}
    public static Point slot(int index) {
        if(index<0 || index>CORE_SLOT) throw new IllegalArgumentException("Invalid slot");
        if(index==CORE_SLOT) return new Point(18,128);
        if(index<2) return new Point(25+20*index,78);
        if(index<29) return new Point(49+18*((index-2)%9),126+18*((index-2)/9));
        return new Point(49+18*(index-29),184);
    }
}
