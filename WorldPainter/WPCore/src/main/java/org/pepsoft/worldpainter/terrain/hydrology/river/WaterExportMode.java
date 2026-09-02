package org.pepsoft.worldpainter.terrain.hydrology.river;

/**
 * Water export strategies balancing vanilla stability, fluid physics, and plugin persistence.
 */
public enum WaterExportMode {
    /** 100% stable in vanilla: flat source pools (level=0) connected by rocky rapids and waterfalls. */
    VANILLA_STABLE_STAIR_STEP,
    /** Experimental vanilla: flowing water states (level=1..7) with physical containment. */
    VANILLA_FLOW_LEVELS,
    /** Permanent smooth flow: exports .wp-fluids.json for Paper/Purpur companion plugin tick freeze. */
    PLUGIN_FROZEN_FLOW,
    /** Custom modded fluid: true 8-layer mesh blocks. */
    MODDED_LAYERED_MESH
}
