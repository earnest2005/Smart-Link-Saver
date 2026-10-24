package com.smartlinksaver.controller;

import com.smartlinksaver.model.Link;
import com.smartlinksaver.service.LinkException;
import com.smartlinksaver.service.LinkManager;
import com.smartlinksaver.util.AppResources;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/** Modal dialog used for both "Add New Link" and "Edit Link". */
public final class LinkDialog {

    /** The values the user entered (already trimmed and validated). */
    public record Result(String name, String url, String category) { }

    private LinkDialog() { }

    /**
     * @param existing the link being edited, or null to add a new one
     * @return the entered values, or empty if the user cancelled
     */
    public static Optional<Result> show(Window owner, LinkManager manager, Link existing) {
        boolean editing = existing != null;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(editing ? "Edit Link" : "Add New Link");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(AppResources.stylesheet());
        pane.getStyleClass().add("app-dialog");

        ButtonType saveType = new ButtonType(editing ? "Update Link" : "Save Link", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // ---- form fields ----
        TextField nameField = new TextField();
        nameField.setPromptText("e.g. GitHub");
        nameField.getStyleClass().add("app-input");

        TextField urlField = new TextField();
        urlField.setPromptText("https://github.com");
        urlField.getStyleClass().add("app-input");

        ComboBox<String> categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll(LinkManager.CATEGORIES);
        categoryBox.setPromptText("Choose a category");
        categoryBox.setMaxWidth(Double.MAX_VALUE);
        categoryBox.getStyleClass().add("app-input");

        if (editing) {
            nameField.setText(existing.getName());
            urlField.setText(existing.getUrl());
            categoryBox.setValue(existing.getCategory());
        }

        Label error = new Label();
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setVisible(false);
        error.managedProperty().bind(error.visibleProperty());

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.add(fieldLabel("Website name"), 0, 0);
        form.add(nameField, 0, 1);
        form.add(fieldLabel("URL"), 0, 2);
        form.add(urlField, 0, 3);
        form.add(fieldLabel("Category"), 0, 4);
        form.add(categoryBox, 0, 5);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(urlField, Priority.ALWAYS);
        GridPane.setHgrow(categoryBox, Priority.ALWAYS);

        Label heading = new Label(editing ? "Edit link" : "Save a new link");
        heading.getStyleClass().add("dialog-heading");

        VBox content = new VBox(12, heading, form, error);
        content.setPadding(new Insets(8, 4, 0, 4));
        content.setPrefWidth(420);
        pane.setContent(content);

        // ---- validate before the dialog is allowed to close ----
        Button saveButton = (Button) pane.lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                manager.validate(nameField.getText(), urlField.getText(), categoryBox.getValue(),
                        editing ? existing.getId() : null);
                error.setVisible(false);
            } catch (LinkException e) {
                error.setText(e.getMessage());
                error.setVisible(true);
                event.consume(); // keep the dialog open
            }
        });

        Platform.runLater(nameField::requestFocus);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isPresent() && answer.get() == saveType) {
            return Optional.of(new Result(nameField.getText().trim(), urlField.getText().trim(),
                    categoryBox.getValue()));
        }
        return Optional.empty();
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }
}
