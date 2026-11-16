package com.faforever.neroxis.generator.terrain.campaign;

import java.util.SplittableRandom;

/**
 * Turns eroded lowland into flat tiers with cliff rims, as on GPG's campaign maps, and leaves ramps where the eroded
 * ground runs between tiers. The cliff shaping is a talus pass: steps above the repose are split between the two
 * cells, after dandrino/terrain-erosion-3-ways simulation.py {@code apply_slippage}.
 */
final class Terraces {
    /** Ground below this height (game units above sea) stays as eroded: beaches and shallows. */
    static final float BEACH = 2f;
    static final int TIERS = 3;
    private static final float MIN_STEP = 8f;
    private static final float MAX_STEP = 11f;
    /** Steepest cliff step per cell after shaping; GPG cliffs top out near 1.8. */
    static final float CLIFF_STEP = 1.7f;
    private static final int TALUS_PASSES = 40;
    /** How far from a tier edge, in cells, a ramp reaches into the flats. */
    private static final float RAMP_REACH = 14f;
    /** Ramp noise above this level opens a ramp; it covers roughly a fifth of the tier edges. */
    private static final float RAMP_LEVEL = .35f;

    /** Tier edges wander up and down by this share of a step, so rims are ragged instead of contour lines. */
    private static final float RIM_JITTER = .3f;

    /** Cells stepping more than this to a neighbour count as cliff and get smoothed; flats stay exact. */
    static final float CLIFF_MIN_STEP = .3f;
    private static final int CLIFF_SMOOTH_PASSES = 3;

    private Terraces() {}

    static void apply(float[] height, int size, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        float step = (float) (MIN_STEP + random.nextDouble() * (MAX_STEP - MIN_STEP));
        FastNoiseLite rampNoise = new FastNoiseLite(random.nextInt());
        rampNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        rampNoise.SetFrequency(9f);
        FastNoiseLite rimNoise = new FastNoiseLite(random.nextInt());
        rimNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        rimNoise.SetFrequency(7f);
        rimNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        rimNoise.SetFractalOctaves(3);

        int[] tier = new int[height.length];
        for (int i = 0; i < height.length; i++) {
            float u = (i % size) / Landform.NOISE_SPAN;
            float v = (i / size) / Landform.NOISE_SPAN;
            float jitter = RIM_JITTER * step * rimNoise.GetNoise(u, v);
            tier[i] = height[i] <= BEACH ? -1 : tierOf(height[i] + jitter, step);
        }
        float[] edgeDistance = distanceToTierEdge(tier, size);
        for (int i = 0; i < height.length; i++) {
            if (tier[i] < 0 || tier[i] >= TIERS) {
                continue;
            }
            float flat = BEACH + tier[i] * step;
            float u = (i % size) / Landform.NOISE_SPAN;
            float v = (i / size) / Landform.NOISE_SPAN;
            float ramp = Landform.smoothstep(RAMP_LEVEL, RAMP_LEVEL + .2f, rampNoise.GetNoise(u, v));
            float reach = StrictMath.max(0, 1 - edgeDistance[i] / RAMP_REACH);
            height[i] = flat + (height[i] - flat) * ramp * reach;
        }
        talus(height, size, CLIFF_STEP, TALUS_PASSES);
        smoothCliffs(height, size, CLIFF_MIN_STEP, CLIFF_SMOOTH_PASSES);
    }

    /** -1 below the beach line, 0..TIERS-1 for a tier, TIERS above the top tier (ridges keep their shape). */
    static int tierOf(float h, float step) {
        if (h <= BEACH) {
            return -1;
        }
        return StrictMath.min(TIERS, (int) ((h - BEACH) / step));
    }

    /** Chamfer distance in cells from every cell to the nearest cell whose 4-neighbour is on another tier. */
    private static float[] distanceToTierEdge(int[] tier, int size) {
        float far = 2f * size;
        float[] d = new float[tier.length];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = x + y * size;
                int t = tier[i];
                boolean edge = (x > 0 && tier[i - 1] != t) || (x < size - 1 && tier[i + 1] != t)
                               || (y > 0 && tier[i - size] != t) || (y < size - 1 && tier[i + size] != t);
                d[i] = edge ? 0 : far;
            }
        }
        float diag = 1.4142135f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = x + y * size;
                if (x > 0) d[i] = StrictMath.min(d[i], d[i - 1] + 1);
                if (y > 0) d[i] = StrictMath.min(d[i], d[i - size] + 1);
                if (x > 0 && y > 0) d[i] = StrictMath.min(d[i], d[i - size - 1] + diag);
                if (x < size - 1 && y > 0) d[i] = StrictMath.min(d[i], d[i - size + 1] + diag);
            }
        }
        for (int y = size - 1; y >= 0; y--) {
            for (int x = size - 1; x >= 0; x--) {
                int i = x + y * size;
                if (x < size - 1) d[i] = StrictMath.min(d[i], d[i + 1] + 1);
                if (y < size - 1) d[i] = StrictMath.min(d[i], d[i + size] + 1);
                if (x < size - 1 && y < size - 1) d[i] = StrictMath.min(d[i], d[i + size + 1] + diag);
                if (x > 0 && y < size - 1) d[i] = StrictMath.min(d[i], d[i + size - 1] + diag);
            }
        }
        return d;
    }

    /** Splits every 4-neighbour step steeper than {@code repose} between its two cells, {@code passes} times. */
    static void talus(float[] height, int size, float repose, int passes) {
        float[] delta = new float[height.length];
        for (int pass = 0; pass < passes; pass++) {
            java.util.Arrays.fill(delta, 0);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int i = x + y * size;
                    if (x < size - 1) settle(height, delta, i, i + 1, repose);
                    if (y < size - 1) settle(height, delta, i, i + size, repose);
                }
            }
            for (int i = 0; i < height.length; i++) {
                height[i] += delta[i];
            }
        }
    }

    private static void settle(float[] height, float[] delta, int a, int b, float repose) {
        float diff = height[a] - height[b];
        float excess = StrictMath.abs(diff) - repose;
        if (excess <= 0) {
            return;
        }
        // A cell can settle against four neighbours in one pass; a sixth of the excess each keeps it from overshooting.
        float move = StrictMath.copySign(excess / 6, diff);
        delta[a] -= move;
        delta[b] += move;
    }

    /**
     * Averages each cliff cell with its 3x3 neighbourhood, {@code passes} times. The talus pass works along the grid
     * axes, so diagonal rims come out as stairs; this rounds them without touching the flats.
     */
    static void smoothCliffs(float[] height, int size, float minStep, int passes) {
        float[] next = new float[height.length];
        for (int pass = 0; pass < passes; pass++) {
            System.arraycopy(height, 0, next, 0, height.length);
            for (int y = 1; y < size - 1; y++) {
                for (int x = 1; x < size - 1; x++) {
                    int i = x + y * size;
                    float h = height[i];
                    float step = StrictMath.max(StrictMath.max(StrictMath.abs(h - height[i - 1]),
                                                               StrictMath.abs(h - height[i + 1])),
                                                StrictMath.max(StrictMath.abs(h - height[i - size]),
                                                               StrictMath.abs(h - height[i + size])));
                    if (step <= minStep) {
                        continue;
                    }
                    float sum = 0;
                    for (int dy = -size; dy <= size; dy += size) {
                        sum += height[i + dy - 1] + height[i + dy] + height[i + dy + 1];
                    }
                    next[i] = sum / 9;
                }
            }
            System.arraycopy(next, 0, height, 0, height.length);
        }
    }
}
