package dev.differentenchant.ruins;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;

/** Native chunk-clipped template placement, marker contents and bounded foundation supports. */
public final class RuinPiece extends TemplateStructurePiece {
    private final long cacheSeed;
    public RuinPiece(StructureTemplateManager manager,Identifier id,BlockPos pos,Rotation rotation) {
        this(manager,id,pos,rotation,0L);
    }
    public RuinPiece(StructureTemplateManager manager,Identifier id,BlockPos pos,Rotation rotation,long cacheSeed) {
        super(Ruins.PIECE,0,manager,id,id.toString(),settings(rotation),pos);
        this.cacheSeed=cacheSeed;
    }
    public RuinPiece(StructureTemplateManager manager,CompoundTag tag) {
        super(Ruins.PIECE,tag,manager,id->settings(tag.read("Rot",Rotation.LEGACY_CODEC).orElse(Rotation.NONE)));
        cacheSeed=tag.getLongOr("CacheSeed",0L);
    }
    public long cacheSeed() {return cacheSeed;}
    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        super.addAdditionalSaveData(context,tag);tag.store("Rot",Rotation.LEGACY_CODEC,getRotation());
        if(isLantern()) tag.putLong("CacheSeed",cacheSeed);
    }
    private boolean isLantern() {return templateName.equals(Ruins.id(LanternCachePolicy.TEMPLATE).toString());}
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox box) {
        if(!box.isInside(pos)) return;
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        var entity=level.getBlockEntity(pos.below());
        if(marker.equals("zombie_spawner") && entity instanceof SpawnerBlockEntity spawner) spawner.setEntityId(EntityType.ZOMBIE,random);
        if(marker.startsWith("books_") && entity instanceof ChiseledBookShelfBlockEntity shelf) {
            var key=ResourceKey.create(Registries.LOOT_TABLE,Ruins.id("chests/"+marker));
            var params=new LootParams.Builder(level.getLevel()).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos.below())).create(LootContextParamSets.CHEST);
            var books=level.getLevel().getServer().reloadableRegistries().getLootTable(key).getRandomItems(params,random.nextLong());
            var slots=new java.util.ArrayList<>(List.of(0,1,2,3,4,5));
            // Proto-chunk entities have no Level yet. setItem would call the live-world updateState path.
            for(var book:books) {if(slots.isEmpty()) break;int slot=slots.remove(random.nextInt(slots.size()));shelf.getItems().set(slot,book.copyWithCount(1));}
            var state=level.getBlockState(pos.below());
            for(int slot=0;slot<6;slot++) state=state.setValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(slot),!shelf.getItem(slot).isEmpty());
            level.setBlock(pos.below(),state,2);
            shelf.setChanged();
        }
    }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox chunkBox,ChunkPos chunk,BlockPos reference) {
        super.postProcess(level,structures,generator,random,chunkBox,chunk,reference);
        if(isLantern()) {
            var loot=ResourceKey.create(Registries.LOOT_TABLE,Ruins.id(LanternCachePolicy.LOOT));
            for(var candidate:LanternCachePolicy.select(cacheSeed)) {
                var local=new BlockPos(candidate.x(),candidate.y(),candidate.z());
                var pos=StructureTemplate.calculateRelativePosition(placeSettings,local).offset(templatePosition);
                if(!chunkBox.isInside(pos)) continue;
                var state=Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING,net.minecraft.core.Direction.UP);
                level.setBlock(pos,state,2);
                if(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
                    barrel.setLootTable(loot);barrel.setLootTableSeed(LanternCachePolicy.lootSeed(cacheSeed,candidate));
                    barrel.setChanged();
                }
            }
        }
        if(templateName.endsWith("small_buried")) return;
        // Only support actual template floor cells, within this chunk, for at most six blocks.
        for(var info:template.filterBlocks(templatePosition,placeSettings,Blocks.STONE_BRICKS)) {
            var pos=info.pos();if(pos.getY()!=templatePosition.getY() || !chunkBox.isInside(pos)) continue;
            for(int depth=1;depth<=6;depth++) {
                var below=pos.below(depth);if(!chunkBox.isInside(below)) break;
                var state=level.getBlockState(below);if(!state.isAir() && !state.liquid()) break;
                level.setBlock(below,Blocks.STONE_BRICKS.defaultBlockState(),2);
            }
        }
    }
}
