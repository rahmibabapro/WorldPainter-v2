package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dry-land height percentiles for texture/snow that must track the open world's
 * actual relief (e.g. Akendorf8k peaks ~120) instead of vanilla Y=150/160/190.
 * Snow starts near the high ridges (p90), not mid-map grass plateaus (p80).
 */
public final class WorldHeightBands {
    public static final int DEFAULT_SAMPLE_STEP = 24;
    public static final float SNOW_LINE_PERCENTILE = 0.90f;
    public static final float FULL_SNOW_PERCENTILE = 0.95f;
    /** Keep at least this vertical span between sparse and full snow. */
    public static final float MIN_SNOW_SPAN = 12.0f;

    private final float snowLine;
    private final float fullSnow;
    private final float sampleMin;
    private final float sampleMax;
    private final int sampleCount;

    public WorldHeightBands(float snowLine, float fullSnow, float sampleMin, float sampleMax, int sampleCount) {
        if (!Float.isFinite(snowLine) || !Float.isFinite(fullSnow) || fullSnow <= snowLine) {
            throw new IllegalArgumentException("Invalid snow band: " + snowLine + " → " + fullSnow);
        }
        this.snowLine = snowLine;
        this.fullSnow = fullSnow;
        this.sampleMin = sampleMin;
        this.sampleMax = sampleMax;
        this.sampleCount = sampleCount;
    }

    public float snowLine() { return snowLine; }
    public float fullSnow() { return fullSnow; }
    /** Alias: summit white / plains ceiling starts at the snow line. */
    public float summitWhiteMinimum() { return snowLine; }
    public float sampleMin() { return sampleMin; }
    public float sampleMax() { return sampleMax; }
    public int sampleCount() { return sampleCount; }

    public static WorldHeightBands from(Dimension dimension) {
        return from(dimension, DEFAULT_SAMPLE_STEP);
    }

    public static WorldHeightBands from(Dimension dimension, int sampleStep) {
        if (dimension == null) throw new IllegalArgumentException("dimension");
        int step = Math.max(8, sampleStep);
        List<Float> dry = new ArrayList<>();
        for (Tile tile : dimension.getTiles()) {
            int baseX = tile.getX() << 7, baseY = tile.getY() << 7;
            for (int ly = 0; ly < 128; ly += step) {
                for (int lx = 0; lx < 128; lx += step) {
                    float h = tile.getHeight(lx, ly);
                    if (!Float.isFinite(h)) continue;
                    int water = tile.getWaterLevel(lx, ly);
                    if (h <= water + 1.0f) continue;
                    dry.add(h);
                }
            }
        }
        if (dry.isEmpty()) {
            // Flat flooded map: keep a tiny band just above water so callers stay valid.
            float water = dimension.getTiles().isEmpty() ? 62f
                    : dimension.getTiles().iterator().next().getWaterLevel(0, 0);
            float line = water + 4f;
            return new WorldHeightBands(line, line + MIN_SNOW_SPAN, line, line + MIN_SNOW_SPAN, 0);
        }
        Collections.sort(dry);
        float min = dry.get(0), max = dry.get(dry.size() - 1);
        float line = percentile(dry, SNOW_LINE_PERCENTILE);
        float full = percentile(dry, FULL_SNOW_PERCENTILE);
        if (full - line < MIN_SNOW_SPAN) {
            // Prefer lifting the full-snow ceiling (may exceed sample max on flat maps)
            // so coverage still has a vertical ramp; only pull the line down if needed.
            full = line + MIN_SNOW_SPAN;
            if (full > max + MIN_SNOW_SPAN) {
                full = max + MIN_SNOW_SPAN;
            }
            if (full - line < MIN_SNOW_SPAN) {
                line = Math.max(min, full - MIN_SNOW_SPAN);
            }
        }
        if (full <= line) {
            line = min;
            full = line + MIN_SNOW_SPAN;
        }
        return new WorldHeightBands(line, full, min, max, dry.size());
    }

    static float percentile(List<Float> sortedAscending, float p) {
        if (sortedAscending.isEmpty()) return Float.NaN;
        float clamped = Math.max(0f, Math.min(1f, p));
        int idx = Math.min(sortedAscending.size() - 1,
                Math.max(0, (int) Math.floor((sortedAscending.size() - 1) * clamped)));
        return sortedAscending.get(idx);
    }

    @Override
    public String toString() {
        return "WorldHeightBands{snowLine=" + snowLine + ", fullSnow=" + fullSnow
                + ", samples=" + sampleCount + ", range=" + sampleMin + "…" + sampleMax + '}';
    }
}
