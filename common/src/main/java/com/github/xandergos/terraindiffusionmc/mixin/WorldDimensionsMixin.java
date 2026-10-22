package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionBiomeSource;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;

/**
 * Keeps the Terrain Diffusion overworld when a data pack overrides {@code minecraft:overworld}.
 *
 * <p>In 1.20.x, dimension files from data packs win over the selected world type. Terralith 2.5.x (the
 * last version for 1.20.1) ships such a file, so with it installed a Terrain Diffusion world would
 * silently generate as a Terralith world. {@code bake} merges the data pack dimensions (its argument) with
 * the world type's or save's own dimensions ({@code this}); when the latter's overworld is Terrain
 * Diffusion, the data pack's overworld is dropped before the merge. Other dimensions and other world
 * types are untouched.
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {
    @Unique
    private static final Logger TERRAIN_DIFFUSION_MC$LOG = LoggerFactory.getLogger("terrain-diffusion-mc");

    @ModifyVariable(method = "bake", at = @At("HEAD"), argsOnly = true)
    private Registry<LevelStem> terrainDiffusionMc$keepTerrainDiffusionOverworld(Registry<LevelStem> dataPackStems) {
        WorldDimensions self = (WorldDimensions) (Object) this;
        LevelStem selectedOverworld = self.get(LevelStem.OVERWORLD).orElse(null);
        if (selectedOverworld == null
                || !(selectedOverworld.generator().getBiomeSource() instanceof TerrainDiffusionBiomeSource)
                || dataPackStems.get(LevelStem.OVERWORLD) == null) {
            return dataPackStems;
        }

        MappedRegistry<LevelStem> filtered = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable(), false);
        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : dataPackStems.entrySet()) {
            if (!entry.getKey().equals(LevelStem.OVERWORLD)) {
                Registry.register(filtered, entry.getKey(), entry.getValue());
            }
        }
        TERRAIN_DIFFUSION_MC$LOG.info("A data pack overrides minecraft:overworld (for example Terralith); "
                + "keeping the Terrain Diffusion overworld for this world.");
        return filtered;
    }
}
