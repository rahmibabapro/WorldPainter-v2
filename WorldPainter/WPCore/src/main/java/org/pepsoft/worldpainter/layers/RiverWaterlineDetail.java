package org.pepsoft.worldpainter.layers;

/**
 * Opts a generated river's dry top-waterline bank lip into local waterlogged
 * slab/stair export detail. Marked cells are the dry Y=W fringe beside open
 * water with air above — not the wet granite bed ({@link RiverSurfaceDetail}).
 * Tile-backed and undoable; the chunk factory ignores stale or leaky markers.
 * Worlds containing this auxiliary layer need an up-to-date WorldPainter V2;
 * the underlying .world container format is unchanged.
 */
public final class RiverWaterlineDetail extends Layer {
    private RiverWaterlineDetail() {
        super("org.pepsoft.worldpainter.v2.RiverWaterlineDetail", "River Waterline Detail",
                "Local waterlogged stair/slab on dry top-waterline river banks", DataSize.BIT, true, 27);
    }

    private Object readResolve() { return INSTANCE; }

    public static final RiverWaterlineDetail INSTANCE = new RiverWaterlineDetail();
    private static final long serialVersionUID = 2026091401L;
}
