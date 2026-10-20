package dev.differentenchant.realm;

/** Bounded, seed-independent geometry over a caller's seeded scalar field. */
public final class FaultEdgeDistance {
    @FunctionalInterface public interface Field { double at(int x,int z); }
    private static final double D=Math.sqrt(.5);
    private static final double[][] DIRECTIONS={{1,0},{D,D},{0,1},{-D,D},{-1,0},{-D,-D},{0,-1},{D,-D}};
    public static double measure(int x,int z,int radius,Field field) {
        if(field.at(x,z)<=0)return -1;
        double gx=field.at(x+16,z)-field.at(x-16,z),gz=field.at(x,z+16)-field.at(x,z-16);
        int first=0;double score=Double.POSITIVE_INFINITY;
        for(int i=0;i<8;i++){double dot=gx*DIRECTIONS[i][0]+gz*DIRECTIONS[i][1];if(dot<score){score=dot;first=i;}}
        double nearest=radius;
        for(int i=0;i<8;i++) {
            var d=DIRECTIONS[(first+i)%8];double previous=0;
            for(double distance=Math.min(16,nearest);;distance=Math.min(distance+24,nearest)) {
                if(field.at(x+(int)Math.round(d[0]*distance),z+(int)Math.round(d[1]*distance))<=0) {
                    double lo=previous,hi=distance;
                    for(int n=0;n<5;n++){double mid=(lo+hi)*.5;if(field.at(x+(int)Math.round(d[0]*mid),z+(int)Math.round(d[1]*mid))>0)lo=mid;else hi=mid;}
                    nearest=(lo+hi)*.5;break;
                }
                if(distance>=nearest)break;
                previous=distance;
            }
        }
        return nearest;
    }
    private FaultEdgeDistance() {}
}
