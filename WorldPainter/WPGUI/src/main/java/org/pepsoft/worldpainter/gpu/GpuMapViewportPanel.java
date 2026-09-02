package org.pepsoft.worldpainter.gpu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Objects;

/**
 * Map viewport panel prototype. Always paints via Java2D tile blit.
 * LWJGL detection is informational only — no OpenGL/Vulkan draw path is wired.
 * Not attached to {@code WorldPainter} main view; experimental.
 */
public class GpuMapViewportPanel extends JPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(GpuMapViewportPanel.class);

    private final GpuTileTextureAtlas atlas;
    private int originTileX;
    private int originTileY;
    private float scale = 1.0f;
    private boolean lwjglAttempted;
    private boolean lwjglActive;

    public GpuMapViewportPanel(GpuTileTextureAtlas atlas) {
        this.atlas = Objects.requireNonNull(atlas, "atlas");
        setBackground(Color.DARK_GRAY);
        setOpaque(true);
    }

    public GpuTileTextureAtlas getAtlas() {
        return atlas;
    }

    public void setView(int originTileX, int originTileY, float scale) {
        this.originTileX = originTileX;
        this.originTileY = originTileY;
        this.scale = Math.max(0.05f, scale);
        repaint();
    }

    public boolean isLwjglActive() {
        return lwjglActive;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (GpuViewportFeature.active()) {
            ensureLwjgl();
        }
        paintTilesJava2d((Graphics2D) g);
    }

    private void ensureLwjgl() {
        if (lwjglAttempted) {
            return;
        }
        lwjglAttempted = true;
        try {
            Class.forName("org.lwjgl.opengl.GL");
            lwjglActive = true;
            LOGGER.info("LWJGL detected; GPU viewport flag on (texture blit via atlas + Java2D until GL canvas wired)");
        } catch (ClassNotFoundException e) {
            lwjglActive = false;
            LOGGER.info("LWJGL not on classpath; GpuMapViewportPanel using Java2D tile blit");
        }
    }

    private void paintTilesJava2d(Graphics2D g2) {
        int tilePx = Math.max(1, Math.round(128 * scale));
        int cols = getWidth() / tilePx + 2;
        int rows = getHeight() / tilePx + 2;
        BufferedImage scratch = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        int[] scratchData = ((DataBufferInt) scratch.getRaster().getDataBuffer()).getData();
        for (int ty = 0; ty < rows; ty++) {
            for (int tx = 0; tx < cols; tx++) {
                int tileX = originTileX + tx;
                int tileY = originTileY + ty;
                int[] argb = atlas.get(tileX, tileY);
                if (argb == null) {
                    continue;
                }
                System.arraycopy(argb, 0, scratchData, 0, Math.min(argb.length, scratchData.length));
                g2.drawImage(scratch, tx * tilePx, ty * tilePx, tilePx, tilePx, null);
            }
        }
    }
}
