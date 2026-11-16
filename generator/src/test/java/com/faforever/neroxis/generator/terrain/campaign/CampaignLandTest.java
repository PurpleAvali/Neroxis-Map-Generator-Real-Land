package com.faforever.neroxis.generator.terrain.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignLandTest {
    private static final int SIZE = 257;

    @Test
    void sameSeedGivesSameLand() {
        assertArrayEquals(CampaignLand.generate(7, SIZE), CampaignLand.generate(7, SIZE));
        assertFalse(java.util.Arrays.equals(CampaignLand.generate(7, SIZE), CampaignLand.generate(8, SIZE)));
    }

    /** A 10 km map; cliff widths are in cells, so smaller grids are mostly cliff. */
    private static final int MISSION_SIZE = 513;
    /** A cell steps less than this to each neighbour when a 2x2 footprint stays within the game's 1.0 variation. */
    private static final float BUILDABLE_STEP = .25f;

    @Test
    void landShareAndBuildableGroundMatchCampaignMaps() {
        int size = MISSION_SIZE;
        for (long seed = 1; seed <= 2; seed++) {
            float[] h = CampaignLand.generate(seed, size);
            int land = 0;
            int flat = 0;
            for (int y = 1; y < size - 1; y++) {
                for (int x = 1; x < size - 1; x++) {
                    int i = x + y * size;
                    if (h[i] <= 0) {
                        continue;
                    }
                    land++;
                    float step = StrictMath.max(StrictMath.abs(h[i] - h[i + 1]), StrictMath.abs(h[i] - h[i + size]));
                    if (step < BUILDABLE_STEP) {
                        flat++;
                    }
                }
            }
            float landShare = (float) land / ((size - 2) * (size - 2));
            float flatShare = (float) flat / land;
            assertTrue(landShare > .5f && landShare < .9f, "seed " + seed + " land share " + landShare);
            assertTrue(flatShare > .6f, "seed " + seed + " buildable share of land " + flatShare);
        }
    }

    @Test
    void tiersStepFromTheBeachLine() {
        assertEquals(-1, Terraces.tierOf(Terraces.BEACH, 8));
        assertEquals(0, Terraces.tierOf(Terraces.BEACH + 7.9f, 8));
        assertEquals(1, Terraces.tierOf(Terraces.BEACH + 8.1f, 8));
        assertEquals(Terraces.TIERS, Terraces.tierOf(Terraces.BEACH + 1000, 8));
    }

    @Test
    void talusWidensAOneCellCliff() {
        int size = 32;
        float[] h = new float[size * size];
        for (int i = 0; i < h.length; i++) {
            h[i] = i % size < size / 2 ? 0 : 10;
        }
        Terraces.talus(h, size, Terraces.CLIFF_STEP, 200);
        float steepest = 0;
        for (int i = 0; i < h.length - 1; i++) {
            if (i % size != size - 1) {
                steepest = StrictMath.max(steepest, StrictMath.abs(h[i + 1] - h[i]));
            }
        }
        assertTrue(steepest < Terraces.CLIFF_STEP + .05f, "steepest step " + steepest);
        assertEquals(5f, (h[size / 2 - 1] + h[size / 2]) / 2, .01f, "talus keeps the ground's volume");
    }

    @Test
    void riversCutDeepestWhereFlowGathers() {
        int size = 65;
        float[] h = new float[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // A valley tilted towards the sea at y = 0: sides rise away from the centre line.
                h[x + y * size] = y < 2 ? -1 : y * .5f + StrictMath.abs(x - size / 2) * .3f;
            }
        }
        float[] before = h.clone();
        StreamErosion.erode(h, size, 10, 1f, 1);
        int row = 40 * size;
        float centreCut = before[row + size / 2] - h[row + size / 2];
        float sideCut = before[row + 4] - h[row + 4];
        assertTrue(centreCut > sideCut, "centre cut " + centreCut + " side cut " + sideCut);
        assertEquals(-1, h[size / 2], "the sea is left alone");
    }

    @Test
    void boxBlurKeepsAConstantField() {
        int size = 16;
        float[] v = new float[size * size];
        java.util.Arrays.fill(v, 3f);
        StreamErosion.boxBlur(v, size, 3);
        for (float f : v) {
            assertEquals(3f, f, 1e-5f);
        }
    }
}
