package edu.scu.csen275.group5.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the soil in the garden.
 * Manages soil properties like moisture, nutrient levels, and composition.
 * Affects plant growth and survival.
 */
public class Soil {
    private double moisture;  // 0.0 to 100.0 percent
    private double nutrients;  // 0.0 to 100.0 percent
    private double pH;  // typical range 5.5 to 8.5
    private double temperature;  // soil temperature in Fahrenheit
    private List<String> currentPests;  // pests currently in the soil

    // Soil properties
    private static final double OPTIMAL_MOISTURE = 60.0;
    private static final double OPTIMAL_PH = 7.0;
    private static final double EVAPORATION_RATE = 2.0;  // percent per day

    public Soil() {
        this.moisture = 60.0;  // Start at optimal moisture
        this.nutrients = 80.0;  // Good nutrient level
        this.pH = 7.0;  // Neutral pH
        this.temperature = 70.0;  // 70°F
        this.currentPests = new ArrayList<>();
    }

    /**
     * Add water to the soil through rain or irrigation
     * @param waterAmount amount of water to add
     */
    public void addWater(double waterAmount) {
        this.moisture += waterAmount;
        // Cap moisture - excess water can cause root rot
        if (this.moisture > 95.0) {
            this.moisture = 95.0;
        }
    }

    /**
     * Simulate daily water evaporation
     */
    public void evaporateWater() {
        this.moisture -= EVAPORATION_RATE;
        if (this.moisture < 0) {
            this.moisture = 0;
        }
    }

    /**
     * Add nutrients to soil (e.g., through fertilizer)
     * @param nutrientAmount amount of nutrients to add
     */
    public void addNutrients(double nutrientAmount) {
        this.nutrients += nutrientAmount;
        if (this.nutrients > 100) {
            this.nutrients = 100;
        }
    }

    /**
     * Simulate nutrient depletion as plants consume them
     * @param depletionAmount amount of nutrients plants consume
     */
    public void depleteNutrients(double depletionAmount) {
        this.nutrients -= depletionAmount;
        if (this.nutrients < 0) {
            this.nutrients = 0;
        }
    }

    /**
     * Update soil temperature based on air temperature
     * Soil temperature changes more slowly than air temperature
     * @param airTemperature current air temperature in Fahrenheit
     */
    public void updateTemperature(int airTemperature) {
        // Soil temperature lags behind air temperature (simplified physics)
        double tempDifference = airTemperature - this.temperature;
        this.temperature += tempDifference * 0.3;  // 30% adjustment per day
    }

    /**
     * Add a pest to the soil
     * @param pestName name of the pest
     */
    public void addPest(String pestName) {
        if (!currentPests.contains(pestName)) {
            currentPests.add(pestName);
        }
    }

    /**
     * Remove a pest from the soil (through treatment)
     * @param pestName name of the pest to remove
     */
    public void removePest(String pestName) {
        currentPests.remove(pestName);
    }

    /**
     * Check if a specific pest is present
     * @param pestName name of the pest to check
     * @return true if pest is present
     */
    public boolean hasPest(String pestName) {
        return currentPests.contains(pestName);
    }

    /**
     * Simulate daily soil degradation
     */
    public void advanceDay() {
        evaporateWater();
        depleteNutrients(0.5);  // Plants consume nutrients daily
        
        // pH naturally tends toward neutral slightly
        if (this.pH < OPTIMAL_PH) {
            this.pH += 0.05;
        } else if (this.pH > OPTIMAL_PH) {
            this.pH -= 0.05;
        }
    }

    // Getters and Setters
    public double getMoisture() { return moisture; }
    public void setMoisture(double moisture) { 
        this.moisture = Math.max(0, Math.min(100, moisture)); 
    }

    public double getNutrients() { return nutrients; }
    public void setNutrients(double nutrients) { 
        this.nutrients = Math.max(0, Math.min(100, nutrients)); 
    }

    public double getPH() { return pH; }
    public void setPH(double pH) { this.pH = pH; }

    public double getTemperature() { return temperature; }

    public List<String> getCurrentPests() { return new ArrayList<>(currentPests); }

    public boolean isPestPresent() {
        return !currentPests.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("Soil - Moisture: %.1f%%, Nutrients: %.1f%%, pH: %.2f, Temp: %.1f°F, Pests: %s",
                moisture, nutrients, pH, temperature, currentPests);
    }
}
