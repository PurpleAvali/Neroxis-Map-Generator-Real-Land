package com.faforever.neroxis.generator.terrain.campaign;

/**
 * River erosion on a height grid: every land cell drains to the sea or the map edge, flow piles up downstream, and
 * each pass lowers the ground by flow times slope plus a little creep. The rate and the fill-sinks-between-passes
 * loop follow mewo2/terrain (terrain.js {@code erosionRate}, {@code doErosion}); sinks are filled with a priority
 * flood (Barnes et al. 2014) instead of mewo2's repeated sweeps, and its pop order gives the flow order for free.
 */
final class StreamErosion {
    /** Lowest a riverbed may cut, in game units relative to sea level; below 0 it floods into an estuary. */
    static final float RIVERBED_FLOOR = -2.5f;
    /** Height added per cell along a filled sink so water keeps running downhill. */
    private static final float FILL_EPSILON = 1e-3f;
    /** Weight of river cutting against hillslope creep, from mewo2's erosionRate. */
    private static final float RIVER_WEIGHT = 1000f;
    private static final float MAX_RATE = 200f;
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DY = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final float[] DIST = {1, 1, 1, 1, 1.4142135f, 1.4142135f, 1.4142135f, 1.4142135f};

    private StreamErosion() {}

    /**
     * Erodes {@code height} in place over {@code passes} passes, each cutting at most {@code depthPerPass}. The total
     * cut is blurred by {@code valleyRadius} cells, so flow lines one cell wide become valleys instead of trenches.
     */
    static void erode(float[] height, int size, int passes, float depthPerPass, int valleyRadius) {
        float[] before = height.clone();
        cut(height, size, passes, depthPerPass);
        float[] change = new float[height.length];
        for (int i = 0; i < height.length; i++) {
            change[i] = height[i] - before[i];
        }
        boxBlur(change, size, valleyRadius);
        boxBlur(change, size, valleyRadius);
        for (int i = 0; i < height.length; i++) {
            height[i] = before[i] > 0 ? StrictMath.max(RIVERBED_FLOOR, before[i] + change[i]) : before[i];
        }
    }

    private static void cut(float[] height, int size, int passes, float depthPerPass) {
        Drainage drainage = new Drainage(size);
        float[] rate = new float[height.length];
        for (int pass = 0; pass < passes; pass++) {
            drainage.fill(height);
            float maxRate = rates(height, drainage, rate);
            if (maxRate <= 0) {
                return;
            }
            for (int i = 0; i < height.length; i++) {
                if (height[i] > 0) {
                    height[i] = StrictMath.max(RIVERBED_FLOOR, height[i] - depthPerPass * rate[i] / maxRate);
                }
            }
        }
    }

    /** Box blur of radius {@code r} in place, rows then columns, clamped at the edges. */
    static void boxBlur(float[] v, int size, int r) {
        float[] line = new float[size];
        for (int pass = 0; pass < 2; pass++) {
            int step = pass == 0 ? 1 : size;
            int lineStep = pass == 0 ? size : 1;
            for (int l = 0; l < size; l++) {
                int start = l * lineStep;
                for (int k = 0; k < size; k++) {
                    line[k] = v[start + k * step];
                }
                float sum = 0;
                for (int k = -r; k <= r; k++) {
                    sum += line[StrictMath.max(0, StrictMath.min(size - 1, k))];
                }
                for (int k = 0; k < size; k++) {
                    v[start + k * step] = sum / (2 * r + 1);
                    sum += line[StrictMath.min(size - 1, k + r + 1)] - line[StrictMath.max(0, k - r)];
                }
            }
        }
    }

    private static float rates(float[] height, Drainage drainage, float[] rate) {
        float[] flux = drainage.accumulate();
        float maxRate = 0;
        for (int i = 0; i < height.length; i++) {
            int down = drainage.downstream[i];
            if (down < 0 || height[i] <= 0) {
                rate[i] = 0;
                continue;
            }
            float slope = (height[i] - height[down]) / drainage.distance[i];
            float r = StrictMath.min(MAX_RATE, RIVER_WEIGHT * (float) StrictMath.sqrt(flux[i]) * slope + slope * slope);
            rate[i] = r;
            maxRate = StrictMath.max(maxRate, r);
        }
        return maxRate;
    }

    /** Priority-flood depression filling with the receiver of each cell and the order cells were reached. */
    private static final class Drainage {
        final int size;
        final int[] downstream;
        final float[] distance;
        final int[] order;
        int count;
        private final boolean[] reached;
        private final CellHeap heap;

        Drainage(int size) {
            this.size = size;
            int cells = size * size;
            downstream = new int[cells];
            distance = new float[cells];
            order = new int[cells];
            reached = new boolean[cells];
            heap = new CellHeap(cells);
        }

        void fill(float[] height) {
            java.util.Arrays.fill(reached, false);
            count = 0;
            for (int i = 0; i < height.length; i++) {
                int x = i % size;
                int y = i / size;
                if (height[i] <= 0 || x == 0 || y == 0 || x == size - 1 || y == size - 1) {
                    reached[i] = true;
                    downstream[i] = -1;
                    distance[i] = 1;
                    heap.push(i, height[i]);
                }
            }
            while (!heap.isEmpty()) {
                int c = heap.pop();
                order[count++] = c;
                int cx = c % size;
                int cy = c / size;
                for (int k = 0; k < DX.length; k++) {
                    int nx = cx + DX[k];
                    int ny = cy + DY[k];
                    if (nx < 0 || ny < 0 || nx >= size || ny >= size) {
                        continue;
                    }
                    int n = nx + ny * size;
                    if (reached[n]) {
                        continue;
                    }
                    reached[n] = true;
                    height[n] = StrictMath.max(height[n], height[c] + FILL_EPSILON * DIST[k]);
                    downstream[n] = c;
                    distance[n] = DIST[k];
                    heap.push(n, height[n]);
                }
            }
            assert count == height.length : "priority flood missed cells";
        }

        float[] accumulate() {
            float[] flux = new float[order.length];
            float cellShare = 1f / order.length;
            for (int k = count - 1; k >= 0; k--) {
                int c = order[k];
                flux[c] += cellShare;
                if (downstream[c] >= 0) {
                    flux[downstream[c]] += flux[c];
                }
            }
            return flux;
        }
    }

    /** Binary min-heap of cells keyed by height, ties broken by index so the result is reproducible. */
    private static final class CellHeap {
        private final int[] cells;
        private final float[] keys;
        private int length;

        CellHeap(int capacity) {
            cells = new int[capacity];
            keys = new float[capacity];
        }

        boolean isEmpty() {
            return length == 0;
        }

        void push(int cell, float key) {
            int i = length++;
            while (i > 0) {
                int parent = (i - 1) >>> 1;
                if (!less(cell, key, cells[parent], keys[parent])) {
                    break;
                }
                cells[i] = cells[parent];
                keys[i] = keys[parent];
                i = parent;
            }
            cells[i] = cell;
            keys[i] = key;
        }

        int pop() {
            int top = cells[0];
            int lastCell = cells[--length];
            float lastKey = keys[length];
            int i = 0;
            while (true) {
                int child = 2 * i + 1;
                if (child >= length) {
                    break;
                }
                if (child + 1 < length && less(cells[child + 1], keys[child + 1], cells[child], keys[child])) {
                    child++;
                }
                if (!less(cells[child], keys[child], lastCell, lastKey)) {
                    break;
                }
                cells[i] = cells[child];
                keys[i] = keys[child];
                i = child;
            }
            cells[i] = lastCell;
            keys[i] = lastKey;
            return top;
        }

        private static boolean less(int a, float keyA, int b, float keyB) {
            return keyA < keyB || (keyA == keyB && a < b);
        }
    }
}
