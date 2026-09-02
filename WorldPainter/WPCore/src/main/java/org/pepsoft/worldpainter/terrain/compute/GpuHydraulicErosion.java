package org.pepsoft.worldpainter.terrain.compute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Experimental GPU compute entry. No backend ships by default; always falls back to
 * {@link CpuHydraulicErosion} unless {@code org.pepsoft.worldpainter.gpuCompute.backend} is set.
 * Not wired into brush tools.
 */
public final class GpuHydraulicErosion implements TerrainComputeOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(GpuHydraulicErosion.class);
    private final TerrainComputeOp cpu = new CpuHydraulicErosion();
    private final TerrainComputeOp gpuBackend;

    public GpuHydraulicErosion() {
        this.gpuBackend = loadBackend();
    }

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("org.pepsoft.worldpainter.gpuCompute", "false"));
    }

    private static TerrainComputeOp loadBackend() {
        String className = System.getProperty("org.pepsoft.worldpainter.gpuCompute.backend");
        if (className == null || className.isBlank()) {
            return null;
        }
        try {
            Object instance = Class.forName(className).getConstructor().newInstance();
            if (instance instanceof TerrainComputeOp op) {
                LOGGER.info("GPU compute backend: {}", op.name());
                return op;
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("GPU compute backend {} failed: {}", className, e.toString());
        }
        return null;
    }

    @Override
    public String name() {
        return gpuBackend != null ? "gpu-hydraulic(" + gpuBackend.name() + ")" : "gpu-hydraulic(fallback-cpu)";
    }

    @Override
    public void apply(float[] heights, int width, int height, ComputeParams params) {
        if (enabled() && gpuBackend != null) {
            try {
                gpuBackend.apply(heights, width, height, params);
                return;
            } catch (RuntimeException e) {
                LOGGER.warn("GPU compute failed, falling back to CPU: {}", e.toString());
            }
        }
        cpu.apply(heights, width, height, params);
    }
}
