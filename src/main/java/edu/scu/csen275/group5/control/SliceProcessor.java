package edu.scu.csen275.group5.control;

import edu.scu.csen275.group5.core.Garden;

/**
 * Processes a single simulation slice - coordinates garden evolution,
 * event generation, and application.
 */
public class SliceProcessor {
    private final Garden garden;
    private final SimulationTimeManager timeManager;
    private final WeatherSimulator weatherSimulator;
    private final AutoEventGenerator eventGenerator;
    private final WeatherTelemetry weatherTelemetry;
    private final GardenLogger logger;
    
    private int minWaterRequirement;
    private int maxRainfallAllowance;
    
    public SliceProcessor(Garden garden, SimulationTimeManager timeManager,
                         WeatherSimulator weatherSimulator, AutoEventGenerator eventGenerator,
                         WeatherTelemetry weatherTelemetry, GardenLogger logger) {
        this.garden = garden;
        this.timeManager = timeManager;
        this.weatherSimulator = weatherSimulator;
        this.eventGenerator = eventGenerator;
        this.weatherTelemetry = weatherTelemetry;
        this.logger = logger;
    this.minWaterRequirement = 5;
    this.maxRainfallAllowance = 20;
    }
    
    /**
     * Update water requirement range for rainfall clamping
     */
    public void updateWaterRequirements(int min, int maxRainfallAllowance) {
        this.minWaterRequirement = min;
        this.maxRainfallAllowance = maxRainfallAllowance;
    }
    
    /**
     * Process one simulation slice with auto events enabled
     */
    public String processSliceWithAutoEvents() {
        // 1. Garden evolution
        garden.advanceSlice();
        
        // 2. Generate and apply automatic events
        AutoEventGenerator.AutoEvents events = eventGenerator.generateEvents(
            timeManager.getCurrentHourOfDay(),
            timeManager.getHoursElapsed()
        );
        
        StringBuilder summary = new StringBuilder();
        applyEvents(events, summary);
        
        // 3. Update weather telemetry
    boolean isNight = weatherSimulator.isNightHour(timeManager.getCurrentHourOfDay());
    weatherTelemetry.nudgeClouds(isNight, timeManager.getHoursElapsed());
        
        // 4. Advance time
        timeManager.processSlices(1);
        
        return summary.length() > 0 ? summary.toString().trim() : null;
    }
    
    /**
     * Process one simulation slice without auto events
     */
    public void processSliceWithoutAutoEvents() {
        garden.advanceSlice();
        
    boolean isNight = weatherSimulator.isNightHour(timeManager.getCurrentHourOfDay());
    weatherTelemetry.nudgeClouds(isNight, timeManager.getHoursElapsed());
        
        timeManager.processSlices(1);
    }
    
    /**
     * Apply generated events to the garden
     */
    private void applyEvents(AutoEventGenerator.AutoEvents events, StringBuilder summary) {
        // Rain event
        if (events.hasRain()) {
            int rainfall = clampRainfall(events.rainfall);
            garden.applyRainfall(rainfall);
            weatherTelemetry.recordRainfall(rainfall, timeManager.getHoursElapsed());
            summary.append(String.format("rain=%du ", rainfall));
            logger.log("RAIN", "Auto rainfall: " + rainfall + " units");
        }
        
        // Temperature event
        if (events.temperature != null && events.temperature > 0) {
            int temp = Math.max(40, Math.min(120, events.temperature));
            garden.applyTemperature(temp);
            weatherTelemetry.recordTemperature(temp);
            summary.append(String.format("temp=%d°F ", temp));
        }
        
        // Parasite event
        if (events.hasParasite()) {
            if (canIntroduceParasite(events.parasite)) {
                garden.triggerParasiteInfestation(events.parasite);
                summary.append(String.format("pest=%s ", events.parasite));
            }
        }
    }
    
    /**
     * Check if parasite can be introduced (pest load limit)
     */
    private boolean canIntroduceParasite(String parasiteName) {
        var existing = garden.getSoil().getCurrentPests();
        if (existing.contains(parasiteName)) {
            return true; // Already present, allow
        }
        return existing.size() < 2; // Max 2 concurrent parasites
    }
    
    /**
     * Clamp rainfall to valid range based on plant water needs
     */
    private int clampRainfall(int requested) {
        if (requested < minWaterRequirement) {
            return minWaterRequirement;
        }
        if (requested > maxRainfallAllowance) {
            return maxRainfallAllowance;
        }
        return requested;
    }
}
