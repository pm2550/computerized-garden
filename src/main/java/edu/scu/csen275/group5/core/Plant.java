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
    private double waterAccumulator;  // accumulates fractional water consumption
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
        this.waterAccumulator = 0.0;
        this.infested = false;
        System.out.println("DEBUG: Created " + name + " with waterReq=" + waterRequirement + ", currentWater=" + currentWater);
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
        this.waterAccumulator = 0.0;  // Reset fractional accumulator
        // Cap the water at 1.5x requirement to prevent over-watering damage
        if (this.currentWater > waterRequirement * 1.5) {
            changeHealth(-5);  // Over-watering damage
            this.currentWater = (int)(waterRequirement * 1.5);
        }
    }

    /**
     * Simulate one slice's water consumption (called every 10 simulated minutes)
     * 1 day = 144 slices (24 hours * 6 slices/hour)
     */
    public void consumeWaterPerSlice() {
        if (!alive) {
            return;
        }
        // Accumulate fractional water consumption to avoid rounding errors
        double sliceConsumption = this.waterRequirement / 144.0;
        waterAccumulator += sliceConsumption;
        
        // Only deduct full units of water
        if (waterAccumulator >= 1.0) {
            int toConsume = (int) waterAccumulator;
            this.currentWater -= toConsume;
            waterAccumulator -= toConsume;
            
            if (this.currentWater < 0) {
                this.currentWater = 0;
            }
        }
        
        // Check water ratio and apply stress
        double waterRatio = this.waterRequirement > 0 
            ? (double) this.currentWater / this.waterRequirement 
            : 1.0;
            
        if (waterRatio < 0.1) {
            // Severely dehydrated - rapid health loss
            changeHealth(-0.21);  // -30/day = -0.21/slice
        } else if (waterRatio < 0.3) {
            // Low water - moderate health loss
            changeHealth(-0.14);  // -20/day = -0.14/slice
        } else if (waterRatio < 0.5) {
            // Slightly low - mild stress
            changeHealth(-0.07);  // -10/day = -0.07/slice
        }
        // Above 50% water ratio = no stress
    }

    /**
     * Apply temperature stress to the plant (called per slice, so effects are scaled down)
     * @param temperature current temperature in Fahrenheit
     */
    public void applyTemperatureStress(int temperature) {
        if (!alive) {
            return;
        }
        // Temperature effects are scaled for per-slice checks (1/144th of daily impact)
        // Check if temperature is within tolerance range
        if (temperature < minTempTolerance || temperature > maxTempTolerance) {
            changeHealth(-0.104);  // -15/day = -0.104/slice
            return;
        }

        // Check if within optimal range
        if (temperature >= optimalTempMin && temperature <= optimalTempMax) {
            // Optimal temperature AND good water = faster health recovery
            double waterRatio = (double) currentWater / waterRequirement;
            if (waterRatio >= 1.0) {
                changeHealth(0.07);  // +10/day when both temp and water are optimal
            } else if (waterRatio >= 0.75) {
                changeHealth(0.035);  // +5/day when temp optimal, water OK
            } else {
                changeHealth(0.014);  // +2/day = +0.014/slice (original)
            }
        } else {
            // Outside optimal but within tolerance
            double waterRatio = (double) currentWater / waterRequirement;
            if (waterRatio >= 1.05) {
                // Good water can compensate for suboptimal temperature
                changeHealth(0.035);  // +5/day when water is excellent
            } else if (waterRatio >= 0.9) {
                // Decent water = slow recovery even with suboptimal temp
                changeHealth(0.007);  // +1/day = +0.007/slice
            } else {
                // Low water + suboptimal temp = decline
                changeHealth(-0.035);  // -5/day = -0.035/slice
            }
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
            changeHealth(-3);  // Parasite causes moderate damage per infection (reduced from -25)
        }
    }

    /**
     * Treat parasite infestation
     */
    public void treatParasite() {
        if (this.infested) {
            this.infested = false;
            changeHealth(-1);  // Treatment causes minimal damage (reduced from -10)
        }
    }

    /**
     * Advance plant age by one day (called every 24 simulated hours = 144 slices)
     */
    public void advanceDay() {
        if (!alive) {
            return;
        }
        this.age++;
    }
    
    /**
     * Process one simulation slice (10 minutes) for this plant
     */
    public void advanceSlice() {
        if (!alive) {
            return;
        }
        consumeWaterPerSlice();
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
        if (delta > 0 && this.health >= 100) {
            return;
        }
        double next = this.health + delta;
        if (next > 100) {
            next = 100;
        } else if (next < 0) {
            next = 0;
        }
        this.health = next;
        this.alive = this.health > 0;
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - Age: %d, Health: %.1f%%, Water: %d, Alive: %b, Infested: %b",
                name, type, age, health, currentWater, alive, infested);
    }
}
