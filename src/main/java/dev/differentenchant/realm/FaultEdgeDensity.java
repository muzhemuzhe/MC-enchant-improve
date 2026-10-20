package dev.differentenchant.realm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Noise-only contour distance. No level, block access, chunk access or tick work. */
public final class FaultEdgeDensity implements DensityFunction.SimpleFunction {
    public static final MapCodec<FaultEdgeDensity> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(f->f.input),
        Codec.intRange(32,256).fieldOf("radius").forGetter(f->f.radius)
    ).apply(i,FaultEdgeDensity::new));
    private final DensityFunction input;
    private final int radius;
    // Native flat_cache supplies the chunk's 2D grid. This constant-size cache
    // additionally avoids repeating a query for each Y during standalone probes.
    private final ThreadLocal<Last> last=ThreadLocal.withInitial(Last::new);
    private static final class Last { boolean valid;int x,z;double value; }
    public FaultEdgeDensity(DensityFunction input,int radius){this.input=input;this.radius=radius;}
    @Override public double compute(FunctionContext context){
        var cache=last.get();int x=context.blockX(),z=context.blockZ();
        if(cache.valid&&cache.x==x&&cache.z==z)return cache.value;
        double value=FaultEdgeDistance.measure(x,z,radius,(xx,zz)->input.compute(new SinglePointContext(xx,0,zz)));
        cache.x=x;cache.z=z;cache.value=value;cache.valid=true;return value;
    }
    @Override public DensityFunction mapAll(Visitor visitor){return visitor.apply(new FaultEdgeDensity(input.mapAll(visitor),radius));}
    @Override public double minValue(){return -1;}
    @Override public double maxValue(){return radius;}
    @Override public KeyDispatchDataCodec<FaultEdgeDensity> codec(){return KeyDispatchDataCodec.of(CODEC);}
}
