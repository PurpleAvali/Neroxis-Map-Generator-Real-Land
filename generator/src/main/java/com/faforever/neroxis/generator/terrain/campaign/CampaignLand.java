package com.faforever.neroxis.generator.terrain.campaign;

import java.util.SplittableRandom;

/**
 * Campaign-style land as heights in game units relative to sea level: ridge belts with one grain and a ragged coast,
 * cut by river erosion, the lowland stepped into flat tiers with cliff rims and ramps, then rivers and lakes.
 */
public final class CampaignLand {
    private static final int EROSION_PASSES = 24;
    /** Deepest cut of one erosion pass, in game units, where flow times slope is highest. */
    private static final float EROSION_DEPTH_PER_PASS = 1.6f;
    /** Half-width in cells over which a river's cut spreads into a valley. */
    private static final int VALLEY_RADIUS = 4;

    private CampaignLand() {}

    /**
     * Grids of {@code size} x {@code size}, row-major (index x + y * size): {@code height} in game units relative
     * to sea level, and {@code flow}, the share of all cells that drain through each cell over the eroded land
     * before terracing (valley floors and riverbeds).
     */
    public record Land(float[] height, float[] flow) {}

    /** The same seed gives the same land. */
    public static Land generate(long seed, int size) {
        SplittableRandom random = new SplittableRandom(seed);
        float[] height = Landform.generate(random.nextLong(), size);
        StreamErosion.erode(height, size, EROSION_PASSES, EROSION_DEPTH_PER_PASS, VALLEY_RADIUS);
        float[] flow = StreamErosion.flow(height, size);
        Terraces.apply(height, size, random.nextLong());
        Water.carve(height, size, random.nextLong());
        return new Land(height, flow);
    }
}
