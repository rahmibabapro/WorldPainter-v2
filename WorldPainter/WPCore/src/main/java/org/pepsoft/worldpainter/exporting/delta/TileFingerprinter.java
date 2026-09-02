package org.pepsoft.worldpainter.exporting.delta;

import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Layer;

import java.util.List;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.layers.Layer.DataSize.BIT;
import static org.pepsoft.worldpainter.layers.Layer.DataSize.BIT_PER_CHUNK;

/**
 * Deterministic 64-bit tile fingerprints for delta export.
 * Samples height, terrain id, and layer values (not height-only).
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

        for (int x = 0; x < TILE_SIZE; x += 4) {
            for (int y = 0; y < TILE_SIZE; y += 4) {
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
                final String name = layer.getName();
                if (name != null) {
                    for (int i = 0; i < name.length(); i++) {
                        hash ^= name.charAt(i);
                        hash *= prime;
                    }
                }
                hash ^= layer.getDataSize().ordinal();
                hash *= prime;
                final boolean bit = (layer.getDataSize() == BIT) || (layer.getDataSize() == BIT_PER_CHUNK);
                for (int x = 0; x < TILE_SIZE; x += 8) {
                    for (int y = 0; y < TILE_SIZE; y += 8) {
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
