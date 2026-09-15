package com.ghost.spacemod.weapon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ghost.spacemod.SpaceMod;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Data-driven design for the Cryo "Absolute Zero" ultimate — the single source of truth shared by
 * the mod (server logic + client render, sent in the cast packet) and the Attack Studio editor.
 * Defaults match the current hardcoded look, so an absent/blank file changes nothing.
 */
public final class EffectConfig {

    /** Flat design so the JSON is easy to hand-edit / drive from a slider UI. */
    public static final class Design {
        // gameplay
        public int cooldownTicks = 600;
        public double radius = 12.0;
        public double dmgWave = 4, dmgSpike = 8, dmgShatter = 6;
        public int eruptStart = 22;
        // circles (client render)
        public double circleRadius = 12.0;
        public double circleLowerY = 0.065, circleUpperY = 3.6;
        public double circleRotSpeed = 1.5;
        public int satelliteCount = 4;
        public String circleColorLower = "8be6ff", circleColorUpper = "8be6ff";
        // crystal crown (client render)
        public int clusters = 14, spikesPerCluster = 7;
        public double borderRadius = 12.0;
        public double heightMin = 2.5, heightMax = 6.5, width = 0.3, lean = 0.0;
        public double growTicks = 8;
        public String crystalColor = "9ee6ff";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Design current = new Design();

    private EffectConfig() {
    }

    public static Design get() {
        return current;
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("spacemod").resolve("absolute_zero.json");
    }

    /** Load from disk; write defaults if missing. Safe to call for reload. */
    public static void load() {
        try {
            Path p = file();
            if (Files.exists(p)) {
                Design d = GSON.fromJson(Files.readString(p), Design.class);
                if (d != null) {
                    current = d;
                    SpaceMod.LOGGER.info("[Space Exploration] loaded absolute_zero.json");
                    return;
                }
            } else {
                Files.createDirectories(p.getParent());
                Files.writeString(p, GSON.toJson(new Design()));
                SpaceMod.LOGGER.info("[Space Exploration] wrote default absolute_zero.json");
            }
        } catch (Exception e) {
            SpaceMod.LOGGER.warn("[Space Exploration] absolute_zero.json load failed, using defaults: " + e);
        }
        current = current == null ? new Design() : current;
    }

    /** Compact JSON of the current design, sent to clients in the cast packet. */
    public static String toJson() {
        return GSON.toJson(current);
    }

    /** Parse a design JSON received on the client; falls back to defaults on error. */
    public static Design parse(String json) {
        if (json == null || json.isEmpty()) {
            return new Design();
        }
        try {
            Design d = GSON.fromJson(json, Design.class);
            return d != null ? d : new Design();
        } catch (Exception e) {
            return new Design();
        }
    }
}
