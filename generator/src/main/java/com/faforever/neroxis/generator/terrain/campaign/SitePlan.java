package com.faforever.neroxis.generator.terrain.campaign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A mission's places the land is shaped around, and the land paths that must join them. Positions are fractions of
 * the map (0..1, x east, z south) so a plan fits any map size; radii are in cells.
 */
public record SitePlan(List<Site> sites, List<Lane> lanes) {
    public enum Kind {
        /** A flat basin ringed by ridges, with passes that funnel attackers onto it. */
        CITY,
        /** Flat lowland where the players gate in; both spawns go here. */
        GATE,
        /** A plateau behind a ridge line facing the nearest city or gate. */
        ARRAY,
        /** Flat buildable ground for a base. */
        BASE
    }

    public record Site(String name, Kind kind, float x, float z, float radius) {}

    public record Lane(String from, String to) {}

    public SitePlan {
        sites = List.copyOf(sites);
        lanes = List.copyOf(lanes);
    }

    public static final SitePlan NONE = new SitePlan(List.of(), List.of());

    public boolean isEmpty() {
        return sites.isEmpty();
    }

    public Site site(String name) {
        return sites.stream()
                    .filter(site -> site.name().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("no site named " + name));
    }

    /**
     * Parses {@code name:KIND:x,z,radius} sites and {@code from-to} lanes, for example {@code city:CITY:0.5,0.55,45}
     * and {@code gate-city}.
     */
    public static SitePlan parse(List<String> siteSpecs, List<String> laneSpecs) {
        List<Site> sites = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (String spec : siteSpecs) {
            String[] parts = spec.split(":");
            String[] numbers = parts.length == 3 ? parts[2].split(",") : new String[0];
            if (parts.length != 3 || numbers.length != 3) {
                throw new IllegalArgumentException("site `" + spec + "` is not name:KIND:x,z,radius");
            }
            Kind kind;
            try {
                kind = Kind.valueOf(parts[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("site `" + spec + "`: kind must be one of CITY, GATE, ARRAY, BASE");
            }
            float x = Float.parseFloat(numbers[0]);
            float z = Float.parseFloat(numbers[1]);
            float radius = Float.parseFloat(numbers[2]);
            if (x < 0 || x > 1 || z < 0 || z > 1 || radius <= 0) {
                throw new IllegalArgumentException("site `" + spec + "`: x and z must be in 0..1, radius above 0");
            }
            if (!names.add(parts[0])) {
                throw new IllegalArgumentException("two sites are named " + parts[0]);
            }
            sites.add(new Site(parts[0], kind, x, z, radius));
        }
        List<Lane> lanes = new ArrayList<>();
        for (String spec : laneSpecs) {
            String[] ends = spec.split("-");
            if (ends.length != 2 || !names.contains(ends[0]) || !names.contains(ends[1])) {
                throw new IllegalArgumentException("lane `" + spec + "` is not from-to between two named sites");
            }
            lanes.add(new Lane(ends[0], ends[1]));
        }
        return new SitePlan(sites, lanes);
    }
}
