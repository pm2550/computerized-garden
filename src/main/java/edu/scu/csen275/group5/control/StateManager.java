package edu.scu.csen275.group5.control;

import edu.scu.csen275.group5.core.Garden;
import java.util.Map;

/**
 * Manages simulation state capture and water requirement tracking.
 */
public class StateManager {
    private final Garden garden;
    private final SimulationTimeManager timeManager;
    private final WeatherSimulator weatherSimulator;
    private final WeatherTelemetry weatherTelemetry;
    
    private int minWaterRequirement;
    private int maxWaterRequirement;
    private int maxRainfallAllowance;
    
    public StateManager(Garden garden, SimulationTimeManager timeManager,
                       WeatherSimulator weatherSimulator, WeatherTelemetry weatherTelemetry) {
        this.garden = garden;
        this.timeManager = timeManager;
        this.weatherSimulator = weatherSimulator;
        this.weatherTelemetry = weatherTelemetry;
        this.minWaterRequirement = 5;
        this.maxWaterRequirement = 20;
        recalculateRainfallAllowance();
    }
    
    /**
     * Capture current garden state with weather and time info
     */
    public Map<String, Object> captureState() {
        Map<String, Object> snapshot = garden.getState();
        int hourOfDay = timeManager.getCurrentHourOfDay();
        int hoursElapsed = timeManager.getHoursElapsed();
        boolean night = weatherSimulator.isNightHour(hourOfDay);
        snapshot.put("hoursElapsed", hoursElapsed);
        snapshot.put("weather", weatherTelemetry.snapshot(night, hourOfDay, hoursElapsed));
        return snapshot;
    }
    
    /**
     * Refresh water requirement range based on current plants
     */
    public void refreshWaterStats() {
        var plants = garden.getPlants();
        if (plants.isEmpty()) {
            minWaterRequirement = 5;
            maxWaterRequirement = 20;
            recalculateRainfallAllowance();
            return;
        }
        
        minWaterRequirement = plants.stream()
                .mapToInt(plant -> plant.getWaterRequirement())
                .min()
                .orElse(5);
        maxWaterRequirement = plants.stream()
                .mapToInt(plant -> plant.getWaterRequirement())
                .max()
                .orElse(20);
        recalculateRainfallAllowance();
    }

    private void recalculateRainfallAllowance() {
        int computed = (int) Math.ceil(maxWaterRequirement * 1.5);
        if (computed < maxWaterRequirement) {
            computed = maxWaterRequirement;
        }
        if (computed < minWaterRequirement) {
            computed = minWaterRequirement;
        }
        this.maxRainfallAllowance = computed;
    }
    
    /**
     * Get minimum water requirement
     */
    public int getMinWaterRequirement() {
        return minWaterRequirement;
    }
    
    /**
     * Get maximum water requirement
     */
    public int getMaxWaterRequirement() {
        return maxWaterRequirement;
    }

    public int getMaxRainfallAllowance() {
        return maxRainfallAllowance;
    }

    public int clampRainfall(int requested) {
        if (requested < minWaterRequirement) {
            return minWaterRequirement;
        }
        if (requested > maxRainfallAllowance) {
            return maxRainfallAllowance;
        }
        return requested;
    }
    
    /**
     * Create a summary string from state snapshot
     */
    public String summarize(Map<String, Object> snapshot) {
        int day = ((Number) snapshot.getOrDefault("day", 0)).intValue();
        int alive = ((Number) snapshot.getOrDefault("alivePlants", 0)).intValue();
        int total = ((Number) snapshot.getOrDefault("totalPlants", 0)).intValue();
        return "Day " + day + ": " + alive + "/" + total + " plants alive.";
    }
}
