package org.pepsoft.worldpainter.exporting;

import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.Material;
import org.pepsoft.util.ProgressReceiver;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.pepsoft.minecraft.Material.AIR;
import static org.pepsoft.util.ProgressReceiver.OperationCancelled;

/**
 * Removes buried terrain interiors while preserving a shell of configurable thickness
 * (WorldEdit {@code //hollow <thickness>} semantics: expand the exterior inward before clearing).
 * Thickness {@code 0} uses iterative 6-neighbour peel (legacy single-block shell from fully enclosed blocks).
 */
public final class ChunkInteriorHollower {
    private static final int CANCELLATION_INTERVAL = 8192;
    /** Default shell thickness matching WorldEdit {@code //hollow 2}. */
    public static final int DEFAULT_HOLLOW_THICKNESS = 2;

    private ChunkInteriorHollower() {
    }

    public static void hollowRegion(MinecraftWorld world, Rectangle area, int minHeight, int maxHeight) throws OperationCancelled {
        hollowRegion(world, area, minHeight, maxHeight, DEFAULT_HOLLOW_THICKNESS, null);
    }

    public static void hollowRegion(MinecraftWorld world, Rectangle area, int minHeight, int maxHeight, ProgressReceiver progressReceiver) throws OperationCancelled {
        hollowRegion(world, area, minHeight, maxHeight, DEFAULT_HOLLOW_THICKNESS, progressReceiver);
    }

    public static void hollowRegion(MinecraftWorld world, Rectangle area, int minHeight, int maxHeight, int thickness, ProgressReceiver progressReceiver) throws OperationCancelled {
        hollowRegion(world, area, minHeight, maxHeight, thickness, progressReceiver, false);
    }

    /**
     * @param turboNoCaves when true (turbo export), skip scanning solid underground columns for air pockets.
     */
    public static void hollowRegion(MinecraftWorld world, Rectangle area, int minHeight, int maxHeight, int thickness, ProgressReceiver progressReceiver, boolean turboNoCaves) throws OperationCancelled {
        if (thickness <= 0) {
            hollowRegionPeel(world, area, minHeight, maxHeight, progressReceiver);
        } else {
            hollowRegionWithThickness(world, area, minHeight, maxHeight, thickness, progressReceiver, turboNoCaves);
        }
    }

    /** Legacy peel: one block layer at a time from fully enclosed interiors. */
    private static void hollowRegionPeel(MinecraftWorld world, Rectangle area, int minHeight, int maxHeight, ProgressReceiver progressReceiver) throws OperationCancelled {
        final int maxPasses = Math.max(128, (maxHeight - minHeight + 1) / 2);
        final int lowestChunkX = (area.x >> 4) - 1;
        final int highestChunkX = ((area.x + area.width - 1) >> 4) + 1;
        final int lowestChunkZ = (area.y >> 4) - 1;
        final int highestChunkZ = ((area.y + area.height - 1) >> 4) + 1;
        int cancellationCounter = 0;
        boolean changed = true;
        for (int pass = 0; pass < maxPasses && changed; pass++) {
            changed = false;
            if (progressReceiver != null) {
                progressReceiver.setProgress((float) pass / maxPasses);
                progressReceiver.checkForCancellation();
            }
            for (int chunkX = lowestChunkX; chunkX <= highestChunkX; chunkX++) {
                for (int chunkZ = lowestChunkZ; chunkZ <= highestChunkZ; chunkZ++) {
                    final Chunk chunk = world.getChunkForEditing(chunkX, chunkZ);
                    if (chunk == null) {
                        continue;
                    }
                    final int chunkWorldX = chunkX << 4;
                    final int chunkWorldZ = chunkZ << 4;
                    for (int dx = 0; dx < 16; dx++) {
                        final int worldX = chunkWorldX + dx;
                        if ((worldX < area.x) || (worldX >= area.x + area.width)) {
                            continue;
                        }
                        for (int dz = 0; dz < 16; dz++) {
                            final int worldZ = chunkWorldZ + dz;
                            if ((worldZ < area.y) || (worldZ >= area.y + area.height)) {
                                continue;
                            }
                            for (int y = minHeight; y < maxHeight; y++) {
                                if ((++cancellationCounter & (CANCELLATION_INTERVAL - 1)) == 0) {
                                    checkCancellation(progressReceiver);
                                }
                                if (isFullyEnclosed(world, worldX, y, worldZ, minHeight, maxHeight)
                                        && isHollowable(world.getMaterialAt(worldX, worldZ, y))) {
                                    world.setMaterialAt(worldX, worldZ, y, AIR);
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (progressReceiver != null) {
            progressReceiver.setProgress(1.0f);
        }
    }

    /**
     * WorldEdit-style {@code //hollow thickness}: flood all air/fluid as exterior, expand that
     * shell inward by {@code thickness} solid layers, then clear remaining hollowable blocks.
     * <p>Uses a single volume seed pass (no second full-volume fluid scan) and packs positions
     * into {@code long} queues to keep memory and GC pressure low on tall regions.
     */
    private static void hollowRegionWithThickness(MinecraftWorld world, Rectangle area, int minHeight, int maxHeight, int thickness, ProgressReceiver progressReceiver, boolean turboNoCaves) throws OperationCancelled {
        final int lowestChunkX = area.x >> 4;
        final int highestChunkX = (area.x + area.width - 1) >> 4;
        final int lowestChunkZ = area.y >> 4;
        final int highestChunkZ = (area.y + area.height - 1) >> 4;
        final ExteriorGrid exterior = new ExteriorGrid(lowestChunkX, lowestChunkZ, highestChunkX, highestChunkZ, minHeight, maxHeight);
        final ArrayDeque<Long> frontier = new ArrayDeque<>(65536);
        final ArrayDeque<Long> fluidPending = new ArrayDeque<>(65536);
        int cancellationCounter = 0;

        final int areaMaxX = area.x + area.width - 1;
        final int areaMaxZ = area.y + area.height - 1;
        final int totalColumns = Math.max(area.width * area.height, 1);
        int columnsDone = 0;

        // Seed: air/fluid + vertical world limits. Collect fluid seeds in the same pass.
        for (int chunkX = lowestChunkX; chunkX <= highestChunkX; chunkX++) {
            for (int chunkZ = lowestChunkZ; chunkZ <= highestChunkZ; chunkZ++) {
                final Chunk chunk = world.getChunkForEditing(chunkX, chunkZ);
                if (chunk == null) {
                    columnsDone += 256;
                    continue;
                }
                final int chunkWorldX = chunkX << 4;
                final int chunkWorldZ = chunkZ << 4;
                for (int dx = 0; dx < 16; dx++) {
                    final int worldX = chunkWorldX + dx;
                    if ((worldX < area.x) || (worldX > areaMaxX)) {
                        continue;
                    }
                    for (int dz = 0; dz < 16; dz++) {
                        final int worldZ = chunkWorldZ + dz;
                        if ((worldZ < area.y) || (worldZ > areaMaxZ)) {
                            continue;
                        }
                        columnsDone++;
                        if ((++cancellationCounter & (CANCELLATION_INTERVAL - 1)) == 0) {
                            checkCancellation(progressReceiver);
                        }
                        seedColumnExterior(world, exterior, frontier, fluidPending, worldX, worldZ, minHeight, maxHeight, turboNoCaves);
                    }
                }
            }
            if (progressReceiver != null) {
                progressReceiver.setProgress(0.35f * Math.min(1f, columnsDone / (float) totalColumns));
            }
        }

        if (progressReceiver != null) {
            progressReceiver.setProgress(0.35f);
            progressReceiver.checkForCancellation();
        }

        // Flood through connected air/water so underwater terrain hollows correctly.
        while (! fluidPending.isEmpty()) {
            if ((++cancellationCounter & (CANCELLATION_INTERVAL - 1)) == 0) {
                checkCancellation(progressReceiver);
            }
            final long packed = fluidPending.pollFirst();
            final int x = exterior.unpackX(packed);
            final int y = exterior.unpackY(packed);
            final int z = exterior.unpackZ(packed);
            tryEnqueueFluidNeighbor(world, exterior, frontier, fluidPending, x + 1, y, z, area, minHeight, maxHeight);
            tryEnqueueFluidNeighbor(world, exterior, frontier, fluidPending, x - 1, y, z, area, minHeight, maxHeight);
            tryEnqueueFluidNeighbor(world, exterior, frontier, fluidPending, x, y + 1, z, area, minHeight, maxHeight);
            tryEnqueueFluidNeighbor(world, exterior, frontier, fluidPending, x, y - 1, z, area, minHeight, maxHeight);
            tryEnqueueFluidNeighbor(world, exterior, frontier, fluidPending, x, y, z + 1, area, minHeight, maxHeight);
            tryEnqueueFluidNeighbor(world, exterior, frontier, fluidPending, x, y, z - 1, area, minHeight, maxHeight);
        }

        if (progressReceiver != null) {
            progressReceiver.setProgress(0.50f);
            progressReceiver.checkForCancellation();
        }

        for (int layer = 0; layer < thickness; layer++) {
            final int frontierSize = frontier.size();
            for (int i = 0; i < frontierSize; i++) {
                if ((++cancellationCounter & (CANCELLATION_INTERVAL - 1)) == 0) {
                    checkCancellation(progressReceiver);
                }
                final long packed = frontier.pollFirst();
                final int x = exterior.unpackX(packed);
                final int y = exterior.unpackY(packed);
                final int z = exterior.unpackZ(packed);
                expandExterior(world, exterior, frontier, x, y, z, area, minHeight, maxHeight);
            }
            if (progressReceiver != null) {
                progressReceiver.setProgress(0.50f + (0.20f * (layer + 1) / thickness));
                progressReceiver.checkForCancellation();
            }
        }

        // Clear non-exterior solid interiors (parallel over chunks; each chunk writes distinct blocks).
        if (progressReceiver != null) {
            progressReceiver.checkForCancellation();
        }
        final AtomicInteger clearedColumns = new AtomicInteger();
        final int[] chunkXRange = rangeInclusive(lowestChunkX, highestChunkX);
        java.util.Arrays.stream(chunkXRange).parallel().forEach(chunkX -> {
            for (int chunkZ = lowestChunkZ; chunkZ <= highestChunkZ; chunkZ++) {
                final Chunk chunk = world.getChunkForEditing(chunkX, chunkZ);
                if (chunk == null) {
                    clearedColumns.addAndGet(256);
                    continue;
                }
                final int chunkWorldX = chunkX << 4;
                final int chunkWorldZ = chunkZ << 4;
                for (int dx = 0; dx < 16; dx++) {
                    final int worldX = chunkWorldX + dx;
                    if ((worldX < area.x) || (worldX > areaMaxX)) {
                        continue;
                    }
                    for (int dz = 0; dz < 16; dz++) {
                        final int worldZ = chunkWorldZ + dz;
                        if ((worldZ < area.y) || (worldZ > areaMaxZ)) {
                            continue;
                        }
                        clearedColumns.incrementAndGet();
                        for (int y = minHeight; y < maxHeight; y++) {
                            if (! exterior.isExterior(worldX, y, worldZ)
                                    && isHollowable(world.getMaterialAt(worldX, worldZ, y))) {
                                world.setMaterialAt(worldX, worldZ, y, AIR);
                            }
                        }
                    }
                }
            }
        });
        if (progressReceiver != null) {
            progressReceiver.setProgress(1.0f);
        }
    }

    /**
     * Seed exterior air/fluid for one column. Turbo mode stops at the first solid block below open air/fluid
     * (caves disabled), avoiding a full-height scan of every underground column.
     */
    private static void seedColumnExterior(MinecraftWorld world, ExteriorGrid exterior, ArrayDeque<Long> frontier,
            ArrayDeque<Long> fluidPending, int worldX, int worldZ, int minHeight, int maxHeight,
            boolean turboNoCaves) {
        if (turboNoCaves) {
            markExteriorSeed(worldX, maxHeight - 1, worldZ, world, exterior, frontier, fluidPending);
            markExteriorSeed(worldX, minHeight, worldZ, world, exterior, frontier, fluidPending);
            for (int y = maxHeight - 2; y > minHeight; y--) {
                final Material material = world.getMaterialAt(worldX, worldZ, y);
                if (! isExteriorFluid(material)) {
                    break;
                }
                markExteriorSeed(worldX, y, worldZ, world, exterior, frontier, fluidPending);
            }
            return;
        }
        for (int y = minHeight; y < maxHeight; y++) {
            final Material material = world.getMaterialAt(worldX, worldZ, y);
            final boolean fluidOrEmpty = isExteriorFluid(material);
            if (fluidOrEmpty || (y == minHeight) || (y == maxHeight - 1)) {
                markExteriorSeed(worldX, y, worldZ, material, exterior, frontier, fluidPending);
            }
        }
    }

    private static void markExteriorSeed(int worldX, int y, int worldZ, MinecraftWorld world, ExteriorGrid exterior,
            ArrayDeque<Long> frontier, ArrayDeque<Long> fluidPending) {
        final Material material = world.getMaterialAt(worldX, worldZ, y);
        markExteriorSeed(worldX, y, worldZ, material, exterior, frontier, fluidPending);
    }

    private static void markExteriorSeed(int worldX, int y, int worldZ, Material material, ExteriorGrid exterior,
            ArrayDeque<Long> frontier, ArrayDeque<Long> fluidPending) {
        if (! exterior.mark(worldX, y, worldZ)) {
            return;
        }
        final long packed = exterior.pack(worldX, y, worldZ);
        frontier.addLast(packed);
        if (isExteriorFluid(material)) {
            fluidPending.addLast(packed);
        }
    }

    private static int[] rangeInclusive(int from, int to) {
        final int[] range = new int[to - from + 1];
        for (int i = 0; i < range.length; i++) {
            range[i] = from + i;
        }
        return range;
    }

    /**
     * Compact exterior map: one lazy {@link BitSet} per world column (x,z) for y in [minHeight, maxHeight).
     */
    private static final class ExteriorGrid {
        private final int originX;
        private final int originZ;
        private final int minHeight;
        private final int heightRange;
        private final int widthX;
        private final int widthZ;
        private final BitSet[][] columns;

        ExteriorGrid(int lowestChunkX, int lowestChunkZ, int highestChunkX, int highestChunkZ, int minHeight, int maxHeight) {
            originX = lowestChunkX << 4;
            originZ = lowestChunkZ << 4;
            this.minHeight = minHeight;
            heightRange = maxHeight - minHeight;
            widthX = (highestChunkX - lowestChunkX + 1) * 16;
            widthZ = (highestChunkZ - lowestChunkZ + 1) * 16;
            columns = new BitSet[widthX][widthZ];
        }

        boolean mark(int x, int y, int z) {
            final int lx = x - originX;
            final int lz = z - originZ;
            if ((lx < 0) || (lx >= widthX) || (lz < 0) || (lz >= widthZ)) {
                return false;
            }
            BitSet column = columns[lx][lz];
            if (column == null) {
                columns[lx][lz] = column = new BitSet(heightRange);
            }
            final int yi = y - minHeight;
            if ((yi < 0) || (yi >= heightRange) || column.get(yi)) {
                return false;
            }
            column.set(yi);
            return true;
        }

        boolean isExterior(int x, int y, int z) {
            final int lx = x - originX;
            final int lz = z - originZ;
            if ((lx < 0) || (lx >= widthX) || (lz < 0) || (lz >= widthZ)) {
                return false;
            }
            final BitSet column = columns[lx][lz];
            if (column == null) {
                return false;
            }
            final int yi = y - minHeight;
            return (yi >= 0) && (yi < heightRange) && column.get(yi);
        }

        long pack(int x, int y, int z) {
            return ((long) (x - originX) << 32) | ((long) (y - minHeight) << 16) | (long) (z - originZ);
        }

        int unpackX(long packed) {
            return originX + (int) (packed >> 32);
        }

        int unpackY(long packed) {
            return minHeight + (int) ((packed >> 16) & 0xFFFF);
        }

        int unpackZ(long packed) {
            return originZ + (int) (packed & 0xFFFF);
        }
    }

    private static void tryEnqueueFluidNeighbor(MinecraftWorld world, ExteriorGrid exterior, ArrayDeque<Long> frontier,
            ArrayDeque<Long> pending, int x, int y, int z, Rectangle area, int minHeight, int maxHeight) {
        if ((x < area.x) || (x >= area.x + area.width)
                || (z < area.y) || (z >= area.y + area.height)
                || (y < minHeight) || (y >= maxHeight)) {
            return;
        }
        if (exterior.isExterior(x, y, z)) {
            return;
        }
        final Material material = world.getMaterialAt(x, z, y);
        if (! isExteriorFluid(material)) {
            return;
        }
        if (exterior.mark(x, y, z)) {
            final long packed = exterior.pack(x, y, z);
            frontier.addLast(packed);
            pending.addLast(packed);
        }
    }

    /** Air, source water, and other fluid blocks act as exterior for hollowing. */
    private static boolean isExteriorFluid(Material material) {
        return (material != null) && (material.empty || material.watery || material.containsWater());
    }

    private static void expandExterior(MinecraftWorld world, ExteriorGrid exterior, ArrayDeque<Long> frontier, int x, int y, int z, Rectangle area, int minHeight, int maxHeight) {
        tryExpandSolidExterior(world, exterior, frontier, x + 1, y, z, area, minHeight, maxHeight);
        tryExpandSolidExterior(world, exterior, frontier, x - 1, y, z, area, minHeight, maxHeight);
        tryExpandSolidExterior(world, exterior, frontier, x, y + 1, z, area, minHeight, maxHeight);
        tryExpandSolidExterior(world, exterior, frontier, x, y - 1, z, area, minHeight, maxHeight);
        tryExpandSolidExterior(world, exterior, frontier, x, y, z + 1, area, minHeight, maxHeight);
        tryExpandSolidExterior(world, exterior, frontier, x, y, z - 1, area, minHeight, maxHeight);
    }

    private static void tryExpandSolidExterior(MinecraftWorld world, ExteriorGrid exterior, ArrayDeque<Long> frontier, int x, int y, int z, Rectangle area, int minHeight, int maxHeight) {
        if ((x < area.x) || (x >= area.x + area.width)
                || (z < area.y) || (z >= area.y + area.height)
                || (y < minHeight) || (y >= maxHeight)) {
            return;
        }
        if (exterior.isExterior(x, y, z)) {
            return;
        }
        final Material material = world.getMaterialAt(x, z, y);
        if ((material == null) || material.empty || isExteriorFluid(material)) {
            return;
        }
        if (exterior.mark(x, y, z)) {
            frontier.addLast(exterior.pack(x, y, z));
        }
    }

    private static void checkCancellation(ProgressReceiver progressReceiver) throws OperationCancelled {
        if (progressReceiver != null) {
            progressReceiver.checkForCancellation();
        }
    }

    private static boolean isHollowable(Material material) {
        return (material != null) && (! material.empty);
    }

    private static boolean isFullyEnclosed(MinecraftWorld world, int x, int y, int z, int minHeight, int maxHeight) {
        final Material material = world.getMaterialAt(x, z, y);
        if ((material == null) || material.empty) {
            return false;
        }
        return isSolidNeighbor(world, x, y + 1, z, minHeight, maxHeight)
                && isSolidNeighbor(world, x, y - 1, z, minHeight, maxHeight)
                && isSolidNeighbor(world, x + 1, y, z, minHeight, maxHeight)
                && isSolidNeighbor(world, x - 1, y, z, minHeight, maxHeight)
                && isSolidNeighbor(world, x, y, z + 1, minHeight, maxHeight)
                && isSolidNeighbor(world, x, y, z - 1, minHeight, maxHeight);
    }

    private static boolean isSolidNeighbor(MinecraftWorld world, int x, int y, int z, int minHeight, int maxHeight) {
        if (y < minHeight || y >= maxHeight) {
            return true;
        }
        final Material material = world.getMaterialAt(x, z, y);
        return (material != null) && (! material.empty) && (! isExteriorFluid(material));
    }
}
