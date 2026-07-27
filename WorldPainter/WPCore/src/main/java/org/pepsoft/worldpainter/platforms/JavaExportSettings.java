/*

 * To change this license header, choose License Headers in Project Properties.

 * To change this template file, choose Tools | Templates

 * and open the template in the editor.

 */

package org.pepsoft.worldpainter.platforms;



import org.pepsoft.worldpainter.exporting.BlockBasedExportSettings;



import java.io.IOException;

import java.io.ObjectInputStream;

import java.util.Objects;



import static org.pepsoft.worldpainter.platforms.JavaExportSettings.FloatMode.DROP;

import static org.pepsoft.worldpainter.platforms.JavaExportSettings.FloatMode.LEAVE_FLOATING;



/**

 *

 * @author Pepijn

 */

public class JavaExportSettings extends BlockBasedExportSettings {

    public JavaExportSettings() {

        this(DROP, DROP, LEAVE_FLOATING, LEAVE_FLOATING, LEAVE_FLOATING, true, true, false, false, false, false, false, false, true);

    }



    public JavaExportSettings(FloatMode waterMode, FloatMode lavaMode, FloatMode sandMode, FloatMode gravelMode, FloatMode cementNode, boolean flowWater, boolean flowLava, boolean calculateSkyLight, boolean calculateBlockLight, boolean calculateLeafDistance, boolean removeFloatingLeaves, boolean makeAllLeavesPersistent, boolean removePlants) {

        this(waterMode, lavaMode, sandMode, gravelMode, cementNode, flowWater, flowLava, calculateSkyLight, calculateBlockLight, calculateLeafDistance, removeFloatingLeaves, makeAllLeavesPersistent, removePlants, true);

    }



    public JavaExportSettings(FloatMode waterMode, FloatMode lavaMode, FloatMode sandMode, FloatMode gravelMode, FloatMode cementNode, boolean flowWater, boolean flowLava, boolean calculateSkyLight, boolean calculateBlockLight, boolean calculateLeafDistance, boolean removeFloatingLeaves, boolean makeAllLeavesPersistent, boolean removePlants, boolean applyOptimizedGameRules) {

        if ((waterMode == null) || (lavaMode == null) || (sandMode == null) || (gravelMode == null) || (cementNode == null)) {

            throw new NullPointerException();

        }

        if (removeFloatingLeaves && (! calculateLeafDistance)) {

            throw new IllegalArgumentException("removeFloatingLeaves requires calculateLeafDistance");

        }

        this.waterMode = waterMode;

        this.lavaMode = lavaMode;

        this.sandMode = sandMode;

        this.gravelMode = gravelMode;

        this.cementMode = cementNode;

        this.flowWater = flowWater;

        this.flowLava = flowLava;

        this.calculateSkyLight = calculateSkyLight;

        this.calculateBlockLight = calculateBlockLight;

        this.calculateLeafDistance = calculateLeafDistance;

        this.removeFloatingLeaves = removeFloatingLeaves;

        this.makeAllLeavesPersistent = makeAllLeavesPersistent;

        this.leavePlants = ! removePlants;

        this.applyOptimizedGameRules = applyOptimizedGameRules;

    }



    public FloatMode getWaterMode() {

        return waterMode;

    }



    public FloatMode getLavaMode() {

        return lavaMode;

    }



    public FloatMode getSandMode() {

        return sandMode;

    }



    public FloatMode getGravelMode() {

        return gravelMode;

    }



    public FloatMode getCementMode() {

        return cementMode;

    }



    public boolean isFlowWater() {

        return flowWater;

    }



    public boolean isFlowLava() {

        return flowLava;

    }



    public boolean isCalculateSkyLight() {

        return calculateSkyLight;

    }



    public boolean isCalculateBlockLight() {

        return calculateBlockLight;

    }



    public boolean isCalculateLeafDistance() {

        return calculateLeafDistance;

    }



    public boolean isRemoveFloatingLeaves() {

        return removeFloatingLeaves;

    }



    public boolean isMakeAllLeavesPersistent() {

        return makeAllLeavesPersistent;

    }



    public boolean isApplyOptimizedGameRules() {

        return applyOptimizedGameRules;

    }



    public JavaExportSettings withMakeAllLeavesPersistent(final boolean makeAllLeavesPersistent) {

        return new JavaExportSettings(waterMode, lavaMode, sandMode, gravelMode, cementMode, flowWater, flowLava, calculateSkyLight, calculateBlockLight, calculateLeafDistance, removeFloatingLeaves, makeAllLeavesPersistent, ! leavePlants, applyOptimizedGameRules);

    }



    public JavaExportSettings withApplyOptimizedGameRules(final boolean applyOptimizedGameRules) {

        return new JavaExportSettings(waterMode, lavaMode, sandMode, gravelMode, cementMode, flowWater, flowLava, calculateSkyLight, calculateBlockLight, calculateLeafDistance, removeFloatingLeaves, makeAllLeavesPersistent, ! leavePlants, applyOptimizedGameRules);

    }



    /**

     * Fast export preset: skip lighting and leaf distance calculation (FAWE {@code //fast} equivalent).

     */

    public JavaExportSettings withoutLightingAndLeaves() {

        return new JavaExportSettings(waterMode, lavaMode, sandMode, gravelMode, cementMode, flowWater, flowLava, false, false, false, false, makeAllLeavesPersistent, ! leavePlants, applyOptimizedGameRules);

    }

    /** Skylight only: enough for underwater columns without full block-light / leaf passes. */
    public JavaExportSettings withSkylightOnly() {
        return new JavaExportSettings(waterMode, lavaMode, sandMode, gravelMode, cementMode, flowWater, flowLava,
                true, false, false, false, makeAllLeavesPersistent, ! leavePlants, applyOptimizedGameRules);
    }



    public static JavaExportSettings fastExportPreset() {

        return new JavaExportSettings(DROP, DROP, LEAVE_FLOATING, LEAVE_FLOATING, LEAVE_FLOATING, true, true, false, false, false, false, false, false, true);

    }



    /**

     * Recommended export preset: fast export plus optimized game rules for map building.

     */

    public static JavaExportSettings optimizedExportPreset() {

        return fastExportPreset();

    }

    /**
     * Aggressive export preset: no lighting, no fluid flow simulation, floating sand/gravel.
     */
    public static JavaExportSettings turboExportPreset() {
        return new JavaExportSettings(LEAVE_FLOATING, LEAVE_FLOATING, LEAVE_FLOATING, LEAVE_FLOATING, LEAVE_FLOATING,
                false, false, false, false, false, false, false, false, true);
    }



    public boolean defersChunkLightingToMinecraft() {

        return ! calculateSkyLight && ! calculateBlockLight;

    }



    @Override

    public boolean equals(Object o) {

        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        JavaExportSettings that = (JavaExportSettings) o;

        return flowWater == that.flowWater && flowLava == that.flowLava && calculateSkyLight == that.calculateSkyLight && calculateBlockLight == that.calculateBlockLight && calculateLeafDistance == that.calculateLeafDistance && waterMode == that.waterMode && lavaMode == that.lavaMode && sandMode == that.sandMode && gravelMode == that.gravelMode && cementMode == that.cementMode && removeFloatingLeaves == that.removeFloatingLeaves && makeAllLeavesPersistent == that.makeAllLeavesPersistent && leavePlants == that.leavePlants && applyOptimizedGameRules == that.applyOptimizedGameRules;

    }



    @Override

    public int hashCode() {

        return Objects.hash(waterMode, lavaMode, sandMode, gravelMode, cementMode, flowWater, flowLava, calculateSkyLight, calculateBlockLight, calculateLeafDistance, removeFloatingLeaves, makeAllLeavesPersistent, leavePlants, applyOptimizedGameRules);

    }



    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {

        in.defaultReadObject();

        if (version < 1) {

            calculateSkyLight = true;

            calculateBlockLight = true;

            calculateLeafDistance = true;

        }

        version = CURRENT_VERSION;

    }



    public boolean isRemovePlants() {

        return ! leavePlants;

    }



    final FloatMode waterMode, lavaMode, sandMode, gravelMode, cementMode;

    final boolean flowWater, flowLava;

    boolean calculateSkyLight, calculateBlockLight, calculateLeafDistance;

    final boolean removeFloatingLeaves, makeAllLeavesPersistent;

    int version = CURRENT_VERSION;

    final boolean leavePlants;

    boolean applyOptimizedGameRules = true;



    private static final int CURRENT_VERSION = 1;

    private static final long serialVersionUID = 1L;



    public enum FloatMode { DROP, SUPPORT, LEAVE_FLOATING }

}

