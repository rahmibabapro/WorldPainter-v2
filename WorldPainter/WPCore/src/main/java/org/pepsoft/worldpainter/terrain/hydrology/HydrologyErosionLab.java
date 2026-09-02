package org.pepsoft.worldpainter.terrain.hydrology;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;

import java.util.Random;

/**
 * Main engine executing Hydrology and Hydraulic Erosion.
 * Supports cancellation, progress reporting, deterministic seeds, and non-destructive analysis.
 */
public final class HydrologyErosionLab {

    private HydrologyErosionLab() {}

    public static HydrologyResult process(float[][] inputHeights, int width, int height,
                                          HydrologyConfig config, ProgressReceiver progress) throws OperationCancelled {
        if (config == null) {
            config = new HydrologyConfig();
        }

        if (progress != null) {
            progress.setProgress(0.1f);
            progress.checkForCancellation();
        }

        // Phase 1: D8 Flow Accumulation
        float[][] flowAcc = D8FlowEngine.computeFlowAccumulation(inputHeights, width, height, config.isFillDepressions());

        if (progress != null) {
            progress.setProgress(0.4f);
            progress.checkForCancellation();
        }

        // Phase 2: Droplet Hydraulic Erosion Simulation
        float[][] erodedHeights = cloneMatrix(inputHeights, width, height);
        float[][] sediment = new float[width][height];

        final Random rand = new Random(config.getSeed());
        final int droplets = config.getDropletCount();

        for (int iter = 0; iter < droplets; iter++) {
            if ((iter & 0x7FF) == 0 && progress != null) {
                float p = 0.4f + (0.6f * (float) iter / droplets);
                progress.setProgress(p);
                progress.checkForCancellation();
            }

            simulateDroplet(erodedHeights, sediment, width, height, rand, config);
        }

        if (progress != null) {
            progress.setProgress(1.0f);
        }

        return new HydrologyResult(width, height, flowAcc, sediment, erodedHeights);
    }

    private static void simulateDroplet(float[][] heights, float[][] sedimentMap, int width, int height,
                                        Random rand, HydrologyConfig config) {
        float posX = rand.nextFloat() * (width - 1);
        float posZ = rand.nextFloat() * (height - 1);
        float dirX = 0f;
        float dirZ = 0f;
        float speed = 1.0f;
        float water = 1.0f;
        float sediment = 0f;

        for (int lifetime = 0; lifetime < config.getMaxDropletLifetime(); lifetime++) {
            int nodeX = (int) posX;
            int nodeZ = (int) posZ;
            float cellOffsetX = posX - nodeX;
            float cellOffsetZ = posZ - nodeZ;

            if (nodeX < 0 || nodeX >= width - 1 || nodeZ < 0 || nodeZ >= height - 1) {
                break;
            }

            // Bilinear gradient interpolation
            float h00 = heights[nodeX][nodeZ];
            float h10 = heights[nodeX + 1][nodeZ];
            float h01 = heights[nodeX][nodeZ + 1];
            float h11 = heights[nodeX + 1][nodeZ + 1];

            float gradX = (h10 - h00) * (1 - cellOffsetZ) + (h11 - h01) * cellOffsetZ;
            float gradZ = (h01 - h00) * (1 - cellOffsetX) + (h11 - h10) * cellOffsetX;

            dirX = (dirX * config.getInertia() - gradX * (1 - config.getInertia()));
            dirZ = (dirZ * config.getInertia() - gradZ * (1 - config.getInertia()));

            float len = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (len != 0) {
                dirX /= len;
                dirZ /= len;
            }

            posX += dirX;
            posZ += dirZ;

            if (posX < 0 || posX >= width - 1 || posZ < 0 || posZ >= height - 1) {
                break;
            }

            int nextNodeX = (int) posX;
            int nextNodeZ = (int) posZ;
            float hNext = heights[nextNodeX][nextNodeZ];
            float deltaH = hNext - h00;

            float sedimentCapacity = Math.max(-deltaH * speed * water * config.getSedimentCapacityFactor(), config.getMinSedimentCapacity());

            if (sediment > sedimentCapacity || deltaH > 0) {
                // Deposit sediment
                float amountToDeposit = (deltaH > 0) ? Math.min(deltaH, sediment) : (sediment - sedimentCapacity) * config.getDepositSpeed();
                sediment -= amountToDeposit;
                heights[nodeX][nodeZ] += amountToDeposit * 0.5f;
                sedimentMap[nodeX][nodeZ] += amountToDeposit;
            } else {
                // Erode ground
                float amountToErode = Math.min((sedimentCapacity - sediment) * config.getErodeSpeed(), -deltaH);
                sediment += amountToErode;
                heights[nodeX][nodeZ] -= amountToErode * 0.5f;
            }

            speed = (float) Math.sqrt(Math.max(0, speed * speed + deltaH * config.getGravity()));
            water *= (1 - config.getEvaporateSpeed());
        }
    }

    private static float[][] cloneMatrix(float[][] src, int w, int h) {
        float[][] dest = new float[w][h];
        for (int i = 0; i < w; i++) {
            System.arraycopy(src[i], 0, dest[i], 0, h);
        }
        return dest;
    }
}
