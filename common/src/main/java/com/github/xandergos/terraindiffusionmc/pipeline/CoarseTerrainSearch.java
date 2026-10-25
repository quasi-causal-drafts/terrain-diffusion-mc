package com.github.xandergos.terraindiffusionmc.pipeline;

import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;

/**
 * Searches the coarse map for biomes and terrain features without running the full terrain model.
 *
 * <p>One coarse cell covers {@code 32 * latentCompression} native pixels per side (256 blocks per side at
 * scale 1, 512 at scale 2, ...). Each cell carries the mean elevation, the 5th-percentile elevation and
 * the climate the model was conditioned on, which is enough to say what a region is like but not where
 * exactly inside the cell a feature sits. Results are therefore reported as cell centres.
 *
 * <p>Steep biomes (stony and frozen peaks) only exist on slopes far steeper than anything visible at
 * coarse resolution. A cell whose mean is well above its 5th percentile has real relief inside it, so for
 * such cells the biome rules are also evaluated for its upper, steep ground.
 *
 * <p>This class has no Minecraft dependencies so it can be tested on its own.
 */
public final class CoarseTerrainSearch {

    /** Fetches a coarse slice with shape [7, ci1-ci0, cj1-cj0]; channel 6 is the blend weight. */
    @FunctionalInterface
    public interface CoarseSource {
        FloatTensor fetch(int ci0, int cj0, int ci1, int cj1) throws Exception;
    }

    @FunctionalInterface
    public interface BiomeIdMatcher {
        /** Whether the classifier biome at block ({@code blockX}, {@code blockZ}) is wanted. */
        boolean test(short biomeId, int blockX, int blockZ);
    }

    /**
     * Terrain features {@code /td-locate} can search for.
     */
    public enum Feature {
        /** High or rugged land: mean elevation of at least 2000 m, or at least 600 m of relief. */
        MOUNTAINS,
        /** The cell with the highest mean elevation within the radius. */
        HIGHEST,
        /** Low, flat land: fully above sea level, mean below 300 m and less than 60 m of relief. */
        FLATLANDS,
        /** Cells whose mean elevation is below sea level. */
        OCEAN,
        /** Cells fully above sea level (their 5th percentile is above sea level). */
        LAND
    }

    /**
     * A matching coarse cell.
     *
     * @param blockX     X of the cell centre
     * @param blockZ     Z of the cell centre
     * @param elevationM elevation (m) the match refers to: the cell mean, or the upper estimate for steep matches
     * @param meanM      mean elevation of the cell (m)
     * @param reliefM    mean minus 5th-percentile elevation (m, low end capped at sea level on land), a measure
     *                   of how rugged the cell is
     * @param biomeId    matched biome id (biome searches), or the cell's dominant biome otherwise
     * @param cellBlocks side length of one coarse cell in blocks
     */
    public record Hit(int blockX, int blockZ, float elevationM, float meanM, float reliefM,
                      short biomeId, int cellBlocks) {
    }

    /** Relief above which a cell is assumed to contain steep ground. */
    static final float STEEP_RELIEF_M = 300f;
    /** Slopes used for steep ground: bare but below the "too steep for snow" cutoff, and clearly bare. */
    static final float[] STEEP_SLOPES = {0.75f, 1.25f};
    /** Standard atmospheric lapse rate (degrees C per metre), used to estimate temperature on higher ground. */
    static final float LAPSE_RATE = -0.0065f;

    static final float MOUNTAIN_MEAN_M = 2000f;
    static final float MOUNTAIN_RELIEF_M = 600f;
    static final float FLAT_MAX_MEAN_M = 300f;
    static final float FLAT_MAX_RELIEF_M = 60f;

    /** Largest search radius, in coarse cells, to keep queries bounded. */
    static final int MAX_RADIUS_CELLS = 96;

    private static final int CHANNELS = 7;

    private final int scale;
    private final CoarseSource source;
    private final int nativePerCell;
    private final float cellMeters;

    public CoarseTerrainSearch(int scale, CoarseSource source, int nativePerCell, float nativeResolutionM) {
        this.scale = Math.max(1, scale);
        this.source = source;
        this.nativePerCell = nativePerCell;
        this.cellMeters = nativePerCell * nativeResolutionM;
    }

    /** Search over the loaded world's coarse map at its current scale. */
    public static CoarseTerrainSearch forCurrentWorld() {
        return new CoarseTerrainSearch(
                WorldScaleManager.getCurrentScale(),
                LocalTerrainProvider::getPipelineCoarse,
                32 * WorldPipelineModelConfig.latentCompression(),
                WorldPipelineModelConfig.nativeResolution());
    }

    public int cellBlocks() {
        return nativePerCell * scale;
    }

    /** Nearest cell (to {@code blockX, blockZ}, within {@code radiusBlocks}) that may contain a matching biome. */
    public Hit findBiome(int blockX, int blockZ, int radiusBlocks, BiomeIdMatcher matcher) throws Exception {
        Grid grid = load(blockX, blockZ, radiusBlocks);
        Hit best = null;
        double bestDist = Double.MAX_VALUE;
        for (int r = 1; r < grid.H - 1; r++) {
            for (int c = 1; c < grid.W - 1; c++) {
                double dist = grid.distance(r, c, blockX, blockZ);
                if (dist > radiusBlocks || dist >= bestDist) continue;

                Hit hit = matchBiome(grid, r, c, matcher);
                if (hit != null) {
                    best = hit;
                    bestDist = dist;
                }
            }
        }
        return best;
    }

    /** Nearest cell with the given feature, or for {@link Feature#HIGHEST} the highest cell, within the radius. */
    public Hit findFeature(int blockX, int blockZ, int radiusBlocks, Feature feature) throws Exception {
        Grid grid = load(blockX, blockZ, radiusBlocks);
        Hit best = null;
        double bestDist = Double.MAX_VALUE;
        float bestElev = -Float.MAX_VALUE;
        for (int r = 1; r < grid.H - 1; r++) {
            for (int c = 1; c < grid.W - 1; c++) {
                double dist = grid.distance(r, c, blockX, blockZ);
                if (dist > radiusBlocks) continue;
                int px = r * grid.W + c;
                float mean = grid.mean[px];
                float relief = grid.relief(px);

                if (feature == Feature.HIGHEST) {
                    if (mean > bestElev || (mean == bestElev && dist < bestDist)) {
                        bestElev = mean;
                        bestDist = dist;
                        best = grid.hit(r, c, mean, dominantBiome(grid, r, c));
                    }
                    continue;
                }
                if (dist >= bestDist || !hasFeature(feature, mean, grid.p5[px], relief)) continue;
                best = grid.hit(r, c, mean, dominantBiome(grid, r, c));
                bestDist = dist;
            }
        }
        return best;
    }

    static boolean hasFeature(Feature feature, float mean, float p5, float relief) {
        switch (feature) {
            case MOUNTAINS:
                return mean > 0f && (mean >= MOUNTAIN_MEAN_M || relief >= MOUNTAIN_RELIEF_M);
            case FLATLANDS:
                return p5 > 0f && mean < FLAT_MAX_MEAN_M && relief < FLAT_MAX_RELIEF_M;
            case OCEAN:
                return mean < 0f;
            case LAND:
                return p5 > 0f;
            default:
                return false;
        }
    }

    private Hit matchBiome(Grid grid, int r, int c, BiomeIdMatcher matcher) {
        int px = r * grid.W + c;
        short dominant = dominantBiome(grid, r, c);
        if (matcher.test(dominant, grid.centerBlockX(c), grid.centerBlockZ(r))) {
            return grid.hit(r, c, grid.mean[px], dominant);
        }

        float relief = grid.relief(px);
        if (grid.mean[px] <= 0f || relief < STEEP_RELIEF_M) {
            return null;
        }
        // Upper, steep ground of a rugged cell: as far above the mean as the 5th percentile is below it.
        float highM = grid.mean[px] + relief;
        float highTemp = grid.temp[px] + LAPSE_RATE * relief;
        for (float slope : STEEP_SLOPES) {
            short steep = BiomeClassifier.classifyAt(highM, slope, highTemp, grid.tStd[px], grid.precip[px],
                    grid.pCV[px], grid.centerBlockZ(r), grid.centerBlockX(c));
            if (matcher.test(steep, grid.centerBlockX(c), grid.centerBlockZ(r))) {
                return grid.hit(r, c, highM, steep);
            }
        }
        return null;
    }

    /** Biome for the cell as a whole, with the (gentle) slope visible at coarse resolution. */
    private short dominantBiome(Grid grid, int r, int c) {
        int px = r * grid.W + c;
        return BiomeClassifier.classifyAt(grid.mean[px], grid.slope(r, c, cellMeters), grid.temp[px],
                grid.tStd[px], grid.precip[px], grid.pCV[px], grid.centerBlockZ(r), grid.centerBlockX(c));
    }

    /** Loads the coarse cells covering the search circle, plus a one-cell border for slope estimates. */
    private Grid load(int blockX, int blockZ, int radiusBlocks) throws Exception {
        int cellBlocks = cellBlocks();
        int radiusCells = Math.min(MAX_RADIUS_CELLS, Math.max(1, -Math.floorDiv(-radiusBlocks, cellBlocks)));
        int centerI = Math.floorDiv(blockZ, cellBlocks);
        int centerJ = Math.floorDiv(blockX, cellBlocks);
        int ci0 = centerI - radiusCells - 1;
        int cj0 = centerJ - radiusCells - 1;
        int ci1 = centerI + radiusCells + 2;
        int cj1 = centerJ + radiusCells + 2;

        FloatTensor slice = source.fetch(ci0, cj0, ci1, cj1);
        return new Grid(slice, ci0, cj0, ci1 - ci0, cj1 - cj0, cellBlocks);
    }

    /** Coarse cells in real units. Rows are Z, columns are X. */
    private static final class Grid {
        final int ci0, cj0, H, W, cellBlocks;
        final float[] mean, p5, temp, tStd, precip, pCV;

        Grid(FloatTensor slice, int ci0, int cj0, int H, int W, int cellBlocks) {
            int n = H * W;
            if (slice.data.length < CHANNELS * n) {
                throw new IllegalStateException("Coarse slice too small: " + slice.data.length + " < " + CHANNELS * n);
            }
            this.ci0 = ci0;
            this.cj0 = cj0;
            this.H = H;
            this.W = W;
            this.cellBlocks = cellBlocks;
            mean = new float[n];
            p5 = new float[n];
            temp = new float[n];
            tStd = new float[n];
            precip = new float[n];
            pCV = new float[n];
            for (int px = 0; px < n; px++) {
                float w = slice.data[6 * n + px];
                if (w <= 1e-8f) {
                    continue;
                }
                mean[px] = signedSquare(slice.data[px] / w);
                p5[px] = signedSquare(slice.data[n + px] / w);
                temp[px] = slice.data[2 * n + px] / w;
                tStd[px] = slice.data[3 * n + px] / w;
                precip[px] = slice.data[4 * n + px] / w;
                pCV[px] = slice.data[5 * n + px] / w;
            }
        }

        private static float signedSquare(float v) {
            return Math.signum(v) * v * v;
        }

        /**
         * Mean minus 5th percentile. For land cells the low end is capped at sea level, so a coastline (whose
         * 5th percentile is ocean floor) does not look like rugged terrain.
         */
        float relief(int px) {
            float low = mean[px] > 0f ? Math.max(0f, p5[px]) : p5[px];
            return Math.max(0f, mean[px] - low);
        }

        /** Sobel slope of the mean elevation, matching BiomeClassifier's slope ratio. */
        float slope(int r, int c, float cellMeters) {
            float dx = 0f, dy = 0f;
            for (int kr = -1; kr <= 1; kr++) {
                for (int kc = -1; kc <= 1; kc++) {
                    float v = Math.max(0f, mean[(r + kr) * W + (c + kc)]);
                    dx += v * kc * (kr == 0 ? 2 : 1);
                    dy += v * kr * (kc == 0 ? 2 : 1);
                }
            }
            dx /= 8f;
            dy /= 8f;
            return (float) Math.sqrt(dx * dx + dy * dy) / cellMeters;
        }

        int centerBlockX(int c) {
            return (cj0 + c) * cellBlocks + cellBlocks / 2;
        }

        int centerBlockZ(int r) {
            return (ci0 + r) * cellBlocks + cellBlocks / 2;
        }

        double distance(int r, int c, int blockX, int blockZ) {
            double dx = centerBlockX(c) - blockX;
            double dz = centerBlockZ(r) - blockZ;
            return Math.sqrt(dx * dx + dz * dz);
        }

        Hit hit(int r, int c, float elevationM, short biomeId) {
            int px = r * W + c;
            return new Hit(centerBlockX(c), centerBlockZ(r), elevationM, mean[px], relief(px), biomeId, cellBlocks);
        }
    }
}
