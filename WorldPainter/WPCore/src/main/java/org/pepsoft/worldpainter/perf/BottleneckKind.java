package org.pepsoft.worldpainter.perf;

/**
 * Phase-0 export/UI bottleneck label used to gate later roadmap phases.
 */
public enum BottleneckKind {
    UI,
    EXPORT_CPU,
    DEFLATE,
    HEAP,
    MIXED,
    UNKNOWN
}
