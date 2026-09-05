package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.*;

import java.util.*;
import java.util.function.BiPredicate;

/** Streaming minimum-height overview plus a bounded tile cache. Never changes the DEM. */
final class RiverTerrainSurvey {
    static final int MAX_CELLS = 1_048_576;
    static final int[][] DIRECTIONS = {{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{1,-1},{-1,1},{1,1}};
    final int minX, minY, maxX, maxY, step, columns, rows;
    final float[] heights;
    final int[] xs, ys;
    final boolean[] wet;
    long sampled;
    private final Dimension dimension;
    private final Runnable check;
    private final BiPredicate<Integer,Integer> avoid;
    private final LinkedHashMap<Long, TileData> tiles = new LinkedHashMap<>(32, .75f, true);

    RiverTerrainSurvey(Dimension dimension, Runnable check, BiPredicate<Integer,Integer> avoid) {
        this.dimension = dimension; this.check = check; this.avoid = avoid;
        int lx = Integer.MAX_VALUE, ly = Integer.MAX_VALUE, hx = Integer.MIN_VALUE, hy = Integer.MIN_VALUE;
        for (Tile t : dimension.getTiles()) {
            check.run();
            lx = Math.min(lx, t.getX()); ly = Math.min(ly, t.getY());
            hx = Math.max(hx, t.getX()); hy = Math.max(hy, t.getY());
        }
        if (lx == Integer.MAX_VALUE) throw new IllegalArgumentException("Dünyada arazi yok.");
        long x0 = (long) lx * 128, y0 = (long) ly * 128;
        long x1 = ((long) hx + 1) * 128 - 1, y1 = ((long) hy + 1) * 128 - 1;
        if (x0 < Integer.MIN_VALUE + 128L || y0 < Integer.MIN_VALUE + 128L
                || x1 > Integer.MAX_VALUE - 128L || y1 > Integer.MAX_VALUE - 128L)
            throw new IllegalArgumentException("Koordinatlar güvenli arama sınırını aşıyor.");
        minX = (int)x0; minY = (int)y0; maxX = (int)x1; maxY = (int)y1;
        long width = x1-x0+1, height = y1-y0+1;
        int spacing = Math.max(8, (int)Math.ceil(Math.sqrt(width*(double)height/MAX_CELLS)));
        while (((width+spacing-1)/spacing)*((height+spacing-1)/spacing)>MAX_CELLS) spacing++;
        step = spacing; columns = (int)((width+step-1)/step); rows = (int)((height+step-1)/step);
        heights = new float[columns*rows]; Arrays.fill(heights, Float.POSITIVE_INFINITY);
        xs = new int[heights.length]; ys = new int[heights.length]; wet = new boolean[heights.length];
    }

    void scan() {
        // Visit only real tiles, not the bounding rectangle of a sparse world.
        List<Tile> ordered = new ArrayList<>(dimension.getTiles());
        ordered.sort(Comparator.comparingInt(Tile::getY).thenComparingInt(Tile::getX));
        for (Tile tile : ordered) {
            check.run();
            TileData data = data(tile.getX(), tile.getY());
            for (int y=0;y<128;y++) {
                check.run();
                for (int x=0;x<128;x++) {
                    int local=x+y*128; sampled++;
                    if (data.blocked[local]) continue;
                    int wx=tile.getX()*128+x, wy=tile.getY()*128+y;
                    int cell=(int)(((long)wx-minX)/step)+(int)(((long)wy-minY)/step)*columns;
                    float h=data.water[local]>Math.round(data.height[local]) ? data.water[local] : data.height[local];
                    // Equal-height minima should represent the middle of the
                    // cell, not its first raster corner (often the valley wall).
                    double cx=minX+(cell%columns+.5)*step-.5,cy=minY+(cell/columns+.5)*step-.5;
                    if (h<heights[cell] || (h==heights[cell]
                            && Math.hypot(wx-cx,wy-cy)<Math.hypot(xs[cell]-cx,ys[cell]-cy))) {
                        heights[cell]=h; xs[cell]=wx; ys[cell]=wy;
                        wet[cell]=data.water[local]>Math.round(data.height[local]);
                    }
                }
            }
        }
    }

    Sample sample(int x,int y) {
        TileData data=data(x>>7,y>>7);
        if (data==null) return new Sample(Float.NaN,0,true);
        int i=(x&127)+(y&127)*128;
        return new Sample(data.height[i],data.water[i],data.blocked[i]);
    }

    private TileData data(int x,int y) {
        long key=((long)x<<32)^(y&0xffffffffL);
        TileData cached=tiles.get(key);
        if (cached!=null) return cached;
        Tile tile=dimension.getTile(x,y);
        if (tile==null) return null;
        float[] h=new float[16384]; int[] w=new int[16384]; boolean[] b=new boolean[16384];
        List<Layer> protections=new ArrayList<>();
        for(Layer layer:new Layer[]{ReadOnly.INSTANCE,NotPresent.INSTANCE,NotPresentBlock.INSTANCE,
                org.pepsoft.worldpainter.layers.Void.INSTANCE,FloodWithLava.INSTANCE,River.INSTANCE})
            if(tile.hasLayer(layer))protections.add(layer);
        for (int ly=0;ly<128;ly++) {
            check.run();
            for (int lx=0;lx<128;lx++) {
                int i=lx+ly*128; h[i]=tile.getHeight(lx,ly); w[i]=tile.getWaterLevel(lx,ly);
                b[i]=!Float.isFinite(h[i]) || (avoid!=null && avoid.test(x*128+lx,y*128+ly));
                for(Layer layer:protections)if(tile.getBitLayerValue(layer,lx,ly)){b[i]=true;break;}
            }
        }
        cached=new TileData(h,w,b);
        if (tiles.size()>=128) tiles.remove(tiles.keySet().iterator().next());
        tiles.put(key,cached); return cached;
    }

    int neighbour(int i,int dx,int dy) {
        int x=i%columns+dx,y=i/columns+dy;
        if (x<0||x>=columns||y<0||y>=rows) return -1;
        int n=x+y*columns;
        if (!Float.isFinite(heights[n])) return -1;
        if (dx!=0&&dy!=0 && (!Float.isFinite(heights[(i/columns)*columns+x])
                || !Float.isFinite(heights[y*columns+i%columns]))) return -1;
        return n;
    }
    double edge(int x,int y) {return Math.min(Math.min((long)x-minX,(long)maxX-x),Math.min((long)y-minY,(long)maxY-y));}
    long estimatedBytes() {return heights.length*13L+128L*16384*9;}
    record Sample(float height,int water,boolean blocked) { boolean wet(){return water>Math.round(height);} }
    private record TileData(float[] height,int[] water,boolean[] blocked) { }
}
