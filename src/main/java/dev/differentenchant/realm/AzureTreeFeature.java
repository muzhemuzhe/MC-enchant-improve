package dev.differentenchant.realm;

import net.minecraft.core.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** All-or-nothing preflight, radius <=7, uses only the native WorldGenRegion write window. */
public final class AzureTreeFeature extends Feature<NoneFeatureConfiguration> {
    public AzureTreeFeature() {super(NoneFeatureConfiguration.CODEC);}
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level=context.level(); var origin=context.origin();
        if(!level.getBlockState(origin.below()).is(BlockTags.DIRT)) return false;
        var shape=AzureTreeShape.create(context.random().nextLong());
        var positions=new java.util.ArrayList<AzureTreeShape.Point>(shape.leaves().keySet());
        shape.wood().forEach(w->positions.add(w.point()));
        for(var point:positions) {
            var at=origin.offset(point.x(),point.y(),point.z());
            if(at.getY()>=level.getMaxY() || !level.ensureCanWrite(at)) return false;
            var state=level.getBlockState(at);
            if(!state.canBeReplaced() || !state.getFluidState().isEmpty()) return false;
        }
        for(var wood:shape.wood()) {
            var p=wood.point(); var axis=switch(wood.axis()){case 'x'->Direction.Axis.X;case 'z'->Direction.Axis.Z;default->Direction.Axis.Y;};
            level.setBlock(origin.offset(p.x(),p.y(),p.z()),RealmContent.LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,axis),2);
        }
        shape.leaves().forEach((p,distance)->level.setBlock(origin.offset(p.x(),p.y(),p.z()),
                RealmContent.LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,distance),2));
        return true;
    }
}
