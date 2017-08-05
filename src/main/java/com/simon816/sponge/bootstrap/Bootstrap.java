package com.simon816.sponge.bootstrap;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.gradle.GradleStartCommon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.jar.Attributes;

public class Bootstrap {

    private static final String COREMOD = "org.spongepowered.mod.SpongeCoremod";
    private static final String PRE_TWEAKER = "com.simon816.sponge.bootstrap.Bootstrap$PreFMLTweaker";
    private static final String POST_TWEAKER = "com.simon816.sponge.bootstrap.Bootstrap$PostFMLTweaker";
    public static final String FML_TWEAKER = "net.minecraftforge.fml.common.launcher.FMLServerTweaker";

    static final Logger logger = LogManager.getLogger("SpongeBootstrap");

    static File spongeJar;

    public static void main(String[] args) {
        logger.info("Detecting environment...");
        try {
            Class.forName("GradleStartServer");
            logger.info("Detected gradle development environment, continuing");
            loadGradle(args);
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("net.minecraft.launchwrapper.Launch");
                logger.info("Found launch wrapper, continuing");
            } catch (ClassNotFoundException e1) {
                System.err.println("Failed to load Launch class");
                e1.printStackTrace();
                System.exit(1);
            } catch (NoClassDefFoundError e2) {
                System.err.println("Failed to load Launch class");
                e2.printStackTrace();
                System.exit(1);
            }
            findAndLoadJars();
            load(args);
        }
    }

    private static void removeFromSysArgs() {
        // Remove SpongeCoremod from args as we load it ourselves
        List<String> coreModsArgs = new ArrayList<String>(Arrays.asList(System.getProperty("fml.coreMods.load", "").split(",")));
        while (coreModsArgs.remove(COREMOD)) {
        }
        StringBuilder coreMods = new StringBuilder();
        for (String cm : coreModsArgs) {
            coreMods.append(cm).append(',');
        }
        System.setProperty("fml.coreMods.load", coreMods.toString());
    }

    private static void findAndLoadJars() {
        File rootDir;
        try {
            rootDir = new File(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParentFile();
        } catch (URISyntaxException e) {
            System.err.println("Could not get jar directory");
            e.printStackTrace();
            System.exit(1);
            return;
        }
        findJar(rootDir, "forge", new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                String fn = pathname.getName().toLowerCase();
                return fn.endsWith(".jar") && fn.contains("forge") && fn.contains("-universal") && supportedVersion(fn);
            }
        });
        spongeJar = findJar(new File(rootDir, "mods"), "sponge", new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                String fn = pathname.getName().toLowerCase();
                return fn.endsWith(".jar") && fn.contains("sponge") && supportedVersion(fn);
            }
        });
    }

    static boolean supportedVersion(String fn) {
        // Replaced by gradle (see build.gradle)
        for (String supported : "@supportedVersions@".split(",")) {
            if (fn.contains(supported)) {
                return true;
            }
        }
        return false;
    }

    private static File findJar(File directory, String jarName, FileFilter filter) {
        File[] files = directory.listFiles(filter);
        if (files == null) {
            System.err.println("An error occured when listing directory contents");
            System.exit(1);
            return null;
        }
        if (files.length == 0) {
            System.err.println("Could not find " + jarName + " jar. Please make sure a " + jarName + " jar exists.");
            System.exit(1);
        }
        int idx = 0;
        if (files.length > 1) {
            System.out.println("Multiple " + jarName + " jars have been detected, please choose");
            for (int i = 0; i < files.length; i++) {
                System.out.println(String.format("%d: %s", i, files[i]));
            }
            @SuppressWarnings("resource")
            Scanner scanner = new Scanner(System.in);
            do {
                idx = scanner.nextInt();
            } while (idx < 0 || idx > files.length - 1);
            // scanner.close(); Don't close - this kills command handling
        }
        File jarFile = files[idx];
        URLClassLoader classLoader = (URLClassLoader) Bootstrap.class.getClassLoader();
        try {
            Method addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrl.setAccessible(true);
            addUrl.invoke(classLoader, jarFile.toURI().toURL());
        } catch (Exception e) {
            System.err.println("Failed to add " + jarName + " jar to classpath");
            e.printStackTrace();
            System.exit(1);
        }
        return jarFile;
    }

    private static void load(String[] args) {
        removeFromSysArgs();
        String[] newArgs = new String[args.length + 6];
        System.arraycopy(args, 0, newArgs, 6, args.length);
        newArgs[0] = "--tweakClass";
        newArgs[1] = PRE_TWEAKER;
        newArgs[2] = "--tweakClass";
        newArgs[3] = FML_TWEAKER;
        newArgs[4] = "--tweakClass";
        newArgs[5] = POST_TWEAKER;
        Launch.main(newArgs);
    }

    private static void loadGradle(String[] args) {
        removeFromSysArgs();
        try {
            GradleHackServer.main(args);
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    public static class PreFMLTweaker extends SimpleTweaker {

        @Override
        public void injectIntoClassLoader(LaunchClassLoader classLoader) {
            // Adds SpongeCoremod to FML's 'root plugins' so it always loads
            // before other coremods
            // Add to end of array so FML plugins are first
            try {
                logger.info("Performing SpongeCoremod injection");
                Field rootPluginsField = CoreModManager.class.getDeclaredField("rootPlugins");
                rootPluginsField.setAccessible(true);
                String[] rootPlugins = (String[]) rootPluginsField.get(null);
                String[] rootPlugins2 = new String[rootPlugins.length + 1];
                System.arraycopy(rootPlugins, 0, rootPlugins2, 0, rootPlugins.length);
                rootPlugins2[rootPlugins.length] = COREMOD;
                rootPluginsField.set(null, rootPlugins2);
                mixinHackLookAwayNow();
                logger.info("SpongeCoremod successfully injected into FML");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void mixinHackLookAwayNow() throws ReflectiveOperationException {
            if (spongeJar == null) { // In dev environment
                return;
            }
            // Stop mixin from trying to load spongeforge for a second time
            Class<?> attrClass = Class.forName("org.spongepowered.asm.launch.platform.MainAttributes");
            Method mOf = attrClass.getMethod("of", File.class);
            mOf.setAccessible(true);
            Object inst = mOf.invoke(null, spongeJar);
            Field fAttr = attrClass.getDeclaredField("attributes");
            fAttr.setAccessible(true);
            Attributes attr = (Attributes) fAttr.get(inst);
            attr.remove(new Attributes.Name("FMLCorePlugin"));
        }
    }

    public static class PostFMLTweaker extends SimpleTweaker {

        @Override
        public void injectIntoClassLoader(LaunchClassLoader classLoader) {
            // Mixin system already loaded early so don't load twice
            @SuppressWarnings("unchecked")
            List<String> tweakClasses = (List<String>) Launch.blackboard.get("TweakClasses");
            boolean duplicateMixin = false;
            while (tweakClasses.remove("org.spongepowered.asm.launch.MixinTweaker")) {
                duplicateMixin = true;
            }
            // Another mod is using mixin system
            if (duplicateMixin) {
                try {
                    // This feels wrong but it works
                    // For some reason 'register' gets called before 'preInit'
                    // even though MixinTweaker calls preInit in constructor
                    Class<?> c = Class.forName("org.spongepowered.asm.launch.MixinBootstrap");
                    Field init = c.getDeclaredField("initialised");
                    init.setAccessible(true);
                    init.set(null, true);
                    tweakClasses.add("org.spongepowered.asm.launch.MixinTweaker");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class SimpleTweaker implements ITweaker {

        @Override
        public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        }

        @Override
        public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        }

        @Override
        public String getLaunchTarget() {
            return "net.minecraft.server.MinecraftServer";
        }

        @Override
        public String[] getLaunchArguments() {
            return new String[0];
        }

    }

    public static class GradleHackServer extends GradleStartCommon {

        public static void main(String[] args) throws Throwable {
            (new GradleHackServer()).launch(args);
        }

        @Override
        protected String getTweakClass() {
            return PRE_TWEAKER;
        }

        @Override
        protected String getBounceClass() {
            return "net.minecraft.launchwrapper.Launch";
        }

        @Override
        protected void preLaunch(Map<String, String> argMap, List<String> extras) {
            // Add the tweak class from GradleStartServer AFTER our tweaker
            extras.add("--tweakClass");
            extras.add(FML_TWEAKER);
            extras.add("--tweakClass");
            extras.add(POST_TWEAKER);
        }

        @Override
        protected void setDefaultArguments(Map<String, String> argMap) {
        }
    }

}
