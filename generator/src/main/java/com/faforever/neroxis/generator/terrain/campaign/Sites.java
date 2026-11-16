package com.faforever.neroxis.generator.terrain.campaign;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Shapes the land around a {@link SitePlan}. Before erosion it lays down the structure (dry flats at each site, a
 * ridge ring with passes around a city, a ridge line between an array and the city it watches), so rivers and tiers
 * form around it; after terracing and water it flattens the sites again and grades every lane into a walkable path.
 */
final class Sites {
    /** Height a lowland site is flattened to; the lowest tier sits at the beach line, this just above it. */
    static final float LOWLAND_FLAT = Terraces.BEACH;
    /** An array's plateau height: above the first tier, below the ridges. */
    static final float ARRAY_FLAT = 17f;
    /** Ground around a site is raised to at least this, out to LAND_REACH radii, so no site is in the sea. */
    private static final float SITE_GROUND = 3f;
    private static final float LAND_REACH = 1.8f;
    /** Players expand from the gate, so its land reaches further: room for their mex clusters 150 cells out. */
    private static final float GATE_LAND_REACH = 3.5f;
    /** The gate basin is flat out to this share of its reach, then eases up into the land around it. */
    private static final float GATE_BASIN_FLAT = .55f;
    /** A flat's edge blends into the land around it over this share of its radius. */
    private static final float EDGE_BLEND = .35f;
    private static final float RING_GAP = 1.4f;
    private static final float RING_WIDTH = 50;
    private static final float RING_HEIGHT = 32;
    /** Half-width of a pass through a city's ridge ring, in radians. */
    private static final float PASS_HALF_ANGLE = .24f;
    private static final float RIDGE_LINE_HEIGHT = 36;
    private static final float RIDGE_LINE_HALF_WIDTH = 24;
    private static final float RIDGE_LINE_OFFSET = 30;
    /** A lane's steepest step per cell: walkable, the game refuses land units above 0.75. */
    static final float LANE_GRADE = .3f;
    private static final int LANE_HALF_WIDTH = 6;
    /** Steepest step left between two cells of a lane's core: walkable with a margin under the game's 0.75. */
    private static final float LANE_CORE_STEP = .5f;
    private static final int LANE_SETTLE_PASSES = 200;
    private static final int LANE_SMOOTHING = 15;
    private static final float CLIFF_STEP = .75f;
    private static final float CLIFF_COST = 60;
    private static final float WATER_COST = 300;
    private static final float CLIMB_COST = 8;
    /** Rims and ridges wander by this share of their size, so sites read as land, not circles. */
    private static final float WOBBLE = .28f;
    private static final float WOBBLE_FREQUENCY = 18f;
    /** Settling re-flattens only this inner share of a site, so its wandering rim survives. */
    private static final float CORE = .65f;

    private Sites() {}

    static void shape(float[] height, int size, SitePlan plan, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        FastNoiseLite wobble = new FastNoiseLite(random.nextInt());
        wobble.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        wobble.SetFrequency(WOBBLE_FREQUENCY);
        wobble.SetFractalType(FastNoiseLite.FractalType.FBm);
        wobble.SetFractalOctaves(3);
        for (SitePlan.Site site : plan.sites()) {
            raiseGround(height, size, site, wobble);
            float flat = site.kind() == SitePlan.Kind.ARRAY ? ARRAY_FLAT : LOWLAND_FLAT + 1;
            flatten(height, size, site, flat, wobble);
        }
        for (SitePlan.Site site : plan.sites()) {
            if (site.kind() == SitePlan.Kind.CITY) {
                ridgeRing(height, size, site, passAngles(plan, site, random), wobble);
            } else if (site.kind() == SitePlan.Kind.ARRAY) {
                SitePlan.@Nullable Site watched = nearest(plan, site, SitePlan.Kind.CITY, SitePlan.Kind.GATE);
                if (watched != null) {
                    ridgeLine(height, size, site, watched, wobble);
                }
            }
        }
    }

    static void settle(float[] height, int size, SitePlan plan) {
        for (SitePlan.Site site : plan.sites()) {
            float level = site.kind() == SitePlan.Kind.ARRAY ? ARRAY_FLAT : LOWLAND_FLAT;
            float core = site.radius() * CORE;
            // Ease the core's edge into the ground around it, so settling leaves no ring of a step.
            stamp(size, site, core, (i, d) -> {
                float t = Landform.smoothstep(core * .6f, core, d);
                height[i] = level + (height[i] - level) * t;
            });
        }
        for (SitePlan.Lane lane : plan.lanes()) {
            int from = cell(plan.site(lane.from()), size);
            int to = cell(plan.site(lane.to()), size);
            grade(height, size, cheapestPath(height, size, from, to));
        }
    }

    /** Land around every site that lakes and rivers must leave dry: the ground each site raises. */
    static boolean[] keepDry(int size, SitePlan plan) {
        boolean[] dry = new boolean[size * size];
        for (SitePlan.Site site : plan.sites()) {
            float reach = site.radius() * (site.kind() == SitePlan.Kind.GATE ? GATE_LAND_REACH : LAND_REACH);
            stamp(size, site, reach, (i, d) -> dry[i] = true);
        }
        return dry;
    }

    static int cell(SitePlan.Site site, int size) {
        return StrictMath.round(site.x() * (size - 1)) + StrictMath.round(site.z() * (size - 1)) * size;
    }

    /**
     * Raises the sea around a site to land. Around a gate it also lowers higher land into an open basin that eases
     * up into its surroundings, since the players build and expand there and a pit ringed by tier cliffs has no room.
     */
    private static void raiseGround(float[] height, int size, SitePlan.Site site, FastNoiseLite wobble) {
        boolean gate = site.kind() == SitePlan.Kind.GATE;
        float base = site.radius() * (gate ? GATE_LAND_REACH : LAND_REACH);
        stamp(size, site, base * (1 + WOBBLE), (i, d) -> {
            float t = d / (base * (1 + WOBBLE * noise(wobble, i, size)));
            if (t >= 1) {
                return;
            }
            height[i] = StrictMath.max(height[i], SITE_GROUND * (1 - t * t));
            if (gate) {
                float open = Landform.smoothstep(GATE_BASIN_FLAT, 1, t);
                height[i] = StrictMath.min(height[i], SITE_GROUND + (height[i] - SITE_GROUND) * open);
            }
        });
    }

    /** Sets the site's disc to {@code level} out to a wandering rim that eases into the land around it. */
    private static void flatten(float[] height, int size, SitePlan.Site site, float level, FastNoiseLite wobble) {
        float reach = site.radius() * (1 + WOBBLE) * (1 + EDGE_BLEND);
        stamp(size, site, reach, (i, d) -> {
            float rim = site.radius() * (1 + WOBBLE * noise(wobble, i, size));
            float outer = rim * (1 + EDGE_BLEND);
            if (d <= rim) {
                height[i] = level;
            } else if (d < outer) {
                float t = Landform.smoothstep(rim, outer, d);
                height[i] = level + (height[i] - level) * t;
            }
        });
    }

    private static float noise(FastNoiseLite noise, int cell, int size) {
        return noise.GetNoise(cell % size / Landform.NOISE_SPAN, cell / size / Landform.NOISE_SPAN);
    }

    /** Pass directions: one toward the nearest array (the attackers), one or two more at random. */
    private static List<Float> passAngles(SitePlan plan, SitePlan.Site city, SplittableRandom random) {
        List<Float> angles = new ArrayList<>();
        SitePlan.@Nullable Site enemy = nearest(plan, city, SitePlan.Kind.ARRAY);
        float toward = enemy != null ? angle(city, enemy) : (float) (random.nextDouble() * 2 * StrictMath.PI);
        angles.add(toward);
        int extra = 1 + random.nextInt(2);
        for (int k = 1; k <= extra; k++) {
            float spread = (float) (StrictMath.PI * (.55 + random.nextDouble() * .5));
            angles.add(toward + (k % 2 == 1 ? spread : -spread));
        }
        return angles;
    }

    private static void ridgeRing(float[] height, int size, SitePlan.Site city, List<Float> passes,
                                  FastNoiseLite wobble) {
        float inner0 = city.radius() * RING_GAP;
        float reach = (inner0 + RING_WIDTH) * (1 + WOBBLE);
        int cx = StrictMath.round(city.x() * (size - 1));
        int cz = StrictMath.round(city.z() * (size - 1));
        stamp(size, city, reach, (i, d) -> {
            float n = noise(wobble, i, size);
            float inner = inner0 * (1 + WOBBLE * n);
            float outer = inner + RING_WIDTH * (1 + .5f * n);
            if (d < inner || d >= outer) {
                return;
            }
            float a = (float) StrictMath.atan2(i / size - cz, i % size - cx);
            float open = 0;
            for (float pass : passes) {
                float diff = (float) StrictMath.abs(StrictMath.IEEEremainder(a - pass, 2 * StrictMath.PI));
                open = StrictMath.max(open, 1 - Landform.smoothstep(PASS_HALF_ANGLE * .6f, PASS_HALF_ANGLE, diff));
            }
            float across = (d - inner) / (outer - inner);
            // The crest wanders too, so the ring reads as a chain of ridges, not a wall.
            float crest = .65f + .35f * noise(wobble, (i + size * size / 2) % (size * size), size);
            float ridge = RING_HEIGHT * crest * (float) StrictMath.sin(StrictMath.PI * across) * (1 - open);
            height[i] = StrictMath.max(height[i], SITE_GROUND + ridge);
        });
    }

    /** A ridge across the line from the array to {@code watched}, between them, so the array sits behind it. */
    private static void ridgeLine(float[] height, int size, SitePlan.Site array, SitePlan.Site watched,
                                  FastNoiseLite wobble) {
        float ax = array.x() * (size - 1);
        float az = array.z() * (size - 1);
        float dx = watched.x() * (size - 1) - ax;
        float dz = watched.z() * (size - 1) - az;
        float length = (float) StrictMath.hypot(dx, dz);
        float ux = dx / length;
        float uz = dz / length;
        float offset = array.radius() * (1 + EDGE_BLEND) + RIDGE_LINE_OFFSET;
        float mx = ax + ux * offset;
        float mz = az + uz * offset;
        float halfLength = array.radius() * 2.5f;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                float px = x - mx;
                float pz = z - mz;
                float along = -px * uz + pz * ux;
                // Bend the line along its length so it reads as a ridge, not a bar.
                float bend = RIDGE_LINE_HALF_WIDTH * 1.5f * wobble.GetNoise(along / Landform.NOISE_SPAN * 2, 0);
                float across = px * ux + pz * uz - bend;
                if (StrictMath.abs(across) >= RIDGE_LINE_HALF_WIDTH || StrictMath.abs(along) >= halfLength) {
                    continue;
                }
                float a = across / RIDGE_LINE_HALF_WIDTH;
                float taper = 1 - Landform.smoothstep(halfLength * .7f, halfLength, StrictMath.abs(along));
                int i = x + z * size;
                float crest = .7f + .3f * noise(wobble, i, size);
                float ridge = RIDGE_LINE_HEIGHT * crest * (1 - a * a) * taper;
                height[i] = StrictMath.max(height[i], SITE_GROUND + ridge);
            }
        }
    }

    /** The cheapest 4-neighbour path from {@code from} to {@code to}: flat ground is cheap, cliffs and water dear. */
    static int[] cheapestPath(float[] height, int size, int from, int to) {
        float[] cost = new float[height.length];
        java.util.Arrays.fill(cost, Float.MAX_VALUE);
        int[] previous = new int[height.length];
        CellHeap heap = new CellHeap(4 * height.length + 1);
        cost[from] = 0;
        previous[from] = -1;
        heap.push(from, 0);
        while (!heap.isEmpty()) {
            int c = heap.pop();
            if (c == to) {
                break;
            }
            int x = c % size;
            int y = c / size;
            int[] next = {x > 0 ? c - 1 : -1, x < size - 1 ? c + 1 : -1, y > 0 ? c - size : -1,
                          y < size - 1 ? c + size : -1};
            for (int n : next) {
                if (n < 0) {
                    continue;
                }
                float step = StrictMath.abs(height[n] - height[c]);
                float c2 = cost[c] + 1 + CLIMB_COST * step + (step > CLIFF_STEP ? CLIFF_COST : 0)
                           + (height[n] <= 0 ? WATER_COST : 0);
                if (c2 < cost[n]) {
                    cost[n] = c2;
                    previous[n] = c;
                    heap.push(n, c2);
                }
            }
        }
        int length = 0;
        for (int c = to; c >= 0; c = previous[c]) {
            length++;
        }
        int[] path = new int[length];
        int k = length;
        for (int c = to; c >= 0; c = previous[c]) {
            path[--k] = c;
        }
        assert path[0] == from : "path does not start at its site";
        return path;
    }

    /**
     * Grades a corridor along {@code path}: the height profile is smoothed, kept dry and limited to LANE_GRADE per
     * cell, then pressed into the ground with sloping shoulders.
     */
    static void grade(float[] height, int size, int[] path) {
        int n = path.length;
        float[] raw = new float[n];
        for (int k = 0; k < n; k++) {
            raw[k] = StrictMath.max(1, height[path[k]]);
        }
        float[] profile = new float[n];
        for (int k = 0; k < n; k++) {
            float sum = 0;
            int count = 0;
            for (int j = StrictMath.max(0, k - LANE_SMOOTHING); j <= StrictMath.min(n - 1, k + LANE_SMOOTHING); j++) {
                sum += raw[j];
                count++;
            }
            profile[k] = sum / count;
        }
        // The ends sit on site cores: keep them at their level so a lane never lifts a site's centre.
        profile[0] = raw[0];
        profile[n - 1] = raw[n - 1];
        for (int k = 1; k < n; k++) {
            profile[k] = StrictMath.max(profile[k - 1] - LANE_GRADE, StrictMath.min(profile[k - 1] + LANE_GRADE,
                                                                                 profile[k]));
        }
        for (int k = n - 2; k >= 0; k--) {
            profile[k] = StrictMath.max(profile[k + 1] - LANE_GRADE, StrictMath.min(profile[k + 1] + LANE_GRADE,
                                                                                 profile[k]));
        }
        int reach = 2 * LANE_HALF_WIDTH;
        // Each cell takes the profile of its nearest path cell, so neighbouring cells sit at nearly the same height.
        float[] nearest = new float[height.length];
        java.util.Arrays.fill(nearest, Float.MAX_VALUE);
        float[] target = new float[height.length];
        for (int k = 0; k < n; k++) {
            int px = path[k] % size;
            int py = path[k] / size;
            for (int y = StrictMath.max(0, py - reach); y <= StrictMath.min(size - 1, py + reach); y++) {
                for (int x = StrictMath.max(0, px - reach); x <= StrictMath.min(size - 1, px + reach); x++) {
                    float d = (float) StrictMath.hypot(x - px, y - py);
                    int i = x + y * size;
                    if (d < nearest[i]) {
                        nearest[i] = d;
                        target[i] = profile[k];
                    }
                }
            }
        }
        for (int i = 0; i < height.length; i++) {
            float d = nearest[i];
            if (d <= reach) {
                float w = d <= LANE_HALF_WIDTH ? 1 : 1 - Landform.smoothstep(LANE_HALF_WIDTH, reach, d);
                height[i] += (target[i] - height[i]) * w;
            }
        }
        settleCore(height, size, nearest);
    }

    /**
     * A winding path passes close to itself, so neighbouring corridor cells can take profiles far apart along it.
     * Splitting every step steeper than LANE_CORE_STEP between two core cells, enough times, leaves the core a
     * walkable band.
     */
    private static void settleCore(float[] height, int size, float[] nearest) {
        float[] delta = new float[height.length];
        for (int pass = 0; pass < LANE_SETTLE_PASSES; pass++) {
            boolean moved = false;
            java.util.Arrays.fill(delta, 0);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int i = x + y * size;
                    if (nearest[i] > LANE_HALF_WIDTH) {
                        continue;
                    }
                    for (int n : new int[]{x < size - 1 ? i + 1 : -1, y < size - 1 ? i + size : -1}) {
                        if (n < 0 || nearest[n] > LANE_HALF_WIDTH) {
                            continue;
                        }
                        float diff = height[i] - height[n];
                        float excess = StrictMath.abs(diff) - LANE_CORE_STEP;
                        if (excess > 0) {
                            float move = StrictMath.copySign(excess / 6, diff);
                            delta[i] -= move;
                            delta[n] += move;
                            moved = true;
                        }
                    }
                }
            }
            if (!moved) {
                return;
            }
            for (int i = 0; i < height.length; i++) {
                height[i] += delta[i];
            }
        }
    }

    private static SitePlan.@Nullable Site nearest(SitePlan plan, SitePlan.Site from, SitePlan.Kind... kinds) {
        SitePlan.@Nullable Site best = null;
        float bestDistance = Float.MAX_VALUE;
        for (SitePlan.Site site : plan.sites()) {
            if (site == from || !List.of(kinds).contains(site.kind())) {
                continue;
            }
            float d = (float) StrictMath.hypot(site.x() - from.x(), site.z() - from.z());
            if (d < bestDistance) {
                bestDistance = d;
                best = site;
            }
        }
        return best;
    }

    private static float angle(SitePlan.Site from, SitePlan.Site to) {
        return (float) StrictMath.atan2(to.z() - from.z(), to.x() - from.x());
    }

    private interface CellAction {
        void apply(int cell, float distance);
    }

    /** Calls {@code action} for every cell within {@code reach} cells of the site's centre. */
    private static void stamp(int size, SitePlan.Site site, float reach, CellAction action) {
        float cx = site.x() * (size - 1);
        float cz = site.z() * (size - 1);
        int r = (int) StrictMath.ceil(reach);
        for (int z = StrictMath.max(0, (int) cz - r); z <= StrictMath.min(size - 1, (int) cz + r); z++) {
            for (int x = StrictMath.max(0, (int) cx - r); x <= StrictMath.min(size - 1, (int) cx + r); x++) {
                float d = (float) StrictMath.hypot(x - cx, z - cz);
                if (d <= reach) {
                    action.apply(x + z * size, d);
                }
            }
        }
    }
}
