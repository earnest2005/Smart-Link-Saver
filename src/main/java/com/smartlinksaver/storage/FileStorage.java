package com.smartlinksaver.storage;

import com.smartlinksaver.model.Link;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and loads links using a plain text file.
 *
 * Format: one link per line, five TAB-separated columns
 * (id, name, url, category, favorite). Every column is percent-encoded
 * (URLEncoder), so tabs, new lines, commas, quotes or '|' inside a name or URL
 * can never break the record structure.
 *
 * The file lives in the user's home folder (~/.smartlinksaver/links.dat),
 * outside the source-code directory.
 */
public class FileStorage {

    private static final String HEADER = "#SmartLinkSaver-v1";
    private static final String SEPARATOR = "\t";
    private static final int COLUMNS = 5;

    private final Path file;
    private String lastLoadWarning;

    public FileStorage() {
        this(defaultPath());
    }

    public FileStorage(Path file) {
        this.file = file;
    }

    /** Default location; can be overridden with -Dsmartlinksaver.data=/some/path.dat */
    public static Path defaultPath() {
        String override = System.getProperty("smartlinksaver.data");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get(System.getProperty("user.home"), ".smartlinksaver", "links.dat");
    }

    public Path getFile() { return file; }

    /** A human-readable warning if the last load skipped damaged data, otherwise null. */
    public String getLastLoadWarning() { return lastLoadWarning; }

    /**
     * Loads all links. Never throws: a missing file gives an empty list, and
     * unreadable or damaged lines are skipped (the original file is backed up first).
     */
    public List<Link> load() {
        lastLoadWarning = null;
        List<Link> links = new ArrayList<>();

        if (!Files.exists(file)) {
            return links; // first run - nothing saved yet
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            // RuntimeException also covers invalid UTF-8 (MalformedInputException is an IOException,
            // but be defensive about anything odd in a damaged file).
            backupDamagedFile();
            lastLoadWarning = "Your saved links file could not be read, so the app started empty. A backup was kept.";
            return links;
        }

        int skipped = 0;
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            Link link = parseLine(line);
            if (link == null) {
                skipped++;
            } else {
                links.add(link);
            }
        }

        if (skipped > 0) {
            backupDamagedFile();
            lastLoadWarning = skipped + " damaged record(s) in the saved file were skipped. A backup was kept.";
        }
        return links;
    }

    /** Writes all links. The data goes to a temp file first, then replaces the real file. */
    public void save(List<Link> links) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Link link : links) {
            lines.add(toLine(link));
        }

        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ---- serialization helpers ----

    private String toLine(Link link) {
        return encode(link.getId()) + SEPARATOR
                + encode(link.getName()) + SEPARATOR
                + encode(link.getUrl()) + SEPARATOR
                + encode(link.getCategory()) + SEPARATOR
                + link.isFavorite();
    }

    /** Returns null when the line is not a valid record. */
    private Link parseLine(String line) {
        String[] parts = line.split(SEPARATOR, -1);
        if (parts.length != COLUMNS) {
            return null;
        }
        try {
            String id = decode(parts[0]);
            String name = decode(parts[1]);
            String url = decode(parts[2]);
            String category = decode(parts[3]);
            if (id.isBlank() || name.isBlank() || url.isBlank()) {
                return null;
            }
            if (!parts[4].equals("true") && !parts[4].equals("false")) {
                return null;
            }
            return new Link(id, name, url, category, Boolean.parseBoolean(parts[4]));
        } catch (IllegalArgumentException e) {
            return null; // bad percent-encoding
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void backupDamagedFile() {
        try {
            Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // A failed backup must never stop the application from starting.
        }
    }
}
