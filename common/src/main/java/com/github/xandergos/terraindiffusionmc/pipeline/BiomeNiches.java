package com.github.xandergos.terraindiffusionmc.pipeline;

import java.util.EnumSet;
import java.util.Set;

/**
 * Matches biomes Terrain Diffusion has no rules for (other mods, vanilla biomes it doesn't place itself) to the
 * classifier biome whose climate and character they share most: their "niche". A biome placed in a niche shares
 * that niche's area with the niche's own biome.
 *
 * <p>Matching uses what every biome declares: temperature, downfall, whether it has precipitation, and its tags.
 * Tags are read from the vanilla, Forge ({@code forge:}) and common ({@code c:}) conventions that biome mods use
 * to describe their biomes (forest, desert, snowy, mountain, sparse, ...).
 *
 * <p>No Minecraft dependencies, so it can be tested on its own.
 */
public final class BiomeNiches {

    /** A biome as seen by the matcher. Tags are full ids ("forge:is_snowy"). */
    public record Descriptor(String id, float temperature, float downfall, boolean precipitation, Set<String> tags) {
    }

    /** Biome character derived from tags (and, for water and snow, also from the id and climate). */
    public enum Category {
        OCEAN, RIVER, BEACH, FOREST, TAIGA, JUNGLE, SAVANNA, BADLANDS, DESERT, SWAMP, PLAINS, MOUNTAIN, PEAK,
        SNOWY, SPARSE, DENSE, ARID, WARM_WATER, COLD_WATER, NETHER, END, CAVE, MUSHROOM, VOID, RARE
    }

    private static final Set<String> TAG_NAMESPACES = Set.of("minecraft", "forge", "c");
    /** Marks the built-in niche descriptors, whose names (e.g. "grove") say nothing about how they are used. */
    private static final String NICHE_MARKER = "terrain-diffusion-mc:niche";

    private BiomeNiches() {
    }

    /** Categories of a biome. */
    public static EnumSet<Category> categories(Descriptor biome) {
        EnumSet<Category> out = EnumSet.noneOf(Category.class);
        for (String tag : biome.tags()) {
            int colon = tag.indexOf(':');
            String namespace = colon < 0 ? "minecraft" : tag.substring(0, colon);
            if (!TAG_NAMESPACES.contains(namespace)) {
                continue;
            }
            String path = tag.substring(colon + 1);
            if (path.endsWith("/nether")) {
                out.add(Category.NETHER);
                continue;
            }
            if (path.endsWith("/end")) {
                out.add(Category.END);
                continue;
            }
            if (path.endsWith("/overworld")) {
                path = path.substring(0, path.length() - "/overworld".length());
            }
            addCategoryForTag(path, out);
        }

        String path = biome.id().substring(biome.id().indexOf(':') + 1);
        if (path.startsWith("cave/") || path.endsWith("_caves") || path.equals("deep_dark")) {
            out.add(Category.CAVE);
        }
        if (path.contains("beach") || path.contains("shore")) {
            out.add(Category.BEACH);
        }
        if (path.contains("river")) {
            out.add(Category.RIVER);
        }
        if (path.contains("frozen")) {
            out.add(Category.SNOWY);
        }
        if (!biome.tags().contains(NICHE_MARKER)) {
            addCategoriesForName(path, out);
        }
        if (out.contains(Category.OCEAN) || path.contains("ocean")) {
            out.add(Category.OCEAN);
            if (path.contains("warm")) {
                out.add(Category.WARM_WATER);
            } else if (path.contains("cold") || path.contains("frozen")) {
                out.add(Category.COLD_WATER);
            }
        }
        if (biome.temperature() < 0.15f) {
            out.add(Category.SNOWY);
        }
        if (out.contains(Category.TAIGA)) {
            out.add(Category.FOREST);
        }
        if (out.contains(Category.DESERT) || out.contains(Category.BADLANDS)
                || (!biome.precipitation() && biome.downfall() <= 0.1f)) {
            out.add(Category.ARID);
        }
        return out;
    }

    /**
     * Hints from the biome's name, for biomes without category tags (Terralith tags few of its biomes). Names
     * are a reliable signal: "orchid_swamp" is a swamp, "desert_canyon" a desert.
     */
    private static void addCategoriesForName(String path, EnumSet<Category> out) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (containsAny(name, "desert", "dune", "oasis")) out.add(Category.DESERT);
        if (containsAny(name, "savanna")) out.add(Category.SAVANNA);
        if (containsAny(name, "jungle", "rainforest", "tropic")) out.add(Category.JUNGLE);
        if (containsAny(name, "taiga", "boreal", "conifer", "pine", "spruce", "fir_")) out.add(Category.TAIGA);
        if (containsAny(name, "forest", "woods", "woodland", "grove")) out.add(Category.FOREST);
        if (containsAny(name, "swamp", "marsh", "bog", "bayou", "fen", "wetland", "mire", "muskeg")) out.add(Category.SWAMP);
        if (containsAny(name, "plains", "meadow", "steppe", "prairie", "grassland", "pasture")) out.add(Category.PLAINS);
        if (containsAny(name, "badlands", "mesa", "canyon")) out.add(Category.BADLANDS);
        if (containsAny(name, "mountain", "peak", "cliff", "highland", "alpine", "crag")) out.add(Category.MOUNTAIN);
        if (containsAny(name, "snowy", "frozen", "glacial", "wintry", "tundra", "ice_")) out.add(Category.SNOWY);
        if (containsAny(name, "shrub", "scrub", "clearing")) out.add(Category.SPARSE);
    }

    private static boolean containsAny(String name, String... keywords) {
        for (String keyword : keywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static void addCategoryForTag(String path, EnumSet<Category> out) {
        switch (path) {
            case "is_ocean", "is_deep_ocean", "ocean", "deep_ocean" -> out.add(Category.OCEAN);
            case "is_river", "river" -> out.add(Category.RIVER);
            case "is_beach", "beach" -> out.add(Category.BEACH);
            case "is_forest", "forest" -> out.add(Category.FOREST);
            case "is_taiga", "taiga", "is_coniferous", "tree_coniferous" -> out.add(Category.TAIGA);
            case "is_jungle", "jungle" -> out.add(Category.JUNGLE);
            case "is_savanna", "savanna" -> out.add(Category.SAVANNA);
            case "is_badlands", "badlands" -> out.add(Category.BADLANDS);
            case "is_desert", "desert" -> out.add(Category.DESERT);
            case "is_swamp", "swamp" -> out.add(Category.SWAMP);
            case "is_plains", "plains" -> out.add(Category.PLAINS);
            case "is_mountain", "mountain", "is_slope", "mountain_slope", "is_hill" -> out.add(Category.MOUNTAIN);
            case "is_peak", "mountain_peak" -> {
                out.add(Category.MOUNTAIN);
                out.add(Category.PEAK);
            }
            case "is_snowy", "snowy", "icy" -> out.add(Category.SNOWY);
            case "is_sparse", "vegetation_sparse" -> out.add(Category.SPARSE);
            case "is_dense", "vegetation_dense" -> out.add(Category.DENSE);
            case "is_dry", "climate_dry" -> out.add(Category.ARID);
            case "is_nether", "in_nether" -> out.add(Category.NETHER);
            case "is_end", "in_the_end" -> out.add(Category.END);
            case "is_cave", "is_underground", "caves", "underground" -> out.add(Category.CAVE);
            case "is_mushroom", "mushroom" -> out.add(Category.MUSHROOM);
            case "is_void", "void" -> out.add(Category.VOID);
            case "is_rare", "rare", "is_magical", "magical" -> out.add(Category.RARE);
            case "has_structure/ocean_ruin_warm", "produces_corals_from_bonemeal" -> out.add(Category.WARM_WATER);
            default -> {
            }
        }
    }

    /**
     * Whether a {@code namespace:*} entry should include this biome: overworld surface biomes only, without
     * rivers, beaches, caves, mushroom islands or the void, which make no sense as ordinary land.
     */
    public static boolean isWildcardEligible(Descriptor biome) {
        EnumSet<Category> c = categories(biome);
        return !(c.contains(Category.NETHER) || c.contains(Category.END) || c.contains(Category.CAVE)
                || c.contains(Category.RIVER) || c.contains(Category.BEACH) || c.contains(Category.MUSHROOM)
                || c.contains(Category.VOID));
    }

    private static final Category[] CHARACTER = {
            Category.FOREST, Category.TAIGA, Category.JUNGLE, Category.SAVANNA, Category.BADLANDS, Category.DESERT,
            Category.SWAMP, Category.PLAINS, Category.MOUNTAIN, Category.PEAK, Category.SNOWY, Category.SPARSE,
            Category.DENSE, Category.ARID, Category.WARM_WATER, Category.COLD_WATER
    };
    // MOUNTAIN is weak on purpose: in Terrain Diffusion the terrain decides where mountains are, so a biome's
    // climate matters more than whether it calls itself a mountain biome.
    private static final double[] CHARACTER_WEIGHT = {
            1.0, 1.2, 1.5, 1.5, 1.0, 1.5,
            1.5, 0.8, 0.6, 1.2, 1.2, 0.5,
            0.5, 1.0, 1.0, 1.0
    };

    /**
     * How well {@code biome} fits {@code niche}; higher is better. Land never matches water and vice versa
     * ({@link Double#NEGATIVE_INFINITY}).
     */
    public static double score(Descriptor biome, Descriptor niche) {
        EnumSet<Category> b = categories(biome);
        EnumSet<Category> n = categories(niche);
        boolean biomeWater = b.contains(Category.OCEAN) || b.contains(Category.RIVER);
        boolean nicheWater = n.contains(Category.OCEAN) || n.contains(Category.RIVER);
        if (biomeWater != nicheWater) {
            return Double.NEGATIVE_INFINITY;
        }
        double score = 0;
        for (int i = 0; i < CHARACTER.length; i++) {
            boolean inBiome = b.contains(CHARACTER[i]);
            boolean inNiche = n.contains(CHARACTER[i]);
            if (inBiome && inNiche) {
                score += CHARACTER_WEIGHT[i];
            } else if (inBiome || inNiche) {
                score -= 0.6 * CHARACTER_WEIGHT[i];
            }
        }
        if ((b.contains(Category.DENSE) && n.contains(Category.SPARSE))
                || (b.contains(Category.SPARSE) && n.contains(Category.DENSE))) {
            score -= 1.0;
        }
        score -= 1.5 * Math.abs(clampTemperature(biome.temperature()) - clampTemperature(niche.temperature()));
        score -= 1.5 * Math.abs(clamp01(biome.downfall()) - clamp01(niche.downfall()));
        if (biome.precipitation() != niche.precipitation()) {
            score -= 0.5;
        }
        return score;
    }

    private static float clampTemperature(float t) {
        return Math.max(-0.7f, Math.min(1.2f, t));
    }

    /**
     * The classifier biomes other biomes can join, described by how Terrain Diffusion uses them rather than by
     * their vanilla definitions (its grove, for example, is dry treeless cold grassland, not a snowy spruce
     * forest). Badlands and meadow are not niches: the classifier never places them.
     */
    public static final java.util.Map<Short, Descriptor> NICHES = buildNiches();

    private static java.util.Map<Short, Descriptor> buildNiches() {
        java.util.Map<Short, Descriptor> n = new java.util.LinkedHashMap<>();
        n.put(BiomeClassifier.PLAINS, niche("minecraft:plains", 0.8f, 0.4f, true, "forge:is_plains"));
        n.put(BiomeClassifier.SNOWY_PLAINS, niche("minecraft:snowy_plains", 0.0f, 0.5f, true, "forge:is_plains", "forge:is_snowy"));
        n.put(BiomeClassifier.GROVE, niche("minecraft:grove", 0.3f, 0.2f, true, "forge:is_plains", "forge:is_sparse"));
        n.put(BiomeClassifier.DESERT, niche("minecraft:desert", 2.0f, 0.0f, false, "forge:is_desert", "forge:is_sparse"));
        n.put(BiomeClassifier.SAVANNA, niche("minecraft:savanna", 2.0f, 0.0f, false, "minecraft:is_savanna", "forge:is_sparse"));
        n.put(BiomeClassifier.SWAMP, niche("minecraft:swamp", 0.8f, 0.9f, true, "forge:is_swamp"));
        n.put(BiomeClassifier.FOREST, niche("minecraft:forest", 0.7f, 0.8f, true, "minecraft:is_forest"));
        n.put(BiomeClassifier.FOREST_SPARSE, niche("terrain-diffusion-mc:forest_sparse", 0.7f, 0.5f, true, "minecraft:is_forest", "forge:is_sparse"));
        n.put(BiomeClassifier.JUNGLE, niche("minecraft:jungle", 0.95f, 0.9f, true, "minecraft:is_jungle", "forge:is_dense"));
        n.put(BiomeClassifier.TAIGA, niche("minecraft:taiga", 0.25f, 0.8f, true, "minecraft:is_taiga"));
        n.put(BiomeClassifier.TAIGA_SPARSE, niche("terrain-diffusion-mc:taiga_sparse", 0.25f, 0.5f, true, "minecraft:is_taiga", "forge:is_sparse"));
        n.put(BiomeClassifier.SNOWY_TAIGA, niche("minecraft:snowy_taiga", -0.5f, 0.4f, true, "minecraft:is_taiga", "forge:is_snowy"));
        n.put(BiomeClassifier.SNOWY_TAIGA_SPARSE, niche("terrain-diffusion-mc:snowy_taiga_sparse", -0.5f, 0.4f, true, "minecraft:is_taiga", "forge:is_snowy", "forge:is_sparse"));
        n.put(BiomeClassifier.WINDSWEPT_HILLS, niche("minecraft:windswept_hills", 0.2f, 0.3f, true, "forge:is_mountain", "forge:is_sparse"));
        n.put(BiomeClassifier.STONY_PEAKS, niche("minecraft:stony_peaks", 1.0f, 0.3f, true, "forge:is_peak"));
        n.put(BiomeClassifier.SNOWY_SLOPES, niche("minecraft:snowy_slopes", -0.3f, 0.9f, true, "forge:is_slope", "forge:is_snowy"));
        n.put(BiomeClassifier.FROZEN_PEAKS, niche("minecraft:frozen_peaks", -0.7f, 0.9f, true, "forge:is_peak", "forge:is_snowy"));
        n.put(BiomeClassifier.WARM_OCEAN, niche("minecraft:warm_ocean", 0.5f, 0.5f, true, "minecraft:is_ocean"));
        n.put(BiomeClassifier.OCEAN, niche("minecraft:ocean", 0.5f, 0.5f, true, "minecraft:is_ocean"));
        n.put(BiomeClassifier.COLD_OCEAN, niche("minecraft:cold_ocean", 0.5f, 0.5f, true, "minecraft:is_ocean"));
        n.put(BiomeClassifier.FROZEN_OCEAN, niche("minecraft:frozen_ocean", 0.0f, 0.5f, true, "minecraft:is_ocean"));
        return java.util.Collections.unmodifiableMap(n);
    }

    private static Descriptor niche(String id, float temperature, float downfall, boolean precipitation, String... tags) {
        Set<String> all = new java.util.HashSet<>(java.util.Arrays.asList(tags));
        all.add(NICHE_MARKER);
        return new Descriptor(id, temperature, downfall, precipitation, Set.copyOf(all));
    }

    /** Classifier id of the niche that suits {@code biome} best, or null if none is compatible. */
    public static Short bestNicheId(Descriptor biome) {
        Short best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (java.util.Map.Entry<Short, Descriptor> entry : NICHES.entrySet()) {
            double s = score(biome, entry.getValue());
            if (s > bestScore) {
                bestScore = s;
                best = entry.getKey();
            }
        }
        return best;
    }

    /** Share of a niche for biomes their mod marks as rare or magical, relative to ordinary biomes. */
    public static final float RARE_WEIGHT = 0.35f;

    /** How much of a niche's area the biome gets relative to the others there: rare biomes get less. */
    public static float weight(Descriptor biome) {
        return categories(biome).contains(Category.RARE) ? RARE_WEIGHT : 1f;
    }

    /** Whether the biome is in {@code minecraft:is_overworld}. */
    public static boolean isOverworldTagged(Descriptor biome) {
        return biome.tags().contains("minecraft:is_overworld");
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
