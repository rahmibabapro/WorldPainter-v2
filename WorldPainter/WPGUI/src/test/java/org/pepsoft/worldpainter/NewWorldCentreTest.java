package org.pepsoft.worldpainter;

import org.junit.Test;

import java.awt.Point;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class NewWorldCentreTest {
    @Test public void evenSixBySixWorldUsesTrueBlockCentre() {
        Set<Point> tiles=rectangle(-3,-3,6,6);
        assertEquals(new Point(0,0),NewWorldDialog.centreOfTileExtent(tiles));
        assertEquals(new Point(0,0),NewWorldDialog.centreOfRectangularTileExtent(-3,-3,6,6));
    }

    @Test public void oddFiveByFiveWorldRetainsItsTrueBlockCentre() {
        Set<Point> tiles=rectangle(-2,-2,5,5);
        assertEquals(new Point(64,64),NewWorldDialog.centreOfTileExtent(tiles));
        assertEquals(new Point(64,64),NewWorldDialog.centreOfRectangularTileExtent(-2,-2,5,5));
    }

    @Test public void missingCentralTileChoosesNearestPresentBlockDeterministically() {
        Set<Point> tiles=rectangle(-1,-1,2,2);
        tiles.remove(new Point(0,0));
        Point centre=NewWorldDialog.centreOfTileExtent(tiles);
        assertEquals(new Point(-1,0),centre);
    }

    private static Set<Point> rectangle(int x0,int y0,int width,int height) {
        Set<Point> result=new LinkedHashSet<>();
        for(int y=y0;y<y0+height;y++)for(int x=x0;x<x0+width;x++)result.add(new Point(x,y));
        return result;
    }
}
