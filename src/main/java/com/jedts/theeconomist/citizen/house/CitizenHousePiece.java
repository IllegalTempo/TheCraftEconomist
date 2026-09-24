package com.jedts.theeconomist.citizen.house;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class CitizenHousePiece extends TemplateStructurePiece {
    public static final BlockPos LOCAL_ENTRANCE = new BlockPos(4, 1, 0);
    public static final BlockPos LOCAL_STORAGE = new BlockPos(2, 1, 2);
    public CitizenHousePiece(StructureTemplateManager manager, BlockPos origin, Rotation rotation) {
        super(CitizenHouseStructures.HOUSE_PIECE, 0, manager, CitizenHouseStructures.HOUSE_ID,
                CitizenHouseStructures.HOUSE_ID.toString(), settings(rotation), origin);
    }
    public CitizenHousePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(CitizenHouseStructures.HOUSE_PIECE, tag, context.structureTemplateManager(),
                id -> settings(Rotation.valueOf(tag.getStringOr("HouseRotation", "NONE"))));
    }
    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(true);
    }
    public BlockPos localToWorld(BlockPos local) {
        return StructureTemplate.calculateRelativePosition(placeSettings, local).offset(templatePosition);
    }
    public BlockPos entrance() { return localToWorld(LOCAL_ENTRANCE); }
    public BlockPos storage() { return localToWorld(LOCAL_STORAGE); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("HouseRotation", placeSettings.getRotation().name());
    }
    @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                                               RandomSource random, BoundingBox box) {}
}
