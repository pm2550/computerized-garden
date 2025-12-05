package edu.scu.csen275.group5.control;

import java.util.Random;

/**
 * Simulates weather patterns including temperature cycles and rainfall.
 * Provides realistic day/night temperature variations.
 */
public class WeatherSimulator {
    private static final int BASE_TEMP_MIN = 50;
    private static final int BASE_TEMP_MAX = 85;
    private static final int TEMP_AMPLITUDE = (BASE_TEMP_MAX - BASE_TEMP_MIN) / 2;
    private static final int TEMP_MIDPOINT = (BASE_TEMP_MAX + BASE_TEMP_MIN) / 2;
    
    private final Random random;
    private int temperatureJitter;
    
    public WeatherSimulator() {
        this.random = new Random();
        this.temperatureJitter = 10;
    }
    
    /**
     * Set temperature variation range
     * @param jitter maximum random variation in degrees
     */
    public void setTemperatureJitter(int jitter) {
        this.temperatureJitter = jitter;
    }
    
    /**
     * Compute realistic diurnal (day/night) temperature based on hour
     * @param hourOfDay 0-23
     * @return temperature in Fahrenheit
     */
    public int computeDiurnalTemperature(int hourOfDay) {
        // Sinusoidal temperature variation
        // Coldest at 6 AM (hour 6), warmest at 6 PM (hour 18)
        double radians = Math.toRadians((hourOfDay - 6) * 15); // 15 degrees per hour
        double normalized = Math.sin(radians);
        int baseTemp = (int) (TEMP_MIDPOINT + TEMP_AMPLITUDE * normalized);
        
        return baseTemp;
    }
    
    /**
     * Compute temperature with random variation
     * @param hourOfDay 0-23
     * @return temperature with jitter applied
     */
    public int computeTemperatureWithVariation(int hourOfDay) {
        int baseTemp = computeDiurnalTemperature(hourOfDay);
        int variation = random.nextInt(temperatureJitter * 2 + 1) - temperatureJitter;
        return baseTemp + variation;
    }
    
    /**
     * Check if it's nighttime (less likely for certain events)
     * @param hourOfDay 0-23
     * @return true if night (8 PM to 6 AM)
     */
    public boolean isNightHour(int hourOfDay) {
        return hourOfDay >= 20 || hourOfDay < 6;
    }
    
    /**
     * Generate random rainfall amount
     * @param min minimum units
     * @param max maximum units
     * @return rainfall amount
     */
    public int generateRainfall(int min, int max) {
        if (max <= min) {
            return min;
        }
        return random.nextInt(max - min + 1) + min;
    }
    
    /**
     * Random utility method
     */
    public int randomBetween(int min, int max) {
        if (max <= min) {
            return min;
        }
        return random.nextInt(max - min + 1) + min;
    }
    
    /**
     * Random chance check
     * @param probability 0.0 to 1.0
     * @return true if event should occur
     */
    public boolean shouldOccur(double probability) {
        return random.nextDouble() < probability;
    }
}
