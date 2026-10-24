package com.smartlinksaver.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Data model for one saved website link.
 * All fields are private (encapsulation); the id never changes after creation.
 */
public class Link {

    private final String id;
    private String name;
    private String url;
    private String category;
    private boolean favorite;

    /** Creates a brand-new link with a freshly generated unique ID. */
    public Link(String name, String url, String category) {
        this(UUID.randomUUID().toString(), name, url, category, false);
    }

    /** Creates a link with a known ID (used when loading from storage). */
    public Link(String id, String name, String url, String category, boolean favorite) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.category = category;
        this.favorite = favorite;
    }

    public String getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    // Two links are "the same" when they share an ID.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Link other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Link{" + name + " -> " + url + " [" + category + "]" + (favorite ? " *" : "") + "}";
    }
}
