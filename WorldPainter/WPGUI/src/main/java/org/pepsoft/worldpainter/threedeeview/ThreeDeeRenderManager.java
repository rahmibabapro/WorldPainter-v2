/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.worldpainter.threedeeview;

import org.pepsoft.util.jobqueue.UniqueJobQueue;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.biomeschemes.CustomBiomeManager;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.threedeeview.Tile3DRenderer.LayerVisibilityMode;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author pepijn
 */
public class ThreeDeeRenderManager {
    public ThreeDeeRenderManager(Dimension dimension, ColourScheme colourScheme, CustomBiomeManager customBiomeManager, int rotation) {
        this(dimension, colourScheme, customBiomeManager, rotation, null, Tile3DRendererOptions.preview());
    }

    public ThreeDeeRenderManager(Dimension dimension, ColourScheme colourScheme, CustomBiomeManager customBiomeManager, int rotation,
                                 Tile3DRenderCache renderCache, Tile3DRendererOptions options) {
        this.dimension = dimension;
        this.colourScheme = colourScheme;
        this.customBiomeManager = customBiomeManager;
        this.rotation = rotation;
        this.renderCache = renderCache;
        this.options = options;
    }
    
    /**
     * Add the tile to the start of the queue of tiles to be rendered.
     * 
     * @param tile The tile to be rendered.
     */
    public synchronized void renderTile(Tile tile) {
        if (renderCache != null) {
            Tile3DRenderCache.CacheKey key = Tile3DRenderCache.CacheKey.of(tile, rotation, layerVisibility, hiddenLayers, options);
            BufferedImage cached = renderCache.get(key);
            if (cached != null) {
                tileFinished(new RenderResult(tile, cached));
                return;
            }
        }
        if (jobQueue == null) {
            startThreads();
        }
        jobQueue.scheduleJobIfNotScheduled(new Tile3DRenderJob(tile));
    }
    
    /**
     * Collect the tiles rendered so far, if any. May be empty.
     * 
     * @return The tiles rendered so far, if any. May be empty.
     */
    @SuppressWarnings("unchecked") // Guaranteed by Java
    public synchronized Set<RenderResult> getRenderedTiles() {
        Set<RenderResult> rc = results;
        results = new HashSet<>();
        return rc;
    }

    /**
     * Blocks until all tiles currently on the queue are rendered.
     */
    public synchronized void renderAllTiles() throws InterruptedException {
        if (jobQueue != null) {
            jobQueue.drain();
            for (Background3DTileRenderer renderThread: renderThreads) {
                renderThread.waitToIdle();
            }
        }
    }
    
    public synchronized void stop() {
        if (renderThreads != null) {
            for (Background3DTileRenderer renderThread : renderThreads) {
                renderThread.halt();
            }
        }
        renderThreads = null;
        jobQueue = null;
        results.clear();
    }

    synchronized void tileFinished(RenderResult renderResult) {
        results.add(renderResult);
    }

    public void setLayerVisibility(LayerVisibilityMode layerVisibility) {
        this.layerVisibility = layerVisibility;
    }

    public void setHiddenLayers(Set<Layer> hiddenLayers) {
        this.hiddenLayers = hiddenLayers;
    }

    public synchronized void setRotation(int rotation) {
        if (this.rotation != rotation) {
            this.rotation = rotation;
            restartThreads();
        }
    }

    public synchronized void setOptions(Tile3DRendererOptions options) {
        if (! this.options.equalsOptions(options)) {
            this.options = options;
            restartThreads();
        }
    }

    public Tile3DRenderCache getRenderCache() {
        return renderCache;
    }

    public int getRotation() {
        return rotation;
    }

    public LayerVisibilityMode getLayerVisibility() {
        return layerVisibility;
    }

    public Set<Layer> getHiddenLayers() {
        return hiddenLayers;
    }

    public Tile3DRendererOptions getOptions() {
        return options;
    }

    private void restartThreads() {
        stop();
    }

    private void startThreads() {
        jobQueue = new UniqueJobQueue<>();
        int noOfThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        renderThreads = new Background3DTileRenderer[noOfThreads];
        for (int i = 0; i < noOfThreads; i++) {
            renderThreads[i] = new Background3DTileRenderer(dimension, colourScheme, customBiomeManager, rotation, jobQueue, this, layerVisibility, hiddenLayers, options);
            renderThreads[i].start();
        }
    }
 
    private final Dimension dimension;
    private final ColourScheme colourScheme;
    private final CustomBiomeManager customBiomeManager;
    private int rotation;
    private final Tile3DRenderCache renderCache;
    private Tile3DRendererOptions options;
    private HashSet<RenderResult> results = new HashSet<>();
    private Background3DTileRenderer[] renderThreads;
    private UniqueJobQueue<Tile3DRenderJob> jobQueue;
    private LayerVisibilityMode layerVisibility;
    private Set<Layer> hiddenLayers;
}