package com.faforever.neroxis.generator.terrain.campaign;

import java.util.SplittableRandom;

/**
 * Inland lakes and the rivers that drain them, on the lowland. The game has one water plane, so water shows wherever
 * the ground is below sea level: a few lakes are dug into lowland far from the sea (Black Day's lakes in hollows),
 * and some drain to the sea through a channel that takes the cheapest lowland path, with noise in the cost so it
 * meanders (Mind Games' channel). Neither cuts into tiers or ridges, so cliffs stay where they are.
 */
final class Water {
    /** Lowland is ground at most this high above sea; rivers and lakes stay on it. */
    static final float LOWLAND_TOP = Terraces.BEACH + 1.5f;
    static final float RIVER_BED = -2f;
    private static final float MIN_RIVER_HALF_WIDTH = 3;
    private static final float MAX_RIVER_HALF_WIDTH = 6;
    /** Chance that a lake drains to the sea through a river. */
    private static final float RIVER_CHANCE = .7f;
    /** How much the meander noise adds to a step's cost; higher bends the river more. */
    private static final float MEANDER = 3f;
    /** Meander noise runs this many times finer than the shore noise. */
    private static final float MEANDER_SCALE = 3f;
    static final float LAKE_FLOOR = -4f;
    /** Lakes sit at least this many cells from the sea and from each other. */
    static final int LAKE_INLAND = 64;
    private static final int LAKE_SPACING = 220;
    private static final float MIN_LAKE_RADIUS = 18;
    private static final float MAX_LAKE_RADIUS = 42;
    private static final int MAX_LAKES = 3;
    private static final int LAKE_TRIES = 4000;
    /** Shores wander in and out by this share of the radius. */
    private static final float SHORE_WANDER = .35f;

    private Water() {}

    static void carve(float[] height, int size, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        int[] seaDistance = distanceToSea(height, size);
        carveLakes(height, size, seaDistance, random);
    }

    private static void carveLakes(float[] height, int size, int[] seaDistance, SplittableRandom random) {
        FastNoiseLite shore = new FastNoiseLite(random.nextInt());
        shore.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        shore.SetFrequency(40f);
        int lakeCount = 1 + random.nextInt(MAX_LAKES);
        int[] centres = new int[lakeCount];
        int placed = 0;
        for (int t = 0; t < LAKE_TRIES && placed < lakeCount; t++) {
            int c = random.nextInt(height.length);
            if (height[c] <= 0 || height[c] > LOWLAND_TOP || seaDistance[c] < LAKE_INLAND
                || tooClose(c, centres, placed, size)) {
                continue;
            }
            float radius = (float) (MIN_LAKE_RADIUS + random.nextDouble() * (MAX_LAKE_RADIUS - MIN_LAKE_RADIUS));
            dig(height, size, c, radius, shore);
            centres[placed++] = c;
            if (random.nextDouble() < RIVER_CHANCE) {
                float halfWidth = (float) (MIN_RIVER_HALF_WIDTH
                                           + random.nextDouble() * (MAX_RIVER_HALF_WIDTH - MIN_RIVER_HALF_WIDTH));
                carveChannel(height, size, riverPath(height, size, seaDistance, c, shore), halfWidth);
            }
        }
    }

    private static boolean tooClose(int c, int[] centres, int placed, int size) {
        for (int k = 0; k < placed; k++) {
            if (StrictMath.hypot(c % size - centres[k] % size, c / size - centres[k] / size) < LAKE_SPACING) {
                return true;
            }
        }
        return false;
    }

    /** A bowl down to LAKE_FLOOR with a wandering shore, cut only into lowland. */
    private static void dig(float[] height, int size, int centre, float radius, FastNoiseLite shore) {
        int cx = centre % size;
        int cy = centre / size;
        int reach = (int) (radius * (1 + SHORE_WANDER)) + 1;
        for (int y = StrictMath.max(0, cy - reach); y < StrictMath.min(size, cy + reach + 1); y++) {
            for (int x = StrictMath.max(0, cx - reach); x < StrictMath.min(size, cx + reach + 1); x++) {
                int i = x + y * size;
                if (height[i] > LOWLAND_TOP) {
                    continue;
                }
                float r = radius * (1 + SHORE_WANDER * shore.GetNoise(x / Landform.NOISE_SPAN,
                                                                      y / Landform.NOISE_SPAN));
                float d = (float) StrictMath.hypot(x - cx, y - cy) / r;
                if (d < 1) {
                    float bowl = LAKE_FLOOR * (1 - d * d) + Terraces.BEACH * d * d;
                    height[i] = StrictMath.min(height[i], bowl);
                }
            }
        }
    }

    /**
     * The cheapest path over lowland from {@code start} to the sea ({@code seaDistance} 0), as cells from the sea
     * back to the start; empty when the lowland around {@code start} never reaches the sea.
     */
    static int[] riverPath(float[] height, int size, int[] seaDistance, int start, FastNoiseLite meander) {
        float[] cost = new float[height.length];
        java.util.Arrays.fill(cost, Float.MAX_VALUE);
        int[] from = new int[height.length];
        // Each cell can be pushed once per neighbour, so four times the cells bounds the heap.
        CellHeap heap = new CellHeap(4 * height.length + 1);
        cost[start] = 0;
        from[start] = -1;
        heap.push(start, 0);
        int end = -1;
        while (!heap.isEmpty()) {
            int c = heap.pop();
            if (seaDistance[c] == 0) {
                end = c;
                break;
            }
            int x = c % size;
            int y = c / size;
            int[] next = {x > 0 ? c - 1 : -1, x < size - 1 ? c + 1 : -1, y > 0 ? c - size : -1,
                          y < size - 1 ? c + size : -1};
            for (int n : next) {
                if (n < 0 || height[n] > LOWLAND_TOP) {
                    continue;
                }
                float u = n % size * MEANDER_SCALE / Landform.NOISE_SPAN;
                float v = n / size * MEANDER_SCALE / Landform.NOISE_SPAN;
                float wiggle = (meander.GetNoise(u, v) + 1) / 2;
                float c2 = cost[c] + 1 + MEANDER * wiggle;
                if (c2 < cost[n]) {
                    cost[n] = c2;
                    from[n] = c;
                    heap.push(n, c2);
                }
            }
        }
        if (end < 0) {
            return new int[0];
        }
        int length = 0;
        for (int c = end; c >= 0; c = from[c]) {
            length++;
        }
        int[] path = new int[length];
        int k = 0;
        for (int c = end; c >= 0; c = from[c]) {
            path[k++] = c;
        }
        return path;
    }

    /** Lowers lowland within {@code halfWidth} of the path to a riverbed with sloping banks. */
    private static void carveChannel(float[] height, int size, int[] path, float halfWidth) {
        int reach = (int) StrictMath.ceil(halfWidth * 2);
        for (int p : path) {
            int px = p % size;
            int py = p / size;
            for (int y = StrictMath.max(0, py - reach); y < StrictMath.min(size, py + reach + 1); y++) {
                for (int x = StrictMath.max(0, px - reach); x < StrictMath.min(size, px + reach + 1); x++) {
                    int i = x + y * size;
                    if (height[i] > LOWLAND_TOP) {
                        continue;
                    }
                    float d = (float) StrictMath.hypot(x - px, y - py) / (2 * halfWidth);
                    if (d < 1) {
                        float bank = RIVER_BED + (Terraces.BEACH - RIVER_BED) * d * d;
                        height[i] = StrictMath.min(height[i], bank);
                    }
                }
            }
        }
    }

    /** 4-neighbour steps from each cell to the nearest cell at or below sea level. */
    static int[] distanceToSea(float[] height, int size) {
        int[] d = new int[height.length];
        int[] queue = new int[height.length];
        int head = 0;
        int tail = 0;
        for (int i = 0; i < height.length; i++) {
            if (height[i] <= 0) {
                queue[tail++] = i;
            } else {
                d[i] = Integer.MAX_VALUE;
            }
        }
        while (head < tail) {
            int c = queue[head++];
            int x = c % size;
            int y = c / size;
            int[] next = {x > 0 ? c - 1 : -1, x < size - 1 ? c + 1 : -1, y > 0 ? c - size : -1,
                          y < size - 1 ? c + size : -1};
            for (int n : next) {
                if (n >= 0 && d[n] == Integer.MAX_VALUE) {
                    d[n] = d[c] + 1;
                    queue[tail++] = n;
                }
            }
        }
        return d;
    }
}
