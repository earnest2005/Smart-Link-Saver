package com.smartlinksaver.service;

import com.smartlinksaver.model.Link;
import com.smartlinksaver.storage.FileStorage;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Business logic layer: keeps the in-memory ArrayList of links, validates input,
 * and asks FileStorage to save after every change. The UI only talks to this class.
 */
public class LinkManager {

    public static final List<String> CATEGORIES =
            List.of("Programming", "Education", "AI/ML", "Projects", "Entertainment", "Other");

    private static final int MAX_NAME_LENGTH = 60;

    private final List<Link> links = new ArrayList<>();
    private final FileStorage storage;

    public LinkManager(FileStorage storage) {
        this.storage = storage;
        for (Link link : storage.load()) {
            // Repair records whose category is unknown (e.g. file edited by hand).
            if (!CATEGORIES.contains(link.getCategory())) {
                link.setCategory("Other");
            }
            links.add(link);
        }
    }

    /** Warning produced while loading the file (damaged data), or null. */
    public String getStartupWarning() {
        return storage.getLastLoadWarning();
    }

    public FileStorage getStorage() {
        return storage;
    }

    // ------------------------------------------------------------------
    // Create / update / delete
    // ------------------------------------------------------------------

    public Link addLink(String name, String url, String category) throws LinkException {
        validate(name, url, category, null);
        Link link = new Link(name.trim(), url.trim(), category);
        links.add(link);
        try {
            persist();
        } catch (LinkException e) {
            links.remove(link); // keep memory and disk consistent
            throw e;
        }
        return link;
    }

    public Link updateLink(String id, String name, String url, String category) throws LinkException {
        Link link = findById(id);
        validate(name, url, category, id);

        String oldName = link.getName();
        String oldUrl = link.getUrl();
        String oldCategory = link.getCategory();

        link.setName(name.trim());
        link.setUrl(url.trim());
        link.setCategory(category);
        try {
            persist();
        } catch (LinkException e) {
            link.setName(oldName);
            link.setUrl(oldUrl);
            link.setCategory(oldCategory);
            throw e;
        }
        return link;
    }

    public void deleteLink(String id) throws LinkException {
        Link link = findById(id);
        int index = links.indexOf(link);
        links.remove(index);
        try {
            persist();
        } catch (LinkException e) {
            links.add(index, link);
            throw e;
        }
    }

    /** Flips the favorite flag and returns the new value. */
    public boolean toggleFavorite(String id) throws LinkException {
        Link link = findById(id);
        boolean previous = link.isFavorite();
        link.setFavorite(!previous);
        try {
            persist();
        } catch (LinkException e) {
            link.setFavorite(previous);
            throw e;
        }
        return link.isFavorite();
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** A copy of every link, oldest first. */
    public List<Link> getAllLinks() {
        return new ArrayList<>(links);
    }

    /**
     * Filters links (newest first).
     *
     * @param query         text to look for in name or URL (case-insensitive); null/blank = no text filter
     * @param category      only this category; null = every category
     * @param favoritesOnly only favorites when true
     */
    public List<Link> search(String query, String category, boolean favoritesOnly) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Link> result = new ArrayList<>();
        for (int i = links.size() - 1; i >= 0; i--) {
            Link link = links.get(i);
            if (favoritesOnly && !link.isFavorite()) continue;
            if (category != null && !category.equals(link.getCategory())) continue;
            if (!needle.isEmpty()
                    && !link.getName().toLowerCase(Locale.ROOT).contains(needle)
                    && !link.getUrl().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            result.add(link);
        }
        return result;
    }

    public int getTotalLinks() {
        return links.size();
    }

    public int getFavoriteCount() {
        int count = 0;
        for (Link link : links) {
            if (link.isFavorite()) count++;
        }
        return count;
    }

    /** Number of different categories that currently contain at least one link. */
    public int getCategoryCount() {
        int count = 0;
        for (int n : countByCategory(false).values()) {
            if (n > 0) count++;
        }
        return count;
    }

    /** Link count per category, in the standard category order. */
    public Map<String, Integer> countByCategory(boolean favoritesOnly) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            counts.put(category, 0);
        }
        for (Link link : links) {
            if (favoritesOnly && !link.isFavorite()) continue;
            counts.merge(link.getCategory(), 1, Integer::sum);
        }
        return counts;
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /**
     * Checks user input and throws a LinkException with a friendly message when it is wrong.
     *
     * @param ignoreId ID of the link being edited (so it does not count as its own duplicate); null when adding
     */
    public void validate(String name, String url, String category, String ignoreId) throws LinkException {
        if (name == null || name.isBlank()) {
            throw new LinkException("Website name is required.");
        }
        if (name.trim().length() > MAX_NAME_LENGTH) {
            throw new LinkException("Website name must be at most " + MAX_NAME_LENGTH + " characters.");
        }
        if (url == null || url.isBlank()) {
            throw new LinkException("URL is required.");
        }
        validateUrlFormat(url.trim());
        if (category == null || !CATEGORIES.contains(category)) {
            throw new LinkException("Please choose a category.");
        }

        String wanted = comparisonKey(url);
        for (Link existing : links) {
            if (existing.getId().equals(ignoreId)) continue;
            if (comparisonKey(existing.getUrl()).equals(wanted)) {
                throw new LinkException("This URL is already saved as \"" + existing.getName() + "\".");
            }
        }
    }

    private void validateUrlFormat(String url) throws LinkException {
        final String message = "Enter a valid URL, e.g. https://www.example.com";

        if (!url.regionMatches(true, 0, "http://", 0, 7) && !url.regionMatches(true, 0, "https://", 0, 8)) {
            throw new LinkException("URL must start with http:// or https://");
        }
        for (int i = 0; i < url.length(); i++) {
            if (Character.isWhitespace(url.charAt(i))) {
                throw new LinkException("URL must not contain spaces.");
            }
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new LinkException(message);
            }
            boolean looksLikeDomain = host.contains(".") && !host.startsWith(".") && !host.endsWith(".");
            if (!looksLikeDomain && !host.equalsIgnoreCase("localhost")) {
                throw new LinkException(message);
            }
        } catch (URISyntaxException e) {
            throw new LinkException(message);
        }
    }

    /** Lower-cased URL without trailing slashes, used to detect duplicates. */
    private static String comparisonKey(String url) {
        return url.trim().toLowerCase(Locale.ROOT).replaceAll("/+$", "");
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private Link findById(String id) throws LinkException {
        for (Link link : links) {
            if (link.getId().equals(id)) return link;
        }
        throw new LinkException("That link no longer exists.");
    }

    private void persist() throws LinkException {
        try {
            storage.save(links);
        } catch (IOException e) {
            throw new LinkException("Could not save your links to disk: " + e.getMessage(), e);
        }
    }
}
