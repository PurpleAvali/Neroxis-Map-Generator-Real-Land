package com.faforever.neroxis.generator.terrain;

import com.faforever.neroxis.generator.GeneratorParameters;
import com.faforever.neroxis.generator.terrain.campaign.CampaignLand;
import com.faforever.neroxis.map.SCMap;
import com.faforever.neroxis.map.SymmetrySettings;
import com.faforever.neroxis.mask.FloatMask;
import lombok.Getter;

import java.util.random.RandomGenerator;

/** Terrain shaped like GPG's campaign maps; spawns are placed on it afterwards. */
@Getter
public class CampaignTerrainGenerator extends SpawnLastTerrainGenerator {
    /** Spawns keep this many cells from the map edge, so a base and its expansions fit around them. */
    private static final int SPAWN_EDGE = 64;
    /** Spawns go only on walkable land that covers at least this share of the map, never in a small pocket. */
    private static final int MAIN_LAND_SHARE_DIVISOR = 8;

    /** Share of all cells draining through each cell; textures paint valley floors and riverbeds from it. */
    private FloatMask flow;

    @Override
    public void initialize(SCMap map, RandomGenerator.SplittableGenerator random,
                           GeneratorParameters generatorParameters, SymmetrySettings symmetrySettings) {
        super.initialize(map, random, generatorParameters, symmetrySettings);
        flow = new FloatMask(map.getSize() + 1, random.split(), symmetrySettings, "flow");
    }

    @Override
    protected void setupTerrainPipeline() {
        int size = map.getSize() + 1;
        float waterHeight = map.getBiome().waterSettings().elevation();
        CampaignLand.Land land = CampaignLand.generate(random.nextLong(), size);
        float[] height = land.height();
        float[] share = land.flow();
        heightmap.setSize(size).set((x, y) -> StrictMath.max(0f, waterHeight + height[x + y * size]));
        flow.setSize(size).set((x, y) -> share[x + y * size]);
    }

    @Override
    protected void setupSpawnMaskPipeline() {
        super.setupSpawnMaskPipeline();
        int size = map.getSize() + 1;
        spawnMask.multiply(passableLand.copy().removeAreasSmallerThan(size * size / MAIN_LAND_SHARE_DIVISOR))
                 .fillEdge(SPAWN_EDGE, false);
    }
}
