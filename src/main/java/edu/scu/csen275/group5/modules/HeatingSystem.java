package edu.scu.csen275.group5.modules;

import edu.scu.csen275.group5.control.GardenLogger;
import edu.scu.csen275.group5.core.Garden;

/**
 * Heating system module
 * responsible for temperature regulation
 */
public class HeatingSystem implements ControllableModule {
    private boolean active;
    private int intensity; // 0-100
    private int targetTemperature;
    private Garden garden;
    private String name = "Heating System";

    public HeatingSystem(Garden garden) {
        this.active = false;
        this.intensity = 50;
        this.targetTemperature = 70; // Default target temperature
        this.garden = garden;
    }

    @Override
    public void activate() {
        this.active = true;
        GardenLogger.log("HEATING", "System activated. Target: " + targetTemperature + "°F, Intensity: " + intensity);
    }

    @Override
    public void deactivate() {
        this.active = false;
        GardenLogger.log("HEATING", "System deactivated");
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public String getModuleName() {
        return name;
    }

    @Override
    public void setIntensity(int level) {
        this.intensity = Math.max(0, Math.min(100, level));
        GardenLogger.log("HEATING", "Intensity set to: " + intensity);
    }

    @Override
    public int getCurrentIntensity() {
        return intensity;
    }

    @Override
    public void update() {
        if (active) {
            int currentTemp = garden.getAirTemperature();
            int tempDifference = targetTemperature - currentTemp;

            if (tempDifference > 0) {
                int heatAmount = calculateHeatAmount(tempDifference);
                // Call the existing methods of the Garden class
                garden.applyTemperature(currentTemp + heatAmount);
                GardenLogger.log("TEMPERATURE", "Heating applied: " + heatAmount + "°F");
            }
        }
    }

    /**
     * Set the target temperature - Verification that fully matches the existing temperature range
     * @param temperature（40-120°F）
     */
    public void setTargetTemperature(int temperature) {
        if (temperature >= 40 && temperature <= 120) {
            this.targetTemperature = temperature;
            GardenLogger.log("HEATING", "Target temperature set to: " + temperature + "°F");
        } else {
            GardenLogger.log("HEATING", "Error: Temperature must be between 40-120°F");
        }
    }

    public int getTargetTemperature() {
        return targetTemperature;
    }

    private int calculateHeatAmount(int tempDifference) {
        return (int) (tempDifference * (intensity / 100.0));
    }
}