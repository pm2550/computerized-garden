package edu.scu.csen275.group5;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import edu.scu.csen275.group5.PrimaryController;
import edu.scu.csen275.group5.SecondaryController;

import java.io.IOException;

/**
 * JavaFX App
 */
public class App extends Application {

    private static Scene scene;
    private static Parent primaryRoot;
    private static Parent secondaryRoot;
    private static PrimaryController primaryController;
    private static SecondaryController secondaryController;

    @Override
    public void start(Stage stage) throws IOException {
        scene = new Scene(loadPrimary(), 1100, 720);
        scene.getStylesheets().add(App.class.getResource("app.css").toExternalForm());
        stage.setTitle("Computerized Garden Control Room");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }

    static void setRoot(String fxml) throws IOException {
        if ("primary".equals(fxml)) {
            scene.setRoot(loadPrimary());
        } else if ("secondary".equals(fxml)) {
            scene.setRoot(loadSecondary());
        } else {
            scene.setRoot(loadFXML(fxml));
        }
    }

    private static Parent loadPrimary() throws IOException {
        if (primaryRoot == null) {
            FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("primary.fxml"));
            Parent root = fxmlLoader.load();
            primaryController = fxmlLoader.getController();
            primaryRoot = root;
        }
        return primaryRoot;
    }

    private static Parent loadSecondary() throws IOException {
        if (secondaryRoot == null) {
            FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("secondary.fxml"));
            Parent root = fxmlLoader.load();
            secondaryController = fxmlLoader.getController();
            secondaryRoot = root;
        }
        return secondaryRoot;
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        // Disable LCD subpixel text and force the T2K text renderer to avoid bold clipping
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");
        // Fall back to software pipeline for more predictable font rasterization on some GPUs
        System.setProperty("prism.order", "sw");
        launch();
    }

}
