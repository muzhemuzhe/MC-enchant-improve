package dev.differentenchant.ruins;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class Ruins {
    public static final StructureType<RuinStructure> TYPE=Registry.register(BuiltInRegistries.STRUCTURE_TYPE,id("ruin"),()->RuinStructure.CODEC);
    public static final StructurePieceType PIECE=Registry.register(BuiltInRegistries.STRUCTURE_PIECE,id("ruin"),
            (StructurePieceType.StructureTemplateType)RuinPiece::new);
    public static Identifier id(String path) {return Identifier.fromNamespaceAndPath("differentenchant",path);}
    public static void initialize() {}
    private Ruins() {}
}
