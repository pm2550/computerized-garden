package edu.scu.csen275.group5.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

/**
 * Helper for visualizing plant status. Uses color coding instead of images
 * to keep things simple but still look decent in the UI.
 */
public class PlantVisualizer {
    
    private static final int TILE_SIZE = 65;  // smaller tiles to fit more plants without scrolling
    
    /**
     * Creates a visual tile for a plant in the garden map.
     * Shows: colored background (health), plant symbol, name, and status.
     */
    public static StackPane createPlantTile(String name, String type, double healthPercent, 
                                             int currentWater, int waterRequirement, int ageDays,
                                             boolean alive) {
        StackPane tile = new StackPane();
        tile.setPrefSize(TILE_SIZE, TILE_SIZE);
        tile.setMaxSize(TILE_SIZE, TILE_SIZE);
        
        // background colored by health
        Rectangle bg = new Rectangle(TILE_SIZE, TILE_SIZE);
        bg.setFill(alive ? getHealthColor(healthPercent) : Color.DARKGRAY);
        bg.setStroke(Color.BLACK);
        bg.setStrokeWidth(1.5);
        bg.setArcWidth(8);
        bg.setArcHeight(8);
        
        // plant symbol (emoji or letter) - smaller for compact tiles
        Label symbol = new Label(getPlantSymbol(type));
        symbol.setFont(Font.font("System", FontWeight.BOLD, 24));
        symbol.setTextFill(Color.WHITE);
        symbol.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 2, 0, 0, 1);");
        
        // plant name below symbol - smaller font
        Label nameLabel = new Label(name);
        nameLabel.setFont(Font.font("System", FontWeight.NORMAL, 8));
        nameLabel.setTextFill(Color.WHITE);
        nameLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 1, 0, 0, 1);");
        
        VBox content = new VBox(1, symbol, nameLabel);
        content.setAlignment(Pos.CENTER);
        
        tile.getChildren().addAll(bg, content);
        
        // tooltip with detailed info
        String waterStatus = getWaterStatus(currentWater, waterRequirement);
        String healthStatus = getHealthStatus(healthPercent);
        String tooltipText = String.format(
            "%s (%s)\nHealth: %.0f%% - %s\nWater: %d/%d - %s\nAge: %d day%s\nStatus: %s",
            name, type, healthPercent, healthStatus, 
            currentWater, waterRequirement, waterStatus,
            Math.max(0, ageDays), ageDays == 1 ? "" : "s",
            alive ? "Alive" : "Dead"
        );
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setShowDelay(Duration.millis(150));
        tooltip.setHideDelay(Duration.millis(200));
        tooltip.setShowDuration(Duration.seconds(10));
        Tooltip.install(tile, tooltip);
        
        return tile;
    }
    
    // maps plant health to a color for display
    public static Color getHealthColor(double healthPercent) {
        if (healthPercent >= 80) {
            return Color.DARKGREEN;  // thriving
        } else if (healthPercent >= 50) {
            return Color.YELLOWGREEN;  // OK
        } else if (healthPercent >= 20) {
            return Color.ORANGE;  // struggling
        } else {
            return Color.DARKRED;  // dying
        }
    }
    
    // returns a single-char symbol based on plant type (for compact display)
    public static String getPlantSymbol(String plantType) {
        if (plantType == null || plantType.isEmpty()) {
            return "?";
        }
        
        String lower = plantType.toLowerCase();
        // match common plant types
        if (lower.contains("rose")) return "🌹";
        if (lower.contains("tomato")) return "🍅";
        if (lower.contains("carrot")) return "🥕";
        if (lower.contains("pepper")) return "🌶";
        if (lower.contains("lettuce")) return "🥬";
        if (lower.contains("cucumber")) return "🥒";
        if (lower.contains("sunflower")) return "🌻";
        
        // fallback: use first letter
        return plantType.substring(0, 1).toUpperCase();
    }
    
    // describes water level in human terms
    public static String getWaterStatus(int currentWater, int requirement) {
        double ratio = (double) currentWater / requirement;
        if (ratio >= 1.0) return "Good";
        if (ratio >= 0.5) return "OK";
        if (ratio > 0) return "Low";
        return "Dry";
    }
    
    // describes health level in human terms
    public static String getHealthStatus(double healthPercent) {
        if (healthPercent >= 80) return "Healthy";
        if (healthPercent >= 50) return "Fair";
        if (healthPercent >= 20) return "Sick";
        if (healthPercent > 0) return "Dying";
        return "Dead";
    }
}
