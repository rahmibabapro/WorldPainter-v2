import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.nio.file.*;
import org.jnbt.*;
public class BlueprintHeader {
    public static void main(String[] args)throws Exception {
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(args[0])))) {
            System.out.printf("magic=%08x headerLength=%d%n",in.readInt(),in.readInt());
            System.out.println(new NBTInputStream(in).readTag());
            int length=in.readInt();System.out.println("thumbnailLength="+length);byte[] thumbnail=in.readNBytes(length);if(args.length>1)Files.write(Path.of(args[1]),thumbnail);System.out.println("compressedLength="+in.readInt());
            CompoundTag blocks=(CompoundTag)new NBTInputStream(new GZIPInputStream(in)).readTag();
            System.out.println("blockData keys="+blocks.getValue().keySet());
            for(var e:blocks.getValue().entrySet())if(!e.getKey().equals("BlockRegion")&&!e.getKey().equals("Entities")&&!e.getKey().equals("BlockEntities"))System.out.println(e);
            ListTag<?> regions=(ListTag<?>)blocks.getTag("BlockRegion");int ymin=Integer.MAX_VALUE,ymax=Integer.MIN_VALUE;
            for(Object o:regions.getValue()){CompoundTag r=(CompoundTag)o;int y=((IntTag)r.getTag("Y")).getValue();ymin=Math.min(y,ymin);ymax=Math.max(y,ymax);}
            System.out.println("sectionY range="+ymin+".."+ymax);
        }
    }
}
