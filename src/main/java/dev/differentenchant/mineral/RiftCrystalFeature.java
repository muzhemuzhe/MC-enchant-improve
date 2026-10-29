package dev.differentenchant.mineral;

import dev.differentenchant.realm.RealmContent;
import net.minecraft.core.*;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Bounded surface search in this chunk's interior. No adjacent chunk tickets. */
public final class RiftCrystalFeature extends Feature<NoneFeatureConfiguration> {
    public RiftCrystalFeature(){super(NoneFeatureConfiguration.CODEC);}
    private static final Direction[] FACES={Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.UP};
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> c){
        var l=c.level();if(!l.getLevel().dimension().equals(RealmContent.REALM))return false;
        int bx=(c.origin().getX()>>4)<<4,bz=(c.origin().getZ()>>4)<<4,placed=0;
        for(int column=0;column<12&&placed<3;column++){
            int x=bx+2+c.random().nextInt(12),z=bz+2+c.random().nextInt(12);
            for(int y=238;y>=36&&placed<3;y--){
                var support=new BlockPos(x,y,z);if(!l.getBlockState(support).is(MineralContent.HOSTS))continue;
                for(var facing:FACES){
                    boolean underside=facing==Direction.DOWN&&y>=132;
                    boolean fault=facing.getAxis().isHorizontal()&&y<=96;
                    boolean ledge=facing==Direction.UP&&y<=80&&c.random().nextInt(8)==0;
                    if(!(underside||fault||ledge))continue;
                    if(placeOn(l,support,facing)){placed++;break;}
                }
            }
        }
        return placed>0;
    }
    private static boolean placeOn(WorldGenLevel l,BlockPos support,Direction facing){
        var pos=support.relative(facing);
        if(!l.ensureCanWrite(pos)||!l.getBlockState(pos).isAir()||!l.getFluidState(pos).isEmpty())return false;
        var state=MineralContent.RIFT_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING,facing);
        if(!state.canSurvive(l,pos))return false;
        return l.setBlock(pos,state,2);
    }
}
