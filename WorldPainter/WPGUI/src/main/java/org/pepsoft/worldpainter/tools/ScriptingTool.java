package org.pepsoft.worldpainter.tools;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import org.pepsoft.util.plugins.PluginManager;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Version;
import org.pepsoft.worldpainter.WPContext;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.plugins.WPPluginManager;
import org.pepsoft.worldpainter.tools.scripts.ScriptingContext;
import org.slf4j.LoggerFactory;

import javax.script.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.pepsoft.worldpainter.plugins.WPPluginManager.DESCRIPTOR_PATH;

/**
 * Headless / CLI scripting host ({@code wpscript}).
 * Structured errors: {@code WP_ERROR code=N kind=K message=...} (#495).
 * Queue: {@code wpscript --queue a.js b.js -- arg1 arg2} (#462 batch).
 * GUI {@link org.pepsoft.worldpainter.tools.scripts.ScriptRunner} never calls {@code System.exit}.
 */
public class ScriptingTool {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        LoggerContext logContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(logContext);
            logContext.reset();
            configurator.doConfigure(ClassLoader.getSystemResourceAsStream("logback-scriptingtool.xml"));
        } catch (JoranException e) {
            // StatusPrinter will handle this
        }
        StatusPrinter.printInCaseOfErrorsOrWarnings(logContext);

        System.err.println("WorldPainter scripting host version " + Version.VERSION + ".\n" +
                "Copyright 2011-2026 pepsoft.org, The Netherlands.\n" +
                "This is free software distributed under the terms of the GPL, version 3, a copy\n" +
                "of which you can find in the installation directory.\n");

        if (args.length < 1) {
            printUsage();
            fail(1, "missing_script", "No script file specified");
            return;
        }

        final List<String> scripts = new ArrayList<>();
        final List<String> scriptArgs = new ArrayList<>();
        if ("--queue".equals(args[0])) {
            int i = 1;
            while (i < args.length && ! "--".equals(args[i])) {
                scripts.add(args[i++]);
            }
            if (i < args.length && "--".equals(args[i])) {
                i++;
            }
            while (i < args.length) {
                scriptArgs.add(args[i++]);
            }
            if (scripts.isEmpty()) {
                fail(1, "empty_queue", "--queue requires at least one script file");
                return;
            }
        } else {
            scripts.add(args[0]);
            for (int i = 1; i < args.length; i++) {
                scriptArgs.add(args[i]);
            }
        }

        bootstrapWorldPainter();

        int failures = 0;
        for (String scriptPath : scripts) {
            final int code = runOneScript(scriptPath, scriptArgs);
            if (code != 0) {
                failures++;
                if (scripts.size() == 1) {
                    System.exit(code);
                    return;
                }
            }
        }
        if (failures > 0) {
            fail(2, "queue_failures", failures + " of " + scripts.size() + " scripts failed");
        }
    }

    private static void bootstrapWorldPainter() throws IOException, ClassNotFoundException {
        try {
            Class.forName("org.pepsoft.worldpainter.DefaultPlugin");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        Configuration config = Configuration.load();
        if (config == null) {
            logger.info("Creating new configuration");
            config = new Configuration();
        }
        Configuration.setInstance(config);

        X509Certificate trustedCert = null;
        try {
            final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            trustedCert = (X509Certificate) certificateFactory.generateCertificate(ClassLoader.getSystemResourceAsStream("wproot.pem"));
        } catch (CertificateException e) {
            logger.error("Certificate exception while loading trusted root certificate", e);
        }

        if (trustedCert != null) {
            final File pluginsDir = new File(Configuration.getConfigDir(), "plugins");
            if (pluginsDir.isDirectory()) {
                PluginManager.loadPlugins(pluginsDir, trustedCert.getPublicKey(), DESCRIPTOR_PATH, Version.VERSION_OBJ, false);
            }
        } else {
            logger.error("Trusted root certificate not available; not loading plugins");
        }
        WPPluginManager.initialise(config.getUuid(), WPContext.INSTANCE);
    }

    private static int runOneScript(String scriptPath, List<String> extraArgs) {
        final File scriptFile = new File(scriptPath);
        if (! scriptFile.isFile()) {
            reportError(1, "not_found", scriptPath + " does not exist or is not a regular file");
            return 1;
        }
        final String scriptFilePath;
        try {
            scriptFilePath = scriptFile.getCanonicalFile().getParent();
        } catch (IOException e) {
            reportError(1, "io", e.getMessage());
            return 1;
        }
        final String scriptFileName = scriptFile.getName();
        int p = scriptFileName.lastIndexOf('.');
        if (p == -1) {
            reportError(1, "no_extension", "Script file name " + scriptFileName + " has no extension");
            return 1;
        }
        final String extension = scriptFileName.substring(p + 1);
        final ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByExtension(extension);
        if (scriptEngine == null) {
            reportError(1, "unsupported_language", "Script file language " + extension + " not supported");
            return 1;
        }
        scriptEngine.put(ScriptEngine.FILENAME, scriptFileName);

        final List<String> argList = new ArrayList<>();
        argList.add(scriptFile.getAbsolutePath());
        final Map<String, String> paramMap = new HashMap<>();
        for (String arg : extraArgs) {
            if (arg.contains("=")) {
                final int eq = arg.indexOf('=');
                paramMap.put(arg.substring(0, eq), arg.substring(eq + 1));
            } else {
                argList.add(arg);
            }
        }

        final Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
        final ScriptingContext context = new ScriptingContext(true);
        bindings.put("wp", context);
        final String[] argArray = argList.toArray(new String[0]);
        bindings.put("argc", argArray.length);
        bindings.put("argv", argArray);
        final String[] scriptArgsArr = new String[Math.max(0, argArray.length - 1)];
        if (argArray.length > 1) {
            System.arraycopy(argArray, 1, scriptArgsArr, 0, scriptArgsArr.length);
        }
        bindings.put("arguments", scriptArgsArr);
        bindings.put("params", paramMap);
        final Map<String, Layer.DataSize> dataSizes = new HashMap<>();
        for (Layer.DataSize dataSize : Layer.DataSize.values()) {
            dataSizes.put(dataSize.name(), dataSize);
        }
        bindings.put("DataSize", dataSizes);
        bindings.put("scriptDir", scriptFilePath);

        try {
            scriptEngine.eval(new FileReader(scriptFile));
            context.checkGoCalled(null);
            System.err.println("WP_OK script=" + scriptFileName);
            return 0;
        } catch (RuntimeException e) {
            logger.error(e.getClass().getSimpleName() + " occurred while executing " + scriptFileName, e);
            reportError(2, "runtime", e.getClass().getSimpleName() + ": " + e.getMessage());
            return 2;
        } catch (ScriptException e) {
            logger.error("ScriptException occurred while executing " + scriptFileName, e);
            reportError(2, "script", e.getMessage());
            return 2;
        } catch (IOException e) {
            reportError(1, "io", e.getMessage());
            return 1;
        }
    }

    private static void printUsage() {
        System.err.println("Usage:\n" +
                "\n" +
                "    wpscript <scriptfile> [<scriptarg> ...]\n" +
                "    wpscript --queue <script1> <script2> [...] [-- <scriptarg> ...]\n" +
                "\n" +
                "Errors: WP_ERROR code=<n> kind=<kind> message=<text>");
    }

    private static void reportError(int code, String kind, String message) {
        System.err.println("WP_ERROR code=" + code + " kind=" + kind + " message=" + message);
    }

    private static void fail(int code, String kind, String message) {
        reportError(code, kind, message);
        System.exit(code);
    }

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ScriptingTool.class);
}
