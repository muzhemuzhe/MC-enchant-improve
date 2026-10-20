package dev.differentenchant.realm;

import java.util.*;

/** Seed-only bounded tree geometry, independent of Minecraft and chunk scheduling. */
public final class AzureTreeShape {
    public record Point(int x, int y, int z) {
        Point offset(int dx, int dy, int dz) {return new Point(x+dx,y+dy,z+dz);}
        int distance(Point other) {return Math.abs(x-other.x)+Math.abs(y-other.y)+Math.abs(z-other.z);}
    }
    public record Wood(Point point, char axis) {}
    public record Shape(List<Wood> wood, Map<Point,Integer> leaves, int height) {}
    public static Shape create(long seed) {
        var random = new Random(seed);
        int height = 8 + random.nextInt(4), bendX = random.nextInt(3)-1, bendZ = random.nextInt(3)-1;
        var wood = new LinkedHashMap<Point,Character>();
        var leaves = new LinkedHashSet<Point>();
        for(int y=0;y<=height;y++) wood.put(new Point(y>=height/2?bendX:0,y,y>=height/2?bendZ:0),'y');
        // Connect the gentle bend with real wood, rather than diagonal floating logs.
        wood.put(new Point(bendX,height/2-1,0),'x');
        wood.put(new Point(bendX,height/2-1,bendZ),'z');
        int rotation = random.nextInt(4);
        int[][] directions={{1,0},{0,1},{-1,0},{0,-1}};
        for(int branch=0;branch<4;branch++) {
            var d=directions[(branch+rotation)%4];
            int y=Math.max(height/2,height-5)+branch%2, length=2+random.nextInt(2);
            for(int k=1;k<=length;k++) {
                var base=new Point(bendX+d[0]*k,y,bendZ+d[1]*k);
                wood.put(base,d[0]!=0?'x':'z');
                if(k==length) {wood.put(base.offset(0,1,0),'y'); crown(leaves,base.offset(0,2,0),2+random.nextInt(2),random);}
            }
        }
        crown(leaves,new Point(bendX,height-1,bendZ),3,random);
        crown(leaves,new Point(bendX,height+1,bendZ),2,random);
        // Short exposed roots, within the feature's preflight envelope.
        for(var d:directions) if(random.nextBoolean()) wood.put(new Point(d[0],0,d[1]),d[0]!=0?'x':'z');
        var distances=new LinkedHashMap<Point,Integer>();
        for(var p:leaves) if(!wood.containsKey(p)) {
            int distance=wood.keySet().stream().mapToInt(p::distance).min().orElse(7);
            if(distance<=6) distances.put(p,distance);
        }
        return new Shape(wood.entrySet().stream().map(e->new Wood(e.getKey(),e.getValue())).toList(),Collections.unmodifiableMap(distances),height);
    }
    private static void crown(Set<Point> leaves, Point center, int radius, Random random) {
        for(int y=0;y<=1;y++) for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) {
            double edge=x*x+z*z;
            int r=radius-y;
            if(edge<=r*r+1 && !(edge>r*r-2 && random.nextInt(5)==0)) leaves.add(center.offset(x,y,z));
        }
    }
    private AzureTreeShape() {}
}
