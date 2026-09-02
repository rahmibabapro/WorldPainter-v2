/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.worldpainter;

import org.jetbrains.annotations.NotNull;
import org.pepsoft.util.swing.TileListener;
import org.pepsoft.util.swing.TiledImageViewer;
import org.pepsoft.worldpainter.biomeschemes.CustomBiomeManager;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.renderers.VoidRenderer;
import org.pepsoft.worldpainter.ramps.ColourRamp;
import org.pepsoft.worldpainter.view.TilePyramidCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableSet;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

/**
 * WorldPainter tile provider for {@link TiledImageViewer}s. Provides tiles based on a {@link TileProvider}, e.g. a
 * WorldPainter {@link Dimension}.
 *
 * @author pepijn
 */
public class WPTileProvider implements org.pepsoft.util.swing.TileProvider, Dimension.Listener, Tile.Listener {
    public WPTileProvider(TileProvider tileProvider, ColourScheme colourScheme, CustomBiomeManager customBiomeManager, Set<Layer> hiddenLayers, boolean contourLines, int contourSeparation, TileRenderer.LightOrigin lightOrigin, boolean active, Effect effect, boolean transparentVoid, ColourRamp colourRamp) {
        this.tileProvider = tileProvider;
        this.colourScheme = colourScheme;
        this.hiddenLayers = ((hiddenLayers != null) && (hiddenLayers != HIDE_ALL_LAYERS)) ? new HashSet<>(hiddenLayers) : null;
        this.hideAllLayers = hiddenLayers == HIDE_ALL_LAYERS;
        this.contourLines = contourLines;
        this.contourSeparation = contourSeparation;
        this.lightOrigin = lightOrigin;
        this.active = active;
        this.customBiomeManager = customBiomeManager;
        tileRendererRef = createNewTileRendererRef();
        this.effect = effect;
        this.transparentVoid = transparentVoid;
        this.colourRamp = colourRamp;
        this.pyramidCache = new TilePyramidCache(256);
        this.pyramidRendererRef = createPyramidRendererRef();
    }

    public WPTileProvider(TileProvider tileProvider, ColourScheme colourScheme, CustomBiomeManager customBiomeManager, Set<Layer> hiddenLayers, boolean contourLines, int contourSeparation, TileRenderer.LightOrigin lightOrigin, ColourRamp colourRamp) {
        this(tileProvider, colourScheme, customBiomeManager, hiddenLayers, contourLines, contourSeparation, lightOrigin, false, null, true, colourRamp);
    }
    
    public synchronized void setHiddenLayers(Set<Layer> hiddenLayers) {
        if (hideAllLayers) {
            throw new IllegalStateException("Cannot set hiddenLayers when hideAllLayers is set");
        }
        this.hiddenLayers.clear();
        if (hiddenLayers != null) {
            this.hiddenLayers.addAll(hiddenLayers);
        }
        tileRendererRef = createNewTileRendererRef();
        pyramidRendererRef = createPyramidRendererRef();
        pyramidCache.clear();
    }

    public synchronized Set<Layer> getHiddenLayers() {
        return unmodifiableSet(hiddenLayers);
    }

    public boolean isHideAllLayers() {
        return hideAllLayers;
    }

    public void setHideAllLayers(boolean hideAllLayers) {
        if (hideAllLayers != this.hideAllLayers) {
            this.hideAllLayers = hideAllLayers;
            tileRendererRef = createNewTileRendererRef();
            pyramidRendererRef = createPyramidRendererRef();
            pyramidCache.clear();
        }
    }

    @Override
    public int getTileSize() {
        return TILE_SIZE;
    }

    @Override
    public boolean isTilePresent(int x, int y) {
        if (zoom == 0) {
            return isUnzoomedTilePresent(x, y);
        } else {
            final int scale = 1 << -zoom;
            for (int dx = 0; dx < scale; dx++) {
                for (int dy = 0; dy < scale; dy++) {
                    if (isUnzoomedTilePresent(x * scale + dx, y * scale + dy)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Override
    public boolean paintTile(final Image tileImage, final int x, final int y, final int imageX, final int imageY) {
        try {
            if (zoom == 0) {
                return paintUnzoomedTile(tileImage, x, y, imageX, imageY);
            } else {
                final Graphics2D g2 = (Graphics2D) tileImage.getGraphics();
                try {
                    g2.setComposite(AlphaComposite.Src);
                    g2.setBackground(new Color(0x00ffffff & VoidRenderer.getColour(), true));
                    final int scale = 1 << -zoom;
                    final int subSize = TILE_SIZE / scale;
                    final int lod = TilePyramidCache.selectLodForZoom(zoom);
                    for (int dx = 0; dx < scale; dx++) {
                        for (int dy = 0; dy < scale; dy++) {
                            final int worldTileX = x * scale + dx;
                            final int worldTileY = y * scale + dy;
                            if (isUnzoomedTilePresent(worldTileX, worldTileY)) {
                                BufferedImage cached = pyramidCache.getTileImage(worldTileX, worldTileY, lod);
                                if (cached == null) {
                                    ensurePyramidTile(worldTileX, worldTileY);
                                    cached = pyramidCache.getTileImage(worldTileX, worldTileY, lod);
                                }
                                if (cached != null) {
                                    g2.drawImage(cached, imageX + dx * subSize, imageY + dy * subSize, subSize, subSize, null);
                                } else {
                                    final Tile tile = tileProvider.getTile(worldTileX, worldTileY);
                                    tileRendererRef.get().renderTile(tile, tileImage, dx * subSize, dy * subSize);
                                }
                            } else {
                                g2.clearRect(imageX + dx * subSize, imageY + dy * subSize, subSize, subSize);
                            }
                        }
                    }
                    applyEffects(g2);
                } finally {
                    g2.dispose();
                }
                return true;
            }
        } catch (Throwable e) {
            logger.error("Exception while generating image for tile at {}, {}", x, y, e);
            return false;
        }
    }

    @Override
    public int getTilePriority(int x, int y) {
        if (zoom == 0) {
            return isUnzoomedTilePresent(x, y) ? 1 : 0;
        } else {
            final int scale = 1 << -zoom;
            for (int dx = 0; dx < scale; dx++) {
                for (int dy = 0; dy < scale; dy++) {
                    if (isUnzoomedTilePresent(x * scale + dx, y * scale + dy)) {
                        return 1;
                    }
                }
            }
            return 0;
        }
    }

    @Override
    public Rectangle getExtent() {
        return tileProvider.getExtent();
    }
    
    @Override
    public void addTileListener(TileListener tileListener) {
        if (active && listeners.isEmpty()) {
            ((Dimension) tileProvider).addDimensionListener(this);
            for (Tile tile: ((Dimension) tileProvider).getTiles()) {
                tile.addListener(this);
            }
        }
        if (! listeners.contains(tileListener)) {
            listeners.add(tileListener);
        }
    }

    @Override
    public void removeTileListener(TileListener tileListener) {
        listeners.remove(tileListener);
        if (active && listeners.isEmpty()) {
            for (Tile tile: ((Dimension) tileProvider).getTiles()) {
                tile.removeListener(this);
            }
            ((Dimension) tileProvider).removeDimensionListener(this);
        }
    }

    @Override
    public boolean isZoomSupported() {
        return true;
    }

    @Override
    public int getZoom() {
        return zoom;
    }

    @Override
    public void setZoom(int zoom) {
        if (zoom != this.zoom) {
            if (zoom > 0) {
                throw new UnsupportedOperationException("Zooming in not supported");
            }
            this.zoom = zoom;
            tileRendererRef = createNewTileRendererRef();
            // LOD set changes; drop cached mipmaps so zoom-out does not show stale LODs
            pyramidCache.clear();
        }
    }
    
    // Dimension.Listener

    @Override
    public void tilesAdded(Dimension dimension, Set<Tile> tiles) {
        for (Tile tile: tiles) {
            tile.addListener(this);
        }
        fireTilesChanged(tiles);
    }

    @Override
    public void tilesRemoved(Dimension dimension, Set<Tile> tiles) {
        for (Tile tile: tiles) {
            tile.removeListener(this);
        }
        fireTilesChanged(tiles);
    }

    @Override public void overlayAdded(Dimension dimension, int index, Overlay overlay) {}
    @Override public void overlayRemoved(Dimension dimension, int index, Overlay overlay) {}

    // Tile.Listener
    
    @Override
    public void heightMapChanged(Tile tile) {
        fireTileChanged(tile);
    }

    @Override
    public void terrainChanged(Tile tile) {
        fireTileChanged(tile);
    }

    @Override
    public void waterLevelChanged(Tile tile) {
        fireTileChanged(tile);
    }

    @Override
    public void layerDataChanged(Tile tile, Set<Layer> changedLayers) {
        fireTileChanged(tile);
    }

    @Override
    public void allBitLayerDataChanged(Tile tile) {
        fireTileChanged(tile);
    }

    @Override
    public void allNonBitlayerDataChanged(Tile tile) {
        fireTileChanged(tile);
    }

    @Override
    public void seedsChanged(Tile tile) {
        fireTileChanged(tile);
    }

    private boolean isUnzoomedTilePresent(int x, int y) {
        return tileProvider.isTilePresent(x, y);
    }
    
    private boolean paintUnzoomedTile(final Image tileImage, final int x, final int y, final int dx, final int dy) {
        if (isUnzoomedTilePresent(x, y)) {
            tileRendererRef.get().renderTile(tileProvider.getTile(x, y), tileImage, dx, dy);
            if (effect != null) {
                final Graphics2D g2 = (Graphics2D) tileImage.getGraphics();
                try {
                    applyEffects(g2);
                } finally {
                    g2.dispose();
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private void ensurePyramidTile(int worldTileX, int worldTileY) {
        synchronized (pyramidCache) {
            if (pyramidCache.getTileImage(worldTileX, worldTileY, 0) != null) {
                return;
            }
            final Tile tile = tileProvider.getTile(worldTileX, worldTileY);
            if (tile == null) {
                return;
            }
            final BufferedImage full = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
            pyramidRendererRef.get().renderTile(tile, full, 0, 0);
            pyramidCache.putTileImage(worldTileX, worldTileY, full);
        }
    }
    
    /**
     * Coalesce paint-stroke invalidations into one {@link TileListener#tilesChanged} bbox flush
     * at the end of the EDT queue (covers Dimension dirty-tile release batches).
     */
    private void fireTileChanged(Tile tile) {
        pendingDirtyTiles.add(tile);
        if (dirtyFlushScheduled.compareAndSet(false, true)) {
            javax.swing.SwingUtilities.invokeLater(this::flushDirtyTiles);
        }
    }

    private void flushDirtyTiles() {
        dirtyFlushScheduled.set(false);
        final Set<Tile> batch = new HashSet<>(pendingDirtyTiles);
        pendingDirtyTiles.clear();
        if (! batch.isEmpty()) {
            fireTilesChanged(batch);
        }
        // More tiles arrived while flushing — schedule another coalesced pass
        if ((! pendingDirtyTiles.isEmpty()) && dirtyFlushScheduled.compareAndSet(false, true)) {
            javax.swing.SwingUtilities.invokeLater(this::flushDirtyTiles);
        }
    }
    
    private void fireTilesChanged(Set<Tile> tiles) {
        Set<Point> coords = tiles.stream().map(this::getTileCoordinates).collect(Collectors.toSet());
        for (Tile tile : tiles) {
            pyramidCache.invalidate(tile.getX(), tile.getY());
        }
        for (TileListener listener: listeners) {
            listener.tilesChanged(this, coords);
        }
    }
    
    /**
     * Convert the actual tile coordinates to zoom-corrected (tile provider
     * coordinate system) coordinates.
     * 
     * @param tile The tile of which to convert the coordinates.
     * @return The coordinates of the tile in the tile provider coordinate
     *     system (corrected for zoom).
     */
    private Point getTileCoordinates(Tile tile) {
        return getTileCoordinates(tile.getX(), tile.getY());
    }

    /**
     * Convert the actual tile coordinates to zoom-corrected (tile provider
     * coordinate system) coordinates.
     * 
     * @param tileX The X tile coordinate to convert.
     * @param tileY The Y tile coordinate to convert.
     * @return The coordinates of the tile in the tile provider coordinate
     *     system (corrected for zoom).
     */
    private Point getTileCoordinates(final int tileX, final int tileY) {
        if (zoom == 0) {
            return new Point(tileX, tileY);
        } else if (zoom < 0) {
            return new Point(tileX >> -zoom, tileY >> -zoom);
        } else {
            return new Point(tileX << zoom, tileY << zoom);
        }
    }

    @NotNull
    private ThreadLocal<TileRenderer> createNewTileRendererRef() {
        return ThreadLocal.withInitial(() -> {
            TileRenderer tileRenderer = new TileRenderer(tileProvider, colourScheme, customBiomeManager, zoom, transparentVoid, colourRamp);
            synchronized (WPTileProvider.this) {
                if (hideAllLayers) {
                    tileRenderer.setHideAllLayers(true);
                } else if (hiddenLayers != null) {
                    tileRenderer.addHiddenLayers(hiddenLayers);
                }
            }
            tileRenderer.setContourLines(contourLines);
            tileRenderer.setContourSeparation(contourSeparation);
            tileRenderer.setLightOrigin(lightOrigin);
            return tileRenderer;
        });
    }

    @NotNull
    private ThreadLocal<TileRenderer> createPyramidRendererRef() {
        return ThreadLocal.withInitial(() -> {
            TileRenderer tileRenderer = new TileRenderer(tileProvider, colourScheme, customBiomeManager, 0, transparentVoid, colourRamp);
            synchronized (WPTileProvider.this) {
                if (hideAllLayers) {
                    tileRenderer.setHideAllLayers(true);
                } else if (hiddenLayers != null) {
                    tileRenderer.addHiddenLayers(hiddenLayers);
                }
            }
            tileRenderer.setContourLines(contourLines);
            tileRenderer.setContourSeparation(contourSeparation);
            tileRenderer.setLightOrigin(lightOrigin);
            return tileRenderer;
        });
    }

    private void applyEffects(Graphics2D g2) {
        if (effect == null) {
            return;
        }
        switch (effect) {
            case FADE_TO_FIFTY_PERCENT:
                g2.setComposite(AlphaComposite.SrcOver.derive(0.5f));
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, TILE_SIZE, TILE_SIZE);
                break;
            case FADE_TO_TWENTYFIVE_PERCENT:
                g2.setComposite(AlphaComposite.SrcOver.derive(0.75f));
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, TILE_SIZE, TILE_SIZE);
                break;
        }
    }

    private final TileProvider tileProvider;
    private final ColourScheme colourScheme;
    private final Set<Layer> hiddenLayers;
    private final boolean contourLines, active, transparentVoid;
    private final int contourSeparation;
    private final TileRenderer.LightOrigin lightOrigin;
    private final List<TileListener> listeners = new ArrayList<>();
    private final CustomBiomeManager customBiomeManager;
    private final Set<Tile> pendingDirtyTiles = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicBoolean dirtyFlushScheduled = new java.util.concurrent.atomic.AtomicBoolean();
    private final Effect effect;
    private final ColourRamp colourRamp;
    private final TilePyramidCache pyramidCache;
    private volatile ThreadLocal<TileRenderer> pyramidRendererRef;

    private int zoom = 0;
    private boolean hideAllLayers;
    private volatile ThreadLocal<TileRenderer> tileRendererRef;

    private static final Logger logger = LoggerFactory.getLogger(WPTileProvider.class);

    public static final Set<Layer> HIDE_ALL_LAYERS = Collections.emptySet();

    public enum Effect {
        FADE_TO_FIFTY_PERCENT, FADE_TO_TWENTYFIVE_PERCENT
    }
}