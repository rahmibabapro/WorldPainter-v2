package org.pepsoft.worldpainter.exporting;

import com.google.common.collect.ImmutableList;
import com.twelvemonkeys.imageio.util.ImageTypeSpecifiers;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static java.awt.image.DataBuffer.TYPE_FLOAT;
import static java.awt.image.DataBuffer.TYPE_INT;
import static java.util.Collections.singletonList;
import static javax.imageio.ImageWriteParam.MODE_EXPLICIT;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.exporting.HeightMapExporter.Format.FLOAT_NORMALISED;
import static org.pepsoft.worldpainter.exporting.HeightMapExporter.Format.INTEGER_HIGH_RESOLUTION;
import static org.pepsoft.worldpainter.exporting.HeightMapExporter.Format.INTEGER_LOW_RESOLUTION;

public class HeightMapExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeightMapExporter.class);

    public HeightMapExporter(Dimension dimension, Format format) {
        this.dimension = dimension;
        this.format = format;
        minHeight = dimension.getMinHeight();
        switch (format) {
            case INTEGER_HIGH_RESOLUTION:
                intHighestHeight = dimension.getHighestIntHeight();
                bitsRequired = (int) Math.ceil(Math.log(((intHighestHeight - minHeight + 1) << 8) - 1) / Math.log(2));
                floatLowestHeight = floatHighestHeight = -Float.MAX_VALUE;
                break;
            case INTEGER_LOW_RESOLUTION:
                intHighestHeight = dimension.getHighestIntHeight();
                bitsRequired = (int) Math.ceil(Math.log(Math.max(1, intHighestHeight - minHeight)) / Math.log(2));
                floatLowestHeight = floatHighestHeight = -Float.MAX_VALUE;
                break;
            case FLOAT_NORMALISED:
            case FLOAT_ONE_TO_ONE:
                final float[] range = dimension.getHeightRange();
                floatLowestHeight = range[0];
                floatHighestHeight = range[1];
                bitsRequired = 32;
                intHighestHeight = Integer.MIN_VALUE;
                break;
            default:
                throw new InternalError();
        }
    }

    public int getBitsRequired() {
        return bitsRequired;
    }

    public List<String> getSupportedFileExtensions() {
        if (bitsRequired <= 16) {
            return ImmutableList.of("png", "tiff");
        } else {
            return singletonList("tiff");
        }
    }

    public String getDefaultFilename() {
        final String defaultExtension = getSupportedFileExtensions().get(0);
        final StringBuilder sb = new StringBuilder();
        sb.append(dimension.getWorld().getName().replaceAll("\\s", "").toLowerCase());
        if (dimension.getAnchor().dim != DIM_NORMAL) {
            sb.append('_');
            sb.append(dimension.getName().replaceAll("\\s", "").toLowerCase());
        }
        switch (format) {
            case FLOAT_NORMALISED:
                sb.append("_normalised-");
                break;
            case INTEGER_HIGH_RESOLUTION:
                sb.append("_high-res-");
                break;
            default:
                sb.append('_');
                break;
        }
        sb.append("heightmap.");
        sb.append(defaultExtension);
        return sb.toString();
    }

    /**
     * @throws IllegalArgumentException when the export cannot proceed (oversized PNG, etc.)
     */
    public boolean exportToFile(File file) {
        final String type = file.getName().substring(file.getName().lastIndexOf('.') + 1).toUpperCase();
        final boolean tiff = type.equals("TIFF") || type.equals("TIF");
        final boolean useTiled = tiff && ! HeightMapSizeCheck.canAllocateSingleBuffer(dimension, format, bitsRequired);

        if (! HeightMapSizeCheck.fitsInJavaArray(dimension)) {
            throw new IllegalArgumentException(HeightMapSizeCheck.describeTooLarge(dimension, format, bitsRequired));
        }
        if ((! tiff) && useTiled) {
            // PNG cannot stream tiles in our path
            throw new IllegalArgumentException(HeightMapSizeCheck.describeTooLarge(dimension, format, bitsRequired)
                    + "\n\nChoose .tiff for large worlds (tiled streaming).");
        }

        try {
            if (useTiled) {
                LOGGER.info("Exporting height map via tiled TIFF streaming ({} tiles)",
                        dimension.getWidth() * dimension.getHeight());
                return exportTiledTiff(file);
            }
            return exportSingleBuffer(file, type);
        } catch (IOException e) {
            throw new RuntimeException("I/O error while exporting image", e);
        }
    }

    private boolean exportSingleBuffer(File file, String type) throws IOException {
        final BufferedImage image;
        final ImageWriter writer;
        final ImageWriteParam params;
        final float scale, offset;
        final double formatMax, dimensionMax;
        switch (format) {
            case INTEGER_LOW_RESOLUTION:
            case INTEGER_HIGH_RESOLUTION:
                if (bitsRequired > 16) {
                    final ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifiers.createGrayscale(32, TYPE_INT);
                    image = imageTypeSpecifier.createBufferedImage(dimension.getWidth() * TILE_SIZE, dimension.getHeight() * TILE_SIZE);
                    final Iterator<ImageWriter> writers = ImageIO.getImageWriters(imageTypeSpecifier, type);
                    if (! writers.hasNext()) {
                        return false;
                    }
                    writer = writers.next();
                    params = writer.getDefaultWriteParam();
                    params.setCompressionMode(MODE_EXPLICIT);
                    params.setCompressionType("LZW");
                    params.setCompressionQuality(0f);
                    formatDescription = "in 32-bit unsigned integer grayscale compressed " + type + " format.";
                    formatMax = Math.pow(2.0, 32.0);
                    dimensionMax = (format == INTEGER_LOW_RESOLUTION) ? (intHighestHeight - minHeight) : ((intHighestHeight - minHeight) << 8);
                } else {
                    image = new BufferedImage(dimension.getWidth() * TILE_SIZE, dimension.getHeight() * TILE_SIZE, (bitsRequired <= 8) ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_USHORT_GRAY);
                    final Iterator<ImageWriter> writers = ImageIO.getImageWriters(ImageTypeSpecifier.createFromRenderedImage(image), type);
                    if (! writers.hasNext()) {
                        return false;
                    }
                    writer = writers.next();
                    params = writer.getDefaultWriteParam();
                    formatDescription = ((bitsRequired <= 8) ? "in 8-bit" : "in 16-bit") + " unsigned integer grayscale " + type + " format.";
                    formatMax = Math.pow(2.0, (bitsRequired <= 8) ? 8.0 : 16.0);
                    dimensionMax = (format == INTEGER_LOW_RESOLUTION) ? (intHighestHeight - minHeight) : ((intHighestHeight - minHeight) << 8);
                }
                scale = offset = -Float.MAX_VALUE;
                break;
            case FLOAT_NORMALISED:
            case FLOAT_ONE_TO_ONE:
                final ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifiers.createGrayscale(32, TYPE_FLOAT);
                image = imageTypeSpecifier.createBufferedImage(dimension.getWidth() * TILE_SIZE, dimension.getHeight() * TILE_SIZE);
                final Iterator<ImageWriter> writers = ImageIO.getImageWriters(imageTypeSpecifier, type);
                if (! writers.hasNext()) {
                    return false;
                }
                writer = writers.next();
                params = writer.getDefaultWriteParam();
                params.setCompressionMode(MODE_EXPLICIT);
                params.setCompressionType("LZW");
                params.setCompressionQuality(0f);
                formatDescription = "in " + ((format == FLOAT_NORMALISED) ? "normalised " : "") + " floating point grayscale compressed " + type + " format.";
                scale = floatHighestHeight - floatLowestHeight;
                offset = floatLowestHeight;
                formatMax = (format == FLOAT_NORMALISED) ? 1.0 : floatHighestHeight;
                dimensionMax = (format == FLOAT_NORMALISED) ? 1.0 : floatHighestHeight;
                break;
            default:
                throw new InternalError();
        }
        if (dimensionMax / formatMax < 0.2) {
            formatDescription += "\n\n"
                    + "PLEASE NOTE: this height map will appear very dark when displayed as\n"
                    + "an image, because the exported values are very small compared to the\n"
                    + "theoretical range of the image format.";
        }
        fillRaster(image.getRaster(), 0, 0, dimension.getWidth() * TILE_SIZE, dimension.getHeight() * TILE_SIZE, scale, offset);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), params);
            return true;
        } finally {
            writer.dispose();
        }
    }

    /**
     * Stream WorldPainter tiles as TIFF tiles without allocating a full-dimension BufferedImage.
     */
    private boolean exportTiledTiff(File file) throws IOException {
        final int width = dimension.getWidth() * TILE_SIZE;
        final int height = dimension.getHeight() * TILE_SIZE;
        final ImageTypeSpecifier typeSpec = createTypeSpecifier();
        final Iterator<ImageWriter> writers = ImageIO.getImageWriters(typeSpec, "TIFF");
        if (! writers.hasNext()) {
            return false;
        }
        final ImageWriter writer = writers.next();
        final ImageWriteParam params = writer.getDefaultWriteParam();
        try {
            if (params.canWriteCompressed()) {
                params.setCompressionMode(MODE_EXPLICIT);
                final String[] types = params.getCompressionTypes();
                if (types != null) {
                    for (String t : types) {
                        if ("LZW".equalsIgnoreCase(t)) {
                            params.setCompressionType(t);
                            break;
                        }
                    }
                }
                params.setCompressionQuality(0f);
            }
            if (params.canWriteTiles()) {
                params.setTilingMode(MODE_EXPLICIT);
                params.setTiling(TILE_SIZE, TILE_SIZE, 0, 0);
            }
            formatDescription = "in tiled TIFF stream (" + HeightMapSizeCheck.bytesPerSample(format, bitsRequired)
                    + " bytes/sample), world " + width + "×" + height + ".";

            final float scale = floatHighestHeight - floatLowestHeight;
            final float offset = floatLowestHeight;
            final BufferedImage tileImage = typeSpec.createBufferedImage(TILE_SIZE, TILE_SIZE);

            try (ImageOutputStream out = ImageIO.createImageOutputStream(file)) {
                writer.setOutput(out);
                writer.prepareWriteEmpty(null, typeSpec, width, height, null, null, params);
                for (Tile tile : dimension.getTiles()) {
                    final int tileOffsetX = (tile.getX() - dimension.getLowestX()) * TILE_SIZE;
                    final int tileOffsetY = (tile.getY() - dimension.getLowestY()) * TILE_SIZE;
                    fillWorldPainterTile(tileImage.getRaster(), tile, scale, offset);
                    writer.prepareReplacePixels(0, new Rectangle(tileOffsetX, tileOffsetY, TILE_SIZE, TILE_SIZE));
                    writer.replacePixels(tileImage, params);
                    writer.endReplacePixels();
                }
                writer.endWriteEmpty();
            }
            return true;
        } finally {
            writer.dispose();
        }
    }

    private ImageTypeSpecifier createTypeSpecifier() {
        return switch (format) {
            case FLOAT_NORMALISED, FLOAT_ONE_TO_ONE -> ImageTypeSpecifiers.createGrayscale(32, TYPE_FLOAT);
            case INTEGER_LOW_RESOLUTION, INTEGER_HIGH_RESOLUTION -> {
                if (bitsRequired > 16) {
                    yield ImageTypeSpecifiers.createGrayscale(32, TYPE_INT);
                } else if (bitsRequired > 8) {
                    yield ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_USHORT_GRAY);
                } else {
                    yield ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_BYTE_GRAY);
                }
            }
        };
    }

    private void fillRaster(WritableRaster raster, int originX, int originY, int width, int height, float scale, float offset) {
        for (Tile tile : dimension.getTiles()) {
            final int tileOffsetX = (tile.getX() - dimension.getLowestX()) * TILE_SIZE;
            final int tileOffsetY = (tile.getY() - dimension.getLowestY()) * TILE_SIZE;
            fillWorldPainterTileInto(raster, tile, tileOffsetX, tileOffsetY, scale, offset);
        }
    }

    private void fillWorldPainterTile(WritableRaster raster, Tile tile, float scale, float offset) {
        fillWorldPainterTileInto(raster, tile, 0, 0, scale, offset);
    }

    private void fillWorldPainterTileInto(WritableRaster raster, Tile tile, int tileOffsetX, int tileOffsetY,
                                          float scale, float offset) {
        switch (format) {
            case INTEGER_HIGH_RESOLUTION:
                for (int dx = 0; dx < TILE_SIZE; dx++) {
                    for (int dy = 0; dy < TILE_SIZE; dy++) {
                        raster.setSample(tileOffsetX + dx, tileOffsetY + dy, 0, tile.getRawHeight(dx, dy));
                    }
                }
                break;
            case INTEGER_LOW_RESOLUTION:
                for (int dx = 0; dx < TILE_SIZE; dx++) {
                    for (int dy = 0; dy < TILE_SIZE; dy++) {
                        raster.setSample(tileOffsetX + dx, tileOffsetY + dy, 0, tile.getIntHeight(dx, dy) - minHeight);
                    }
                }
                break;
            case FLOAT_ONE_TO_ONE:
                for (int dx = 0; dx < TILE_SIZE; dx++) {
                    for (int dy = 0; dy < TILE_SIZE; dy++) {
                        raster.setSample(tileOffsetX + dx, tileOffsetY + dy, 0, tile.getHeight(dx, dy));
                    }
                }
                break;
            case FLOAT_NORMALISED:
                for (int dx = 0; dx < TILE_SIZE; dx++) {
                    for (int dy = 0; dy < TILE_SIZE; dy++) {
                        raster.setSample(tileOffsetX + dx, tileOffsetY + dy, 0, (tile.getHeight(dx, dy) - offset) / scale);
                    }
                }
                break;
        }
    }

    public String getFormatDescription() {
        return formatDescription;
    }

    private final Dimension dimension;
    private final Format format;
    private final int minHeight, intHighestHeight, bitsRequired;
    private final float floatLowestHeight, floatHighestHeight;
    private String formatDescription;

    public enum Format { INTEGER_LOW_RESOLUTION, INTEGER_HIGH_RESOLUTION, FLOAT_NORMALISED, FLOAT_ONE_TO_ONE}
}
