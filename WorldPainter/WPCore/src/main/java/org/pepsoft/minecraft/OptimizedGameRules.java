package org.pepsoft.minecraft;

import org.jnbt.ByteTag;
import org.jnbt.CompoundTag;
import org.jnbt.IntTag;
import org.jnbt.Tag;

import java.util.HashMap;
import java.util.Map;

import static org.pepsoft.minecraft.Constants.TAG_DATA_;

/**
 * Performance-oriented game rules preset written to {@code data/minecraft/game_rules.dat} on export.
 */
public final class OptimizedGameRules {
    private OptimizedGameRules() {
    }

    /** Root tag for {@code game_rules.dat} (contains a {@code data} compound). */
    public static CompoundTag createFileRoot() {
        final Map<String, Tag> root = new HashMap<>();
        root.put(TAG_DATA_, createRulesData());
        return new CompoundTag("", root);
    }

    /** Game rules compound stored inside the {@code data} tag. */
    public static CompoundTag createRulesData() {
        final Map<String, Tag> rules = new HashMap<>();
        boolRule(rules, "doMobSpawning", false);
        boolRule(rules, "doDaylightCycle", false);
        boolRule(rules, "doWeatherCycle", false);
        boolRule(rules, "doFireTick", false);
        boolRule(rules, "mobGriefing", false);
        intRule(rules, "randomTickSpeed", 0);
        boolRule(rules, "doTraderSpawning", false);
        boolRule(rules, "doPatrolSpawning", false);
        boolRule(rules, "doWardenSpawning", false);
        boolRule(rules, "doInsomnia", false);
        boolRule(rules, "announceAdvancements", false);
        return new CompoundTag(TAG_DATA_, rules);
    }

    private static void boolRule(Map<String, Tag> rules, String name, boolean value) {
        rules.put(name, new ByteTag(name, (byte) (value ? 1 : 0)));
    }

    private static void intRule(Map<String, Tag> rules, String name, int value) {
        rules.put(name, new IntTag(name, value));
    }
}
