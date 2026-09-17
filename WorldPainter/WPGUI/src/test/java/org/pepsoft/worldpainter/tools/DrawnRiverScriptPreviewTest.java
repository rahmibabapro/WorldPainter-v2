package org.pepsoft.worldpainter.tools;
import org.junit.Test;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

public class DrawnRiverScriptPreviewTest {
    @Test public void shippedAdapterTransfersSettingsWithoutCallingTheCarver() throws Exception {
        String source;
        try(var in=getClass().getResourceAsStream("/org/pepsoft/worldpainter/scripts/rivers/river_from_line.js")){assertNotNull(in);source=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        var matcher=Pattern.compile("(?ms)^function queueDrawnNetworkPreview\\(.*?^}").matcher(source);assertTrue(matcher.find());
        var engine=new NashornScriptEngineFactory().getScriptEngine();
        engine.eval("var called=0;var app={},dimension={};var riverPreset={startWidth:3,endWidth:12,maxDepth:.85};"
                +"var bankSmoothing=true,shallowGraniteDetail=false,shallowGraniteSeed=77;function print(){};"
                +"var Java={type:function(name){if(name!=='org.pepsoft.worldpainter.tools.DrawnRiverDialog')throw 'wrong bridge';"
                +"return {queueScriptPreview:function(a,d,l,s,m,depth,smooth,granite,seed){"
                +"if(a!==app||d!==dimension||s!==3||m!==12||depth!==.85||!smooth||granite||seed!==77)throw 'settings lost';called++;}};}};");
        engine.eval(matcher.group());engine.eval("queueDrawnNetworkPreview({getDataSize:function(){return 'BIT';}});if(called!==1)throw 'preview not reached';");
        assertTrue(source.indexOf("queueDrawnNetworkPreview(riverLayer);")<source.indexOf("collectRiverLine(dimension"));
        String dialog;
        try(var in=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(
                "src/main/java/org/pepsoft/worldpainter/tools/DrawnRiverDialog.java"))) {
            dialog=in.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
        assertTrue(dialog.contains("session.prepare()"));
        assertTrue(dialog.contains("Nehri hazırla"));
        assertThrows(javax.script.ScriptException.class,()->engine.eval("queueDrawnNetworkPreview({getDataSize:function(){return 'NIBBLE';}});"));
    }
}
