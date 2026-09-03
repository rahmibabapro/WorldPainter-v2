import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.SnowDepth;
import org.pepsoft.worldpainter.layers.Void;
import org.pepsoft.worldpainter.heightMaps.ConstantHeightMap;
import org.pepsoft.worldpainter.themes.SimpleTheme;
import org.pepsoft.worldpainter.tools.scripts.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

/** In-memory integration measurement only: never reads/writes a user's .world file. */
public class VerifyTransfer {
    static final int SIZE=384;
    static final long SEED=771339L;
    static final Map<Material,Terrain> mapping=new LinkedHashMap<>();
    static final Map<Terrain,Material> inverse=new HashMap<>();
    static AxiomTextureProfile profile;
    static Path out;
    static String key(Material m){return AxiomTextureProfile.materialKey(m);}
    static Dimension dimension(){
        var platform=DefaultPlugin.JAVA_ANVIL_1_19;
        TileFactory factory=new HeightMapTileFactory(0,new ConstantHeightMap(90),-64,320,false,SimpleTheme.createSingleTerrain(Terrain.STONE,-64,320,0));
        Dimension d=new Dimension(new World2(platform,-64,320),"Integration measurement",0,factory,NORMAL_DETAIL);
        for(int ty=0;ty<SIZE/128;ty++)for(int tx=0;tx<SIZE/128;tx++){Tile t=factory.createTile(tx,ty);for(int y=0;y<128;y++)for(int x=0;x<128;x++){t.setWaterLevel(x,y,0);t.setTerrain(x,y,Terrain.STONE);}d.addTile(t);}return d;
    }
    static boolean valid(int x,int y){return x>=0&&y>=0&&x<profile.getWidth()&&y<profile.getLength()&&profile.getSurfaceMaterial(x,y)!=null;}
    static double h(int x,int y,double fallback){return valid(x,y)?profile.getSurfaceHeight(x,y):fallback;}
    static double interpolate(double x,double y){int xi=(int)Math.floor(x),yi=(int)Math.floor(y);double fx=x-xi,fy=y-yi,base=h((int)Math.round(x),(int)Math.round(y),0);return (1-fy)*((1-fx)*h(xi,yi,base)+fx*h(xi+1,yi,base))+fy*((1-fx)*h(xi,yi+1,base)+fx*h(xi+1,yi+1,base));}
    static Material material(Dimension d,int x,int y){return inverse.get(d.getTerrainAt(x,y));}
    static boolean dry(Dimension d,int x,int y){return x>=0&&y>=0&&x<SIZE&&y<SIZE&&!d.getBitLayerValueAt(Void.INSTANCE,x,y);}
    static int color(Material m){return m==null?0xeeeeee:ColourScheme.DEFAULT.getColour(m);}
    static void snowMask(Dimension d,int x,int y,boolean white){d.getTileForEditing(x>>7,y>>7).setBitLayerValue(Frost.INSTANCE,x&127,y&127,white);}
    static String finalKey(Dimension d,int x,int y){return d.getBitLayerValueAt(Frost.INSTANCE,x,y)?"minecraft:snow[layers="+d.getLayerValueAt(SnowDepth.INSTANCE,x,y)+"]":key(material(d,x,y));}
    static double adjacency(Dimension d,boolean finalSurface){long pairs=0,same=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){if(!dry(d,x,y))continue;for(int axis=0;axis<2;axis++){int nx=x+(axis==0?1:0),ny=y+(axis==1?1:0);if(!dry(d,nx,ny))continue;pairs++;if(finalSurface?finalKey(d,x,y).equals(finalKey(d,nx,ny)):material(d,x,y).name.equals(material(d,nx,ny).name))same++;}}return same/(double)pairs;}
    static Map<String,Long> counts(Dimension d,boolean finalSurface){Map<String,Long> m=new TreeMap<>();for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(dry(d,x,y))m.merge(finalSurface?finalKey(d,x,y):key(material(d,x,y)),1L,Long::sum);return m;}
    static double tv(Map<String,Long>a,Map<String,Long>b){double na=a.values().stream().mapToLong(Long::longValue).sum(),nb=b.values().stream().mapToLong(Long::longValue).sum(),s=0;Set<String> all=new HashSet<>(a.keySet());all.addAll(b.keySet());for(String k:all)s+=Math.abs(a.getOrDefault(k,0L)/na-b.getOrDefault(k,0L)/nb);return s/2;}
    static void conditional(Dimension d,String label,BufferedWriter writer)throws Exception{Map<String,Long> all=new TreeMap<>();float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(dry(d,x,y)){min=Math.min(min,d.getHeightAt(x,y));max=Math.max(max,d.getHeightAt(x,y));}for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(dry(d,x,y)){float hh=d.getHeightAt(x,y);int hb=Math.min(7,(int)((hh-min)/(max-min)*8));double dx=(sample(d,x+4,y,hh)-sample(d,x-4,y,hh))/8,dy=(sample(d,x,y+4,hh)-sample(d,x,y-4,hh))/8,slope=Math.toDegrees(Math.atan(Math.hypot(dx,dy)));int sb=slope<10?0:slope<25?1:slope<40?2:slope<55?3:4;all.merge(label+"\t"+hb+"\t"+sb+"\t"+finalKey(d,x,y),1L,Long::sum);}for(var e:all.entrySet())writer.write(e.getKey()+"\t"+e.getValue()+"\n");}
    static double sample(Dimension d,int x,int y,double fallback){return dry(d,x,y)?d.getHeightAt(x,y):fallback;}
    static void scenario(boolean warped)throws Exception{
        String name=warped?"changed-relief":"same-relief-quilt";
        Dimension expected=dimension(),actual=dimension(),random=dimension();Material[] raw=new Material[SIZE*SIZE];
        List<int[]> donors=new ArrayList<>();for(int y=0;y<profile.getLength();y++)for(int x=0;x<profile.getWidth();x++)if(valid(x,y))donors.add(new int[]{x,y});Random rng=new Random(SEED);
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
            double sx=warped?x+12*Math.sin(y/37.0):x,sy=warped?y+9*Math.sin(x/43.0):y;int ix=(int)Math.round(sx),iy=(int)Math.round(sy);boolean exists=valid(ix,iy);
            float height=(float)(warped?80+1.12*interpolate(sx,sy)+3*Math.sin(x/51.0)*Math.cos(y/67.0):90+(exists?profile.getSurfaceHeight(ix,iy):0));
            for(Dimension d:List.of(expected,actual,random)){d.setHeightAt(x,y,height);if(!exists){d.getTileForEditing(x>>7,y>>7).setBitLayerValue(Void.INSTANCE,x&127,y&127,true);d.setWaterLevelAt(x,y,200);}}
            if(!exists)continue;raw[x+y*SIZE]=profile.getSurfaceMaterial(ix,iy);expected.setTerrainAt(x,y,mapping.get(profile.getSurfaceMaterial(ix,iy)));snowMask(expected,x,y,profile.isSnowMask(ix,iy));
            int[] donor=donors.get(rng.nextInt(donors.size()));random.setTerrainAt(x,y,mapping.get(profile.getSurfaceMaterial(donor[0],donor[1])));snowMask(random,x,y,profile.isSnowMask(donor[0],donor[1]));
        }
        long start=System.nanoTime();var transfer=AxiomTextureTransfer.apply(actual,profile,mapping,SEED,null,(x,y,white)->snowMask(actual,x,y,white));double transferSeconds=(System.nanoTime()-start)/1e9;
        long expectedMask=0,actualMask=0,maskIntersection=0,maskUnion=0;boolean[] transferredMask=new boolean[SIZE*SIZE];
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(dry(expected,x,y)){boolean a=expected.getBitLayerValueAt(Frost.INSTANCE,x,y),b=actual.getBitLayerValueAt(Frost.INSTANCE,x,y);transferredMask[x+y*SIZE]=b;if(a)expectedMask++;if(b)actualMask++;if(a&&b)maskIntersection++;if(a||b)maskUnion++;}
        var expectedSnow=SmoothSnow.applyBlueprintMask(expected,SEED,null);var actualSnow=SmoothSnow.applyBlueprintMask(actual,SEED,null);SmoothSnow.applyBlueprintMask(random,SEED,null);
        long badSnow=0,protectedChanged=0,whiteTerrain=0,plantFill=0,heightChanged=0;long[] snowDepthCounts=new long[9];for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){if(actual.getHeightAt(x,y)!=expected.getHeightAt(x,y))heightChanged++;if(!dry(actual,x,y)){if(actual.getTerrainAt(x,y)!=Terrain.STONE)protectedChanged++;continue;}int depth=actual.getLayerValueAt(SnowDepth.INSTANCE,x,y);if(actual.getBitLayerValueAt(Frost.INSTANCE,x,y)){if(actual.getHeightAt(x,y)<160||depth<1||depth>8||!transferredMask[x+y*SIZE])badSnow++;else snowDepthCounts[depth]++;}Material m=material(actual,x,y);if(AxiomTextureProfile.isSnowMaskMaterial(m))whiteTerrain++;if(m.name.startsWith("minecraft:potted_")||m.name.endsWith("_sapling")||m.name.endsWith("_leaves")||m.name.endsWith("_stairs")||m.name.endsWith("_slab")||m.name.endsWith("_fence"))plantFill++;}
        Map<String,Long> ec=counts(expected,false),ac=counts(actual,false),rc=counts(random,false),ev=counts(expected,true),av=counts(actual,true);
        try(BufferedWriter writer=Files.newBufferedWriter(out.resolve(name+"-counts.tsv"),StandardCharsets.UTF_8)){writer.write("population\tstate\tcount\n");for(var group:Map.of("expected_surface",ec,"actual_surface",ac,"random_surface",rc,"expected_snow_surface",ev,"actual_snow_surface",av).entrySet())for(var e:group.getValue().entrySet())writer.write(group.getKey()+"\t"+e.getKey()+"\t"+e.getValue()+"\n");}
        try(BufferedWriter writer=Files.newBufferedWriter(out.resolve(name+"-conditional.tsv"),StandardCharsets.UTF_8)){writer.write("population\theight_band_8\tslope_band_0_10_25_40_55_90\tstate\tcount\n");conditional(expected,"expected",writer);conditional(actual,"actual",writer);conditional(random,"independent_random",writer);}
        BufferedImage image=new BufferedImage(SIZE*4+60,SIZE+65,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());g.setColor(Color.BLACK);String[] titles={"Raw blueprint material (on target relief)","Expected source terrain + smooth snow","Actual patch transfer + smooth snow","IID diagnostic (same source proportions)"};for(int p=0;p<4;p++)g.drawString(titles[p],p*(SIZE+20),18);
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){image.setRGB(x,y+30,color(raw[x+y*SIZE]));int p=1;for(Dimension d:List.of(expected,actual,random)){int rgb=dry(d,x,y)?(d.getBitLayerValueAt(Frost.INSTANCE,x,y)?0xf4f7fa:color(material(d,x,y))):0xeeeeee;image.setRGB(x+p*(SIZE+20),y+30,rgb);p++;}}
        g.setColor(Color.BLACK);g.drawString("One pixel = one block; diagnostic top colours (not a Minecraft screenshot). Measured white blocks remain below the snow mask.",0,SIZE+55);g.dispose();ImageIO.write(image,"png",out.resolve(name+"-comparison.png").toFile());
        String report="{\n  \"scenario\": \""+name+"\", \"targetSize\":384, \"identityShortcut\":"+transfer.identityReconstruction()+",\n  \"transferSeconds\":"+transferSeconds+", \"patches\":"+transfer.patches()+", \"ribbonBytes\":"+transfer.ribbonBytes()+",\n  \"painted\":"+transfer.paintedCells()+", \"untextured\":"+transfer.untexturedCells()+", \"protectedChanged\":"+protectedChanged+", \"heightChanged\":"+heightChanged+", \"mossRedirectedCells\":"+transfer.mossRedirectedCells()+",\n  \"expectedMaskCells\":"+expectedMask+", \"actualMaskCells\":"+actualMask+", \"maskIntersectionOverUnion\":"+(maskIntersection/(double)Math.max(1,maskUnion))+",\n  \"expectedSnowCells\":"+expectedSnow.snowCovered()+", \"actualSnowCells\":"+actualSnow.snowCovered()+", \"invalidSnowCells\":"+badSnow+", \"measuredWhiteTerrainCells\":"+whiteTerrain+", \"plantTerrainFillerCells\":"+plantFill+", \"snowDepthCounts0to8\":"+Arrays.toString(snowDepthCounts)+",\n  \"expectedSurfaceAdjacency\":"+adjacency(expected,false)+", \"actualSurfaceAdjacency\":"+adjacency(actual,false)+", \"randomSurfaceAdjacency\":"+adjacency(random,false)+",\n  \"surfaceProportionTV\":"+tv(ec,ac)+", \"snowSurfaceProportionTV\":"+tv(ev,av)+"\n}\n";
        Files.writeString(out.resolve(name+"-metrics.json"),report,StandardCharsets.UTF_8);System.out.println(report);
        if(transfer.identityReconstruction()||badSnow>0||protectedChanged>0||plantFill>0||heightChanged>0||transfer.untexturedCells()>0)throw new AssertionError("Concrete transfer invariant failed in "+name);
    }
    public static void main(String[]args)throws Exception{out=Path.of(args[1]);Files.createDirectories(out);profile=AxiomTextureProfile.load(new File(args[0]),null);System.out.println(profile.getSummary());int slot=0;for(Material m:profile.getMaterials()){Terrain terrain=Terrain.getCustomTerrain(slot);Terrain.setCustomMaterial(slot,new MixedMaterial("Measurement: "+key(m),new MixedMaterial.Row(m,1,1.0f),-1,m.colour));mapping.put(m,terrain);inverse.put(terrain,m);slot++;}scenario(false);scenario(true);}
}
