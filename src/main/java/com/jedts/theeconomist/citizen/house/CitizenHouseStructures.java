package com.jedts.theeconomist.citizen.house;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class CitizenHouseStructures {
    public static final Identifier HOUSE_ID = Identifier.fromNamespaceAndPath("theeconomist", "citizen_house");
    public static final ResourceKey<Structure> HOUSE_KEY = ResourceKey.create(Registries.STRUCTURE, HOUSE_ID);
    public static final StructureType<CitizenHouseStructure> HOUSE_TYPE = () -> CitizenHouseStructure.CODEC;
    public static final StructurePieceType HOUSE_PIECE = CitizenHousePiece::new;
    private CitizenHouseStructures() {}
    public static void register() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE, HOUSE_ID, HOUSE_TYPE);
        Registry.register(BuiltInRegistries.STRUCTURE_PIECE, HOUSE_ID, HOUSE_PIECE);
    }
}
