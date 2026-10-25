package com.github.xandergos.terraindiffusionmc.pipeline;

/**
 * Splits an area between several biomes in large, coherent regions (roughly 700 blocks across), so biomes
 * that share a classifier niche alternate region by region instead of block by block.
 *
 * <p>Deterministic: depends only on the coordinates and the salt, never on world state, so the same place
 * always gets the same biome. No Minecraft dependencies.
 */
public final class RegionPicker {
    private static final float WARP_AMPLITUDE = 90f;
    private static final FastNoiseLite CELLS = makeCells();
    private static final FastNoiseLite WARP = makeWarp();

    private RegionPicker() {
    }

    private static FastNoiseLite makeCells() {
        FastNoiseLite noise = new FastNoiseLite(6143);
        noise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        noise.SetFrequency(1f / 700f);
        noise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Hybrid);
        noise.SetCellularReturnType(FastNoiseLite.CellularReturnType.CellValue);
        noise.SetCellularJitter(1.0f);
        return noise;
    }

    private static FastNoiseLite makeWarp() {
        FastNoiseLite noise = new FastNoiseLite(2719);
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(1f / 220f);
        return noise;
    }

    /**
     * Index in {@code [0, count)} for the region containing block ({@code x}, {@code z}). Different salts give
     * independent region layouts, so pools for different niches don't line up.
     */
    public static int pick(int count, int salt, float x, float z) {
        if (count <= 1) {
            return 0;
        }
        int index = (int) (unit(salt, x, z) * count);
        return Math.max(0, Math.min(count - 1, index));
    }

    /**
     * Like {@link #pick}, but entry {@code i} gets a share of the area proportional to its weight.
     * {@code cumulative[i]} is the sum of the weights of entries {@code 0..i}.
     */
    public static int pickWeighted(float[] cumulative, int salt, float x, float z) {
        int count = cumulative.length;
        if (count <= 1) {
            return 0;
        }
        float target = unit(salt, x, z) * cumulative[count - 1];
        for (int i = 0; i < count - 1; i++) {
            if (target < cumulative[i]) {
                return i;
            }
        }
        return count - 1;
    }

    /** Region value in [0, 1) for the block, constant within a region. */
    private static float unit(int salt, float x, float z) {
        float sx = x + 3917f * salt;
        float sz = z - 2111f * salt;
        float wx = sx + WARP_AMPLITUDE * WARP.GetNoise(sx, sz);
        float wz = sz + WARP_AMPLITUDE * WARP.GetNoise(sx + 1000f, sz - 1000f);
        return Math.max(0f, Math.min(0.99999f, (CELLS.GetNoise(wx, wz) + 1f) * 0.5f));
    }
}
