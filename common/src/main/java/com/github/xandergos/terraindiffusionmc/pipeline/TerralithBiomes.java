package com.github.xandergos.terraindiffusionmc.pipeline;

/**
 * Optional Terralith variants for the classifier's vanilla biome classes.
 *
 * <p>{@link BiomeClassifier} still decides the vanilla class from real temperature, rainfall, altitude, slope
 * and tree cover. {@link #refine} then picks a Terralith biome that fits the same place, using those
 * variables plus two large-scale region noises: one for everyday variety, one for rare themed regions
 * (moonlight, lavender, sakura) that are shared by neighbouring forests and meadows so they appear as
 * coherent areas.
 *
 * <p>A variant id encodes both the Terralith biome and the vanilla class it replaces. Without Terralith
 * installed the biome source resolves every variant to that vanilla class, so worlds are identical to the
 * vanilla mapping.
 *
 * <p>Terrain Diffusion keeps its own surface rules, which choose ground blocks by biome. Each Terralith
 * biome here therefore only ever replaces vanilla classes of one {@link Family}, and is listed in that
 * family's surface rules (worldgen/noise_settings/terrain_diffusion.json).
 *
 * <p>Biomes whose identity comes from Terralith's own terrain shaping or surface rules (spires, canyons,
 * chasms, volcanic and geothermal areas, sky and floating islands, rivers, caves) are not used: in a Terrain
 * Diffusion world they would keep only their name.
 */
public final class TerralithBiomes {

    /** Surface rule groups in Terrain Diffusion's noise settings. */
    public enum Family {
        /** Grass above sea level, dirt below. */
        DEFAULT,
        /** Snow layer on top: snowy plains and taigas. */
        SNOW_TOP,
        /** Sand and sandstone: desert. */
        SAND,
        /** Snow blocks: snowy slopes and frozen peaks. */
        SNOW_BLOCK,
        /** Bare stone: stony peaks and windswept hills. */
        STONE
    }

    /**
     * Terralith biomes used. The ordinal is part of the variant id, so only ever append to this list.
     */
    public enum TerralithBiome {
        ALPINE_GROVE("alpine_grove", Family.SNOW_TOP),
        ALPINE_HIGHLANDS("alpine_highlands", Family.DEFAULT),
        AMETHYST_RAINFOREST("amethyst_rainforest", Family.DEFAULT),
        ANCIENT_SANDS("ancient_sands", Family.SAND),
        ASHEN_SAVANNA("ashen_savanna", Family.DEFAULT),
        BASALT_CLIFFS("basalt_cliffs", Family.STONE),
        BIRCH_TAIGA("birch_taiga", Family.DEFAULT),
        BLOOMING_PLATEAU("blooming_plateau", Family.DEFAULT),
        BLOOMING_VALLEY("blooming_valley", Family.DEFAULT),
        CLOUD_FOREST("cloud_forest", Family.DEFAULT),
        COLD_SHRUBLAND("cold_shrubland", Family.DEFAULT),
        DESERT_OASIS("desert_oasis", Family.SAND),
        FORESTED_HIGHLANDS("forested_highlands", Family.DEFAULT),
        FROZEN_CLIFFS("frozen_cliffs", Family.SNOW_BLOCK),
        GRANITE_CLIFFS("granite_cliffs", Family.STONE),
        HAZE_MOUNTAIN("haze_mountain", Family.DEFAULT),
        HIGHLANDS("highlands", Family.DEFAULT),
        HOT_SHRUBLAND("hot_shrubland", Family.DEFAULT),
        ICE_MARSH("ice_marsh", Family.SNOW_TOP),
        JUNGLE_MOUNTAINS("jungle_mountains", Family.DEFAULT),
        LAVENDER_FOREST("lavender_forest", Family.DEFAULT),
        LAVENDER_VALLEY("lavender_valley", Family.DEFAULT),
        LUSH_DESERT("lush_desert", Family.SAND),
        LUSH_VALLEY("lush_valley", Family.DEFAULT),
        MOONLIGHT_GROVE("moonlight_grove", Family.DEFAULT),
        MOONLIGHT_VALLEY("moonlight_valley", Family.DEFAULT),
        ORCHID_SWAMP("orchid_swamp", Family.DEFAULT),
        ROCKY_JUNGLE("rocky_jungle", Family.DEFAULT),
        ROCKY_MOUNTAINS("rocky_mountains", Family.STONE),
        ROCKY_SHRUBLAND("rocky_shrubland", Family.DEFAULT),
        SAKURA_GROVE("sakura_grove", Family.DEFAULT),
        SAKURA_VALLEY("sakura_valley", Family.DEFAULT),
        SANDSTONE_VALLEY("sandstone_valley", Family.SAND),
        SAVANNA_BADLANDS("savanna_badlands", Family.DEFAULT),
        SAVANNA_SLOPES("savanna_slopes", Family.DEFAULT),
        SHIELD("shield", Family.DEFAULT),
        SHIELD_CLEARING("shield_clearing", Family.DEFAULT),
        SHRUBLAND("shrubland", Family.DEFAULT),
        SIBERIAN_TAIGA("siberian_taiga", Family.SNOW_TOP),
        SNOWY_CHERRY_GROVE("snowy_cherry_grove", Family.SNOW_TOP),
        SNOWY_MAPLE_FOREST("snowy_maple_forest", Family.SNOW_TOP),
        SNOWY_SHIELD("snowy_shield", Family.SNOW_TOP),
        STEPPE("steppe", Family.DEFAULT),
        TEMPERATE_HIGHLANDS("temperate_highlands", Family.DEFAULT),
        TROPICAL_JUNGLE("tropical_jungle", Family.DEFAULT),
        VALLEY_CLEARING("valley_clearing", Family.DEFAULT),
        WHITE_CLIFFS("white_cliffs", Family.STONE),
        WINTRY_FOREST("wintry_forest", Family.SNOW_TOP),
        YOSEMITE_LOWLANDS("yosemite_lowlands", Family.DEFAULT);

        private final String path;
        private final Family family;

        TerralithBiome(String path, Family family) {
            this.path = path;
            this.family = family;
        }

        /** Biome path in the {@code terralith} namespace. */
        public String path() {
            return path;
        }

        public Family family() {
            return family;
        }
    }

    private static final TerralithBiome[] BIOMES = TerralithBiome.values();

    /** Classifier biomes each Terralith biome can stand in for (matches {@link #refine}). */
    private static final java.util.Map<TerralithBiome, short[]> BASES = buildBases();

    private static java.util.Map<TerralithBiome, short[]> buildBases() {
        java.util.Map<TerralithBiome, short[]> bases = new java.util.EnumMap<>(TerralithBiome.class);
        bases.put(TerralithBiome.ALPINE_GROVE, ids(BiomeClassifier.SNOWY_TAIGA, BiomeClassifier.SNOWY_TAIGA_SPARSE));
        bases.put(TerralithBiome.ALPINE_HIGHLANDS, ids(BiomeClassifier.PLAINS, BiomeClassifier.GROVE));
        bases.put(TerralithBiome.AMETHYST_RAINFOREST, ids(BiomeClassifier.JUNGLE));
        bases.put(TerralithBiome.ANCIENT_SANDS, ids(BiomeClassifier.DESERT));
        bases.put(TerralithBiome.ASHEN_SAVANNA, ids(BiomeClassifier.SAVANNA));
        bases.put(TerralithBiome.BASALT_CLIFFS, ids(BiomeClassifier.STONY_PEAKS));
        bases.put(TerralithBiome.BIRCH_TAIGA, ids(BiomeClassifier.FOREST, BiomeClassifier.TAIGA));
        bases.put(TerralithBiome.BLOOMING_PLATEAU, ids(BiomeClassifier.PLAINS));
        bases.put(TerralithBiome.BLOOMING_VALLEY, ids(BiomeClassifier.FOREST, BiomeClassifier.PLAINS));
        bases.put(TerralithBiome.CLOUD_FOREST, ids(BiomeClassifier.TAIGA));
        bases.put(TerralithBiome.COLD_SHRUBLAND, ids(BiomeClassifier.PLAINS, BiomeClassifier.GROVE));
        bases.put(TerralithBiome.DESERT_OASIS, ids(BiomeClassifier.DESERT));
        bases.put(TerralithBiome.FORESTED_HIGHLANDS, ids(BiomeClassifier.FOREST, BiomeClassifier.TAIGA));
        bases.put(TerralithBiome.FROZEN_CLIFFS, ids(BiomeClassifier.FROZEN_PEAKS));
        bases.put(TerralithBiome.GRANITE_CLIFFS, ids(BiomeClassifier.STONY_PEAKS));
        bases.put(TerralithBiome.HAZE_MOUNTAIN, ids(BiomeClassifier.TAIGA_SPARSE));
        bases.put(TerralithBiome.HIGHLANDS, ids(BiomeClassifier.PLAINS, BiomeClassifier.FOREST_SPARSE));
        bases.put(TerralithBiome.HOT_SHRUBLAND, ids(BiomeClassifier.FOREST_SPARSE));
        bases.put(TerralithBiome.ICE_MARSH, ids(BiomeClassifier.SNOWY_PLAINS));
        bases.put(TerralithBiome.JUNGLE_MOUNTAINS, ids(BiomeClassifier.JUNGLE));
        bases.put(TerralithBiome.LAVENDER_FOREST, ids(BiomeClassifier.FOREST, BiomeClassifier.FOREST_SPARSE));
        bases.put(TerralithBiome.LAVENDER_VALLEY, ids(BiomeClassifier.PLAINS));
        bases.put(TerralithBiome.LUSH_DESERT, ids(BiomeClassifier.DESERT));
        bases.put(TerralithBiome.LUSH_VALLEY, ids(BiomeClassifier.TAIGA));
        bases.put(TerralithBiome.MOONLIGHT_GROVE, ids(BiomeClassifier.FOREST, BiomeClassifier.FOREST_SPARSE));
        bases.put(TerralithBiome.MOONLIGHT_VALLEY, ids(BiomeClassifier.PLAINS));
        bases.put(TerralithBiome.ORCHID_SWAMP, ids(BiomeClassifier.SWAMP));
        bases.put(TerralithBiome.ROCKY_JUNGLE, ids(BiomeClassifier.JUNGLE));
        bases.put(TerralithBiome.ROCKY_MOUNTAINS, ids(BiomeClassifier.STONY_PEAKS, BiomeClassifier.WINDSWEPT_HILLS));
        bases.put(TerralithBiome.ROCKY_SHRUBLAND, ids(BiomeClassifier.PLAINS, BiomeClassifier.GROVE));
        bases.put(TerralithBiome.SAKURA_GROVE, ids(BiomeClassifier.FOREST, BiomeClassifier.FOREST_SPARSE));
        bases.put(TerralithBiome.SAKURA_VALLEY, ids(BiomeClassifier.PLAINS));
        bases.put(TerralithBiome.SANDSTONE_VALLEY, ids(BiomeClassifier.DESERT));
        bases.put(TerralithBiome.SAVANNA_BADLANDS, ids(BiomeClassifier.SAVANNA));
        bases.put(TerralithBiome.SAVANNA_SLOPES, ids(BiomeClassifier.SAVANNA));
        bases.put(TerralithBiome.SHIELD, ids(BiomeClassifier.TAIGA, BiomeClassifier.TAIGA_SPARSE));
        bases.put(TerralithBiome.SHIELD_CLEARING, ids(BiomeClassifier.TAIGA_SPARSE));
        bases.put(TerralithBiome.SHRUBLAND, ids(BiomeClassifier.SAVANNA));
        bases.put(TerralithBiome.SIBERIAN_TAIGA, ids(BiomeClassifier.SNOWY_TAIGA));
        bases.put(TerralithBiome.SNOWY_CHERRY_GROVE, ids(BiomeClassifier.SNOWY_TAIGA_SPARSE));
        bases.put(TerralithBiome.SNOWY_MAPLE_FOREST, ids(BiomeClassifier.SNOWY_TAIGA));
        bases.put(TerralithBiome.SNOWY_SHIELD, ids(BiomeClassifier.SNOWY_TAIGA_SPARSE));
        bases.put(TerralithBiome.STEPPE, ids(BiomeClassifier.GROVE));
        bases.put(TerralithBiome.TEMPERATE_HIGHLANDS, ids(BiomeClassifier.FOREST));
        bases.put(TerralithBiome.TROPICAL_JUNGLE, ids(BiomeClassifier.JUNGLE));
        bases.put(TerralithBiome.VALLEY_CLEARING, ids(BiomeClassifier.PLAINS));
        bases.put(TerralithBiome.WHITE_CLIFFS, ids(BiomeClassifier.STONY_PEAKS));
        bases.put(TerralithBiome.WINTRY_FOREST, ids(BiomeClassifier.SNOWY_TAIGA));
        bases.put(TerralithBiome.YOSEMITE_LOWLANDS, ids(BiomeClassifier.FOREST_SPARSE));
        return java.util.Collections.unmodifiableMap(bases);
    }

    private static short[] ids(short... ids) {
        return ids;
    }

    /** Classifier biomes the given Terralith biome can stand in for. */
    public static short[] basesOf(TerralithBiome biome) {
        return BASES.get(biome).clone();
    }

    /** Variant ids start here; below it are plain classifier ids (all below 128). */
    static final int VARIANT_BASE = 1024;
    private static final int BASE_BITS = 7;
    private static final int BASE_MASK = (1 << BASE_BITS) - 1;

    // Altitude bands (metres). MOUNTAIN matches BiomeClassifier's mountain band.
    static final float MOUNTAIN_M = 2500f;
    static final float HIGHLAND_M = 800f;
    static final float LOWLAND_M = 200f;
    static final float COAST_M = 60f;

    // Themed regions, as fractions of the theme noise: the top 14% of regions carry a theme.
    static final float MOONLIGHT_FROM = 0.86f;
    static final float LAVENDER_FROM = 0.90f;
    static final float SAKURA_FROM = 0.95f;

    private static final float WARP_AMPLITUDE = 80f;
    private static final FastNoiseLite WARP_NOISE = makeWarpNoise();
    private static final FastNoiseLite VARIETY_NOISE = makeRegionNoise(7331, 1f / 600f);
    private static final FastNoiseLite THEME_NOISE = makeRegionNoise(9157, 1f / 900f);

    private TerralithBiomes() {
    }

    private static FastNoiseLite makeRegionNoise(int seed, float frequency) {
        FastNoiseLite noise = new FastNoiseLite(seed);
        noise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        noise.SetFrequency(frequency);
        noise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Hybrid);
        noise.SetCellularReturnType(FastNoiseLite.CellularReturnType.CellValue);
        noise.SetCellularJitter(1.0f);
        return noise;
    }

    private static FastNoiseLite makeWarpNoise() {
        FastNoiseLite noise = new FastNoiseLite(4421);
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(1f / 200f);
        return noise;
    }

    public static boolean isVariant(short id) {
        return id >= VARIANT_BASE;
    }

    /** The Terralith biome a variant id stands for. Only valid when {@link #isVariant} is true. */
    public static TerralithBiome terralithOf(short id) {
        return BIOMES[(id - VARIANT_BASE) >> BASE_BITS];
    }

    /** The vanilla classifier id a variant replaces, or the id itself for plain ids. */
    public static short fallbackOf(short id) {
        return isVariant(id) ? (short) ((id - VARIANT_BASE) & BASE_MASK) : id;
    }

    static short variant(TerralithBiome biome, short base) {
        return (short) (VARIANT_BASE + (biome.ordinal() << BASE_BITS) + base);
    }

    /** Tree moisture as computed in BiomeClassifier (aridity scaled by rainfall seasonality). */
    static float treeMoisture(float temp, float tSeason, float precip, float pCV) {
        float tStd = tSeason / 100f;
        float tEff = Math.max(0f, temp + 0.5f * tStd);
        float pet = Math.max(250f, 250f + 25f * tEff + 0.7f * tEff * tEff);
        float aridity = precip / Math.max(1f, pet);
        float seasonPenalty = 1f - 0.35f * Math.min(1f, pCV / 100f);
        return aridity * seasonPenalty;
    }

    /**
     * Picks a Terralith variant of {@code base} for one location, or returns {@code base} to keep the vanilla
     * biome. {@code temp} and {@code precip} include the classifier's noise perturbations; {@code i, j} are
     * the classifier's world coordinates (row = Z, column = X).
     */
    static short refine(short base, float elevM, float slope, float temp, float tSeason, float precip, float pCV,
                        float i, float j) {
        float altM = Math.max(0f, elevM);
        boolean mountain = altM > MOUNTAIN_M;
        boolean highland = altM >= HIGHLAND_M;
        boolean lowland = altM < LOWLAND_M;
        boolean coastal = elevM >= 0f && altM < COAST_M;
        float moisture = treeMoisture(temp, tSeason, precip, pCV);

        float wx = j + WARP_AMPLITUDE * WARP_NOISE.GetNoise(j, i);
        float wy = i + WARP_AMPLITUDE * WARP_NOISE.GetNoise(j + 1000f, i - 1000f);
        float v = unit(VARIETY_NOISE.GetNoise(wx, wy));
        float theme = unit(THEME_NOISE.GetNoise(wx, wy));
        boolean themeClimate = temp >= 5f && temp < 20f;
        TerralithBiome forestTheme = themeClimate ? pickTheme(theme, TerralithBiome.MOONLIGHT_GROVE,
                TerralithBiome.LAVENDER_FOREST, TerralithBiome.SAKURA_GROVE) : null;
        TerralithBiome valleyTheme = themeClimate ? pickTheme(theme, TerralithBiome.MOONLIGHT_VALLEY,
                TerralithBiome.LAVENDER_VALLEY, TerralithBiome.SAKURA_VALLEY) : null;

        switch (base) {
            case BiomeClassifier.PLAINS:
                if (mountain) {
                    if (v < 0.45f) return variant(TerralithBiome.ALPINE_HIGHLANDS, base);
                    if (v < 0.70f) return variant(TerralithBiome.BLOOMING_PLATEAU, base);
                    return base;
                }
                // Treeless but moist: mostly cold, short-season land; temperate meadows are the rare case.
                if (temp < 0f) return v < 0.4f ? variant(TerralithBiome.ROCKY_SHRUBLAND, base) : base;
                if (temp < 8f) {
                    if (v < 0.35f) return variant(TerralithBiome.VALLEY_CLEARING, base);
                    if (v < 0.55f) return variant(TerralithBiome.COLD_SHRUBLAND, base);
                    if (highland) return variant(TerralithBiome.HIGHLANDS, base);
                    return base;
                }
                if (valleyTheme != null) return variant(valleyTheme, base);
                if (v < 0.30f) return variant(TerralithBiome.VALLEY_CLEARING, base);
                if (v < 0.55f) return variant(TerralithBiome.BLOOMING_VALLEY, base);
                if (highland) return variant(TerralithBiome.HIGHLANDS, base);
                return base;

            case BiomeClassifier.FOREST:
                if (forestTheme != null) return variant(forestTheme, base);
                if (highland) return variant(v < 0.5f ? TerralithBiome.TEMPERATE_HIGHLANDS : TerralithBiome.FORESTED_HIGHLANDS, base);
                if (v < 0.25f) return variant(TerralithBiome.BLOOMING_VALLEY, base);
                if (v < 0.40f && temp < 14f) return variant(TerralithBiome.BIRCH_TAIGA, base);
                return base;

            case BiomeClassifier.FOREST_SPARSE:
                if (forestTheme != null) return variant(forestTheme, base);
                if (temp >= 20f) return v < 0.5f ? variant(TerralithBiome.HOT_SHRUBLAND, base) : base;
                if (highland) return v < 0.5f ? variant(TerralithBiome.HIGHLANDS, base) : base;
                if (v < 0.35f) return variant(TerralithBiome.YOSEMITE_LOWLANDS, base);
                return base;

            case BiomeClassifier.TAIGA:
                if (mountain) {
                    if (precip >= 900f && v < 0.4f) return variant(TerralithBiome.CLOUD_FOREST, base);
                    if (v < 0.5f) return variant(TerralithBiome.FORESTED_HIGHLANDS, base);
                    return base;
                }
                if (v < 0.40f) return variant(temp >= 5f ? TerralithBiome.BIRCH_TAIGA : TerralithBiome.SHIELD, base);
                if (v < 0.55f) return variant(TerralithBiome.LUSH_VALLEY, base);
                return base;

            case BiomeClassifier.TAIGA_SPARSE:
                if (mountain) return (precip >= 600f && v < 0.5f) ? variant(TerralithBiome.HAZE_MOUNTAIN, base) : base;
                if (v < 0.40f) return variant(TerralithBiome.SHIELD, base);
                if (v < 0.55f) return variant(TerralithBiome.SHIELD_CLEARING, base);
                return base;

            case BiomeClassifier.SNOWY_TAIGA:
                if (mountain) return v < 0.6f ? variant(TerralithBiome.ALPINE_GROVE, base) : base;
                if (precip >= 700f) return variant(TerralithBiome.SIBERIAN_TAIGA, base);
                if (v < 0.5f) return variant(TerralithBiome.WINTRY_FOREST, base);
                if (v < 0.7f && precip < 500f) return variant(TerralithBiome.SNOWY_MAPLE_FOREST, base);
                return base;

            case BiomeClassifier.SNOWY_TAIGA_SPARSE:
                if (mountain) return variant(TerralithBiome.ALPINE_GROVE, base);
                if (theme >= SAKURA_FROM) return variant(TerralithBiome.SNOWY_CHERRY_GROVE, base);
                if (v < 0.55f) return variant(TerralithBiome.SNOWY_SHIELD, base);
                return base;

            case BiomeClassifier.SNOWY_PLAINS:
                if (lowland && precip >= 500f && v < 0.5f) return variant(TerralithBiome.ICE_MARSH, base);
                return base;

            case BiomeClassifier.GROVE:
                if (v >= 0.8f) return base;
                if (mountain && v < 0.5f) return variant(TerralithBiome.ALPINE_HIGHLANDS, base);
                if (temp < 0f) return variant(TerralithBiome.ROCKY_SHRUBLAND, base);
                if (temp < 8f) return variant(TerralithBiome.COLD_SHRUBLAND, base);
                return variant(TerralithBiome.STEPPE, base);

            case BiomeClassifier.WINDSWEPT_HILLS:
                return v < 0.6f ? variant(TerralithBiome.ROCKY_MOUNTAINS, base) : base;

            case BiomeClassifier.DESERT:
                if (theme >= SAKURA_FROM) return variant(TerralithBiome.DESERT_OASIS, base);
                if (precip < 150f && v < 0.6f) return variant(TerralithBiome.ANCIENT_SANDS, base);
                if (precip >= 250f && v < 0.5f) return variant(TerralithBiome.LUSH_DESERT, base);
                if (v >= 0.6f && v < 0.75f) return variant(TerralithBiome.SANDSTONE_VALLEY, base);
                return base;

            case BiomeClassifier.SAVANNA:
                if (moisture < 0.35f) {
                    if (v < 0.5f) return variant(TerralithBiome.ASHEN_SAVANNA, base);
                    if (v < 0.7f) return variant(TerralithBiome.SAVANNA_BADLANDS, base);
                    return base;
                }
                if (highland && v < 0.6f) return variant(TerralithBiome.SAVANNA_SLOPES, base);
                if (v < 0.35f) return variant(TerralithBiome.SHRUBLAND, base);
                return base;

            case BiomeClassifier.JUNGLE:
                if (highland && v < 0.6f) return variant(TerralithBiome.JUNGLE_MOUNTAINS, base);
                if (slope >= 0.4f && v < 0.6f) return variant(TerralithBiome.ROCKY_JUNGLE, base);
                if (moisture >= 1.3f && theme >= LAVENDER_FROM) return variant(TerralithBiome.AMETHYST_RAINFOREST, base);
                if (v < 0.5f) return variant(TerralithBiome.TROPICAL_JUNGLE, base);
                return base;

            case BiomeClassifier.SWAMP:
                return v < 0.5f ? variant(TerralithBiome.ORCHID_SWAMP, base) : base;

            case BiomeClassifier.STONY_PEAKS:
                if (coastal) {
                    if (temp < 12f) return variant(TerralithBiome.WHITE_CLIFFS, base);
                    if (temp < 22f) return variant(TerralithBiome.GRANITE_CLIFFS, base);
                    return variant(TerralithBiome.BASALT_CLIFFS, base);
                }
                return v < 0.4f ? variant(TerralithBiome.ROCKY_MOUNTAINS, base) : base;

            case BiomeClassifier.FROZEN_PEAKS:
                return coastal ? variant(TerralithBiome.FROZEN_CLIFFS, base) : base;

            default:
                // Oceans and snowy slopes keep their vanilla biome.
                return base;
        }
    }

    private static TerralithBiome pickTheme(float theme, TerralithBiome moonlight, TerralithBiome lavender,
                                            TerralithBiome sakura) {
        if (theme >= SAKURA_FROM) return sakura;
        if (theme >= LAVENDER_FROM) return lavender;
        if (theme >= MOONLIGHT_FROM) return moonlight;
        return null;
    }

    /** Maps a cell value in [-1, 1] to [0, 1). */
    private static float unit(float cellValue) {
        return Math.max(0f, Math.min(0.99999f, (cellValue + 1f) * 0.5f));
    }
}
