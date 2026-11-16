package com.faforever.neroxis.generator.terrain.campaign;

/** Binary min-heap of cells keyed by a float, ties broken by index so results are reproducible. */
final class CellHeap {
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
