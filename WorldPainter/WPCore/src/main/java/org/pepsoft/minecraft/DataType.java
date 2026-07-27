package org.pepsoft.minecraft;

import java.util.Locale;

public enum DataType {
    REGION, ENTITIES, POI;

    /** Minecraft dimension folder name (always ASCII lowercase, locale-independent). */
    public String folderName() {
        return name().toLowerCase(Locale.ROOT);
    }
}