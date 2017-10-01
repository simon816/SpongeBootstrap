package net.minecraftforge.gradle;

import java.util.List;
import java.util.Map;

/**
 * Stub GradleStartCommon class
 */
public abstract class GradleStartCommon {

    protected abstract void setDefaultArguments(Map<String, String> argMap);

    protected abstract void preLaunch(Map<String, String> argMap, List<String> extras);

    protected abstract String getBounceClass();

    protected abstract String getTweakClass();

    protected void launch(String[] args) throws Throwable {
        throw new IllegalStateException("Tried to launch the stub GradleStartCommon class");
    }

}
