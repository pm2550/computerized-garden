package edu.scu.csen275.group5.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a plant in the garden with its properties and current state.
 * Handles plant-specific logic for water needs, temperature tolerance, and parasite vulnerability.
 */
public class Plant {
    private String name;
    private String type;  // e.g., "Rose", "Tomato"
    private int waterRequirement;  // units of water needed per day
    private int currentWater;  // current water level
    private List<String> vulnerableParasites;  // list of parasites this plant is vulnerable to
    private double health;  // 0.0 to 100.0, represents plant health percentage
    private int age;  // days since planting
    private boolean alive;
    private int daysSinceWatering;  // tracks consecutive days without water
    private boolean infested;  // whether plant is currently infested

    // Temperature tolerance (in Fahrenheit)
    private int optimalTempMin;
    private int optimalTempMax;
    private int maxTempTolerance;  // max temperature the plant can survive
    private int minTempTolerance;  // min temperature the plant can survive

    public Plant(String name, String type, int waterRequirement, 
                 int optimalTempMin, int optimalTempMax,
                 int minTempTolerance, int maxTempTolerance) {
        this.name = name;
        this.type = type;
        this.waterRequirement = waterRequirement;
        this.currentWater = waterRequirement;  // Start well-watered
        this.vulnerableParasites = new ArrayList<>();
        this.health = 100.0;  // Start at perfect health
        this.age = 0;
        this.alive = true;
        this.daysSinceWatering = 0;
        this.infested = false;
        this.optimalTempMin = optimalTempMin;
        this.optimalTempMax = optimalTempMax;
        this.minTempTolerance = minTempTolerance;
        this.maxTempTolerance = maxTempTolerance;
    }

    /**
     * Apply water to the plant
     * @param waterAmount amount of water to apply
     */
    public void addWater(int waterAmount) {
        if (!alive) {
            return;
        }
        this.currentWater += waterAmount;
        this.daysSinceWatering = 0;  // Reset the counter
        // Cap the water at 1.5x requirement to prevent over-watering damage
        if (this.currentWater > waterRequirement * 1.5) {
            changeHealth(-5);  // Over-watering damage
            this.currentWater = (int)(waterRequirement * 1.5);
        }
    }

    /**
     * Simulate one day's water consumption
     */
    public void consumeWater() {
        if (!alive) {
            return;
        }
        this.currentWater -= this.waterRequirement;
        if (this.currentWater < 0) {
            this.daysSinceWatering++;
            this.currentWater = 0;
            
            // Health decreases if plant is not watered
            if (this.daysSinceWatering == 1) {
                changeHealth(-10);
            } else if (this.daysSinceWatering == 2) {
                changeHealth(-20);
            } else if (this.daysSinceWatering >= 3) {
                changeHealth(-30);  // Rapid health decline after 3 days
            }
        }
    }

    /**
     * Apply temperature stress to the plant
     * @param temperature current temperature in Fahrenheit
     */
    public void applyTemperatureStress(int temperature) {
        if (!alive) {
            return;
        }
        // Check if temperature is within tolerance range
        if (temperature < minTempTolerance || temperature > maxTempTolerance) {
            changeHealth(-15);  // Severe damage outside tolerance
            return;
        }

        // Check if within optimal range
        if (temperature >= optimalTempMin && temperature <= optimalTempMax) {
            changeHealth(2);  // Slight health recovery in optimal conditions
        } else {
            // Outside optimal but within tolerance
            changeHealth(-5);
        }
    }

    /**
     * Infect plant with a parasite
     * @param parasiteName name of the parasite
     */
    public void infectParasite(String parasiteName) {
        if (!alive) {
            return;
        }
        if (vulnerableParasites.contains(parasiteName)) {
            this.infested = true;
            changeHealth(-25);  // Parasite causes significant health loss
        }
    }

    /**
     * Treat parasite infestation
     */
    public void treatParasite() {
        if (this.infested) {
            this.infested = false;
            changeHealth(-10);  // Treatment also damages plant slightly
        }
    }

    /**
     * Advance one day in the simulation
     */
    public void advanceDay() {
        if (!alive) {
            return;
        }
        this.age++;
        this.consumeWater();
        if (this.health > 100) {
            this.health = 100;
        }
    }

    // Getters and Setters
    public String getName() { return name; }
    public String getType() { return type; }
    public int getWaterRequirement() { return waterRequirement; }
    public int getCurrentWater() { return currentWater; }
    public double getHealth() { return health; }
    public int getAge() { return age; }
    public boolean isAlive() { return alive; }
    public boolean isInfested() { return infested; }
    public List<String> getVulnerableParasites() { return vulnerableParasites; }
    public int getDaysSinceWatering() { return daysSinceWatering; }

    public void addVulnerableParasite(String parasite) {
        if (!vulnerableParasites.contains(parasite)) {
            vulnerableParasites.add(parasite);
        }
    }

    public void setHealth(double health) {
        this.health = Math.max(0, Math.min(100, health));
        this.alive = this.health > 0;
    }

    private void changeHealth(double delta) {
        if (!alive) {
            return;
        }
        this.health += delta;
        if (this.health > 100) {
            this.health = 100;
        }
        if (this.health <= 0) {
            this.health = 0;
            this.alive = false;
        }
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - Age: %d, Health: %.1f%%, Water: %d, Alive: %b, Infested: %b",
                name, type, age, health, currentWater, alive, infested);
    }
}
