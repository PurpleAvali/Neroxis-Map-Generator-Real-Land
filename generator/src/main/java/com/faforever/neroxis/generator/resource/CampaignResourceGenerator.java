package com.faforever.neroxis.generator.resource;

import com.faforever.neroxis.map.Marker;
import com.faforever.neroxis.map.SymmetryType;
import com.faforever.neroxis.mask.BooleanMask;
import com.faforever.neroxis.util.DebugUtil;
import com.faforever.neroxis.util.vector.Vector2;
import com.faforever.neroxis.util.vector.Vector3;

import java.util.List;

/** Mass laid out by {@link CampaignMexSites}: the same reachable mass for every player, the rest in clusters. */
public class CampaignResourceGenerator extends ResourceGenerator {
    /** Mexes on a 20 km map, near Black Day's 63; scales with map area. */
    private static final int MEXES_AT_20KM = 60;
    private static final float CELLS_AT_20KM = 1024f;

    @Override
    public void setupPipeline() {
        resourceMask.init(passableLand).subtract(unbuildable).deflate(4).fillEdge(16, false);
    }

    @Override
    public void placeResources() {
        DebugUtil.timedRun("com.faforever.neroxis.map.generator", "generateResources", () -> {
            map.getMexes().clear();
            BooleanMask allowedMask = resourceMask.getFinalMask().subtract(mexDeadZone).limitToSymmetryRegion();
            BooleanMask passableMask = passableLand.getFinalMask();
            int size = allowedMask.getSize();
            if (passableMask.getSize() != size) {
                throw new IllegalStateException("passable mask is " + passableMask.getSize() + ", not " + size);
            }
            boolean[] allowed = new boolean[size * size];
            boolean[] passable = new boolean[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    allowed[x + y * size] = allowedMask.getPrimitive(x, y);
                    passable[x + y * size] = passableMask.getPrimitive(x, y);
                }
            }

            CampaignMexSites sites = new CampaignMexSites(allowed, size, random.split());
            int numSymPoints = symmetrySettings.spawnSymmetry().getNumSymPoints();
            for (int i = 0; i < map.getSpawnCount(); i += numSymPoints) {
                Vector3 spawn = map.getSpawn(i).getPosition();
                int cell = StrictMath.round(spawn.x()) + StrictMath.round(spawn.z()) * size;
                sites.placePlayer(cell, CampaignMexSites.reachable(passable, size, cell));
            }
            sites.fillMap(getMexCount() / numSymPoints);
            addMarkers(sites.placed(), size, allowedMask);

            hydroPlacer.placeHydros(generatorParameters.spawnCount(), resourceMask.getFinalMask().deflate(8));
        });
    }

    private void addMarkers(List<Integer> cells, int size, BooleanMask symmetryMask) {
        for (int cell : cells) {
            Vector3 position = new Vector3(new Vector2(cell % size + .5f, cell / size + .5f));
            int id = map.getMexCount();
            map.addMex(new Marker("Mex %d".formatted(id), position));
            List<Vector2> mirrors = symmetryMask.getSymmetryPoints(position, SymmetryType.SPAWN);
            for (int i = 0; i < mirrors.size(); i++) {
                map.addMex(new Marker("Mex %d sym %d".formatted(id, i), new Vector3(mirrors.get(i))));
            }
        }
    }

    @Override
    protected int getMexCount() {
        float side = generatorParameters.mapSize() / CELLS_AT_20KM;
        return StrictMath.round(MEXES_AT_20KM * side * side);
    }
}
