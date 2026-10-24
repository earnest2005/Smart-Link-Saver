package com.smartlinksaver.service;

import com.smartlinksaver.model.Link;
import com.smartlinksaver.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinkManagerTest {

    @TempDir
    Path tempDir;

    private Path dataFile;
    private LinkManager manager;

    @BeforeEach
    void setUp() {
        dataFile = tempDir.resolve("links.dat");
        manager = new LinkManager(new FileStorage(dataFile));
    }

    private LinkManager reopen() {
        return new LinkManager(new FileStorage(dataFile));
    }

    // ---------- add / view ----------

    @Test
    void addLinkStoresItWithUniqueId() throws LinkException {
        Link a = manager.addLink("GitHub", "https://github.com", "Programming");
        Link b = manager.addLink("YouTube", "https://youtube.com", "Entertainment");
        assertEquals(2, manager.getTotalLinks());
        assertNotEquals(a.getId(), b.getId());
    }

    @Test
    void emptyFieldsAreRejected() {
        assertThrows(LinkException.class, () -> manager.addLink("", "https://a.com", "Other"));
        assertThrows(LinkException.class, () -> manager.addLink("A", "  ", "Other"));
        assertThrows(LinkException.class, () -> manager.addLink("A", "https://a.com", null));
        assertEquals(0, manager.getTotalLinks());
    }

    @Test
    void invalidUrlsAreRejected() {
        String[] bad = {"github.com", "ftp://files.example.com", "http://", "https://nodot",
                "https://exa mple.com", "javascript:alert(1)", "https://.com"};
        for (String url : bad) {
            assertThrows(LinkException.class, () -> manager.addLink("X", url, "Other"), url);
        }
        assertDoesNotThrow(() -> manager.addLink("Local", "http://localhost:8080/app", "Projects"));
    }

    @Test
    void duplicateUrlsAreRejectedIgnoringCaseAndTrailingSlash() throws LinkException {
        manager.addLink("Site", "https://Example.com/", "Other");
        assertThrows(LinkException.class, () -> manager.addLink("Again", "https://example.com", "Other"));
    }

    // ---------- edit / delete / favorite ----------

    @Test
    void editChangesAllFieldsAndKeepsId() throws LinkException {
        Link link = manager.addLink("Old", "https://old.com", "Other");
        manager.updateLink(link.getId(), "New", "https://new.com", "Education");
        Link changed = manager.getAllLinks().get(0);
        assertEquals(link.getId(), changed.getId());
        assertEquals("New", changed.getName());
        assertEquals("https://new.com", changed.getUrl());
        assertEquals("Education", changed.getCategory());
    }

    @Test
    void editingWithSameUrlIsNotADuplicate() throws LinkException {
        Link link = manager.addLink("Site", "https://site.com", "Other");
        assertDoesNotThrow(() -> manager.updateLink(link.getId(), "Renamed", "https://site.com", "Other"));
    }

    @Test
    void deleteRemovesLinkAndUpdatesStats() throws LinkException {
        Link link = manager.addLink("A", "https://a.com", "Other");
        manager.addLink("B", "https://b.com", "Other");
        manager.deleteLink(link.getId());
        assertEquals(1, manager.getTotalLinks());
        assertThrows(LinkException.class, () -> manager.deleteLink(link.getId()));
    }

    @Test
    void favoriteToggleWorksAndIsCounted() throws LinkException {
        Link link = manager.addLink("A", "https://a.com", "Other");
        assertTrue(manager.toggleFavorite(link.getId()));
        assertEquals(1, manager.getFavoriteCount());
        assertFalse(manager.toggleFavorite(link.getId()));
        assertEquals(0, manager.getFavoriteCount());
    }

    // ---------- search / filter ----------

    @Test
    void searchIsCaseInsensitiveOnNameAndUrl() throws LinkException {
        manager.addLink("GitHub", "https://github.com", "Programming");
        manager.addLink("Khan Academy", "https://khanacademy.org", "Education");
        assertEquals(1, manager.search("GITHUB", null, false).size());
        assertEquals(1, manager.search("khanacademy", null, false).size());
        assertEquals(2, manager.search("", null, false).size());
        assertEquals(0, manager.search("zzz", null, false).size());
    }

    @Test
    void categoryAndFavoriteFiltersCombine() throws LinkException {
        Link a = manager.addLink("A", "https://a.com", "Programming");
        manager.addLink("B", "https://b.com", "Programming");
        manager.addLink("C", "https://c.com", "Education");
        manager.toggleFavorite(a.getId());
        assertEquals(2, manager.search(null, "Programming", false).size());
        assertEquals(1, manager.search(null, "Programming", true).size());
        assertEquals(1, manager.search(null, "Education", false).size());
        assertEquals(2, manager.getCategoryCount());
    }

    // ---------- persistence ----------

    @Test
    void linksSurviveRestart() throws LinkException {
        Link link = manager.addLink("GitHub", "https://github.com", "Programming");
        manager.toggleFavorite(link.getId());

        LinkManager restarted = reopen();
        assertEquals(1, restarted.getTotalLinks());
        Link loaded = restarted.getAllLinks().get(0);
        assertEquals(link.getId(), loaded.getId());
        assertTrue(loaded.isFavorite());
    }

    @Test
    void delimitersInNamesAndUrlsDoNotCorruptStorage() throws LinkException {
        String nastyName = "Tabs\tand\nnewlines | commas, \"quotes\" 100% café";
        manager.addLink(nastyName, "https://example.com/a?x=1&y=2,3;4%20z#frag", "Other");
        manager.addLink("Second", "https://second.com", "Other");

        LinkManager restarted = reopen();
        assertEquals(2, restarted.getTotalLinks());
        assertEquals("Second", restarted.getAllLinks().get(1).getName());
        assertEquals(nastyName, restarted.getAllLinks().get(0).getName());
        assertEquals("https://example.com/a?x=1&y=2,3;4%20z#frag", restarted.getAllLinks().get(0).getUrl());
    }

    @Test
    void corruptedFileDoesNotCrashAndKeepsGoodRecords() throws LinkException, IOException {
        manager.addLink("Good", "https://good.com", "Other");
        String saved = Files.readString(dataFile);
        Files.writeString(dataFile, saved + "this is garbage\nonly\ttwo\n%ZZ\t%ZZ\t%ZZ\t%ZZ\ttrue\n");

        LinkManager restarted = reopen();
        assertEquals(1, restarted.getTotalLinks());
        assertNotNull(restarted.getStartupWarning());
    }

    @Test
    void missingFileGivesEmptyList() {
        assertEquals(List.of(), manager.getAllLinks());
        assertNull(manager.getStartupWarning());
    }

    @Test
    void unknownCategoryInFileFallsBackToOther() throws LinkException, IOException {
        manager.addLink("A", "https://a.com", "Programming");
        String saved = Files.readString(dataFile).replace("Programming", "Weird");
        Files.writeString(dataFile, saved);
        assertEquals("Other", reopen().getAllLinks().get(0).getCategory());
    }
}
