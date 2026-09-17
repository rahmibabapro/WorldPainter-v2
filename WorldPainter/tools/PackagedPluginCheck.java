import org.pepsoft.worldpainter.plugins.WPPluginManager;
import org.pepsoft.worldpainter.layers.LayerManager;
import org.pepsoft.worldpainter.layers.Caves;
import java.util.UUID;

/** Run with Java 21 and ONLY the packaged JAR on the class path, not target/classes. */
class PackagedPluginCheck {
    public static void main(String[] args) {
        WPPluginManager.initialise(UUID.randomUUID(), null);
        var plugins = WPPluginManager.getInstance().getAllPlugins();
        for(String required : new String[]{"Default", "JavaPlatformProvider", "DefaultCustomObjects", "DefaultLayerEditorProvider"}) {
            if(plugins.stream().noneMatch(p -> required.equals(p.getName()))) throw new AssertionError("Packaged plugin missing: " + required);
        }
        if(!LayerManager.getInstance().getLayers().contains(Caves.INSTANCE)) throw new AssertionError("Core layers missing");
        System.out.println("Packaged plugins and core layers OK");
    }
}
