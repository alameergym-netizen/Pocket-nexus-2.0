package com.limelight.pocket.core;

/** Global shortcuts never compete with a visible PC stream or its temporary dialogs. */
public final class HorizonInputOwner {
    private HorizonInputOwner() {}
    public static boolean isStreamClass(String className) {
        return "com.limelight.Game".equals(className) || ".Game".equals(className);
    }
    public static boolean streamOwnsInput(boolean activityVisible, boolean observedStreamWindow) {
        return activityVisible || observedStreamWindow;
    }
}
