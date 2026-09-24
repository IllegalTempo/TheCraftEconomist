package com.jedts.theeconomist.citizen.house;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import java.util.Optional;
import java.util.function.Predicate;

public final class CitizenHouseStructure extends Structure {
    public static final MapCodec<CitizenHouseStructure> CODEC = simpleCodec(CitizenHouseStructure::new);
    public CitizenHouseStructure(StructureSettings settings) { super(settings); }
    @Override public StructureType<?> type() { return CitizenHouseStructures.HOUSE_TYPE; }
    @Override public StructureStart generate(Holder<Structure> holder, ResourceKey<Level> dimension,
            RegistryAccess registries, ChunkGenerator generator, BiomeSource biomes, Climate.Sampler climate,
            RandomState randomState, StructureTemplateManager templates, long seed, ChunkPos chunk,
            int references, LevelHeightAccessor heights, Predicate<Holder<Biome>> validBiome) {
        if (!Level.OVERWORLD.equals(dimension)) return StructureStart.INVALID_START;
        return super.generate(holder, dimension, registries, generator, biomes, climate, randomState,
                templates, seed, chunk, references, heights, validBiome);
    }
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Rotation rotation = Rotation.getRandom(context.random());
        // A rotation about the center keeps the entire house and its anchor in the start chunk.
        BlockPos origin = new BlockPos(context.chunkPos().getMinBlockX() + 8, 0,
                context.chunkPos().getMinBlockZ() + 8);
        BlockPos shift = StructureTemplate.transform(new BlockPos(4, 0, 3), Mirror.NONE, rotation, BlockPos.ZERO);
        origin = origin.subtract(shift);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int x = 0; x < 9; x++) for (int z = 0; z < 7; z++) {
            BlockPos p = StructureTemplate.transform(new BlockPos(x, 0, z), Mirror.NONE, rotation, BlockPos.ZERO).offset(origin);
            int y = surface(context, p);
            if (!dryGround(context, p, y)) return Optional.empty();
            min = Math.min(min, y); max = Math.max(max, y);
        }
        BlockPos outside = StructureTemplate.transform(new BlockPos(4, 0, -1), Mirror.NONE, rotation, BlockPos.ZERO).offset(origin);
        int entranceY = surface(context, outside);
        var column = context.chunkGenerator().getBaseColumn(outside.getX(), outside.getZ(), context.heightAccessor(), context.randomState());
        // Put the floor at the entrance's ground height so the door is reachable without a jump.
        boolean entranceOpen = dryGround(context, outside, entranceY)
                && column.getBlock(entranceY).isAir() && column.getBlock(entranceY + 1).isAir()
                && entranceY == max;
        if (!HouseSiteRules.accepts(new HouseSiteRules.Site(true, max - min, entranceOpen))) return Optional.empty();
        BlockPos placedOrigin = origin.atY(max - 1);
        return Optional.of(new GenerationStub(placedOrigin, builder -> builder.addPiece(
                new CitizenHousePiece(context.structureTemplateManager(), placedOrigin, rotation))));
    }
    private static int surface(GenerationContext c, BlockPos p) {
        return c.chunkGenerator().getBaseHeight(p.getX(), p.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
                c.heightAccessor(), c.randomState());
    }
    private static boolean dryGround(GenerationContext c, BlockPos p, int y) {
        if (y <= c.heightAccessor().getMinY() || y + 5 >= c.heightAccessor().getMaxY()) return false;
        var ground = c.chunkGenerator().getBaseColumn(p.getX(), p.getZ(), c.heightAccessor(), c.randomState()).getBlock(y - 1);
        return ground.isSolid() && ground.getFluidState().isEmpty();
    }
}
