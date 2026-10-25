package com.github.xandergos.terraindiffusionmc.world;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;

/**
 * The running server's biome registry, captured when the server starts creating its levels
 * ({@code MinecraftServerMixin}). The biome source's own lookup can only fetch biomes by name; this registry
 * can list every biome and its tags, which {@code biomes.allowed} needs for {@code mod:*} entries and for
 * matching other mods' biomes to the right climate.
 */
public final class ServerBiomeRegistry {
    private static volatile Registry<Biome> biomes;

    private ServerBiomeRegistry() {
    }

    public static void capture(RegistryAccess registryAccess) {
        biomes = registryAccess.registryOrThrow(Registries.BIOME);
    }

    /** The captured registry, or null before the first server has started creating levels. */
    public static Registry<Biome> get() {
        return biomes;
    }
}
