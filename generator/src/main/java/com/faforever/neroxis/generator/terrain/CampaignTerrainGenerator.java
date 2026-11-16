package com.faforever.neroxis.generator.terrain;

import com.faforever.neroxis.generator.GeneratorParameters;
import com.faforever.neroxis.generator.terrain.campaign.CampaignLand;
import com.faforever.neroxis.generator.terrain.campaign.SitePlan;
import com.faforever.neroxis.map.Marker;
import com.faforever.neroxis.map.SCMap;
import com.faforever.neroxis.map.Spawn;
import com.faforever.neroxis.map.SymmetrySettings;
import com.faforever.neroxis.mask.FloatMask;
import com.faforever.neroxis.util.vector.Vector2;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.random.RandomGenerator;

/** Terrain shaped like GPG's campaign maps; spawns are placed on it afterwards. */
@Getter
public class CampaignTerrainGenerator extends SpawnLastTerrainGenerator {
    /** Spawns keep this many cells from the map edge, so a base and its expansions fit around them. */
    private static final int SPAWN_EDGE = 64;
    /** Spawns go only on walkable land that covers at least this share of the map, never in a small pocket. */
    private static final int MAIN_LAND_SHARE_DIVISOR = 8;
    /** Spawns on a gate site sit this share of its radius either side of its centre. */
    private static final float GATE_SPAWN_SPREAD = .5f;

    /** Places the land is shaped around; empty for a free map. Blank markers named SITE_<name> mark them. */
    @Setter
    private SitePlan sitePlan = SitePlan.NONE;
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
        CampaignLand.Land land = CampaignLand.generate(random.nextLong(), size, sitePlan);
        float[] height = land.height();
        float[] share = land.flow();
        heightmap.setSize(size).set((x, y) -> StrictMath.max(0f, waterHeight + height[x + y * size]));
        flow.setSize(size).set((x, y) -> share[x + y * size]);
    }

    /** With a gate site, every spawn goes on it, spread across its middle; otherwise spawns are placed as usual. */
    @Override
    public void placeSpawns() {
        int size = map.getSize() + 1;
        for (SitePlan.Site site : sitePlan.sites()) {
            map.addBlank(new Marker("SITE_" + site.name(), new Vector2(site.x() * (size - 1), site.z() * (size - 1))));
        }
        SitePlan.@Nullable Site gate = sitePlan.sites()
                                     .stream()
                                     .filter(site -> site.kind() == SitePlan.Kind.GATE)
                                     .findFirst()
                                     .orElse(null);
        if (gate == null) {
            super.placeSpawns();
            return;
        }
        int count = generatorParameters.spawnCount();
        float spread = gate.radius() * GATE_SPAWN_SPREAD;
        for (int k = 0; k < count; k++) {
            float offset = count == 1 ? 0 : spread * (2f * k / (count - 1) - 1);
            Vector2 position = new Vector2(gate.x() * (size - 1) + offset, gate.z() * (size - 1));
            map.addSpawn(new Spawn("ARMY_%d".formatted(k + 1), position, new Vector2(0, 0), 0));
        }
    }

    @Override
    protected void setupSpawnMaskPipeline() {
        super.setupSpawnMaskPipeline();
        int size = map.getSize() + 1;
        spawnMask.multiply(passableLand.copy().removeAreasSmallerThan(size * size / MAIN_LAND_SHARE_DIVISOR))
                 .fillEdge(SPAWN_EDGE, false);
    }
}
