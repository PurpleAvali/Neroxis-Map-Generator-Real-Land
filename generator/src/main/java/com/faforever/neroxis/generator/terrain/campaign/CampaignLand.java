package com.faforever.neroxis.generator.terrain.campaign;

import java.util.SplittableRandom;

/**
 * Campaign-style land as heights in game units relative to sea level: ridge belts with one grain and a ragged coast,
 * cut by river erosion, then the lowland stepped into flat tiers with cliff rims and ramps.
 */
public final class CampaignLand {
    private static final int EROSION_PASSES = 24;
    /** Deepest cut of one erosion pass, in game units, where flow times slope is highest. */
    private static final float EROSION_DEPTH_PER_PASS = 1.6f;
    /** Half-width in cells over which a river's cut spreads into a valley. */
    private static final int VALLEY_RADIUS = 4;

    private CampaignLand() {}

    /** A {@code size} x {@code size} grid, row-major (index x + y * size); the same seed gives the same grid. */
    public static float[] generate(long seed, int size) {
        SplittableRandom random = new SplittableRandom(seed);
        float[] height = Landform.generate(random.nextLong(), size);
        StreamErosion.erode(height, size, EROSION_PASSES, EROSION_DEPTH_PER_PASS, VALLEY_RADIUS);
        Terraces.apply(height, size, random.nextLong());
        return height;
    }
}
