import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.layers.bo2.AxiomBlueprint;
import javax.vecmath.Point3i;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.pepsoft.worldpainter.ColourScheme;

/** Read-only measurements of a supplied blueprint. All coordinates use WP x/y horizontal, z up. */
public final class BlueprintMeasure {
    static final Map<String,Map<String,Long>> counts = new TreeMap<>();
    static final Map<String,Long> conditional = new TreeMap<>();
    static AxiomBlueprint bp;
    static int w,l,h;
    static int[] top,ground;
    static Material[] visible,substrate;
    static int minGround=Integer.MAX_VALUE,maxGround=Integer.MIN_VALUE;
    static boolean shaped(Material m) {String n=m.name;return n.endsWith("_stairs")||n.endsWith("_slab")||n.endsWith("_wall")||n.endsWith("_fence")||n.endsWith("_fence_gate")||n.endsWith("_banner")||n.endsWith("_trapdoor")||n.endsWith("_door")||n.equals("minecraft:pointed_dripstone");}
    static void add(String category,String key) {counts.computeIfAbsent(category,k->new TreeMap<>()).merge(key,1L,Long::sum);}
    static String state(Material m) {Map<String,String> p=m.getProperties();return m.name+((p==null||p.isEmpty())?"":"["+new TreeMap<>(p).toString().replace("{","").replace("}","")+"]");}
    static String category(Material m) {
        String n=m.name.substring(m.name.indexOf(':')+1);
        if(n.equals("air")||n.endsWith("_air")||n.equals("structure_void"))return "air";
        if(n.equals("water")||n.equals("lava")||n.equals("bubble_column"))return "fluid";
        if(n.equals("snow"))return "snow_layer";
        if(n.endsWith("_leaves"))return "leaves";
        if(n.contains("grass")&&!n.endsWith("_block")||n.endsWith("_flower")||n.contains("fern")||n.endsWith("_sapling")||n.contains("vine")||n.contains("mushroom")&&!n.endsWith("_block")&&!n.endsWith("_stem")||n.endsWith("_carpet")||n.equals("dead_bush")||n.equals("pink_petals")||n.equals("wildflowers")||n.equals("leaf_litter")||n.equals("lily_of_the_valley")||n.equals("dandelion")||n.equals("poppy")||n.equals("allium")||n.equals("azure_bluet")||n.equals("oxeye_daisy")||n.equals("cornflower")||n.equals("glow_lichen")||n.equals("sweet_berry_bush")||n.endsWith("_tulip"))return "plant";
        if(shaped(m))return "shaped_detail";
        return "substrate";
    }
    static boolean filled(int x,int y,int z) {return z>=0&&z<h&&x>=0&&x<w&&y>=0&&y<l&&bp.getMask(x,y,z)&&!category(bp.getMaterial(x,y,z)).equals("air");}
    static int sample(int x,int y,int fallback) {return x>=0&&x<w&&y>=0&&y<l&&ground[x+y*w]>=0?ground[x+y*w]:fallback;}
    static double[] gradient(int x,int y,int radius) {int z=ground[x+y*w];return new double[]{(sample(x+radius,y,z)-sample(x-radius,y,z))/(2.0*radius),(sample(x,y+radius,z)-sample(x,y-radius,z))/(2.0*radius)};}
    static double slope(int x,int y,int r) {double[]g=gradient(x,y,r);return Math.toDegrees(Math.atan(Math.hypot(g[0],g[1])));}
    static int slopeBand(double s) {return s<10?0:s<25?1:s<40?2:s<55?3:4;}
    static String family(Material m) {String n=m.name; if(n.contains("snow")||n.contains("ice")||n.contains("white")||n.contains("diorite")||n.contains("calcite")||n.contains("birch")||n.contains("pale_oak"))return "light";if(n.contains("grass")||n.contains("moss")||n.contains("leaves"))return "green";if(n.contains("mud")||n.contains("dirt")||n.contains("clay")||n.contains("terracotta")||n.contains("acacia"))return "brown";return "rock_other";}
    static void conditional(String type,int hb,int sb,String key) {conditional.merge(type+"\t"+hb+"\t"+sb+"\t"+key,1L,Long::sum);}
    static void writeCounts(Path out)throws IOException {
        try(BufferedWriter writer=Files.newBufferedWriter(out.resolve("counts.tsv"),StandardCharsets.UTF_8)) {
            writer.write("population\tblock\tcount\tpercent\n");
            for(var entry:counts.entrySet()) {long sum=entry.getValue().values().stream().mapToLong(Long::longValue).sum();
                var sorted=new ArrayList<>(entry.getValue().entrySet());sorted.sort(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
                for(var e:sorted)writer.write(entry.getKey()+"\t"+e.getKey()+"\t"+e.getValue()+"\t"+String.format(Locale.ROOT,"%.8f",100.0*e.getValue()/sum)+"\n");
            }
        }
    }
    public static void main(String[] args)throws Exception {
        long start=System.nanoTime();Path out=Path.of(args[1]);Files.createDirectories(out);
        bp=AxiomBlueprint.load(new File(args[0]));Point3i d=bp.getDimensions();w=d.x;l=d.y;h=d.z;
        System.out.println("Loaded "+d+" in "+((System.nanoTime()-start)/1e9)+"s");
        top=new int[w*l];ground=new int[w*l];visible=new Material[w*l];substrate=new Material[w*l];Arrays.fill(top,-1);Arrays.fill(ground,-1);
        long occupied=0,explicitAir=0,multiRun=0,empty=0;
        for(int y=0;y<l;y++)for(int x=0;x<w;x++) {
            int runs=0;boolean inRun=false,foundSupport=false;int i=x+y*w;
            for(int z=h-1;z>=0;z--) {
                if(!bp.getMask(x,y,z)) {inRun=false;continue;}
                Material m=bp.getMaterial(x,y,z);String c=category(m);
                if(c.equals("air")){explicitAir++;inRun=false;continue;}
                occupied++;if(!inRun){runs++;inRun=true;}
                add("volume_name",m.name);add("volume_state",state(m));add("volume_category",c);
                if(top[i]<0){top[i]=z;visible[i]=m;add("top_visible_name",m.name);add("top_visible_state",state(m));add("top_visible_category",c);}
                if(!foundSupport&&(c.equals("substrate")||c.equals("shaped_detail"))){foundSupport=true;add("top_support_including_shapes_name",m.name);add("top_support_including_shapes_state",state(m));}
                if(ground[i]<0&&c.equals("substrate")){ground[i]=z;substrate[i]=m;minGround=Math.min(z,minGround);maxGround=Math.max(z,maxGround);add("top_substrate_name",m.name);add("top_substrate_state",state(m));}
            }
            if(runs>1)multiRun++;if(runs==0)empty++;
        }
        System.out.println("occupied="+occupied+" top="+(w*l-empty)+" empty="+empty+" multiRun="+multiRun+" substrate min="+minGround+" max="+maxGround+" names="+counts.get("top_substrate_name").size()+" states="+counts.get("top_substrate_state").size());
        writeCounts(out);
        int[] sortedHeights=Arrays.stream(ground).filter(z->z>=0).sorted().toArray();
        try(BufferedWriter writer=Files.newBufferedWriter(out.resolve("height-percentiles.tsv"),StandardCharsets.UTF_8)) {writer.write("percentile\tsubstrate_z\n");for(int p:new int[]{0,1,5,10,25,50,75,90,95,99,100})writer.write(p+"\t"+sortedHeights[(int)Math.round(p/100.0*(sortedHeights.length-1))]+"\n");}
        BufferedImage maps=new BufferedImage(w*3+40,l+70,BufferedImage.TYPE_INT_RGB);Graphics2D mg=maps.createGraphics();mg.setColor(Color.WHITE);mg.fillRect(0,0,maps.getWidth(),maps.getHeight());mg.setColor(Color.BLACK);mg.drawString("Visible uppermost block",0,18);mg.drawString("Full-block substrate (details stripped)",w+20,18);mg.drawString("Substrate height (black="+minGround+" white="+maxGround+")",w*2+40,18);
        for(int y=0;y<l;y++)for(int x=0;x<w;x++){int i=x+y*w;if(top[i]>=0)maps.setRGB(x,y+30,ColourScheme.DEFAULT.getColour(visible[i]));if(ground[i]>=0){maps.setRGB(x+w+20,y+30,ColourScheme.DEFAULT.getColour(substrate[i]));int gray=(int)Math.round(255.0*(ground[i]-minGround)/(maxGround-minGround));maps.setRGB(x+w*2+40,y+30,new Color(gray,gray,gray).getRGB());}}
        mg.setColor(Color.BLACK);mg.drawString("One pixel = one block; X right, WorldPainter Y down; colours are app's material preview colours.",0,l+55);mg.dispose();ImageIO.write(maps,"png",out.resolve("source-surface-maps.png").toFile());
        try(BufferedWriter writer=Files.newBufferedWriter(out.resolve("material-spatial-summary.tsv"),StandardCharsets.UTF_8)) {
            writer.write("block\tcount\tmin_z\tmean_z\tmax_z\tmean_x\tmean_y\tshaped_detail\n");Map<String,long[]> loc=new TreeMap<>();
            for(int y=0;y<l;y++)for(int x=0;x<w;x++){int i=x+y*w;if(substrate[i]==null)continue;long[] a=loc.computeIfAbsent(substrate[i].name,k->new long[]{0,Long.MAX_VALUE,0,Long.MIN_VALUE,0,0,shaped(substrate[i])?1:0});a[0]++;a[1]=Math.min(a[1],ground[i]);a[2]+=ground[i];a[3]=Math.max(a[3],ground[i]);a[4]+=x;a[5]+=y;}
            for(var e:loc.entrySet()){long[] a=e.getValue();writer.write(e.getKey()+"\t"+a[0]+"\t"+a[1]+"\t"+(a[2]/(double)a[0])+"\t"+a[3]+"\t"+(a[4]/(double)a[0])+"\t"+(a[5]/(double)a[0])+"\t"+(a[6]==1)+"\n");}
        }
        for(String kind:List.of("volume_name","top_visible_name","top_substrate_name")) {
            System.out.println(kind);counts.get(kind).entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(15).forEach(System.out::println);
        }
        try(BufferedWriter writer=Files.newBufferedWriter(out.resolve("surface-samples.tsv"),StandardCharsets.UTF_8)) {
            writer.write("x\ty\ttop_z\tsubstrate_z\tnormalized_height\tslope1\tslope4\tslope8\tdx4\tdy4\tsubstrate\tvisible\n");
            for(int y=0;y<l;y++)for(int x=0;x<w;x++) {int i=x+y*w;if(ground[i]<0)continue;
                double hn=(ground[i]-minGround)/(double)Math.max(1,maxGround-minGround);int hb=Math.min(15,(int)(hn*16));
                double s1=slope(x,y,1),s4=slope(x,y,4),s8=slope(x,y,8);double[] g4=gradient(x,y,4);
                conditional("top_slope1",hb,slopeBand(s1),substrate[i].name);conditional("top_slope4",hb,slopeBand(s4),substrate[i].name);conditional("top_slope8",hb,slopeBand(s8),substrate[i].name);
                String aspect=Math.abs(g4[0])>Math.abs(g4[1])?(g4[0]>0?"west":"east"):(g4[1]>0?"north":"south");
                conditional("top_aspect_"+aspect,hb,slopeBand(s4),substrate[i].name);
                writer.write(x+"\t"+y+"\t"+top[i]+"\t"+ground[i]+"\t"+String.format(Locale.ROOT,"%.6f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f",hn,s1,s4,s8,g4[0],g4[1])+"\t"+state(substrate[i])+"\t"+state(visible[i])+"\n");
            }
        }
        int[][] dirs={{0,-1},{1,0},{0,1},{-1,0}};String[] names={"north","east","south","west"};
        long clipped=0;
        for(int z=0;z<h;z++)for(int y=0;y<l;y++)for(int x=0;x<w;x++) {
            if(!filled(x,y,z))continue;Material m=bp.getMaterial(x,y,z);String cat=category(m);
            if(!filled(x,y,z+1)) {add("up_faces_all_name",m.name);if(cat.equals("substrate"))add("up_faces_substrate_name",m.name);}
            for(int dir=0;dir<4;dir++) {
                int nx=x+dirs[dir][0],ny=y+dirs[dir][1];if(nx<0||nx>=w||ny<0||ny>=l){clipped++;continue;}
                if(!filled(nx,ny,z)){
                    add("side_internal_"+names[dir],m.name);
                    if(ground[nx+ny*w]>=0&&cat.equals("substrate")&&z>ground[nx+ny*w]){
                        add("side_above_neighbor_"+names[dir],m.name);
                        int hb=Math.max(0,Math.min(15,(int)((z-minGround)*16.0/Math.max(1,maxGround-minGround))));
                        conditional("side_"+names[dir],hb,-1,m.name);
                    }
                }
            }
        }
        writeCounts(out);
        try(BufferedWriter writer=Files.newBufferedWriter(out.resolve("conditional.tsv"),StandardCharsets.UTF_8)) {
            writer.write("population\theight_band_16\tslope_band_0_10_25_40_55_90\tblock\tcount\n");for(var e:conditional.entrySet())writer.write(e.getKey()+"\t"+e.getValue()+"\n");
        }
        try(BufferedWriter writer=Files.newBufferedWriter(out.resolve("correlation.tsv"),StandardCharsets.UTF_8)) {
            writer.write("distance\taxis\tpairs\texact_matches\tmatch_fraction\tfamily_matches\tfamily_fraction\n");
            for(int dist:new int[]{1,2,3,4,6,8,12,16,24,32,48,64})for(int axis=0;axis<2;axis++) {
                long pairs=0,same=0,fam=0;
                for(int y=0;y<l;y++)for(int x=0;x<w;x++){int nx=x+(axis==0?dist:0),ny=y+(axis==1?dist:0);if(nx>=w||ny>=l)continue;int a=x+y*w,b=nx+ny*w;if(substrate[a]==null||substrate[b]==null)continue;pairs++;if(substrate[a].name.equals(substrate[b].name))same++;if(family(substrate[a]).equals(family(substrate[b])))fam++;}
                writer.write(dist+"\t"+(axis==0?"x":"y")+"\t"+pairs+"\t"+same+"\t"+(same/(double)pairs)+"\t"+fam+"\t"+(fam/(double)pairs)+"\n");
            }
            for(int dist:new int[]{1,2,4,8,16,32})for(int axis=0;axis<2;axis++) {
                long pairs=0,same=0,fam=0;
                for(int y=0;y<l;y++)for(int x=0;x<w;x++){int a=x+y*w;if(substrate[a]==null)continue;double[]g=gradient(x,y,4);double mag=Math.hypot(g[0],g[1]);if(mag<Math.tan(Math.toRadians(25)))continue;int dx=(int)Math.round((axis==0?g[0]:-g[1])*dist/mag),dy=(int)Math.round((axis==0?g[1]:g[0])*dist/mag);int nx=x+dx,ny=y+dy;if(nx<0||ny<0||nx>=w||ny>=l)continue;int b=nx+ny*w;if(substrate[b]==null)continue;pairs++;if(substrate[a].name.equals(substrate[b].name))same++;if(family(substrate[a]).equals(family(substrate[b])))fam++;}
                writer.write(dist+"\t"+(axis==0?"along_slope_gt25":"along_contour_gt25")+"\t"+pairs+"\t"+same+"\t"+(same/(double)pairs)+"\t"+fam+"\t"+(fam/(double)pairs)+"\n");
            }
        }
        try(BufferedWriter writer=Files.newBufferedWriter(out.resolve("patches.tsv"),StandardCharsets.UTF_8)) {
            writer.write("block\tarea\tmin_x\tmin_y\tmax_x\tmax_y\n");boolean[] seen=new boolean[w*l];int[] queue=new int[w*l];
            for(int i=0;i<w*l;i++)if(!seen[i]&&substrate[i]!=null){int head=0,tail=0;queue[tail++]=i;seen[i]=true;int x0=i%w,y0=i/w,x1=x0,y1=y0;String material=substrate[i].name;
                while(head<tail){int p=queue[head++],x=p%w,y=p/w;x0=Math.min(x0,x);x1=Math.max(x1,x);y0=Math.min(y0,y);y1=Math.max(y1,y);for(int[] dir:dirs){int nx=x+dir[0],ny=y+dir[1];if(nx<0||nx>=w||ny<0||ny>=l)continue;int q=nx+ny*w;if(!seen[q]&&substrate[q]!=null&&material.equals(substrate[q].name)){seen[q]=true;queue[tail++]=q;}}}
                writer.write(material+"\t"+tail+"\t"+x0+"\t"+y0+"\t"+x1+"\t"+y1+"\n");
            }
        }
        String summary="{\n  \"width\": "+w+", \"length\": "+l+", \"height\": "+h+",\n  \"nonAirBlocks\": "+occupied+", \"explicitAir\": "+explicitAir+",\n  \"topColumns\": "+(w*l-empty)+", \"emptyColumns\": "+empty+", \"multiRunColumns\": "+multiRun+",\n  \"minSubstrateHeight\": "+minGround+", \"maxSubstrateHeight\": "+maxGround+",\n  \"volumeNames\": "+counts.get("volume_name").size()+", \"volumeStates\": "+counts.get("volume_state").size()+",\n  \"visibleTopNames\": "+counts.get("top_visible_name").size()+", \"substrateTopNames\": "+counts.get("top_substrate_name").size()+", \"substrateTopStates\": "+counts.get("top_substrate_state").size()+",\n  \"boundarySideFacesExcluded\": "+clipped+", \"elapsedSeconds\": "+((System.nanoTime()-start)/1e9)+"\n}\n";
        Files.writeString(out.resolve("summary.json"),summary,StandardCharsets.UTF_8);System.out.println(summary);
    }
}
