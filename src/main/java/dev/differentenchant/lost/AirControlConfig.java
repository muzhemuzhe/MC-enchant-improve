package dev.differentenchant.lost;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import dev.differentenchant.research.ResearchContent;
/** Server-selected movement coefficients, synchronized at connection before normal play. */
public final class AirControlConfig {
    public static Settings current=new Settings(.96,.92,.88,.010,.018,.026);
    public record Settings(double drag1,double drag2,double drag3,double control1,double control2,double control3) implements CustomPacketPayload {
        public Settings {for(double d:new double[]{drag1,drag2,drag3})if(!Double.isFinite(d)||d<.7||d>1)throw new IllegalArgumentException("air_drag must be 0.7..1");for(double c:new double[]{control1,control2,control3})if(!Double.isFinite(c)||c<0||c>.05)throw new IllegalArgumentException("air_control must be 0..0.05");}
        public double drag(int level){return new double[]{drag1,drag2,drag3}[Math.clamp(level,1,3)-1];}
        public double control(int level){return new double[]{control1,control2,control3}[Math.clamp(level,1,3)-1];}
        public static final Type<Settings> TYPE=new Type<>(ResearchContent.id("air_control"));
        public static final StreamCodec<FriendlyByteBuf,Settings> CODEC=StreamCodec.of((b,p)->{b.writeDouble(p.drag1);b.writeDouble(p.drag2);b.writeDouble(p.drag3);b.writeDouble(p.control1);b.writeDouble(p.control2);b.writeDouble(p.control3);},b->new Settings(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()));
        @Override public Type<Settings> type(){return TYPE;}
    }
    public static void load() {
        try {var path=FabricLoader.getInstance().getConfigDir().resolve("differentenchant-lost.properties");if(!Files.exists(path))Files.writeString(path,"# Paper Kite horizontal drag and steering, levels I/II/III. Server syncs to clients.\nair_drag=0.96,0.92,0.88\nair_control=0.010,0.018,0.026\n");
            var p=new Properties();try(var in=Files.newInputStream(path)){p.load(in);}double[] d=Arrays.stream(p.getProperty("air_drag").split(",")).mapToDouble(Double::parseDouble).toArray(),c=Arrays.stream(p.getProperty("air_control").split(",")).mapToDouble(Double::parseDouble).toArray();if(d.length!=3||c.length!=3)throw new IllegalArgumentException("Three coefficients required");current=new Settings(d[0],d[1],d[2],c[0],c[1],c[2]);
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load lost enchantment settings",e);}
    }
    private AirControlConfig(){}
}
