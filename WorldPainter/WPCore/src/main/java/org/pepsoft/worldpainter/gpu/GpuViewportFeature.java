package org.pepsoft.worldpainter.gpu;

/**
 * Feature flags for the LWJGL / GPU 2D map viewport (Phase 2).
 * Default off; enable with {@code -Dorg.pepsoft.worldpainter.gpuViewport=true}.
 */
public final class GpuViewportFeature {
    private GpuViewportFeature() {
    }

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("org.pepsoft.worldpainter.gpuViewport", "false"));
    }

    public static boolean java2dFallbackForced() {
        return Boolean.parseBoolean(System.getProperty("org.pepsoft.worldpainter.gpuViewport.forceJava2d", "false"));
    }

    /** True when GPU viewport should actually be used. */
    public static boolean active() {
        return enabled() && ! java2dFallbackForced();
    }
}
