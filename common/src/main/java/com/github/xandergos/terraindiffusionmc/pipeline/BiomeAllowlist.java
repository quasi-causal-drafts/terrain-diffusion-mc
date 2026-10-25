package com.github.xandergos.terraindiffusionmc.pipeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The {@code biomes.allowed} setting: which biomes Terrain Diffusion worlds may use.
 *
 * <p>Entries, separated by commas, semicolons or spaces:
 * <ul>
 *   <li>{@code default}: every biome Terrain Diffusion places out of the box;</li>
 *   <li>{@code namespace:path}: one biome. Vanilla, Terrain Diffusion and Terralith biomes may omit the
 *       namespace (looked up in that order);</li>
 *   <li>{@code namespace:*}: every overworld surface biome from that namespace (see
 *       {@link BiomeNiches#isWildcardEligible});</li>
 *   <li>a leading {@code -} removes the biomes an entry names. Removals apply after everything else.</li>
 * </ul>
 * Without any entry that adds biomes, the list starts from {@code default}.
 *
 * <p>No Minecraft dependencies, so it can be tested on its own.
 */
public final class BiomeAllowlist {
    public static final String DEFAULT = "default";
    private static final String[] BARE_NAME_NAMESPACES = {"minecraft", "terrain-diffusion-mc", "terralith"};

    public enum Kind { DEFAULT, ID, WILDCARD }

    /** One entry. {@code namespace} is null for bare names. */
    public record Entry(boolean exclude, Kind kind, String namespace, String path) {
        @Override
        public String toString() {
            String body = switch (kind) {
                case DEFAULT -> DEFAULT;
                case WILDCARD -> namespace + ":*";
                case ID -> namespace == null ? path : namespace + ":" + path;
            };
            return (exclude ? "-" : "") + body;
        }
    }

    /**
     * @param allowed  biome ids that may be used
     * @param warnings problems to log
     */
    public record Result(Set<String> allowed, List<String> warnings) {
    }

    private BiomeAllowlist() {
    }

    /** Parses the setting. Malformed entries are skipped. */
    public static List<Entry> parse(String raw) {
        List<Entry> entries = new ArrayList<>();
        if (raw == null) {
            return entries;
        }
        for (String token : raw.toLowerCase(Locale.ROOT).split("[,;\\s]+")) {
            String entry = token.trim();
            boolean exclude = entry.startsWith("-") || entry.startsWith("!");
            if (exclude) {
                entry = entry.substring(1).trim();
            }
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.equals(DEFAULT)) {
                entries.add(new Entry(exclude, Kind.DEFAULT, null, null));
                continue;
            }
            int colon = entry.indexOf(':');
            if (colon < 0) {
                entries.add(new Entry(exclude, Kind.ID, null, entry));
            } else if (entry.substring(colon + 1).equals("*")) {
                entries.add(new Entry(exclude, Kind.WILDCARD, entry.substring(0, colon), null));
            } else {
                entries.add(new Entry(exclude, Kind.ID, entry.substring(0, colon), entry.substring(colon + 1)));
            }
        }
        return entries;
    }

    /**
     * Expands entries into biome ids.
     *
     * @param entries      parsed entries
     * @param defaultIds   biomes Terrain Diffusion places out of the box (only registered ones)
     * @param registered   every registered biome, for wildcards and existence checks
     * @param registryComplete whether {@code registered} lists every registered biome; if not, wildcards only
     *                     cover {@code defaultIds} and unknown explicit ids are kept (they may still exist)
     */
    public static Result expand(List<Entry> entries, Set<String> defaultIds,
                                Map<String, BiomeNiches.Descriptor> registered, boolean registryComplete) {
        List<String> warnings = new ArrayList<>();
        Set<String> allowed = new LinkedHashSet<>();
        Set<String> removed = new LinkedHashSet<>();
        boolean anyAddition = entries.stream().anyMatch(e -> !e.exclude());
        if (!anyAddition) {
            allowed.addAll(defaultIds);
        }
        List<String> unknown = new ArrayList<>();
        boolean warnedIncomplete = false;
        for (Entry entry : entries) {
            Collection<String> ids;
            switch (entry.kind()) {
                case DEFAULT -> ids = defaultIds;
                case WILDCARD -> {
                    if (!registryComplete && !warnedIncomplete) {
                        warnings.add("biomes.allowed: the biome registry wasn't available yet, so \""
                                + entry + "\" only covers biomes Terrain Diffusion knows by name");
                        warnedIncomplete = true;
                    }
                    ids = wildcard(entry.namespace(), defaultIds, registered, registryComplete);
                    if (ids.isEmpty() && registryComplete) {
                        warnings.add("biomes.allowed: \"" + entry + "\" matches no biomes (is that mod installed?)");
                    }
                }
                default -> {
                    String id = resolveId(entry.namespace(), entry.path(), defaultIds, registered, registryComplete);
                    if (id == null) {
                        unknown.add(entry.toString().replaceFirst("^-", ""));
                        ids = List.of();
                    } else {
                        ids = List.of(id);
                    }
                }
            }
            (entry.exclude() ? removed : allowed).addAll(ids);
        }
        allowed.removeAll(removed);
        if (!unknown.isEmpty()) {
            warnings.add("biomes.allowed: " + unknown + " not found (misspelled, or the mod isn't installed); ignored");
        }
        return new Result(allowed, warnings);
    }

    private static Collection<String> wildcard(String namespace, Set<String> defaultIds,
                                               Map<String, BiomeNiches.Descriptor> registered, boolean registryComplete) {
        Set<String> ids = new TreeSet<>();
        if (!registryComplete) {
            for (String id : defaultIds) {
                if (id.startsWith(namespace + ":")) {
                    ids.add(id);
                }
            }
            return ids;
        }
        // Mods that tag their overworld biomes get only those; this keeps out End or Nether biomes that
        // aren't tagged as such (Biomes O' Plenty's End biomes, for example).
        boolean namespaceTagsOverworld = registered.values().stream()
                .anyMatch(d -> d.id().startsWith(namespace + ":") && BiomeNiches.isOverworldTagged(d));
        for (BiomeNiches.Descriptor biome : registered.values()) {
            if (biome.id().startsWith(namespace + ":")
                    && BiomeNiches.isWildcardEligible(biome)
                    && (!namespaceTagsOverworld || BiomeNiches.isOverworldTagged(biome))) {
                ids.add(biome.id());
            }
        }
        return ids;
    }

    private static String resolveId(String namespace, String path, Set<String> defaultIds,
                                    Map<String, BiomeNiches.Descriptor> registered, boolean registryComplete) {
        if (namespace != null) {
            String id = namespace + ":" + path;
            return exists(id, defaultIds, registered, registryComplete, true) ? id : null;
        }
        for (String candidateNamespace : BARE_NAME_NAMESPACES) {
            String id = candidateNamespace + ":" + path;
            if (exists(id, defaultIds, registered, registryComplete, false)) {
                return id;
            }
        }
        return null;
    }

    private static boolean exists(String id, Set<String> defaultIds, Map<String, BiomeNiches.Descriptor> registered,
                                  boolean registryComplete, boolean explicitNamespace) {
        if (defaultIds.contains(id) || registered.containsKey(id)) {
            return true;
        }
        // Without the full registry, trust explicitly namespaced ids; the caller checks they really exist.
        return !registryComplete && explicitNamespace;
    }
}
