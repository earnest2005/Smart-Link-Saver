package com.smartlinksaver.util;

/** Small helper for locating bundled resources. */
public final class AppResources {

    private AppResources() { }

    /** URL string of the JavaFX CSS stylesheet. */
    public static String stylesheet() {
        return AppResources.class.getResource("/css/style.css").toExternalForm();
    }
}
