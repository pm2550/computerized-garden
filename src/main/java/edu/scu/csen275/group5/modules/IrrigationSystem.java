package edu.scu.csen275.group5.modules;

import edu.scu.csen275.group5.control.GardenLogger;
import edu.scu.csen275.group5.core.Garden;

/**
 * Sprinkler irrigation system module
 * responsible for simulating rainfall and humidity control
 */
public class IrrigationSystem implements ControllableModule {
    private boolean active;
    private int intensity; // 0-100
    private Garden garden;
    private String name = "Irrigation System";

    public IrrigationSystem(Garden garden) {
        this.active = false;
        this.intensity = 50;
        this.garden = garden;
    }

    @Override
    public void activate() {
        this.active = true;
        GardenLogger.log("IRRIGATION", "System activated with intensity: " + intensity);
    }

    @Override
    public void deactivate() {
        this.active = false;
        GardenLogger.log("IRRIGATION", "System deactivated");
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
        GardenLogger.log("IRRIGATION", "Intensity set to: " + intensity);
    }

    @Override
    public int getCurrentIntensity() {
        return intensity;
    }


    /**
     * Simulated rainfall events
     * @param amount precipitation
     */
    public void simulateRainfall(int amount) {
        if (active) {
            int effectiveAmount = (int) (amount * (intensity / 100.0));
            // Call the existing methods of the Garden class
            garden.applyRainfall(effectiveAmount);
            GardenLogger.log("RAIN", "Irrigation system applied rainfall: " + effectiveAmount + " units");
        } else {
            GardenLogger.log("IRRIGATION", "Warning: Rainfall simulation attempted but system is inactive");
        }
    }
}