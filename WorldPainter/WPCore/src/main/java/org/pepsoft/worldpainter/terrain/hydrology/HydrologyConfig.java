package org.pepsoft.worldpainter.terrain.hydrology;

import java.io.Serializable;

/**
 * Parameters for deterministic Hydrology & Erosion analysis.
 */
public class HydrologyConfig implements Serializable {
    private boolean fillDepressions = true;
    private int flowThreshold = 15;
    private int dropletCount = 50000;
    private float inertia = 0.05f;
    private float sedimentCapacityFactor = 4.0f;
    private float minSedimentCapacity = 0.01f;
    private float erodeSpeed = 0.3f;
    private float depositSpeed = 0.3f;
    private float evaporateSpeed = 0.01f;
    private float gravity = 4.0f;
    private int maxDropletLifetime = 30;
    private long seed = 424242L;

    public boolean isFillDepressions() { return fillDepressions; }
    public void setFillDepressions(boolean fillDepressions) { this.fillDepressions = fillDepressions; }

    public int getFlowThreshold() { return flowThreshold; }
    public void setFlowThreshold(int flowThreshold) { this.flowThreshold = flowThreshold; }

    public int getDropletCount() { return dropletCount; }
    public void setDropletCount(int dropletCount) { this.dropletCount = dropletCount; }

    public float getInertia() { return inertia; }
    public void setInertia(float inertia) { this.inertia = inertia; }

    public float getSedimentCapacityFactor() { return sedimentCapacityFactor; }
    public void setSedimentCapacityFactor(float sedimentCapacityFactor) { this.sedimentCapacityFactor = sedimentCapacityFactor; }

    public float getMinSedimentCapacity() { return minSedimentCapacity; }
    public void setMinSedimentCapacity(float minSedimentCapacity) { this.minSedimentCapacity = minSedimentCapacity; }

    public float getErodeSpeed() { return erodeSpeed; }
    public void setErodeSpeed(float erodeSpeed) { this.erodeSpeed = erodeSpeed; }

    public float getDepositSpeed() { return depositSpeed; }
    public void setDepositSpeed(float depositSpeed) { this.depositSpeed = depositSpeed; }

    public float getEvaporateSpeed() { return evaporateSpeed; }
    public void setEvaporateSpeed(float evaporateSpeed) { this.evaporateSpeed = evaporateSpeed; }

    public float getGravity() { return gravity; }
    public void setGravity(float gravity) { this.gravity = gravity; }

    public int getMaxDropletLifetime() { return maxDropletLifetime; }
    public void setMaxDropletLifetime(int maxDropletLifetime) { this.maxDropletLifetime = maxDropletLifetime; }

    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }

    private static final long serialVersionUID = 1L;
}
