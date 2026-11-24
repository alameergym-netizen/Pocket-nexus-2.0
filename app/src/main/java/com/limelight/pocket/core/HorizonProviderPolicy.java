package com.limelight.pocket.core;

import com.limelight.pocket.home.HomeItem;

/**
 * Classifies existing launch paths without claiming capabilities from installation alone.
 * An external activity launch is never proof that a game session is running.
 * No IO, permissions, or changes to the existing launcher.
 */
public final class HorizonProviderPolicy {
    public enum Provider { PC_STREAM, PC_LIBRARY, CLOUD, RETRO, ANDROID, NATIVE, SHORTCUT, PC_BROWSER, NEXUS_TOOL, UNKNOWN }
    public enum Observation { INTERNAL_STREAM, EXTERNAL_UNCONFIRMED, PROVIDER_DEPENDENT, NOT_A_GAME, UNKNOWN }
    private HorizonProviderPolicy() {}
    public static Provider provider(HomeItem item) {
        if (item == null || item.kind == null) return Provider.UNKNOWN;
        switch (item.kind) {
            case HomeItem.PC: return Provider.PC_STREAM;
            case HomeItem.PC_LIBRARY_GAME: return Provider.PC_LIBRARY;
            case HomeItem.CLOUD: return Provider.CLOUD;
            case HomeItem.RETRO: return Provider.RETRO;
            case HomeItem.ANDROID: return Provider.ANDROID;
            case HomeItem.NATIVE: return Provider.NATIVE;
            case HomeItem.SHORTCUT: return Provider.SHORTCUT;
            case HomeItem.PC_GAMES: return Provider.PC_BROWSER;
            case HomeItem.NEXUS: return Provider.NEXUS_TOOL;
            default: return Provider.UNKNOWN;
        }
    }
    public static Observation observation(Provider p) {
        if (p == null) return Observation.UNKNOWN;
        switch (p) {
            case PC_STREAM:
            case PC_LIBRARY:
                return Observation.INTERNAL_STREAM;
            case CLOUD:
                return Observation.PROVIDER_DEPENDENT;
            case RETRO:
            case ANDROID:
            case NATIVE:
            case SHORTCUT:
                return Observation.EXTERNAL_UNCONFIRMED;
            case PC_BROWSER:
            case NEXUS_TOOL:
                return Observation.NOT_A_GAME;
            default:
                return Observation.UNKNOWN;
        }
    }
}
