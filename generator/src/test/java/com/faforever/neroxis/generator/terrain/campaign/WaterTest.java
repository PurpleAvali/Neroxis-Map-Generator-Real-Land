package com.faforever.neroxis.generator.terrain.campaign;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterTest {
    private static final int SIZE = 400;

    /** Lowland at the beach line with the sea along the left edge and a tier plateau on the right. */
    private static float[] lowland() {
        float[] h = new float[SIZE * SIZE];
        for (int i = 0; i < h.length; i++) {
            int x = i % SIZE;
            h[i] = x < 10 ? -5 : x > 330 ? 20 : Terraces.BEACH;
        }
        return h;
    }

    @Test
    void lakesAreDugInlandAndNeverIntoTiers() {
        float[] h = lowland();
        Water.carve(h, SIZE, 3);
        int[] seaDistance = Water.distanceToSea(lowland(), SIZE);
        boolean inlandWater = false;
        for (int i = 0; i < h.length; i++) {
            if (h[i] < 0 && i % SIZE >= 10 && seaDistance[i] >= Water.LAKE_INLAND / 2) {
                inlandWater = true;
            }
            if (i % SIZE > 330) {
                assertEquals(20f, h[i], "water cut into the tier");
            }
        }
        assertTrue(inlandWater, "no inland water");
    }

    @Test
    void aRiverRunsFromItsStartToTheSeaOverLowland() {
        float[] h = lowland();
        int[] seaDistance = Water.distanceToSea(h, SIZE);
        FastNoiseLite meander = new FastNoiseLite(1);
        int start = 250 + 200 * SIZE;
        int[] path = Water.riverPath(h, SIZE, seaDistance, start, meander);
        assertTrue(path.length > 200, "path " + path.length);
        assertEquals(0, seaDistance[path[0]], "the river does not reach the sea");
        assertEquals(start, path[path.length - 1]);
        assertTrue(Arrays.stream(path).allMatch(c -> h[c] <= Water.LOWLAND_TOP), "the river climbs a tier");
    }

    @Test
    void noRiverWhenLowlandNeverReachesTheSea() {
        float[] h = new float[SIZE * SIZE];
        Arrays.fill(h, 20);
        h[200 + 200 * SIZE] = Terraces.BEACH;
        int[] seaDistance = Water.distanceToSea(h, SIZE);
        assertEquals(0, Water.riverPath(h, SIZE, seaDistance, 200 + 200 * SIZE, new FastNoiseLite(1)).length);
    }
}
