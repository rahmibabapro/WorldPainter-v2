package org.pepsoft.worldpainter.terrain.hydrology;

import java.util.*;

/**
 * Deterministic D8 Drainage and Flow Accumulation algorithm.
 */
public final class D8FlowEngine {
    private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] DZ = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final float[] DIST = {1.4142f, 1.0f, 1.4142f, 1.0f, 1.0f, 1.4142f, 1.0f, 1.4142f};

    private D8FlowEngine() {}

    public static float[][] computeFlowAccumulation(float[][] heightmap, int width, int height, boolean fillDepressions) {
        float[][] filled = fillDepressions ? fillDepressions(heightmap, width, height) : heightmap;
        int[][] flowDir = new int[width][height];
        int[] inDegree = new int[width * height];

        // Step 1: Calculate D8 flow direction
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                float currentH = filled[x][z];
                float maxSlope = 0.0f;
                int bestDir = -1;

                for (int d = 0; d < 8; d++) {
                    int nx = x + DX[d];
                    int nz = z + DZ[d];
                    if (nx >= 0 && nx < width && nz >= 0 && nz < height) {
                        float diff = currentH - filled[nx][nz];
                        float slope = diff / DIST[d];
                        if (slope > maxSlope) {
                            maxSlope = slope;
                            bestDir = d;
                        }
                    }
                }

                flowDir[x][z] = bestDir;
                if (bestDir != -1) {
                    int targetX = x + DX[bestDir];
                    int targetZ = z + DZ[bestDir];
                    inDegree[targetZ * width + targetX]++;
                }
            }
        }

        // Step 2: Topological sorting & accumulation
        float[][] accumulation = new float[width][height];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                accumulation[x][z] = 1.0f; // Base rainfall
            }
        }

        Queue<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < width * height; i++) {
            if (inDegree[i] == 0) {
                queue.add(i);
            }
        }

        while (!queue.isEmpty()) {
            int idx = queue.poll();
            int x = idx % width;
            int z = idx / width;

            int dir = flowDir[x][z];
            if (dir != -1) {
                int targetX = x + DX[dir];
                int targetZ = z + DZ[dir];
                accumulation[targetX][targetZ] += accumulation[x][z];

                int targetIdx = targetZ * width + targetX;
                inDegree[targetIdx]--;
                if (inDegree[targetIdx] == 0) {
                    queue.add(targetIdx);
                }
            }
        }

        return accumulation;
    }

    public static float[][] fillDepressions(float[][] heightmap, int width, int height) {
        float[][] filled = new float[width][height];
        boolean[][] visited = new boolean[width][height];
        PriorityQueue<Cell> pq = new PriorityQueue<>(Comparator.comparingDouble(c -> c.h));

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                if (x == 0 || x == width - 1 || z == 0 || z == height - 1) {
                    filled[x][z] = heightmap[x][z];
                    visited[x][z] = true;
                    pq.add(new Cell(x, z, heightmap[x][z]));
                } else {
                    filled[x][z] = Float.POSITIVE_INFINITY;
                }
            }
        }

        while (!pq.isEmpty()) {
            Cell cell = pq.poll();
            for (int d = 0; d < 8; d++) {
                int nx = cell.x + DX[d];
                int nz = cell.z + DZ[d];
                if (nx >= 0 && nx < width && nz >= 0 && nz < height && !visited[nx][nz]) {
                    visited[nx][nz] = true;
                    float newH = Math.max(heightmap[nx][nz], cell.h);
                    filled[nx][nz] = newH;
                    pq.add(new Cell(nx, nz, newH));
                }
            }
        }

        return filled;
    }

    private static class Cell {
        final int x, z;
        final float h;
        Cell(int x, int z, float h) {
            this.x = x;
            this.z = z;
            this.h = h;
        }
    }
}
