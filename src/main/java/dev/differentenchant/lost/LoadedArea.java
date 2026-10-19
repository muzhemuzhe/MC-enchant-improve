package dev.differentenchant.lost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
/** Check every X/Z chunk a short collision ray can visit without asking the level to obtain one. */
public final class LoadedArea {
    public static boolean ray(ServerLevel level,Vec3 from,Vec3 to){
        int minX=Mth.floor(Math.min(from.x,to.x))>>4,maxX=Mth.floor(Math.max(from.x,to.x))>>4,minZ=Mth.floor(Math.min(from.z,to.z))>>4,maxZ=Mth.floor(Math.max(from.z,to.z))>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(level.getChunkSource().getChunkNow(x,z)==null)return false;return true;
    }
    private LoadedArea(){}
}
