package com.faforever.neroxis.generator.texture;

import com.faforever.neroxis.generator.GeneratorParameters;
import com.faforever.neroxis.generator.terrain.CampaignTerrainGenerator;
import com.faforever.neroxis.generator.terrain.TerrainGenerator;
import com.faforever.neroxis.map.SCMap;
import com.faforever.neroxis.map.SymmetrySettings;
import com.faforever.neroxis.mask.BooleanMask;
import com.faforever.neroxis.mask.FloatMask;
import com.faforever.neroxis.mask.IntegerMask;
import com.faforever.neroxis.util.DebugUtil;
import com.faforever.neroxis.util.ImageUtil;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.random.RandomGenerator;

public abstract class LegacyTextureGenerator extends TextureGenerator {
    protected BooleanMask realLand;
    protected BooleanMask realPlateaus;
    protected FloatMask accentGroundTexture;
    protected FloatMask waterBeachTexture;
    protected FloatMask accentSlopesTexture;
    protected FloatMask accentPlateauTexture;
    protected FloatMask slopesTexture;
    protected FloatMask underWaterTexture;
    protected FloatMask rockTexture;
    protected FloatMask accentRockTexture;
    protected IntegerMask terrainType;
    /** Flow from campaign terrain, or null for every other terrain style. */
    private @Nullable FloatMask campaignFlow;
    /** Ground this far above the water counts as an upper tier; the lowest tier sits a little above it. */
    private static final float UPPER_TIER_ABOVE_WATER = 9f;
    /** A cell is a riverbed when this share of the map drains through it, about 4000 cells on a 20 km map. */
    private static final float RIVERBED_FLOW_SHARE = 4e-3f;

    @Override
    protected void setupTexturePipeline() {
        BooleanMask flat = slope.copyAsBooleanMask(.05f).invert();
        BooleanMask slopes = slope.copyAsBooleanMask(.15f);
        BooleanMask accentSlopes = slope.copyAsBooleanMask(.75f).invert().subtract(flat);
        BooleanMask rock = slope.copyAsBooleanMask(.75f);
        BooleanMask accentRock = slope.copyAsBooleanMask(.75f).inflate(2);
        BooleanMask realWater = realLand.copy().invert();

        BooleanMask shadowsInWater = shadowsMask.copy().multiply(realWater.copy().setSize(map.getSize()));
        shadows.setToValue(shadowsInWater.copy(), 1f);
        shadowsInWater.add(realLand.copy().setSize(map.getSize()), shadowsInWater.copy().inflate(6));
        shadows.subtract(realWater.copy().setSize(map.getSize()),
                         shadowsInWater.copyAsFloatMask(0, 1).blur(6))
               .blur(1);

        int textureSize = generatorParameters.mapSize() + 1;
        int mapSize = generatorParameters.mapSize();
        accentGroundTexture.setSize(textureSize)
                           .addPerlinNoise(mapSize / 8, 1f)
                           .addGaussianNoise(.05f)
                           .clampMax(1f)
                           .setToValue(realWater, 0f)
                           .blur(2);
        if (campaignFlow != null) {
            setupCampaignLayers(campaignFlow, rock);
        } else {
            accentPlateauTexture.setSize(textureSize)
                                .addPerlinNoise(mapSize / 16, 1f)
                                .addGaussianNoise(.05f)
                                .clampMax(1f)
                                .setToValue(realPlateaus.copy().invert(), 0f)
                                .blur(8);
            slopesTexture.init(slopes, 0f, .75f).blur(16).add(slopes, .5f).blur(16).clampMax(1f);
            accentSlopesTexture.setSize(textureSize)
                               .addPerlinNoise(mapSize / 16, .5f)
                               .addGaussianNoise(.05f)
                               .clampMax(1f)
                               .setToValue(accentSlopes.copy().invert(), 0f)
                               .blur(16);
        }
        underWaterTexture.init(realWater.deflate(1), 0f, .7f)
                         .add(scaledWaterDepth.copy().multiply(.3f))
                         .clampMax(1f)
                         .blur(1);
        waterBeachTexture.init(realWater.inflate(12).subtract(realPlateaus), 0f, 1f).blur(12);
        rockTexture.init(rock, 0f, 1f).blur(4).add(rock, 1f).blur(2).clampMax(1f);
        accentRockTexture.setSize(textureSize)
                         .addPerlinNoise(mapSize / 16, 1f)
                         .addGaussianNoise(.05f)
                         .clampMax(1f)
                         .setToValue(accentRock.copy().invert(), 0f)
                         .blur(2);
        texturesLowMask.setComponents(accentGroundTexture, accentPlateauTexture, slopesTexture, accentSlopesTexture);
        texturesHighMask.setComponents(waterBeachTexture, underWaterTexture, rockTexture, accentRockTexture);

        setupTerrainType(mapSize);
    }

    /**
     * Layers that follow the shape of campaign terrain: upper tiers darker than the lowland, ramps and gentle slopes
     * on their own layer, and scree where cliffs meet the ground below plus riverbeds where water gathers.
     */
    private void setupCampaignLayers(FloatMask flow, BooleanMask rock) {
        float water = biome.waterSettings().elevation();
        BooleanMask upperTiers = heightmap.copyAsBooleanMask(water + UPPER_TIER_ABOVE_WATER);
        BooleanMask gentle = slope.copyAsBooleanMask(.05f).subtract(rock);
        BooleanMask hollow = heightmap.copy().blur(6).subtract(heightmap).copyAsBooleanMask(.25f);
        BooleanMask cliffFoot = rock.copy().inflate(6).subtract(rock).multiply(hollow);
        BooleanMask riverbed = flow.copyAsBooleanMask(RIVERBED_FLOW_SHARE).inflate(2).multiply(realLand);

        accentPlateauTexture.init(upperTiers, 0f, .8f).blur(6);
        slopesTexture.init(gentle, 0f, .7f).blur(3);
        accentSlopesTexture.init(cliffFoot, 0f, 1f).add(riverbed, 1f).clampMax(1f).blur(3);
    }

    protected void setupTerrainType(int mapSize) {
        terrainType.setSize(mapSize);

        List<Integer> terrainTypes = map.getBiome().terrainMaterials().terrainTypes();
        terrainType.add(terrainTypes.getFirst())
                   .setToValue(accentGroundTexture.setSize(mapSize).copyAsBooleanMask(.5f), terrainTypes.get(1))
                   .setToValue(accentPlateauTexture.setSize(mapSize).copyAsBooleanMask(.5f), terrainTypes.get(2))
                   .setToValue(slopesTexture.setSize(mapSize).copyAsBooleanMask(.3f), terrainTypes.get(3))
                   .setToValue(accentSlopesTexture.setSize(mapSize).copyAsBooleanMask(.3f), terrainTypes.get(4))
                   .setToValue(waterBeachTexture.setSize(mapSize).copyAsBooleanMask(.5f), terrainTypes.get(5))
                   // We need to change the order here, otherwise accentRock will overwrite the rock texture completely
                   .setToValue(accentRockTexture.setSize(mapSize).copyAsBooleanMask(.35f), terrainTypes.get(7))
                   .setToValue(rockTexture.setSize(mapSize).copyAsBooleanMask(.55f), terrainTypes.get(6))
                   .setToValue(underWaterTexture.setSize(mapSize).copyAsBooleanMask(.7f), terrainTypes.get(8))
                   .setToValue(underWaterTexture.setSize(mapSize).copyAsBooleanMask(.8f), terrainTypes.get(9));
    }

    @Override
    public void initialize(SCMap map, RandomGenerator.SplittableGenerator random,
                           GeneratorParameters generatorParameters,
                           SymmetrySettings symmetrySettings, TerrainGenerator terrainGenerator) {
        super.initialize(map, random, generatorParameters, symmetrySettings, terrainGenerator);
        realLand = heightmap.copyAsBooleanMask(biome.waterSettings().elevation());
        if (terrainGenerator instanceof CampaignTerrainGenerator campaign) {
            campaignFlow = new FloatMask(1, random.split(), symmetrySettings, "campaignFlow").init(campaign.getFlow());
        }
        realPlateaus = heightmap.copyAsBooleanMask(biome.waterSettings().elevation() + 3f);
        accentGroundTexture = new FloatMask(1, random.split(), symmetrySettings, "accentGroundTexture");
        waterBeachTexture = new FloatMask(1, random.split(), symmetrySettings, "waterBeachTexture");
        accentSlopesTexture = new FloatMask(1, random.split(), symmetrySettings, "accentSlopesTexture");
        accentPlateauTexture = new FloatMask(1, random.split(), symmetrySettings, "accentPlateauTexture");
        slopesTexture = new FloatMask(1, random.split(), symmetrySettings, "slopesTexture");
        underWaterTexture = new FloatMask(1, random.split(), symmetrySettings, "underWaterTexture");
        rockTexture = new FloatMask(1, random.split(), symmetrySettings, "rockTexture");
        accentRockTexture = new FloatMask(1, random.split(), symmetrySettings, "accentRockTexture");
        terrainType = new IntegerMask(1, random.split(), symmetrySettings, "terrainType");
    }

    @Override
    public void setTextures() {
        DebugUtil.timedRun("com.faforever.neroxis.map.generator", "generateTextures", () -> {
            map.setTextureMasksScaled(map.getTextureMasksLow(), texturesLowMask.getFinalMask());
            map.setTextureMasksScaled(map.getTextureMasksHigh(), texturesHighMask.getFinalMask());
            map.setTerrainType(map.getTerrainType(), terrainType.getFinalMask());
            map.setMapNormalTexture(ImageUtil.getMapNormalTexture(normals.getFinalMask()));
            map.setMapInfoTexture(ImageUtil.getMapInfoTexture(scaledWaterDepth.getFinalMask(),
                                                              shadows.getFinalMask()));
        });
    }
}
