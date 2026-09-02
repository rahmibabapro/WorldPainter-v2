package org.pepsoft.worldpainter.buildgraph;

import java.io.Serializable;
import java.util.Objects;

/**
 * Deterministic cache key for Derived Data Cache (DDC).
 */
public final class BuildKey implements Serializable {
    public final String nodeId;
    public final int nodeVersion;
    public final long inputHash;
    public final long seed;
    public final String cellCoordinate;

    public BuildKey(String nodeId, int nodeVersion, long inputHash, long seed, String cellCoordinate) {
        this.nodeId = nodeId;
        this.nodeVersion = nodeVersion;
        this.inputHash = inputHash;
        this.seed = seed;
        this.cellCoordinate = cellCoordinate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BuildKey)) return false;
        BuildKey buildKey = (BuildKey) o;
        return nodeVersion == buildKey.nodeVersion && inputHash == buildKey.inputHash && seed == buildKey.seed && Objects.equals(nodeId, buildKey.nodeId) && Objects.equals(cellCoordinate, buildKey.cellCoordinate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, nodeVersion, inputHash, seed, cellCoordinate);
    }

    @Override
    public String toString() {
        return nodeId + "_v" + nodeVersion + "_" + cellCoordinate + "_" + Long.toHexString(inputHash ^ seed);
    }

    private static final long serialVersionUID = 1L;
}
