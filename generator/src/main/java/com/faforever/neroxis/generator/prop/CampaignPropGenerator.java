package com.faforever.neroxis.generator.prop;

import com.faforever.neroxis.generator.GeneratorParameters;
import com.faforever.neroxis.generator.terrain.CampaignTerrainGenerator;
import com.faforever.neroxis.generator.terrain.TerrainGenerator;
import com.faforever.neroxis.map.SCMap;
import com.faforever.neroxis.map.SymmetrySettings;
import com.faforever.neroxis.mask.BooleanMask;
import com.faforever.neroxis.mask.FloatMask;
import org.jspecify.annotations.Nullable;

import java.util.random.RandomGenerator;

/**
 * Basic props plus woods along valley floors and lake shores where water gathers, and clear ground where a base
 * goes: wide around each spawn, a little around every mex.
 */
public class CampaignPropGenerator extends BasicPropGenerator {
    /** Clear radius around a spawn, in cells: room for a starting base. */
    private static final float BASE_CLEARING = 60;
    private static final float MEX_CLEARING = 6;
    private static final float HYDRO_CLEARING = 10;
    /** Valley woods grow where at least this share of the map drains through, about 300 cells at 20 km. */
    private static final float VALLEY_FLOW_SHARE = 3e-4f;
    /** Share of valley ground under woods. */
    private static final float VALLEY_WOOD_DENSITY = .45f;

    private @Nullable FloatMask flow;

    @Override
    public void initialize(SCMap map, RandomGenerator.SplittableGenerator random,
                           GeneratorParameters generatorParameters,
                           SymmetrySettings symmetrySettings, TerrainGenerator terrainGenerator) {
        super.initialize(map, random, generatorParameters, symmetrySettings, terrainGenerator);
        if (terrainGenerator instanceof CampaignTerrainGenerator campaign) {
            flow = new FloatMask(1, random.split(), symmetrySettings, "propFlow").init(campaign.getFlow());
        }
    }

    @Override
    protected void setupPropPipeline() {
        super.setupPropPipeline();
        if (flow == null) {
            return;
        }
        int mapSize = map.getSize();
        BooleanMask woods = new BooleanMask(mapSize / 4, random.split(), symmetrySettings, "valleyWoods");
        woods.randomize(VALLEY_WOOD_DENSITY).inflate(1).erode(.5f).setSize(mapSize + 1);
        BooleanMask valleys = flow.copyAsBooleanMask(VALLEY_FLOW_SHARE).inflate(5);
        treeMask.add(woods.multiply(valleys).multiply(passableLand.copy().deflate(2)).fillEdge(8, false));
    }

    @Override
    protected BooleanMask generatePropExclusionMasks() {
        BooleanMask noProps = new BooleanMask(1, null, symmetrySettings, "noProps");
        noProps.init(unbuildable.getFinalMask());
        generateExclusionZones(noProps, BASE_CLEARING, MEX_CLEARING, HYDRO_CLEARING);
        return noProps;
    }
}
