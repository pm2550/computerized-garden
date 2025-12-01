package edu.scu.csen275.group5;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * JavaFX App
 */
public class App extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        scene = new Scene(loadFXML("primary"), 1100, 720);
        scene.getStylesheets().add(App.class.getResource("app.css").toExternalForm());
        stage.setTitle("Computerized Garden Control Room");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
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
