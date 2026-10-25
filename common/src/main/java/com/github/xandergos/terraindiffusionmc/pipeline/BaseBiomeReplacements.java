package com.github.xandergos.terraindiffusionmc.pipeline;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Replacements for the classifier's base biomes (vanilla plus Terrain Diffusion's sparse variants) listed in
 * {@code vanilla.disabled_biomes}.
 *
 * <p>A disabled biome is replaced by the one the user names ({@code swamp=forest}), or else by the nearest
 * biome in {@link #FALLBACKS} that isn't disabled itself. The fallbacks follow the classifier's own
 * decisions where possible (swamp is a warm lowland dense forest, so it falls back to forest; snowy plains and
 * snowy slopes are the lowland and mountain forms of the same treeless snow), and otherwise keep the same
 * surface blocks and climate.
 *
 * <p>No Minecraft dependencies, so it can be tested on its own.
 */
public final class BaseBiomeReplacements {
    private static final String MINECRAFT = "minecraft:";
    private static final String TERRAIN_DIFFUSION = "terrain-diffusion-mc:";

    /** Base biome ids as used by {@link BiomeClassifier}, with their biome ids. */
    public static final Map<Short, String> BIOMES;
    /** Ordered fallbacks for each base biome. */
    static final Map<Short, short[]> FALLBACKS;

    static {
        Map<Short, String> biomes = new LinkedHashMap<>();
        biomes.put(BiomeClassifier.PLAINS, MINECRAFT + "plains");
        biomes.put(BiomeClassifier.SNOWY_PLAINS, MINECRAFT + "snowy_plains");
        biomes.put(BiomeClassifier.DESERT, MINECRAFT + "desert");
        biomes.put(BiomeClassifier.SWAMP, MINECRAFT + "swamp");
        biomes.put(BiomeClassifier.FOREST, MINECRAFT + "forest");
        biomes.put(BiomeClassifier.TAIGA, MINECRAFT + "taiga");
        biomes.put(BiomeClassifier.SNOWY_TAIGA, MINECRAFT + "snowy_taiga");
        biomes.put(BiomeClassifier.SAVANNA, MINECRAFT + "savanna");
        biomes.put(BiomeClassifier.WINDSWEPT_HILLS, MINECRAFT + "windswept_hills");
        biomes.put(BiomeClassifier.JUNGLE, MINECRAFT + "jungle");
        biomes.put(BiomeClassifier.BADLANDS, MINECRAFT + "badlands");
        biomes.put(BiomeClassifier.MEADOW, MINECRAFT + "meadow");
        biomes.put(BiomeClassifier.GROVE, MINECRAFT + "grove");
        biomes.put(BiomeClassifier.SNOWY_SLOPES, MINECRAFT + "snowy_slopes");
        biomes.put(BiomeClassifier.FROZEN_PEAKS, MINECRAFT + "frozen_peaks");
        biomes.put(BiomeClassifier.STONY_PEAKS, MINECRAFT + "stony_peaks");
        biomes.put(BiomeClassifier.WARM_OCEAN, MINECRAFT + "warm_ocean");
        biomes.put(BiomeClassifier.OCEAN, MINECRAFT + "ocean");
        biomes.put(BiomeClassifier.COLD_OCEAN, MINECRAFT + "cold_ocean");
        biomes.put(BiomeClassifier.FROZEN_OCEAN, MINECRAFT + "frozen_ocean");
        biomes.put(BiomeClassifier.FOREST_SPARSE, TERRAIN_DIFFUSION + "forest_sparse");
        biomes.put(BiomeClassifier.TAIGA_SPARSE, TERRAIN_DIFFUSION + "taiga_sparse");
        biomes.put(BiomeClassifier.SNOWY_TAIGA_SPARSE, TERRAIN_DIFFUSION + "snowy_taiga_sparse");
        BIOMES = Collections.unmodifiableMap(biomes);

        Map<Short, short[]> fallbacks = new LinkedHashMap<>();
        fallbacks.put(BiomeClassifier.PLAINS, ids(BiomeClassifier.MEADOW, BiomeClassifier.FOREST_SPARSE));
        fallbacks.put(BiomeClassifier.MEADOW, ids(BiomeClassifier.PLAINS, BiomeClassifier.FOREST_SPARSE));
        fallbacks.put(BiomeClassifier.GROVE, ids(BiomeClassifier.PLAINS, BiomeClassifier.MEADOW));
        fallbacks.put(BiomeClassifier.SNOWY_PLAINS, ids(BiomeClassifier.SNOWY_SLOPES, BiomeClassifier.SNOWY_TAIGA_SPARSE));
        fallbacks.put(BiomeClassifier.SNOWY_SLOPES, ids(BiomeClassifier.SNOWY_PLAINS, BiomeClassifier.FROZEN_PEAKS));
        fallbacks.put(BiomeClassifier.FROZEN_PEAKS, ids(BiomeClassifier.SNOWY_SLOPES, BiomeClassifier.STONY_PEAKS));
        fallbacks.put(BiomeClassifier.STONY_PEAKS, ids(BiomeClassifier.WINDSWEPT_HILLS, BiomeClassifier.FROZEN_PEAKS));
        fallbacks.put(BiomeClassifier.WINDSWEPT_HILLS, ids(BiomeClassifier.STONY_PEAKS, BiomeClassifier.GROVE));
        fallbacks.put(BiomeClassifier.DESERT, ids(BiomeClassifier.BADLANDS, BiomeClassifier.SAVANNA));
        fallbacks.put(BiomeClassifier.BADLANDS, ids(BiomeClassifier.DESERT, BiomeClassifier.SAVANNA));
        fallbacks.put(BiomeClassifier.SAVANNA, ids(BiomeClassifier.FOREST_SPARSE, BiomeClassifier.PLAINS));
        fallbacks.put(BiomeClassifier.JUNGLE, ids(BiomeClassifier.FOREST, BiomeClassifier.FOREST_SPARSE));
        fallbacks.put(BiomeClassifier.SWAMP, ids(BiomeClassifier.FOREST, BiomeClassifier.PLAINS));
        fallbacks.put(BiomeClassifier.FOREST, ids(BiomeClassifier.FOREST_SPARSE, BiomeClassifier.PLAINS));
        fallbacks.put(BiomeClassifier.FOREST_SPARSE, ids(BiomeClassifier.FOREST, BiomeClassifier.PLAINS));
        fallbacks.put(BiomeClassifier.TAIGA, ids(BiomeClassifier.TAIGA_SPARSE, BiomeClassifier.FOREST));
        fallbacks.put(BiomeClassifier.TAIGA_SPARSE, ids(BiomeClassifier.TAIGA, BiomeClassifier.PLAINS));
        fallbacks.put(BiomeClassifier.SNOWY_TAIGA, ids(BiomeClassifier.SNOWY_TAIGA_SPARSE, BiomeClassifier.TAIGA));
        fallbacks.put(BiomeClassifier.SNOWY_TAIGA_SPARSE, ids(BiomeClassifier.SNOWY_TAIGA, BiomeClassifier.SNOWY_PLAINS));
        fallbacks.put(BiomeClassifier.WARM_OCEAN, ids(BiomeClassifier.OCEAN, BiomeClassifier.COLD_OCEAN));
        fallbacks.put(BiomeClassifier.OCEAN, ids(BiomeClassifier.COLD_OCEAN, BiomeClassifier.WARM_OCEAN));
        fallbacks.put(BiomeClassifier.COLD_OCEAN, ids(BiomeClassifier.OCEAN, BiomeClassifier.FROZEN_OCEAN));
        fallbacks.put(BiomeClassifier.FROZEN_OCEAN, ids(BiomeClassifier.COLD_OCEAN, BiomeClassifier.OCEAN));
        FALLBACKS = Collections.unmodifiableMap(fallbacks);
    }

    /**
     * Result of applying the config.
     *
     * @param candidates for each disabled base id, biome ids to try in order (the first one that exists in the
     *                   registry is used); base ids that aren't disabled are absent
     * @param warnings   problems to log (unknown names, nothing left to fall back to)
     */
    public record Result(Map<Short, List<String>> candidates, List<String> warnings) {
    }

    private BaseBiomeReplacements() {
    }

    private static short[] ids(short... ids) {
        return ids;
    }

    /**
     * Applies {@code vanilla.disabled_biomes} entries: biome name to the replacement the user chose, or an
     * empty string to pick one automatically. Names may omit the namespace.
     */
    public static Result compute(Map<String, String> disabledEntries) {
        List<String> warnings = new ArrayList<>();
        Map<Short, String> explicit = new LinkedHashMap<>();
        Set<Short> disabled = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, String> entry : disabledEntries.entrySet()) {
            Short id = baseIdOf(entry.getKey());
            if (id == null) {
                unknown.add(entry.getKey());
                continue;
            }
            disabled.add(id);
            if (!entry.getValue().isEmpty()) {
                explicit.put(id, qualify(entry.getValue()));
            }
        }
        if (!unknown.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String biome : BIOMES.values()) {
                names.add(biome.substring(biome.indexOf(':') + 1));
            }
            warnings.add("vanilla.disabled_biomes: " + unknown + " not placed by Terrain Diffusion (ignored). "
                    + "Biomes that can be disabled: " + String.join(", ", names));
        }

        Map<Short, List<String>> candidates = new LinkedHashMap<>();
        for (short id : disabled) {
            List<String> list = new ArrayList<>();
            String chosen = explicit.get(id);
            if (chosen != null) {
                Short chosenBase = baseIdOf(chosen);
                if (chosenBase == null || !disabled.contains(chosenBase)) {
                    list.add(chosen);
                } else {
                    // The chosen replacement is disabled too: continue from its fallbacks.
                    Short next = firstEnabledFallback(chosenBase, disabled);
                    if (next != null) {
                        list.add(BIOMES.get(next));
                    }
                }
            }
            Short auto = firstEnabledFallback(id, disabled);
            if (auto != null && !list.contains(BIOMES.get(auto))) {
                list.add(BIOMES.get(auto));
            }
            if (list.isEmpty()) {
                warnings.add("vanilla.disabled_biomes: no replacement left for " + BIOMES.get(id)
                        + " (its fallbacks are disabled too), so it stays enabled.");
                list.add(BIOMES.get(id));
            }
            candidates.put(id, list);
        }
        return new Result(candidates, warnings);
    }

    /** Nearest base biome reachable through {@link #FALLBACKS} that isn't disabled, breadth first. */
    static Short firstEnabledFallback(short start, Set<Short> disabled) {
        Deque<Short> queue = new ArrayDeque<>();
        Set<Short> visited = new HashSet<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            short current = queue.poll();
            for (short next : FALLBACKS.getOrDefault(current, new short[0])) {
                if (!visited.add(next)) {
                    continue;
                }
                if (!disabled.contains(next)) {
                    return next;
                }
                queue.add(next);
            }
        }
        return null;
    }

    /** Base id for a biome name with or without namespace, or null if the classifier doesn't place it. */
    public static Short baseIdOf(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<Short, String> entry : BIOMES.entrySet()) {
            String full = entry.getValue();
            if (full.equals(normalized) || full.substring(full.indexOf(':') + 1).equals(normalized)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Full biome id: base biomes get their own namespace, other names without one default to minecraft. */
    static String qualify(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        Short base = baseIdOf(normalized);
        if (base != null) {
            return BIOMES.get(base);
        }
        return normalized.contains(":") ? normalized : MINECRAFT + normalized;
    }
}
