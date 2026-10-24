package com.smartlinksaver.util;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/** Opens a URL in the system's default browser using java.awt.Desktop. */
public final class BrowserLauncher {

    private BrowserLauncher() { }

    /**
     * @throws UnsupportedOperationException if this environment cannot open a browser
     *         (for example a headless server)
     */
    public static void open(String url) throws IOException, URISyntaxException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new UnsupportedOperationException("Opening a browser is not supported on this system.");
        }
        Desktop.getDesktop().browse(new URI(url));
    }
}
