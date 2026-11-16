package com.faforever.neroxis.generator.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Where mass goes on a campaign map, as cells of a square grid. Every player gets the same mass on ground their
 * spawn can walk to: a ring of base mexes and a few clusters further out, as on GPG's co-op starts (Black Day,
 * Dawn and Mind Games give 6-12 mexes within 150 units of a player base). Clusters elsewhere give the rest of the
 * map mass to fight over.
 */
final class CampaignMexSites {
    static final int BASE_MEXES = 6;
    static final float BASE_MIN = 10;
    static final float BASE_MAX = 45;
    /** Mexes in each of a player's expansion clusters; fixed so both players get the same. */
    static final int[] PLAYER_CLUSTERS = {3, 3, 2};
    static final float EXPANSION_MIN = 55;
    static final float EXPANSION_MAX = 150;
    private static final float CLUSTER_RADIUS = 8;
    private static final float MEX_SPACING = 5;
    private static final float CLUSTER_SPACING = 40;
    /** A ring that has no room is widened by this factor, at most this many times, before giving up. */
    private static final float WIDEN = 1.4f;
    private static final int MAX_WIDENINGS = 3;
    /** Random draws per wanted site before a ring counts as full. */
    private static final int TRIES_PER_SITE = 400;

    private final boolean[] allowed;
    private final int size;
    private final RandomGenerator random;
    private final List<Integer> placed = new ArrayList<>();

    CampaignMexSites(boolean[] allowed, int size, RandomGenerator random) {
        assert allowed.length == size * size : "allowed grid is not size x size";
        this.allowed = allowed.clone();
        this.size = size;
        this.random = random;
    }

    List<Integer> placed() {
        return List.copyOf(placed);
    }

    /** Places one player's mexes on {@code reachable} ground around {@code spawn}; returns the new cells. */
    List<Integer> placePlayer(int spawn, boolean[] reachable) {
        int start = placed.size();
        float widen = 1;
        int widenings = 0;
        while (placed.size() - start < BASE_MEXES) {
            if (!placeIn(spawn, BASE_MIN, BASE_MAX * widen, reachable, MEX_SPACING)) {
                widen *= WIDEN;
                if (++widenings > MAX_WIDENINGS) {
                    throw new IllegalStateException("no room for base mexes around spawn " + cellText(spawn));
                }
            }
        }
        for (int count : PLAYER_CLUSTERS) {
            widen = 1;
            widenings = 0;
            while (!placeCluster(spawn, EXPANSION_MIN, EXPANSION_MAX * widen, reachable, count)) {
                widen *= WIDEN;
                if (++widenings > MAX_WIDENINGS) {
                    throw new IllegalStateException("no room for mex clusters around spawn " + cellText(spawn));
                }
            }
        }
        return List.copyOf(placed.subList(start, placed.size()));
    }

    /** Adds clusters of 2-4 anywhere allowed until {@code total} mexes are placed or no room is left. */
    void fillMap(int total) {
        int misses = 0;
        while (placed.size() < total && misses < TRIES_PER_SITE) {
            int centre = random.nextInt(allowed.length);
            int count = StrictMath.min(total - placed.size(), 2 + random.nextInt(3));
            if (allowed[centre] && farFromPlaced(centre, CLUSTER_SPACING) && clusterAround(centre, null, count)) {
                misses = 0;
            } else {
                misses++;
            }
        }
    }

    private boolean placeCluster(int spawn, float min, float max, boolean[] reachable, int count) {
        for (int t = 0; t < TRIES_PER_SITE; t++) {
            int centre = pickInRing(spawn, min, max);
            if (centre >= 0 && reachable[centre] && allowed[centre] && farFromPlaced(centre, CLUSTER_SPACING)
                && clusterAround(centre, reachable, count)) {
                return true;
            }
        }
        return false;
    }

    /** Places {@code count} mexes within CLUSTER_RADIUS of {@code centre}, or none if they do not fit. */
    private boolean clusterAround(int centre, boolean[] reachable, int count) {
        int start = placed.size();
        for (int t = 0; t < TRIES_PER_SITE && placed.size() - start < count; t++) {
            placeIn(centre, 0, CLUSTER_RADIUS, reachable, MEX_SPACING);
        }
        if (placed.size() - start == count) {
            return true;
        }
        placed.subList(start, placed.size()).clear();
        return false;
    }

    private boolean placeIn(int around, float min, float max, boolean[] reachable, float spacing) {
        for (int t = 0; t < TRIES_PER_SITE; t++) {
            int c = pickInRing(around, min, max);
            if (c >= 0 && allowed[c] && (reachable == null || reachable[c]) && farFromPlaced(c, spacing)) {
                placed.add(c);
                return true;
            }
        }
        return false;
    }

    private int pickInRing(int around, float min, float max) {
        double angle = random.nextDouble() * 2 * StrictMath.PI;
        double r = min + random.nextDouble() * (max - min);
        int x = (int) StrictMath.round(around % size + r * StrictMath.cos(angle));
        int y = (int) StrictMath.round(around / size + r * StrictMath.sin(angle));
        return x < 0 || y < 0 || x >= size || y >= size ? -1 : x + y * size;
    }

    private boolean farFromPlaced(int cell, float spacing) {
        for (int p : placed) {
            if (distance(p, cell) < spacing) {
                return false;
            }
        }
        return true;
    }

    float distance(int a, int b) {
        return (float) StrictMath.hypot(a % size - b % size, a / size - b / size);
    }

    private String cellText(int cell) {
        return "(" + cell % size + ", " + cell / size + ")";
    }

    /** Cells reachable from {@code start} by 4-neighbour steps over {@code passable}. */
    static boolean[] reachable(boolean[] passable, int size, int start) {
        boolean[] seen = new boolean[passable.length];
        if (!passable[start]) {
            return seen;
        }
        int[] queue = new int[passable.length];
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        seen[start] = true;
        while (head < tail) {
            int c = queue[head++];
            int x = c % size;
            int y = c / size;
            int[] next = {x > 0 ? c - 1 : -1, x < size - 1 ? c + 1 : -1, y > 0 ? c - size : -1,
                          y < size - 1 ? c + size : -1};
            for (int n : next) {
                if (n >= 0 && passable[n] && !seen[n]) {
                    seen[n] = true;
                    queue[tail++] = n;
                }
            }
        }
        return seen;
    }
}
