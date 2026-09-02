package org.pepsoft.worldpainter.layers.bo2;

import org.pepsoft.minecraft.Entity;
import org.pepsoft.minecraft.Material;
import org.pepsoft.minecraft.TileEntity;
import org.pepsoft.util.AttributeKey;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.objects.AbstractObject;
import org.pepsoft.worldpainter.objects.MirroredObject;
import org.pepsoft.worldpainter.objects.RotatedObject;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.vecmath.Point3i;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static org.pepsoft.minecraft.Material.AIR;
import static org.pepsoft.worldpainter.objects.WPObject.ATTRIBUTE_OFFSET;

/**
 * Batch transform helpers for Axiom {@code .bp} blueprints.
 *
 * <p>Rotation and mirroring wrap with {@link RotatedObject}/{@link MirroredObject}.
 * Y offset mutates a clone's offset attribute (WorldPainter vertical = {@code offset.z}).
 * Air replacement wraps with a lightweight material filter.
 */
public final class AxiomBlueprintBatch {
    private AxiomBlueprintBatch() {
        // utility
    }

    /**
     * Return a clone of {@code blueprint} with vertical offset adjusted by {@code dy}
     * (WorldPainter Z / UI "Y axis").
     */
    public static AxiomBlueprint applyYOffset(AxiomBlueprint blueprint, int dy) {
        final AxiomBlueprint clone = blueprint.clone();
        final Point3i offset = clone.getOffset();
        clone.setAttribute(ATTRIBUTE_OFFSET, new Point3i(offset.x, offset.y, offset.z + dy));
        return clone;
    }

    /** Rotate 90° clockwise around the vertical axis. */
    public static WPObject rotate90(WPObject object, Platform platform) {
        return new RotatedObject(object, 1, platform);
    }

    /** Rotate 180° around the vertical axis. */
    public static WPObject rotate180(WPObject object, Platform platform) {
        return new RotatedObject(object, 2, platform);
    }

    /** Rotate 270° clockwise (90° counter-clockwise) around the vertical axis. */
    public static WPObject rotate270(WPObject object, Platform platform) {
        return new RotatedObject(object, 3, platform);
    }

    /**
     * Mirror the object. When {@code mirrorYAxis} is {@code true}, mirror across the
     * WorldPainter Y axis (Minecraft Z); otherwise mirror across the X axis.
     */
    public static WPObject mirror(WPObject object, boolean mirrorYAxis, Platform platform) {
        return new MirroredObject(object, mirrorYAxis, platform);
    }

    /**
     * Return a view where {@link Material#AIR} (and legacy air names)
     * are replaced with {@code replacement}.
     */
    public static WPObject replaceAir(WPObject object, Material replacement) {
        if (replacement == null) {
            throw new NullPointerException("replacement");
        }
        return new AirReplacingObject(object, replacement);
    }

    private static final class AirReplacingObject extends AbstractObject {
        private final WPObject object;
        private final Material replacement;

        AirReplacingObject(WPObject object, Material replacement) {
            this.object = object;
            this.replacement = replacement;
        }

        @Override
        public Point3i getDimensions() {
            return object.getDimensions();
        }

        @Override
        public Material getMaterial(int x, int y, int z) {
            final Material material = object.getMaterial(x, y, z);
            return isAir(material) ? replacement : material;
        }

        @Override
        public boolean getMask(int x, int y, int z) {
            if (object.getMask(x, y, z)) {
                return true;
            }
            // Former air cells become solid when replaced
            return isAir(object.getMaterial(x, y, z));
        }

        @Override
        public List<Entity> getEntities() {
            return object.getEntities();
        }

        @Override
        public List<TileEntity> getTileEntities() {
            return object.getTileEntities();
        }

        @Override
        public String getName() {
            return object.getName();
        }

        @Override
        public void setName(String name) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Map<String, Serializable> getAttributes() {
            return object.getAttributes();
        }

        @Override
        public void setAttributes(Map<String, Serializable> attributes) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public <T extends Serializable> void setAttribute(AttributeKey<T> key, T value) {
            throw new UnsupportedOperationException("Not supported");
        }

        private static boolean isAir(Material material) {
            return (material == null) || (material == AIR) || material.air;
        }

        private static final long serialVersionUID = 1L;
    }
}
