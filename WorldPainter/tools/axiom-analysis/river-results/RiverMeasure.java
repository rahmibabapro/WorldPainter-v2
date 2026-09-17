import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.layers.bo2.AxiomBlueprint;
import javax.vecmath.Point3i;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Read-only source geometry measurements. Z is local WP vertical, not Minecraft world Y. */
public class RiverMeasure {
    static AxiomBlueprint bp;
    static int w,l,h;
    static int[] wet, waterCount, waterloggedCount;
    static double[] support;
    static String[] supportName;
    static Map<String,TreeMap<String,Integer>> hist=new TreeMap<>();
    static void count(String population,Object value){hist.computeIfAbsent(population,k->new TreeMap<>()).merge(String.valueOf(value),1,Integer::sum);}
    static Material mat(int x,int y,int z){return z>=0&&z<h&&bp.getMask(x,y,z)?bp.getMaterial(x,y,z):null;}
    static boolean waterlogged(Material m){return m!=null&&m.getProperties()!=null&&"true".equals(m.getProperties().get("waterlogged"));}
    static boolean isWater(Material m){return m!=null&&m.name.equals("minecraft:water");}
    static boolean support(Material m){if(m==null)return false;String n=m.name.substring(m.name.indexOf(':')+1);return Set.of("grass_block","dirt","coarse_dirt","podzol","dirt_path","farmland","gravel","stone","granite","andesite","diorite","mossy_cobblestone","cobblestone").contains(n)||n.endsWith("_stairs")||n.endsWith("_slab");}
    static double height(Material m,int z){if(m.name.endsWith("_slab")&&"bottom".equals(m.getProperties().get("type")))return z+0.5;return z+1.0;}
    static String fmt(double d){return String.format(Locale.ROOT,"%.3f",d);}
    public static void main(String[] args)throws Exception{
        bp=AxiomBlueprint.load(new File(args[0]));Point3i d=bp.getDimensions();w=d.x;l=d.y;h=d.z;
        wet=new int[w*l];Arrays.fill(wet,-1);support=new double[w*l];Arrays.fill(support,-1);supportName=new String[w*l];waterCount=new int[w*l];waterloggedCount=new int[w*l];
        for(int y=0;y<l;y++)for(int x=0;x<w;x++)for(int z=h-1;z>=0;z--){
            int i=x+y*w;Material m=mat(x,y,z);if(m==null)continue;
            if(isWater(m)){waterCount[i]++;count("water_block_local_z",z);}
            if(waterlogged(m)){waterloggedCount[i]++;count("waterlogged_block_name",m.name);}
            if(wet[i]<0&&(isWater(m)||waterlogged(m)))wet[i]=z;
            if(support[i]<0&&support(m)){support[i]=height(m,z);supportName[i]=m.name;}
        }
        Path out=Path.of(args[1]);
        try(var writer=Files.newBufferedWriter(out.resolve("wet-columns.tsv"))){writer.write("x\ty\thighest_wet_cell_z\twater_blocks\twaterlogged_blocks\tterrain_support_top\tupper_water_cell_minus_support\tsupport_name\n");
            for(int y=0;y<l;y++)for(int x=0;x<w;x++){
                int i=x+y*w;if(wet[i]<0)continue;
                count("wet_column_water_count",waterCount[i]);count("wet_column_waterlogged_count",waterloggedCount[i]);count("wet_column_top_z",wet[i]);
                count("wet_column_support_material",supportName[i]);count("wet_column_envelope_depth",fmt(wet[i]+1-support[i]));
                writer.write(x+"\t"+y+"\t"+wet[i]+"\t"+waterCount[i]+"\t"+waterloggedCount[i]+"\t"+fmt(support[i])+"\t"+fmt(wet[i]+1-support[i])+"\t"+supportName[i]+"\n");
                for(int[] dir:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int nx=x+dir[0],ny=y+dir[1];if(nx<0||nx>=w||ny<0||ny>=l)continue;int j=nx+ny*w;
                    if(wet[j]<0&&support[j]>=0){count("dry_wet_edge_terrain_rise",fmt(support[j]-support[i]));count("dry_wet_edge_bank_above_water_envelope",fmt(support[j]-(wet[i]+1)));}
                    if(wet[j]>=0&&nx+ny*w>i){count("wet_neighbors_surface_cell_step",Math.abs(wet[j]-wet[i]));count("wet_neighbors_bed_top_step",fmt(Math.abs(support[j]-support[i])));}
                }
            }
        }
        try(var writer=Files.newBufferedWriter(out.resolve("river-metrics.tsv"))){writer.write("population\tvalue\tcount\tpercent\n");for(var population:hist.entrySet()){int total=population.getValue().values().stream().mapToInt(Integer::intValue).sum();for(var row:population.getValue().entrySet())writer.write(population.getKey()+"\t"+row.getKey()+"\t"+row.getValue()+"\t"+fmt(100.0*row.getValue()/total)+"\n");}}
        try(var writer=Files.newBufferedWriter(out.resolve("cross-sections-x.tsv"))){writer.write("y\twet_x_min\twet_x_max\twet_column_count\tx_runs\tlowest_wet_top_z\thighest_wet_top_z\n");for(int y=0;y<l;y++){
            int min=w,max=-1,n=0,runs=0,lo=h,hi=-1;boolean previous=false;
            for(int x=0;x<w;x++){int z=wet[x+y*w];if(z>=0){min=Math.min(min,x);max=x;n++;lo=Math.min(lo,z);hi=Math.max(hi,z);if(!previous)runs++;previous=true;}else previous=false;}
            writer.write(y+"\t"+min+"\t"+max+"\t"+n+"\t"+runs+"\t"+lo+"\t"+hi+"\n");}}
        try(var writer=Files.newBufferedWriter(out.resolve("top-map.txt"))){writer.write("Each character is one horizontal block. .=dry, 0..9=highest wet local z, G=granite without water.\n");for(int y=0;y<l;y++){writer.write(String.format("%02d ",y));for(int x=0;x<w;x++){int i=x+y*w;writer.write(wet[i]>=0?Character.forDigit(wet[i],16):supportName[i]!=null&&supportName[i].contains("granite")?'G':'.');}writer.write("\n");}}
        for(var p:hist.entrySet())System.out.println(p.getKey()+" "+p.getValue());
    }
}
