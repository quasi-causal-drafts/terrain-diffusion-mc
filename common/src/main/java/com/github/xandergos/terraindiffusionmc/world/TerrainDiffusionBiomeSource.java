package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.BaseBiomeReplacements;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeAllowlist;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeNiches;
import com.github.xandergos.terraindiffusionmc.pipeline.CoarseTerrainSearch;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.github.xandergos.terraindiffusionmc.pipeline.RegionPicker;
import com.github.xandergos.terraindiffusionmc.pipeline.TerralithBiomes;
import com.github.xandergos.terraindiffusionmc.pipeline.TerralithBiomes.TerralithBiome;
import com.mojang.datafixers.util.Pair;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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


    private static final short PLAINS_ID = 1;

    private final HolderGetter<Biome> biomeLookup;
    /** Biome choices after {@code biomes.allowed}; built on first use, immutable afterwards. */
    private volatile Resolution resolution = null;

    /** What each classifier id becomes. */
    private static final class Resolution {
        /** Allowed Terralith biomes by {@link TerralithBiome} ordinal; null if absent or not allowed. */
        final Holder<Biome>[] terralith;
        /** Biomes to choose from for each base classifier id; never empty. */
        final Map<Short, Holder<Biome>[]> pools;
        /** Cumulative weights for each pool (rare biomes get a smaller share). */
        final Map<Short, float[]> weights;
        /** Every biome {@link #resolve} can return. */
        final List<Holder<Biome>> possible;

        Resolution(Holder<Biome>[] terralith, Map<Short, Holder<Biome>[]> pools, Map<Short, float[]> weights,
                   List<Holder<Biome>> possible) {
            this.terralith = terralith;
            this.pools = pools;
            this.weights = weights;
            this.possible = possible;
        }
    }

    /** A biome that may be placed in a niche, with its share of the niche's area. */
    private record Choice(Holder<Biome> biome, float weight) {
    }

    public TerrainDiffusionBiomeSource(HolderGetter<Biome> biomeLookup) {
        this.biomeLookup = biomeLookup;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    private Resolution resolution() {
        Resolution current = resolution;
        return current != null ? current : buildResolution();
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return resolution().possible.stream();
    }

    /**
     * Resolves a classifier id at a block position: an allowed Terralith biome if the classifier picked one,
     * otherwise one of the biomes allowed for that base biome, chosen by large regions when there are several.
     */
    private Holder<Biome> resolve(short id, int blockX, int blockZ) {
        Resolution r = resolution();
        if (TerralithBiomes.isVariant(id)) {
            Holder<Biome> terralith = r.terralith[TerralithBiomes.terralithOf(id).ordinal()];
            if (terralith != null) {
                return terralith;
            }
            id = TerralithBiomes.fallbackOf(id);
        }
        Holder<Biome>[] pool = r.pools.get(id);
        if (pool == null) {
            id = PLAINS_ID;
            pool = r.pools.get(id);
        }
        return pool.length == 1 ? pool[0] : pool[RegionPicker.pickWeighted(r.weights.get(id), id, blockX, blockZ)];
    }

    // ---------------------------------------------------------------------------------------------------------
    // biomes.allowed
    // ---------------------------------------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private synchronized Resolution buildResolution() {
        if (resolution != null) {
            return resolution;
        }
        Map<Short, Holder<Biome>> base = Map.ofEntries(
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
        TerralithBiome[] terralithBiomes = TerralithBiome.values();
        Holder<Biome>[] terralithPresent = lookUpTerralithBiomes();
        boolean terralithInstalled = Arrays.stream(terralithPresent).anyMatch(Objects::nonNull);

        // Every registered biome, when the server's registry is available (it lists biomes and their tags).
        Registry<Biome> registry = ServerBiomeRegistry.get();
        Map<String, BiomeNiches.Descriptor> registered = new LinkedHashMap<>();
        if (registry != null) {
            registry.holders().forEach(holder -> {
                String id = holder.key().location().toString();
                registered.put(id, describe(id, holder));
            });
        }

        // Biomes placed out of the box: the classifier's own biomes, plus Terralith's when installed.
        Set<String> defaults = new LinkedHashSet<>();
        Set<String> handledByClassifier = new HashSet<>();
        for (short id : BiomeNiches.NICHES.keySet()) {
            defaults.add(BaseBiomeReplacements.BIOMES.get(id));
            handledByClassifier.add(BaseBiomeReplacements.BIOMES.get(id));
        }
        for (TerralithBiome biome : terralithBiomes) {
            handledByClassifier.add("terralith:" + biome.path());
            if (terralithPresent[biome.ordinal()] != null) {
                defaults.add("terralith:" + biome.path());
            }
        }

        String rawSetting = TerrainDiffusionConfig.biomesAllowed();
        List<BiomeAllowlist.Entry> entries = new ArrayList<>(BiomeAllowlist.parse(rawSetting));
        entries.addAll(legacyRemovals(terralithInstalled));
        boolean customized = !entries.isEmpty();
        BiomeAllowlist.Result allowlist = BiomeAllowlist.expand(entries, defaults, registered, registry != null);
        allowlist.warnings().forEach(LOG::warn);
        Set<String> allowed = allowlist.allowed();

        Holder<Biome>[] terralith = new Holder[terralithBiomes.length];
        for (TerralithBiome biome : terralithBiomes) {
            if (allowed.contains("terralith:" + biome.path())) {
                terralith[biome.ordinal()] = terralithPresent[biome.ordinal()];
            }
        }

        // Biomes the classifier has no rules for join the niche whose climate suits them best.
        Map<Short, List<Choice>> extras = new HashMap<>();
        List<String> placements = new ArrayList<>();
        for (String id : allowed) {
            if (handledByClassifier.contains(id)) {
                continue;
            }
            Holder<Biome> holder = lookUpBiome(id);
            if (holder == null) {
                LOG.warn("biomes.allowed: biome {} not found; ignored", id);
                continue;
            }
            BiomeNiches.Descriptor descriptor = registered.containsKey(id) ? registered.get(id) : describe(id, holder);
            Short niche = BiomeNiches.bestNicheId(descriptor);
            if (niche == null) {
                LOG.warn("biomes.allowed: no suitable place for {}; ignored", id);
                continue;
            }
            extras.computeIfAbsent(niche, k -> new ArrayList<>()).add(new Choice(holder, BiomeNiches.weight(descriptor)));
            placements.add(shortName(id) + " -> " + shortName(BaseBiomeReplacements.BIOMES.get(niche)));
        }

        // Each base biome: itself if allowed, else Terralith biomes standing in for it; plus its extras.
        Map<Short, List<Choice>> rawPools = new HashMap<>();
        for (short id : BiomeNiches.NICHES.keySet()) {
            List<Choice> pool = new ArrayList<>();
            if (allowed.contains(BaseBiomeReplacements.BIOMES.get(id))) {
                pool.add(new Choice(base.get(id), 1f));
            } else {
                for (TerralithBiome biome : terralithBiomes) {
                    Holder<Biome> holder = terralith[biome.ordinal()];
                    if (holder != null && contains(TerralithBiomes.basesOf(biome), id)) {
                        pool.add(new Choice(holder, 1f));
                    }
                }
            }
            pool.addAll(extras.getOrDefault(id, List.of()));
            rawPools.put(id, pool);
        }

        // Base biomes left with nothing borrow the pool of the nearest one that has something.
        Map<Short, Holder<Biome>[]> pools = new HashMap<>();
        Map<Short, float[]> weights = new HashMap<>();
        Set<Holder<Biome>> possible = new LinkedHashSet<>();
        List<String> replacements = new ArrayList<>();
        for (Map.Entry<Short, List<Choice>> pool : rawPools.entrySet()) {
            short id = pool.getKey();
            List<Choice> choices = pool.getValue();
            if (choices.isEmpty()) {
                Short alternative = BaseBiomeReplacements.firstMatching(id,
                        candidate -> !rawPools.getOrDefault(candidate, List.of()).isEmpty());
                if (alternative != null) {
                    choices = rawPools.get(alternative);
                    replacements.add(shortName(BaseBiomeReplacements.BIOMES.get(id)) + " -> "
                            + shortName(BaseBiomeReplacements.BIOMES.get(alternative)) + "'s biomes");
                } else {
                    choices = List.of(new Choice(base.get(id), 1f));
                    LOG.warn("biomes.allowed: nothing allowed fits {}, so it stays", BaseBiomeReplacements.BIOMES.get(id));
                }
            }
            Holder<Biome>[] holders = new Holder[choices.size()];
            float[] cumulative = new float[choices.size()];
            float total = 0f;
            for (int i = 0; i < choices.size(); i++) {
                holders[i] = choices.get(i).biome();
                total += choices.get(i).weight();
                cumulative[i] = total;
                possible.add(holders[i]);
            }
            pools.put(id, holders);
            weights.put(id, cumulative);
        }
        for (Holder<Biome> holder : terralith) {
            if (holder != null) {
                possible.add(holder);
            }
        }

        if (customized) {
            LOG.info("biomes.allowed: {} biomes allowed ({} from other mods or not placed by default)",
                    allowed.size(), placements.size());
            if (!placements.isEmpty()) {
                LOG.info("biomes.allowed: placed by climate: {}", String.join(", ", placements));
            }
            if (!replacements.isEmpty()) {
                LOG.info("biomes.allowed: not allowed, replaced: {}", String.join(", ", replacements));
            }
        }
        Resolution built = new Resolution(terralith, pools, weights, List.copyOf(possible));
        resolution = built;
        return built;
    }

    /** {@code terralith.disabled_biomes} and {@code vanilla.disabled_biomes} (older settings) as removals. */
    private static List<BiomeAllowlist.Entry> legacyRemovals(boolean terralithInstalled) {
        List<BiomeAllowlist.Entry> removals = new ArrayList<>();
        Set<String> terralith = TerrainDiffusionConfig.terralithDisabledBiomes();
        if (terralithInstalled) {
            for (String name : terralith) {
                removals.add(name.equals(TerrainDiffusionConfig.ALL_TERRALITH_BIOMES)
                        ? new BiomeAllowlist.Entry(true, BiomeAllowlist.Kind.WILDCARD, "terralith", null)
                        : new BiomeAllowlist.Entry(true, BiomeAllowlist.Kind.ID, "terralith", name));
            }
        }
        Map<String, String> vanilla = TerrainDiffusionConfig.vanillaDisabledBiomes();
        for (String name : vanilla.keySet()) {
            int colon = name.indexOf(':');
            removals.add(new BiomeAllowlist.Entry(true, BiomeAllowlist.Kind.ID,
                    colon < 0 ? null : name.substring(0, colon), colon < 0 ? name : name.substring(colon + 1)));
        }
        if (!terralith.isEmpty() || !vanilla.isEmpty()) {
            LOG.info("terralith.disabled_biomes / vanilla.disabled_biomes are applied as removals; "
                    + "you can move them into biomes.allowed as -name entries");
        }
        if (vanilla.values().stream().anyMatch(v -> !v.isEmpty())) {
            LOG.warn("vanilla.disabled_biomes: replacements after \"=\" are no longer used; "
                    + "biomes that aren't allowed are replaced by the closest allowed ones");
        }
        return removals;
    }

    /**
     * Terralith biomes the classifier maps to, or nulls when Terralith is not installed. The getter can throw for
     * keys that do not exist (depending on which registry stage it came from), so absence is detected by catching
     * that rather than by asking for the key first.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Holder<Biome>[] lookUpTerralithBiomes() {
        TerralithBiome[] wanted = TerralithBiome.values();
        Holder<Biome>[] found = new Holder[wanted.length];
        int count = 0;
        for (TerralithBiome biome : wanted) {
            found[biome.ordinal()] = lookUpBiome("terralith:" + biome.path());
            if (found[biome.ordinal()] != null) {
                count++;
            }
        }
        if (count > 0) {
            LOG.info("Terralith detected: {} of {} mapped Terralith biomes present", count, wanted.length);
        } else {
            LOG.info("Terralith not installed: using vanilla biomes");
        }
        return found;
    }

    /** A biome by id through this source's own lookup (keeps holders consistent), or null if it doesn't exist. */
    private Holder<Biome> lookUpBiome(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        try {
            return this.biomeLookup.get(ResourceKey.create(Registries.BIOME, location)).orElse(null);
        } catch (RuntimeException absent) {
            return null;
        }
    }

    /** Climate and tags of a biome, for niche matching. */
    private static BiomeNiches.Descriptor describe(String id, Holder<Biome> holder) {
        Biome biome = holder.value();
        Set<String> tags = new HashSet<>();
        try {
            holder.tags().forEach(tag -> tags.add(tag.location().toString()));
        } catch (RuntimeException tagsNotBound) {
            // Tags aren't bound yet: match on climate only.
        }
        return new BiomeNiches.Descriptor(id, biome.getBaseTemperature(), readDownfall(biome), biome.hasPrecipitation(), tags);
    }

    /**
     * A biome's downfall. It has no public getter in 1.20.1, but the codec that syncs biomes to clients
     * includes it, so encode the biome with that and read the field. 0.5 if that fails.
     */
    private static float readDownfall(Biome biome) {
        try {
            return Biome.NETWORK_CODEC.encodeStart(JsonOps.INSTANCE, biome)
                    .resultOrPartial(error -> { })
                    .filter(JsonElement::isJsonObject)
                    .map(json -> json.getAsJsonObject().get("downfall"))
                    .filter(Objects::nonNull)
                    .map(JsonElement::getAsFloat)
                    .orElse(0.5f);
        } catch (RuntimeException e) {
            return 0.5f;
        }
    }

    private static boolean contains(short[] ids, short id) {
        for (short candidate : ids) {
            if (candidate == id) {
                return true;
            }
        }
        return false;
    }

    private static String shortName(String biomeId) {
        return biomeId.startsWith("minecraft:") ? biomeId.substring("minecraft:".length()) : biomeId;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler noise) {
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
            Holder<Biome> entry = resolve(data.biomeIds[localZ][localX], blockX, blockZ);
            if (entry != null) return entry;
        }

        return resolve(PLAINS_ID, blockX, blockZ);
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
        try {
            CoarseTerrainSearch.Hit hit = CoarseTerrainSearch.forCurrentWorld().findBiome(
                    origin.getX(), origin.getZ(), radius,
                    (biomeId, blockX, blockZ) -> {
                        Holder<Biome> biome = resolve(biomeId, blockX, blockZ);
                        return biome != null && predicate.test(biome);
                    });
            if (hit == null) {
                return null;
            }
            int y = HeightConverter.estimateMinecraftHeight(hit.elevationM(), WorldScaleManager.getCurrentScale());
            return Pair.of(new BlockPos(hit.blockX(), Math.max(y, SEA_LEVEL_Y), hit.blockZ()), resolve(hit.biomeId(), hit.blockX(), hit.blockZ()));
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
