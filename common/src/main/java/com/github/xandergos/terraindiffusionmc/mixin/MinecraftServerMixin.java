package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.TerrainDiffusionLifecycle;
import com.github.xandergos.terraindiffusionmc.pipeline.SpawnSelector;
import com.github.xandergos.terraindiffusionmc.world.ServerBiomeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void overrideWorldSpawn(ServerLevel world, ServerLevelData worldProperties,
                                           boolean bonusChest, boolean debugWorld,
                                           CallbackInfo ci) {
        // Only Terrain Diffusion overworlds: in other world types the coarse map says nothing about the terrain.
        if (!TerrainDiffusionLifecycle.isTerrainDiffusionOverworld(world)) {
            return;
        }

        BlockPos spawnPos = SpawnSelector.findSpawnBlockPos();
        worldProperties.setSpawn(spawnPos, 0f);
        ci.cancel();
    }

    /**
     * Captures the biome registry before any level (and so any biome source) is created, so
     * {@code biomes.allowed} can list every biome and its tags.
     */
    @Inject(method = "createLevels", at = @At("HEAD"))
    private void terrainDiffusionMc$captureBiomeRegistry(ChunkProgressListener listener, CallbackInfo ci) {
        ServerBiomeRegistry.capture(((MinecraftServer) (Object) this).registryAccess());
    }
}
