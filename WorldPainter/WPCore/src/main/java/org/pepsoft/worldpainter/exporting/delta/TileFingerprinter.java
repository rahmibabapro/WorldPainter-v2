package org.pepsoft.worldpainter.exporting.delta;

import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Layer;

import java.util.List;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.layers.Layer.DataSize.BIT;
import static org.pepsoft.worldpainter.layers.Layer.DataSize.BIT_PER_CHUNK;

/**
 * Deterministic 64-bit tile fingerprints for delta export.
 * Covers every height, terrain id, water level and stored layer cell. Sampling
 * is unsafe here: a missed cell would silently omit an edit from delta export.
 * Global exporter/material dependencies are not represented by this tile hash.
 */
public final class TileFingerprinter {
    private TileFingerprinter() {
    }

    public static long computeTileFingerprint(Tile tile) {
        if (tile == null) {
            return 0L;
        }
        long hash = 0xcbf29ce484222325L;
        final long prime = 0x100000001b3L;

        hash ^= (tile.getX() * 31L + tile.getY());
        hash *= prime;

        for (int x = 0; x < TILE_SIZE; x++) {
            for (int y = 0; y < TILE_SIZE; y++) {
                final int h = Float.floatToIntBits(tile.getHeight(x, y));
                hash = fnvMix(hash, prime, h);

                final int terrainOrdinal = tile.getTerrain(x, y).ordinal();
                hash = fnvMix(hash, prime, terrainOrdinal);

                hash = fnvMix(hash, prime, tile.getWaterLevel(x, y));
            }
        }

        // Seed set size/hash — object layers depend on planted seeds
        final java.util.HashSet<?> seeds = tile.getSeeds();
        hash = fnvMix(hash, prime, seeds != null ? seeds.size() : 0);
        if (seeds != null) {
            hash ^= seeds.hashCode();
            hash *= prime;
        }

        final List<Layer> layers = tile.getLayers();
        if (layers != null) {
            for (Layer layer : layers) {
                final String name = layer.getId();
                if (name != null) {
                    for (int i = 0; i < name.length(); i++) {
                        hash ^= name.charAt(i);
                        hash *= prime;
                    }
                }
                hash ^= layer.getDataSize().ordinal();
                hash *= prime;
                if (layer.getDataSize() == Layer.DataSize.NONE) {
                    continue;
                }
                final boolean bit = (layer.getDataSize() == BIT) || (layer.getDataSize() == BIT_PER_CHUNK);
                // BIT_PER_CHUNK has exactly one value per 16x16 chunk. All
                // other stored layer types require every individual cell.
                final int step = layer.getDataSize() == BIT_PER_CHUNK ? 16 : 1;
                for (int x = 0; x < TILE_SIZE; x += step) {
                    for (int y = 0; y < TILE_SIZE; y += step) {
                        if (bit) {
                            hash ^= tile.getBitLayerValue(layer, x, y) ? 1 : 0;
                        } else {
                            hash ^= tile.getLayerValue(layer, x, y) & 0xff;
                        }
                        hash *= prime;
                    }
                }
            }
        }

        return hash;
    }

    private static long fnvMix(long hash, long prime, int value) {
        hash ^= (value & 0xff);
        hash *= prime;
        hash ^= ((value >>> 8) & 0xff);
        hash *= prime;
        hash ^= ((value >>> 16) & 0xff);
        hash *= prime;
        hash ^= ((value >>> 24) & 0xff);
        hash *= prime;
        return hash;
    }
}
