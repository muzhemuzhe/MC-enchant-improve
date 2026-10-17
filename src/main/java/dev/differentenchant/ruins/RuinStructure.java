package dev.differentenchant.ruins;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** One template-backed type, with separate surface and buried datapack placements. */
public final class RuinStructure extends Structure {
    public static final MapCodec<RuinStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Identifier.CODEC.fieldOf("template").forGetter(s->s.template),Codec.BOOL.optionalFieldOf("buried",false).forGetter(s->s.buried)).apply(i,RuinStructure::new));
    private final Identifier template;
    private final boolean buried;
    public RuinStructure(StructureSettings settings,Identifier template,boolean buried) {super(settings);this.template=template;this.buried=buried;}
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var anchor=new BlockPos(context.chunkPos().getMiddleBlockX(),0,context.chunkPos().getMiddleBlockZ());
        var rotation=Rotation.getRandom(context.random());
        var probe=new RuinPiece(context.structureTemplateManager(),template,anchor,rotation);
        if(probe.template().getSize().getY()==0) return Optional.empty();
        var box=probe.getBoundingBox();int[] heights=new int[9];int index=0;
        for(int x:new int[]{box.minX(),(box.minX()+box.maxX())/2,box.maxX()}) for(int z:new int[]{box.minZ(),(box.minZ()+box.maxZ())/2,box.maxZ()})
            heights[index++]=context.chunkGenerator().getFirstOccupiedHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
        var floor=RuinPlacementPolicy.floor(heights,buried,context.chunkGenerator().getSeaLevel(),context.heightAccessor().getMinY(),14+context.random().nextInt(19));
        if(floor.isEmpty() || context.heightAccessor().isOutsideBuildHeight(floor.getAsInt()+probe.template().getSize().getY()-1)) return Optional.empty();
        var position=anchor.atY(floor.getAsInt());
        return Optional.of(new GenerationStub(position,builder->builder.addPiece(new RuinPiece(context.structureTemplateManager(),template,position,rotation))));
    }
    @Override public StructureType<?> type() {return Ruins.TYPE;}
}
