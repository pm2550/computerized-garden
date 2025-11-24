package edu.scu.csen275.group5.ui;

import javafx.scene.paint.Color;

/**
 * Helper for visualizing plant status. Uses color coding instead of images
 * to keep things simple but still look decent in the UI.
 */
public class PlantVisualizer {
    
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
