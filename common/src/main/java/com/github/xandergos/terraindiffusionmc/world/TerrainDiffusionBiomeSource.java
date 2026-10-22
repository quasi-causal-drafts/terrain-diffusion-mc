package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.CoarseTerrainSearch;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.github.xandergos.terraindiffusionmc.pipeline.TerralithBiomes;
import com.github.xandergos.terraindiffusionmc.pipeline.TerralithBiomes.TerralithBiome;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Map.entry;

public class TerrainDiffusionBiomeSource extends BiomeSource {
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionBiomeSource.class);
    private static final ResourceKey<Biome> FOREST_SPARSE = ResourceKey.create(Registries.BIOME, new ResourceLocation("terrain-diffusion-mc", "forest_sparse"));
    private static final ResourceKey<Biome> TAIGA_SPARSE = ResourceKey.create(Registries.BIOME, new ResourceLocation("terrain-diffusion-mc", "taiga_sparse"));
    private static final ResourceKey<Biome> SNOWY_TAIGA_SPARSE = ResourceKey.create(Registries.BIOME, new ResourceLocation("terrain-diffusion-mc", "snowy_taiga_sparse"));

    public static final Codec<TerrainDiffusionBiomeSource> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    RegistryOps.retrieveGetter(Registries.BIOME)
            ).apply(instance, instance.stable(TerrainDiffusionBiomeSource::new)));


    private HolderGetter<Biome> biomeLookup;
    /** Vanilla classifier ids. Assigned last in {@link #requireBiomeIdMap}, after {@link #terralithBiomes}. */
    private volatile Map<Short, Holder<Biome>> biomeIdMap = null;
    /** Terralith biomes by {@link TerralithBiome} ordinal; null entries when Terralith is not installed. */
    private Holder<Biome>[] terralithBiomes;

    public TerrainDiffusionBiomeSource(HolderGetter<Biome> biomeLookup) {
        this.biomeLookup = biomeLookup;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    private void requireBiomeIdMap() {
        if (biomeIdMap == null) {
            initBiomeIdMap();
        }
    }

    private synchronized void initBiomeIdMap() {
        if (biomeIdMap == null) {
            terralithBiomes = lookUpTerralithBiomes();
            biomeIdMap = Map.ofEntries(
                    entry((short) 1, this.biomeLookup.getOrThrow(Biomes.PLAINS)),
                    entry((short) 3, this.biomeLookup.getOrThrow(Biomes.SNOWY_PLAINS)),
                    entry((short) 5, this.biomeLookup.getOrThrow(Biomes.DESERT)),
                    entry((short) 6, this.biomeLookup.getOrThrow(Biomes.SWAMP)),
                    entry((short) 8, this.biomeLookup.getOrThrow(Biomes.FOREST)),
                    entry((short) 15, this.biomeLookup.getOrThrow(Biomes.TAIGA)),
                    entry((short) 16, this.biomeLookup.getOrThrow(Biomes.SNOWY_TAIGA)),
                    entry((short) 17, this.biomeLookup.getOrThrow(Biomes.SAVANNA)),
                    entry((short) 19, this.biomeLookup.getOrThrow(Biomes.WINDSWEPT_HILLS)),
                    entry((short) 23, this.biomeLookup.getOrThrow(Biomes.JUNGLE)),
                    entry((short) 26, this.biomeLookup.getOrThrow(Biomes.BADLANDS)),
                    entry((short) 29, this.biomeLookup.getOrThrow(Biomes.MEADOW)),
                    entry((short) 31, this.biomeLookup.getOrThrow(Biomes.GROVE)),
                    entry((short) 32, this.biomeLookup.getOrThrow(Biomes.SNOWY_SLOPES)),
                    entry((short) 33, this.biomeLookup.getOrThrow(Biomes.FROZEN_PEAKS)),
                    entry((short) 35, this.biomeLookup.getOrThrow(Biomes.STONY_PEAKS)),
                    entry((short) 41, this.biomeLookup.getOrThrow(Biomes.WARM_OCEAN)),
                    entry((short) 44, this.biomeLookup.getOrThrow(Biomes.OCEAN)),
                    entry((short) 46, this.biomeLookup.getOrThrow(Biomes.COLD_OCEAN)),
                    entry((short) 48, this.biomeLookup.getOrThrow(Biomes.FROZEN_OCEAN)),
                    entry((short) 108, this.biomeLookup.getOrThrow(FOREST_SPARSE)),
                    entry((short) 115, this.biomeLookup.getOrThrow(TAIGA_SPARSE)),
                    entry((short) 116, this.biomeLookup.getOrThrow(SNOWY_TAIGA_SPARSE))
            );
        }
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        requireBiomeIdMap();
        return Stream.concat(biomeIdMap.values().stream(), Arrays.stream(terralithBiomes).filter(Objects::nonNull));
    }

    /**
     * Terralith biomes used by the classifier, or nulls when Terralith is not installed. The getter can throw
     * for keys that do not exist (depending on which registry stage it came from), so absence is detected by
     * catching that rather than by asking for the key first.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Holder<Biome>[] lookUpTerralithBiomes() {
        TerralithBiome[] wanted = TerralithBiome.values();
        Holder<Biome>[] found = new Holder[wanted.length];
        int count = 0;
        for (TerralithBiome biome : wanted) {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, new ResourceLocation("terralith", biome.path()));
            try {
                found[biome.ordinal()] = this.biomeLookup.get(key).orElse(null);
            } catch (RuntimeException absent) {
                found[biome.ordinal()] = null;
            }
            if (found[biome.ordinal()] != null) {
                count++;
            }
        }
        if (count > 0) {
            LOG.info("Terralith detected: using {} of {} mapped Terralith biomes", count, wanted.length);
        } else {
            LOG.info("Terralith not installed: using vanilla biomes");
        }
        return found;
    }

    /** Resolves a classifier id: a Terralith biome when installed, otherwise the vanilla biome it replaces. */
    private Holder<Biome> resolve(short id) {
        if (TerralithBiomes.isVariant(id)) {
            Holder<Biome> terralith = terralithBiomes[TerralithBiomes.terralithOf(id).ordinal()];
            if (terralith != null) {
                return terralith;
            }
            id = TerralithBiomes.fallbackOf(id);
        }
        return biomeIdMap.get(id);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler noise) {
        requireBiomeIdMap();
        Holder<Biome> defaultEntry = biomeIdMap.get((short) 1);

        // x, y, z are in quart coordinates (block / 4)
        int blockX = QuartPos.toBlock(x);
        int blockZ = QuartPos.toBlock(z);

        int tileSize = TerrainDiffusionConfig.tileSize();
        int tileShift = Integer.numberOfTrailingZeros(tileSize);

        int tileX = blockX >> tileShift;
        int tileZ = blockZ >> tileShift;

        int blockStartX = tileX << tileShift;
        int blockStartZ = tileZ << tileShift;
        int blockEndX = blockStartX + tileSize;
        int blockEndZ = blockStartZ + tileSize;

        HeightmapData data = LocalTerrainProvider.getInstance().fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);
        if (data != null && data.biomeIds != null) {
            int localX = Math.max(0, Math.min(data.width  - 1, blockX - blockStartX));
            int localZ = Math.max(0, Math.min(data.height - 1, blockZ - blockStartZ));
            Holder<Biome> entry = resolve(data.biomeIds[localZ][localX]);
            if (entry != null) return entry;
        }

        return defaultEntry;
    }

    /**
     * Backs {@code /locate biome}. Sampling real biomes would mean running the full terrain model over
     * thousands of chunks, so this searches the coarse map instead and returns the centre of the nearest
     * coarse cell that likely contains the biome (one cell is 256 x scale blocks across).
     *
     * <p>Only used by the locate command; world generation uses {@link #findBiomeHorizontal}, which is
     * left unchanged.
     */
    @Override
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(BlockPos origin, int radius, int horizontalBlockCheckInterval, int verticalBlockCheckInterval, Predicate<Holder<Biome>> predicate, Climate.Sampler noiseSampler, LevelReader world) {
        requireBiomeIdMap();
        try {
            CoarseTerrainSearch.Hit hit = CoarseTerrainSearch.forCurrentWorld().findBiome(
                    origin.getX(), origin.getZ(), radius,
                    biomeId -> {
                        Holder<Biome> biome = resolve(biomeId);
                        return biome != null && predicate.test(biome);
                    });
            if (hit == null) {
                return null;
            }
            int y = HeightConverter.estimateMinecraftHeight(hit.elevationM(), WorldScaleManager.getCurrentScale());
            return Pair.of(new BlockPos(hit.blockX(), Math.max(y, SEA_LEVEL_Y), hit.blockZ()), resolve(hit.biomeId()));
        } catch (Exception e) {
            LOG.warn("Coarse biome search failed: {}", e.toString());
            return null;
        }
    }

    private static final int SEA_LEVEL_Y = 63;

    @Override
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(int x, int y, int z, int radius, int blockCheckInterval, Predicate<Holder<Biome>> predicate, RandomSource random, boolean bl, Climate.Sampler noiseSampler) {
        return null;
    }
}
