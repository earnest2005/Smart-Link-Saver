package com.smartlinksaver.controller;

import com.smartlinksaver.model.Link;
import com.smartlinksaver.service.LinkException;
import com.smartlinksaver.service.LinkManager;
import com.smartlinksaver.util.AppResources;
import com.smartlinksaver.util.BrowserLauncher;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the whole dashboard UI in code and connects every control to LinkManager.
 * Every user action ends with refresh(), so the screen always matches the data.
 */
public class DashboardController {

    /** The four screens reachable from the sidebar. */
    private enum View { DASHBOARD, ALL_LINKS, FAVORITES, CATEGORIES }

    private static final int RECENT_LIMIT = 6;

    private final LinkManager manager;

    private View currentView = View.DASHBOARD;
    private String selectedCategory = null; // null = all categories

    private final BorderPane root = new BorderPane();
    private final Map<View, Button> navButtons = new EnumMap<>(View.class);
    private final Label pageTitle = new Label();
    private final TextField searchField = new TextField();
    private final VBox contentBox = new VBox(16);
    private final Label toast = new Label();
    private final PauseTransition toastTimer = new PauseTransition(Duration.seconds(3));

    public DashboardController(LinkManager manager) {
        this.manager = manager;
        buildLayout();
        refresh();
    }

    public Parent getRoot() {
        return root;
    }

    /** Shows a warning if saved data had to be repaired when the app started. */
    public void showStartupMessages() {
        String warning = manager.getStartupWarning();
        if (warning != null) {
            showToast(warning, false, 7);
        }
    }

    // ==================================================================
    // Layout
    // ==================================================================

    private void buildLayout() {
        root.getStyleClass().add("root-pane");
        root.setLeft(buildSidebar());

        ScrollPane scroll = new ScrollPane(contentBox);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");
        contentBox.setPadding(new Insets(8, 24, 24, 24));
        contentBox.getStyleClass().add("main-area");

        VBox center = new VBox(buildHeader(), scroll);
        center.getStyleClass().add("main-area");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        toast.getStyleClass().add("toast");
        toast.setVisible(false);
        toast.setMouseTransparent(true);
        StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toast, new Insets(0, 28, 24, 0));

        root.setCenter(new StackPane(center, toast));
    }

    private VBox buildSidebar() {
        Label badge = new Label("S");
        badge.getStyleClass().add("logo-badge");
        Label brand = new Label("Smart Link Saver");
        brand.getStyleClass().add("brand-title");
        HBox logo = new HBox(10, badge, brand);
        logo.setAlignment(Pos.CENTER_LEFT);
        logo.setPadding(new Insets(0, 0, 16, 0));

        VBox sidebar = new VBox(4, logo);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);
        sidebar.setPadding(new Insets(20, 12, 16, 12));

        addNavButton(sidebar, View.DASHBOARD, "Dashboard");
        addNavButton(sidebar, View.ALL_LINKS, "All Links");
        addNavButton(sidebar, View.FAVORITES, "Favorites");
        addNavButton(sidebar, View.CATEGORIES, "Categories");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label footer = new Label("Links are saved locally\non this computer");
        footer.getStyleClass().add("sidebar-footer");
        Tooltip.install(footer, new Tooltip("File: " + manager.getStorage().getFile()));
        sidebar.getChildren().addAll(spacer, footer);
        return sidebar;
    }

    private void addNavButton(VBox sidebar, View view, String text) {
        Button button = new Button(text);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> {
            currentView = view;
            selectedCategory = null;
            refresh();
        });
        navButtons.put(view, button);
        sidebar.getChildren().add(button);
    }

    private HBox buildHeader() {
        pageTitle.getStyleClass().add("page-title");

        searchField.setPromptText("Search by name or URL...");
        searchField.getStyleClass().add("search-field");
        searchField.setPrefWidth(320);
        // Runs on every keystroke, so results update immediately.
        searchField.textProperty().addListener((obs, oldText, newText) -> refresh());

        Button addButton = new Button("Add New Link");
        addButton.getStyleClass().add("primary-button");
        addButton.setOnAction(e -> onAddLink());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(16, pageTitle, spacer, searchField, addButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(24, 24, 16, 24));
        header.getStyleClass().add("main-area");
        return header;
    }

    // ==================================================================
    // Refresh: rebuild the content area from the current data
    // ==================================================================

    private void refresh() {
        for (Map.Entry<View, Button> entry : navButtons.entrySet()) {
            entry.getValue().getStyleClass().remove("nav-button-active");
            if (entry.getKey() == currentView) {
                entry.getValue().getStyleClass().add("nav-button-active");
            }
        }

        String query = searchField.getText();
        boolean searching = query != null && !query.isBlank();
        contentBox.getChildren().clear();

        switch (currentView) {
            case DASHBOARD -> {
                pageTitle.setText("Dashboard");
                contentBox.getChildren().add(buildStatsRow());
                List<Link> found = manager.search(query, null, false);
                if (!searching && found.size() > RECENT_LIMIT) {
                    found = found.subList(0, RECENT_LIMIT);
                }
                contentBox.getChildren().add(buildCardsSection(searching ? "Search results" : "Recent links", found));
            }
            case ALL_LINKS -> {
                pageTitle.setText("All Links");
                contentBox.getChildren().add(buildCategoryChips(false));
                contentBox.getChildren().add(buildCardsSection(null, manager.search(query, selectedCategory, false)));
            }
            case FAVORITES -> {
                pageTitle.setText("Favorites");
                contentBox.getChildren().add(buildCategoryChips(true));
                contentBox.getChildren().add(buildCardsSection(null, manager.search(query, selectedCategory, true)));
            }
            case CATEGORIES -> {
                pageTitle.setText("Categories");
                contentBox.getChildren().add(buildCategoryTiles());
                String heading = selectedCategory == null ? "All links" : selectedCategory;
                contentBox.getChildren().add(buildCardsSection(heading, manager.search(query, selectedCategory, false)));
            }
        }
    }

    // ------------------------------------------------------------------
    // Statistics, filters
    // ------------------------------------------------------------------

    private HBox buildStatsRow() {
        HBox row = new HBox(16,
                buildStatCard("Total Links", manager.getTotalLinks()),
                buildStatCard("Favorite Links", manager.getFavoriteCount()),
                buildStatCard("Total Categories", manager.getCategoryCount()));
        return row;
    }

    private HBox buildStatCard(String title, int value) {
        Label caption = new Label(title);
        caption.getStyleClass().add("stat-title");
        Label number = new Label(String.valueOf(value));
        number.getStyleClass().add("stat-value");

        HBox card = new HBox(new VBox(4, caption, number));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("stat-card");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    /** Row of filter buttons: "All" plus one per category, each with its link count. */
    private FlowPane buildCategoryChips(boolean favoritesOnly) {
        Map<String, Integer> counts = manager.countByCategory(favoritesOnly);
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();

        FlowPane chips = new FlowPane(8, 8);
        chips.getChildren().add(createChip("All (" + total + ")", selectedCategory == null, null));
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String category = entry.getKey();
            chips.getChildren().add(createChip(category + " (" + entry.getValue() + ")",
                    category.equals(selectedCategory), category));
        }
        return chips;
    }

    private Button createChip(String text, boolean selected, String category) {
        Button chip = new Button(text);
        chip.getStyleClass().add("chip");
        if (selected) chip.getStyleClass().add("chip-selected");
        chip.setOnAction(e -> {
            selectedCategory = category;
            refresh();
        });
        return chip;
    }

    /** Large clickable tiles shown on the Categories screen. */
    private FlowPane buildCategoryTiles() {
        FlowPane tiles = new FlowPane(12, 12);
        for (Map.Entry<String, Integer> entry : manager.countByCategory(false).entrySet()) {
            String category = entry.getKey();
            int count = entry.getValue();

            Label dot = new Label(category.substring(0, 1));
            dot.getStyleClass().add("tile-icon");
            Label name = new Label(category);
            name.getStyleClass().add("tile-title");
            Label size = new Label(count == 1 ? "1 link" : count + " links");
            size.getStyleClass().add("tile-subtitle");

            VBox tile = new VBox(8, dot, new VBox(2, name, size));
            tile.getStyleClass().add("category-tile");
            if (category.equals(selectedCategory)) tile.getStyleClass().add("tile-selected");
            tile.setPrefWidth(180);
            tile.setOnMouseClicked(e -> {
                // Clicking the selected tile again clears the filter.
                selectedCategory = category.equals(selectedCategory) ? null : category;
                refresh();
            });
            tiles.getChildren().add(tile);
        }
        return tiles;
    }

    // ------------------------------------------------------------------
    // Link cards
    // ------------------------------------------------------------------

    private VBox buildCardsSection(String heading, List<Link> links) {
        VBox section = new VBox(12);
        if (heading != null) {
            Label title = new Label(heading);
            title.getStyleClass().add("section-title");
            section.getChildren().add(title);
        }

        if (links.isEmpty()) {
            section.getChildren().add(buildEmptyState());
            return section;
        }

        FlowPane cards = new FlowPane(16, 16); // wraps automatically when the window is resized
        for (Link link : links) {
            cards.getChildren().add(buildCard(link));
        }
        section.getChildren().add(cards);
        return section;
    }

    private VBox buildCard(Link link) {
        Label avatar = new Label(link.getName().substring(0, 1).toUpperCase());
        avatar.getStyleClass().add("card-avatar");

        Label name = new Label(link.getName());
        name.getStyleClass().add("card-title");
        name.setMaxWidth(Double.MAX_VALUE);
        Label category = new Label(link.getCategory());
        category.getStyleClass().add("category-badge");
        VBox titleBox = new VBox(4, name, category);
        titleBox.setMinWidth(0);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Button favorite = new Button(link.isFavorite() ? "\u2605" : "\u2606");
        favorite.getStyleClass().add("fav-button");
        if (link.isFavorite()) favorite.getStyleClass().add("fav-active");
        favorite.setTooltip(new Tooltip(link.isFavorite() ? "Remove from favorites" : "Add to favorites"));
        favorite.setOnAction(e -> onToggleFavorite(link));

        HBox top = new HBox(10, avatar, titleBox, favorite);
        top.setAlignment(Pos.CENTER_LEFT);

        Label url = new Label(link.getUrl());
        url.getStyleClass().add("card-url");
        url.setMaxWidth(Double.MAX_VALUE);
        url.setTooltip(new Tooltip(link.getUrl()));

        Button open = new Button("Open");
        open.getStyleClass().addAll("card-button", "open-button");
        open.setOnAction(e -> onOpenLink(link));
        Button edit = new Button("Edit");
        edit.getStyleClass().addAll("card-button", "edit-button");
        edit.setOnAction(e -> onEditLink(link));
        Button delete = new Button("Delete");
        delete.getStyleClass().addAll("card-button", "delete-button");
        delete.setOnAction(e -> onDeleteLink(link));
        HBox actions = new HBox(8, open, edit, delete);

        VBox card = new VBox(10, top, url, actions);
        card.getStyleClass().add("link-card");
        card.setPrefWidth(310);
        card.setMinWidth(270);
        return card;
    }

    private VBox buildEmptyState() {
        boolean noLinksAtAll = manager.getTotalLinks() == 0;
        boolean searching = searchField.getText() != null && !searchField.getText().isBlank();

        String title;
        String message;
        if (noLinksAtAll) {
            title = "No links saved yet";
            message = "Click \"Add New Link\" to save your first website.";
        } else if (currentView == View.FAVORITES && !searching && selectedCategory == null) {
            title = "No favorites yet";
            message = "Tap the star on any link card to add it here.";
        } else {
            title = "No matching links";
            message = "Try a different search or category.";
        }

        Label heading = new Label(title);
        heading.getStyleClass().add("empty-title");
        Label text = new Label(message);
        text.getStyleClass().add("empty-text");

        VBox box = new VBox(6, heading, text);
        if (noLinksAtAll) {
            Button add = new Button("Add New Link");
            add.getStyleClass().add("primary-button");
            add.setOnAction(e -> onAddLink());
            VBox.setMargin(add, new Insets(8, 0, 0, 0));
            box.getChildren().add(add);
        }
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("empty-state");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    // ==================================================================
    // Actions
    // ==================================================================

    private void onAddLink() {
        Optional<LinkDialog.Result> result = LinkDialog.show(root.getScene().getWindow(), manager, null);
        if (result.isEmpty()) return;
        try {
            manager.addLink(result.get().name(), result.get().url(), result.get().category());
            refresh();
            showToast("Link added successfully.", true);
        } catch (LinkException e) {
            showToast(e.getMessage(), false);
        }
    }

    private void onEditLink(Link link) {
        Optional<LinkDialog.Result> result = LinkDialog.show(root.getScene().getWindow(), manager, link);
        if (result.isEmpty()) return;
        try {
            manager.updateLink(link.getId(), result.get().name(), result.get().url(), result.get().category());
            refresh();
            showToast("Link updated successfully.", true);
        } catch (LinkException e) {
            showToast(e.getMessage(), false);
        }
    }

    private void onDeleteLink(Link link) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(root.getScene().getWindow());
        confirm.setTitle("Delete Link");
        confirm.setHeaderText("Delete \"" + link.getName() + "\"?");
        confirm.setContentText("This link will be permanently removed.");
        confirm.getDialogPane().getStylesheets().add(AppResources.stylesheet());
        confirm.getDialogPane().getStyleClass().add("app-dialog");

        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) return;

        try {
            manager.deleteLink(link.getId());
            refresh();
            showToast("Link deleted.", true);
        } catch (LinkException e) {
            showToast(e.getMessage(), false);
        }
    }

    private void onToggleFavorite(Link link) {
        try {
            boolean nowFavorite = manager.toggleFavorite(link.getId());
            refresh();
            showToast(nowFavorite ? "Added to favorites." : "Removed from favorites.", true);
        } catch (LinkException e) {
            showToast(e.getMessage(), false);
        }
    }

    /** Opens the browser on a background thread so the window never freezes. */
    private void onOpenLink(Link link) {
        Thread worker = new Thread(() -> {
            try {
                BrowserLauncher.open(link.getUrl());
                Platform.runLater(() -> showToast("Opening " + link.getName() + "...", true));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(link.getUrl());
                    Clipboard.getSystemClipboard().setContent(content);
                    showToast("Could not open the browser. The URL was copied to your clipboard.", false, 5);
                });
            }
        }, "open-browser");
        worker.setDaemon(true);
        worker.start();
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private void showToast(String message, boolean success) {
        showToast(message, success, 3);
    }

    private void showToast(String message, boolean success, double seconds) {
        toast.setText(message);
        toast.getStyleClass().removeAll("toast-success", "toast-error");
        toast.getStyleClass().add(success ? "toast-success" : "toast-error");
        toast.setVisible(true);

        toastTimer.stop();
        toastTimer.setDuration(Duration.seconds(seconds));
        toastTimer.setOnFinished(e -> toast.setVisible(false));
        toastTimer.playFromStart();
    }

    private static String categoryColor(String category) {
        return switch (category) {
            case "Programming" -> "#6366f1";
            case "Education" -> "#0ea5e9";
            case "AI/ML" -> "#a855f7";
            case "Projects" -> "#f59e0b";
            case "Entertainment" -> "#ec4899";
            default -> "#64748b";
        };
    }
}
