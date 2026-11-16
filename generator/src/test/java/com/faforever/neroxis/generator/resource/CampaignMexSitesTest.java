package com.faforever.neroxis.generator.resource;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignMexSitesTest {
    private static final int SIZE = 512;
    private static final int PLAYER_MEXES = CampaignMexSites.BASE_MEXES + Arrays.stream(
            CampaignMexSites.PLAYER_CLUSTERS).sum();

    private static boolean[] all(boolean value) {
        boolean[] grid = new boolean[SIZE * SIZE];
        Arrays.fill(grid, value);
        return grid;
    }

    @Test
    void everyPlayerGetsTheSameMassNearTheirSpawn() {
        CampaignMexSites sites = new CampaignMexSites(all(true), SIZE, new SplittableRandom(3));
        int[] spawns = {100 + 100 * SIZE, 400 + 380 * SIZE};
        for (int spawn : spawns) {
            List<Integer> mine = sites.placePlayer(spawn, all(true));
            assertEquals(PLAYER_MEXES, mine.size());
            long base = mine.stream().filter(c -> sites.distance(c, spawn) <= CampaignMexSites.BASE_MAX).count();
            assertTrue(base >= CampaignMexSites.BASE_MEXES, "base mexes " + base);
            assertTrue(mine.stream().allMatch(c -> sites.distance(c, spawn) <= CampaignMexSites.EXPANSION_MAX + 8));
        }
    }

    @Test
    void playerMassStaysOnGroundTheSpawnCanWalkTo() {
        // A wall at x = 120 cuts the spawn's side off from the rest.
        boolean[] passable = all(true);
        for (int y = 0; y < SIZE; y++) {
            passable[120 + y * SIZE] = false;
        }
        int spawn = 60 + 256 * SIZE;
        boolean[] reach = CampaignMexSites.reachable(passable, SIZE, spawn);
        assertFalse(reach[200 + 256 * SIZE]);
        CampaignMexSites sites = new CampaignMexSites(all(true), SIZE, new SplittableRandom(5));
        List<Integer> mine = sites.placePlayer(spawn, reach);
        assertEquals(PLAYER_MEXES, mine.size());
        assertTrue(mine.stream().allMatch(c -> c % SIZE < 120), "a mex is across the wall");
    }

    @Test
    void noRoomFailsLoudly() {
        boolean[] allowed = all(false);
        CampaignMexSites sites = new CampaignMexSites(allowed, SIZE, new SplittableRandom(1));
        assertThrows(IllegalStateException.class, () -> sites.placePlayer(256 + 256 * SIZE, all(true)));
    }

    @Test
    void mapFillStopsAtTheTotalAndKeepsSpacing() {
        CampaignMexSites sites = new CampaignMexSites(all(true), SIZE, new SplittableRandom(9));
        sites.fillMap(40);
        List<Integer> placed = sites.placed();
        assertEquals(40, placed.size());
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                assertTrue(sites.distance(placed.get(i), placed.get(j)) >= 5, "mexes closer than 5 cells");
            }
        }
    }

    @Test
    void sameSeedSameSites() {
        CampaignMexSites a = new CampaignMexSites(all(true), SIZE, new SplittableRandom(4));
        CampaignMexSites b = new CampaignMexSites(all(true), SIZE, new SplittableRandom(4));
        a.placePlayer(200 + 200 * SIZE, all(true));
        b.placePlayer(200 + 200 * SIZE, all(true));
        assertEquals(a.placed(), b.placed());
    }
}
