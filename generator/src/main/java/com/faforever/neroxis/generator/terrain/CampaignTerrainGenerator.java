package com.faforever.neroxis.generator.terrain;

import com.faforever.neroxis.generator.terrain.campaign.CampaignLand;

/** Terrain shaped like GPG's campaign maps; spawns are placed on it afterwards. */
public class CampaignTerrainGenerator extends SpawnLastTerrainGenerator {
    /** Spawns keep this many cells from the map edge, so a base and its expansions fit around them. */
    private static final int SPAWN_EDGE = 64;
    /** Spawns go only on walkable land that covers at least this share of the map, never in a small pocket. */
    private static final int MAIN_LAND_SHARE_DIVISOR = 8;

    @Override
    protected void setupTerrainPipeline() {
        int size = map.getSize() + 1;
        float waterHeight = map.getBiome().waterSettings().elevation();
        float[] land = CampaignLand.generate(random.nextLong(), size);
        heightmap.setSize(size).set((x, y) -> StrictMath.max(0f, waterHeight + land[x + y * size]));
    }

    @Override
    protected void setupSpawnMaskPipeline() {
        super.setupSpawnMaskPipeline();
        int size = map.getSize() + 1;
        spawnMask.multiply(passableLand.copy().removeAreasSmallerThan(size * size / MAIN_LAND_SHARE_DIVISOR))
                 .fillEdge(SPAWN_EDGE, false);
    }
}
