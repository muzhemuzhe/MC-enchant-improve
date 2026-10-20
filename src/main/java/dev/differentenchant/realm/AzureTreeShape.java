package dev.differentenchant.realm;

import java.util.*;

/** Seed-only silhouettes. Face-connected wood and real leaf-path distances. */
public final class AzureTreeShape {
    public enum Kind { SPIRE, WINDSWEPT, FORKED, ANCIENT }
    public record Point(int x,int y,int z) {
        public Point offset(int dx,int dy,int dz){return new Point(x+dx,y+dy,z+dz);}
        int distance(Point other){return Math.abs(x-other.x)+Math.abs(y-other.y)+Math.abs(z-other.z);}
    }
    public record Wood(Point point,char axis) {}
    public record Shape(List<Wood> wood,Map<Point,Integer> leaves,int height,Kind kind) {}
    public static Shape create(long seed) {
        int roll=new Random(seed^0x5d368fb9L).nextInt(100);
        return create(seed,roll<36?Kind.SPIRE:roll<68?Kind.WINDSWEPT:roll<95?Kind.FORKED:Kind.ANCIENT);
    }
    public static Shape create(long seed,Kind kind) {
        var r=new Random(seed);var b=new Builder(r);
        int h=7+r.nextInt(4),direction=r.nextInt(4);int[][] directions={{1,0},{0,1},{-1,0},{0,-1}};
        var wind=directions[direction];
        int lean=kind==Kind.WINDSWEPT?2+r.nextInt(2):r.nextInt(2);
        var tip=new Point(wind[0]*lean,h,wind[1]*lean);
        if(kind==Kind.ANCIENT) {
            h=9+r.nextInt(3);tip=new Point(wind[0],h,wind[1]);
            for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++)b.stem(new Point(dx,0,dz),tip.offset(dx,0,dz));
            for(int n=0;n<5;n++) {
                double a=(direction+n*1.256637)+r.nextDouble()*.25;
                int length=3+r.nextInt(2),y=h-3+r.nextInt(3);
                var end=new Point(tip.x()+(int)Math.round(Math.cos(a)*length),y+2+r.nextInt(2),tip.z()+(int)Math.round(Math.sin(a)*length));
                b.line(new Point(tip.x(),y-2,tip.z()),end);
                b.crown(end.offset(0,1,0),2.8+r.nextDouble()*.4,3.1+r.nextDouble(),2.6+r.nextDouble()*.5);
            }
            b.line(tip,tip.offset(0,3,0));b.crown(tip.offset(0,4,0),2.8,3.4,2.8);
            for(var d:directions)b.line(new Point(0,0,0),new Point(d[0]*2,0,d[1]*2));
        } else if(kind==Kind.SPIRE) {
            b.stem(new Point(0,0,0),tip.offset(0,2,0));
            for(int n=0;n<3;n++) {
                var d=directions[(direction+n)%4];int y=h-4+n*3;
                var end=new Point(tip.x()+d[0],y,tip.z()+d[1]);
                b.line(new Point(tip.x(),y-1,tip.z()),end);
                b.crown(end.offset(0,1,0),n==2?1.7:2.0,2.6,1.8+r.nextDouble()*.5);
            }
        } else if(kind==Kind.WINDSWEPT) {
            b.stem(new Point(0,0,0),tip);
            for(int n=0;n<3;n++) {
                var side=directions[(direction+(n==1?1:3))%4];int y=h-3+n;
                var base=new Point((int)Math.round(tip.x()*((double)y/h)),y,(int)Math.round(tip.z()*((double)y/h)));
                var end=tip.offset(wind[0]*(1+n/2)+side[0]*(n==0?1:2),n-1,wind[1]*(1+n/2)+side[1]*(n==0?1:2));
                b.line(base,end);b.crown(end.offset(0,1,0),2.2,2.6+r.nextDouble()*.4,2.2);
            }
            b.line(tip,tip.offset(wind[0],2,wind[1]));
            b.crown(tip.offset(wind[0],3,wind[1]),1.9,2.3,1.9);
        } else {
            var fork=new Point(0,4+r.nextInt(2),0);b.stem(new Point(0,0,0),fork);
            for(int n=0;n<3;n++) {
                var d=directions[(direction+n)%4];int reach=2+r.nextInt(2);
                var end=new Point(d[0]*reach,h+n%2,d[1]*reach);
                b.stem(fork,end);b.crown(end.offset(0,1,0),2.2,2.9+r.nextDouble()*.5,2.0+r.nextDouble()*.3);
            }
        }
        if(kind!=Kind.ANCIENT)for(var d:directions)if(r.nextInt(3)==0)b.line(new Point(0,0,0),new Point(d[0],0,d[1]));
        return b.finish(kind);
    }
    private static final class Builder {
        final Random random;final Map<Point,Character> wood=new LinkedHashMap<>();final Set<Point> leaves=new LinkedHashSet<>();
        Builder(Random random){this.random=random;}
        void stem(Point from,Point to) {
            var last=from;wood.put(from,'y');int rise=to.y()-from.y();
            for(int y=1;y<=rise;y++) {
                var next=new Point(from.x()+(int)Math.round((to.x()-from.x())*(double)y/rise),from.y()+y,
                        from.z()+(int)Math.round((to.z()-from.z())*(double)y/rise));
                line(last,next);last=next;
            }
        }
        void line(Point from,Point to) {
            var p=from;wood.putIfAbsent(p,'y');
            while(!p.equals(to)) {
                if(p.y()!=to.y()){p=p.offset(0,Integer.signum(to.y()-p.y()),0);wood.put(p,'y');}
                if(p.x()!=to.x()){p=p.offset(Integer.signum(to.x()-p.x()),0,0);wood.put(p,'x');}
                if(p.z()!=to.z()){p=p.offset(0,0,Integer.signum(to.z()-p.z()));wood.put(p,'z');}
            }
        }
        void crown(Point center,double rx,double ry,double rz) {
            for(int x=-(int)Math.ceil(rx);x<=Math.ceil(rx);x++)for(int y=-(int)Math.ceil(ry);y<=Math.ceil(ry);y++)for(int z=-(int)Math.ceil(rz);z<=Math.ceil(rz);z++) {
                double d=x*x/(rx*rx)+y*y/(ry*ry)+z*z/(rz*rz);
                if(d<1. && random.nextDouble()>(d>.70?.24:.07))leaves.add(center.offset(x,y,z));
            }
            for(int n=0;n<3;n++)if(random.nextBoolean())leaves.add(center.offset(random.nextInt(3)-1,-(int)ry-1,random.nextInt(3)-1));
        }
        Shape finish(Kind kind) {
            leaves.removeAll(wood.keySet());
            var distances=new HashMap<Point,Integer>();var queue=new ArrayDeque<Point>();
            for(var p:wood.keySet()){distances.put(p,0);queue.add(p);}
            int[][] offsets={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            while(!queue.isEmpty()) {
                var p=queue.remove();int d=distances.get(p)+1;if(d>6)continue;
                for(var step:offsets){var at=p.offset(step[0],step[1],step[2]);if(leaves.contains(at)&&!distances.containsKey(at)){distances.put(at,d);queue.add(at);}}
            }
            var supported=new LinkedHashMap<Point,Integer>();for(var p:leaves)if(distances.containsKey(p))supported.put(p,distances.get(p));
            int height=wood.keySet().stream().mapToInt(Point::y).max().orElseThrow()+1;
            return new Shape(wood.entrySet().stream().map(e->new Wood(e.getKey(),e.getValue())).toList(),Collections.unmodifiableMap(supported),height,kind);
        }
    }
    private AzureTreeShape() {}
}
