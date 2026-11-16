package com.faforever.neroxis.generator.terrain.campaign;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SitesTest {
    /** The game refuses land units across a cell edge that rises more than this. */
    private static final float WALKABLE_STEP = .75f;

    private static final SitePlan MISSION_2 = SitePlan.parse(
            List.of("gate:GATE:0.22,0.80,40", "city:CITY:0.45,0.58,55", "array:ARRAY:0.78,0.22,38",
                    "seraph:BASE:0.85,0.10,35"), List.of("gate-city", "city-array", "array-seraph"));

    /**
     * Grid cells (the squares between height points, indexed by their top-left point) reachable from the cell at
     * {@code start}, by the game's rule (OpenSupCom pathfinding_grid): a cell is passable when it is dry and none of
     * its four edges rises more than WALKABLE_STEP.
     */
    static boolean[] walkable(float[] h, int size, int start) {
        boolean[] passable = new boolean[h.length];
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                int i = x + y * size;
                float a = h[i];
                float b = h[i + 1];
                float c = h[i + size];
                float d = h[i + size + 1];
                float steepest = StrictMath.max(StrictMath.max(StrictMath.abs(a - b), StrictMath.abs(c - d)),
                                                StrictMath.max(StrictMath.abs(a - c), StrictMath.abs(b - d)));
                passable[i] = StrictMath.min(StrictMath.min(a, b), StrictMath.min(c, d)) > 0
                              && steepest <= WALKABLE_STEP;
            }
        }
        boolean[] seen = new boolean[h.length];
        int[] queue = new int[h.length];
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        seen[start] = passable[start];
        while (head < tail && seen[start]) {
            int c = queue[head++];
            int x = c % size;
            int y = c / size;
            int[] next = {x > 0 ? c - 1 : -1, x < size - 2 ? c + 1 : -1, y > 0 ? c - size : -1,
                          y < size - 2 ? c + size : -1};
            for (int n : next) {
                if (n >= 0 && !seen[n] && passable[n]) {
                    seen[n] = true;
                    queue[tail++] = n;
                }
            }
        }
        return seen;
    }

    /** A land seed whose gate-city lane winds back past itself; its corridor once had steps the game refuses. */
    private static final long WINDING_LANE_SEED = 8515256671545291656L;

    @Test
    void lanesJoinTheirSitesOnFoot() {
        int size = 1025;
        for (long seed : new long[]{1, 2, 3, 4, 5, 6, WINDING_LANE_SEED}) {
            float[] h = CampaignLand.generate(seed, size, MISSION_2).height();
            boolean[] fromGate = walkable(h, size, Sites.cell(MISSION_2.site("gate"), size));
            for (String site : List.of("city", "array", "seraph")) {
                assertTrue(fromGate[Sites.cell(MISSION_2.site(site), size)], "seed " + seed + ": " + site);
            }
        }
    }

    @Test
    void sitesAreDryAndFlatAtTheirCentre() {
        int size = 1025;
        float[] h = CampaignLand.generate(21, size, MISSION_2).height();
        assertEquals(Sites.LOWLAND_FLAT, h[Sites.cell(MISSION_2.site("city"), size)]);
        assertEquals(Sites.ARRAY_FLAT, h[Sites.cell(MISSION_2.site("array"), size)]);
    }

    @Test
    void aGradedLaneNeverStepsMoreThanItsGrade() {
        int size = 200;
        float[] h = new float[size * size];
        for (int i = 0; i < h.length; i++) {
            int x = i % size;
            h[i] = x > 90 && x < 110 ? 40 : 2;
        }
        int[] path = Sites.cheapestPath(h, size, 20 + 100 * size, 180 + 100 * size);
        Sites.grade(h, size, path);
        for (int k = 1; k < path.length; k++) {
            assertTrue(StrictMath.abs(h[path[k]] - h[path[k - 1]]) <= Sites.LANE_GRADE + 1e-4f, "step at " + k);
        }
        assertTrue(walkable(h, size, 20 + 100 * size)[180 + 100 * size], "the ridge still blocks the lane");
    }

    @Test
    void badPlansFailLoudly() {
        assertThrows(IllegalArgumentException.class, () -> SitePlan.parse(List.of("a:TOWER:0.5,0.5,10"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> SitePlan.parse(List.of("a:CITY:1.5,0.5,10"), List.of()));
        assertThrows(IllegalArgumentException.class,
                     () -> SitePlan.parse(List.of("a:CITY:0.5,0.5,10"), List.of("a-b")));
    }
}
