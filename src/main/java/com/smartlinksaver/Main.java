package com.smartlinksaver;

import com.smartlinksaver.controller.DashboardController;
import com.smartlinksaver.service.LinkManager;
import com.smartlinksaver.storage.FileStorage;
import com.smartlinksaver.util.AppResources;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Application entry point: wires storage, business logic and the dashboard together. */
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        FileStorage storage = new FileStorage();
        LinkManager manager = new LinkManager(storage);
        DashboardController dashboard = new DashboardController(manager);

        Scene scene = new Scene(dashboard.getRoot(), 1180, 760);
        scene.getStylesheets().add(AppResources.stylesheet());

        stage.setTitle("Smart Link Saver");
        stage.setMinWidth(980);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();

        dashboard.showStartupMessages();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
