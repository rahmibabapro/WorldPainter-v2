/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.pepsoft.worldpainter.operations;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.panels.DefaultFilter;
import org.pepsoft.worldpainter.simd.HeightBlendKernels;

import javax.swing.*;

/**
 *
 * @author pepijn
 */
public class Height extends AbstractBrushOperation {
    public Height(WorldPainter view) {
        super("Height", "Raise or lower the terrain", view, 100, "operation.height");
    }

    @Override
    public JPanel getOptionsPanel() {
        return optionsPanel;
    }

    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        final float adjustment = (float) Math.pow(dynamicLevel * getLevel() * 2, 2.0);
        final Dimension dimension = getDimension();
        if (dimension == null) {
            // Probably some kind of race condition
            return;
        }
        final float minZ, maxZ;
        if (getFilter() instanceof DefaultFilter) {
            final DefaultFilter filter = (DefaultFilter) getFilter();
            if (filter.getAboveLevel() != Integer.MIN_VALUE) {
                minZ = Math.max(filter.getAboveLevel(), dimension.getMinHeight());
            } else {
                minZ = dimension.getMinHeight();
            }
            if (filter.getBelowLevel() != Integer.MIN_VALUE) {
                maxZ = Math.min(filter.getBelowLevel(), dimension.getMaxHeight());
            } else {
                maxZ = dimension.getMaxHeight() - 1;
            }
        } else {
            minZ = dimension.getMinHeight();
            maxZ = dimension.getMaxHeight() - 1;
        }
        boolean applyTheme = options.isApplyTheme();
        dimension.setEventsInhibited(true);
        try {
            final int radius = getEffectiveRadius();
            // Large brushes: batch blend into arrays then apply (SIMD-friendly path)
            if (radius >= BATCH_RADIUS_THRESHOLD && HeightBlendKernels.enabled()) {
                tickBatched(dimension, centreX, centreY, inverse, adjustment, minZ, maxZ, applyTheme, radius);
            } else {
                tickScalar(dimension, centreX, centreY, inverse, adjustment, minZ, maxZ, applyTheme, radius);
            }
        } finally {
            dimension.setEventsInhibited(false);
        }
    }

    private void tickScalar(Dimension dimension, int centreX, int centreY, boolean inverse,
                            float adjustment, float minZ, float maxZ, boolean applyTheme, int radius) {
        for (int x = centreX - radius; x <= centreX + radius; x++) {
            for (int y = centreY - radius; y <= centreY + radius; y++) {
                final float currentHeight = dimension.getHeightAt(x, y);
                final float targetHeight = inverse ? Math.max(currentHeight - adjustment, minZ) : Math.min(currentHeight + adjustment, maxZ);
                final float strength = getFullStrength(centreX, centreY, x, y);
                if (strength > 0.0f) {
                    final float newHeight = HeightBlendKernels.blendOne(currentHeight, targetHeight, strength);
                    if (inverse ? (newHeight < currentHeight) : (newHeight > currentHeight)) {
                        dimension.setHeightAt(x, y, newHeight);
                        if (applyTheme) {
                            dimension.applyTheme(x, y);
                        }
                    }
                }
            }
        }
    }

    private void tickBatched(Dimension dimension, int centreX, int centreY, boolean inverse,
                             float adjustment, float minZ, float maxZ, boolean applyTheme, int radius) {
        final int diameter = radius * 2 + 1;
        final int capacity = diameter * diameter;
        ensureBuffers(capacity);
        int n = 0;
        for (int x = centreX - radius; x <= centreX + radius; x++) {
            for (int y = centreY - radius; y <= centreY + radius; y++) {
                final float strength = getFullStrength(centreX, centreY, x, y);
                if (strength <= 0.0f) {
                    continue;
                }
                final float currentHeight = dimension.getHeightAt(x, y);
                final float targetHeight = inverse ? Math.max(currentHeight - adjustment, minZ) : Math.min(currentHeight + adjustment, maxZ);
                xs[n] = x;
                ys[n] = y;
                currentBuf[n] = currentHeight;
                targetBuf[n] = targetHeight;
                strengthBuf[n] = strength;
                n++;
            }
        }
        if (n == 0) {
            return;
        }
        HeightBlendKernels.blend(currentBuf, targetBuf, strengthBuf, outBuf, n);
        for (int i = 0; i < n; i++) {
            final float currentHeight = currentBuf[i];
            final float newHeight = outBuf[i];
            if (inverse ? (newHeight < currentHeight) : (newHeight > currentHeight)) {
                dimension.setHeightAt(xs[i], ys[i], newHeight);
                if (applyTheme) {
                    dimension.applyTheme(xs[i], ys[i]);
                }
            }
        }
    }

    private void ensureBuffers(int capacity) {
        if (currentBuf != null && currentBuf.length >= capacity) {
            return;
        }
        currentBuf = new float[capacity];
        targetBuf = new float[capacity];
        strengthBuf = new float[capacity];
        outBuf = new float[capacity];
        xs = new int[capacity];
        ys = new int[capacity];
    }

    private static final int BATCH_RADIUS_THRESHOLD = 16;

    private float[] currentBuf, targetBuf, strengthBuf, outBuf;
    private int[] xs, ys;

    private final TerrainShapingOptions<Height> options = new TerrainShapingOptions<>();
    private final TerrainShapingOptionsPanel optionsPanel = new TerrainShapingOptionsPanel("Height", "<ul><li>Left-click to raise the terrain<li>Right-click to lower the terrain</ul>", options);
}
