package com.faforever.neroxis.generator.terrain.campaign;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * The uneroded land: a coastline ragged at every scale and ridge belts that share one grain, in game height units
 * relative to sea level (negative is sea). Ridged noise stretched along the grain and domain warped follows
 * dandrino/terrain-erosion-3-ways ridge_noise.py and domain_warping.py; noise is FastNoiseLite.
 */
final class Landform {
    static final float SEA_FLOOR = -14f;
    /** Sea depth per unit of coast noise below sea level; 0.15 below reaches the floor. */
    private static final float SEA_DEPTH_PER_NOISE = 95f;
    /** Land rises this much from the coast to the far inland, before ridges. */
    static final float INLAND_RISE = 18f;
    /** Height of the highest ridge crest above the land under it. */
    static final float RIDGE_HEIGHT = 46f;
    private static final float MIN_LAND_FRACTION = .58f;
    private static final float MAX_LAND_FRACTION = .80f;
    /** Ridges are this many times longer along the grain than across it. */
    private static final float GRAIN_STRETCH = 3.2f;
    /** Share of the land under ridge belts; GPG maps keep most land open. */
    private static final float BELT_COVER = .42f;

    /** Cells per unit of noise: one unit spans a 20 km map, so landforms keep their size on any map. */
    static final float NOISE_SPAN = 1024f;

    private Landform() {}

    static float[] generate(long seed, int size) {
        SplittableRandom random = new SplittableRandom(seed);
        float grain = (float) (random.nextDouble() * StrictMath.PI);
        float landFraction = MIN_LAND_FRACTION + (float) random.nextDouble() * (MAX_LAND_FRACTION - MIN_LAND_FRACTION);

        FastNoiseLite warp = noise(random.nextInt(), 1.1f, FastNoiseLite.FractalType.DomainWarpProgressive, 3);
        warp.SetDomainWarpType(FastNoiseLite.DomainWarpType.OpenSimplex2);
        warp.SetDomainWarpAmp(.22f);
        FastNoiseLite coast = noise(random.nextInt(), 1.6f, FastNoiseLite.FractalType.FBm, 6);
        FastNoiseLite belts = noise(random.nextInt(), 1.3f, FastNoiseLite.FractalType.FBm, 2);
        FastNoiseLite ridges = noise(random.nextInt(), 2.4f, FastNoiseLite.FractalType.Ridged, 5);

        float cos = (float) StrictMath.cos(grain);
        float sin = (float) StrictMath.sin(grain);
        float[] land = new float[size * size];
        float[] ridge = new float[size * size];
        float[] belt = new float[size * size];
        FastNoiseLite.Vector2 p = new FastNoiseLite.Vector2(0, 0);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = x + y * size;
                p.x = x / NOISE_SPAN;
                p.y = y / NOISE_SPAN;
                warp.DomainWarp(p);
                land[i] = coast.GetNoise(p.x, p.y);
                belt[i] = belts.GetNoise(p.x, p.y);
                float along = p.x * cos + p.y * sin;
                float across = -p.x * sin + p.y * cos;
                ridge[i] = ridges.GetNoise(along / GRAIN_STRETCH, across);
            }
        }

        float seaLevel = quantile(land, 1 - landFraction);
        float landTop = quantile(land, .995f);
        float beltLevel = quantile(belt, 1 - BELT_COVER);
        float[] height = new float[size * size];
        for (int i = 0; i < height.length; i++) {
            float above = land[i] - seaLevel;
            if (above <= 0) {
                height[i] = StrictMath.max(SEA_FLOOR, above * SEA_DEPTH_PER_NOISE);
                continue;
            }
            float inland = StrictMath.min(1, above / (landTop - seaLevel));
            float beltWeight = smoothstep(beltLevel - .15f, beltLevel + .1f, belt[i]) * smoothstep(0, .25f, inland);
            float crest = (ridge[i] + 1) / 2;
            height[i] = inland * INLAND_RISE + beltWeight * crest * crest * RIDGE_HEIGHT;
        }
        return height;
    }

    private static FastNoiseLite noise(int seed, float frequency, FastNoiseLite.FractalType fractal, int octaves) {
        FastNoiseLite noise = new FastNoiseLite(seed);
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(frequency);
        noise.SetFractalType(fractal);
        noise.SetFractalOctaves(octaves);
        return noise;
    }

    static float quantile(float[] values, float q) {
        float[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[StrictMath.min(sorted.length - 1, (int) (q * sorted.length))];
    }

    static float smoothstep(float edge0, float edge1, float x) {
        float t = StrictMath.max(0, StrictMath.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }
}
