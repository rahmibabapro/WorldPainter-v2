package org.pepsoft.worldpainter.layers;

/**
 * Opts a generated river's wet granite floor into local slab/stair export detail.
 * A marked wet cell beside a real, supporting dry bank is rendered with the
 * mud-brick slab/stair family to form a natural one-cell shoreline fringe.
 * This is tile-backed, undoable data, not a global smoothing preference. The
 * chunk factory ignores it on dry terrain, lava and non-granite surfaces.
 * Like other V2 auxiliary layers, saved worlds containing it need an up-to-date
 * WorldPainter V2; the underlying .world container format is unchanged.
 */
public final class RiverSurfaceDetail extends Layer {
    private RiverSurfaceDetail() {
        super("org.pepsoft.worldpainter.v2.RiverSurfaceDetail", "River Surface Detail",
                "Local 2x2x2 granite-bed and mud-brick shoreline detail on generated rivers", DataSize.BIT, true, 26);
    }

    private Object readResolve() { return INSTANCE; }

    public static final RiverSurfaceDetail INSTANCE = new RiverSurfaceDetail();
    private static final long serialVersionUID = 2026090301L;
}
