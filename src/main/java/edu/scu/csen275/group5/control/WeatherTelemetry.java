package edu.scu.csen275.group5.control;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Tracks weather telemetry data including rainfall and cloud cover.
 * Used for weather condition display in UI.
 */
public class WeatherTelemetry {
    private static final int ACTIVE_RAIN_WINDOW_HOURS = 1;
    
    private double cloudCoverFraction = 0.35;
    private int lastTemperature = 70;
    private int lastRainAmount = 0;
    private int lastRainSimHour = -1;
    private final Random random = new Random();

    public void recordRainfall(int amount, int currentHour) {
        lastRainAmount = amount;
        lastRainSimHour = currentHour;
        cloudCoverFraction = Math.min(1.0, cloudCoverFraction + 0.2);
    }

    public void recordTemperature(int temperature) {
        lastTemperature = temperature;
    }

    public void nudgeClouds(boolean isNight, int hoursElapsed) {
        double target = isRainActive(hoursElapsed)
                ? 0.85
                : (isNight ? 0.35 : 0.45 + random.nextDouble() * 0.25);
        cloudCoverFraction += (target - cloudCoverFraction) * 0.2;
        cloudCoverFraction = clamp(cloudCoverFraction, 0.05, 1.0);
    }

    public Map<String, Object> snapshot(boolean isNight, int hourOfDay, int hoursElapsed) {
        boolean raining = isRainActive(hoursElapsed);
        Map<String, Object> weather = new LinkedHashMap<>();
        double clouds = clamp(cloudCoverFraction, 0.0, 1.0);
        
        weather.put("isNight", isNight);
        weather.put("dayPhase", isNight ? "Night" : "Day");
        weather.put("hourOfDay", hourOfDay);
        weather.put("cloudCoverFraction", clouds);
        weather.put("cloudCoverPct", (int) Math.round(clouds * 100));
        weather.put("raining", raining);
        weather.put("activeRainAmount", raining ? lastRainAmount : 0);
        weather.put("lastRainAmount", lastRainAmount);
        weather.put("hoursSinceRain", lastRainSimHour < 0 ? -1
                : Math.max(0, hoursElapsed - lastRainSimHour));
        weather.put("temperature", lastTemperature);
        weather.put("condition", describeCondition(isNight, raining, clouds));
        
        return weather;
    }

    private boolean isRainActive(int hoursElapsed) {
        return lastRainSimHour >= 0 && (hoursElapsed - lastRainSimHour) < ACTIVE_RAIN_WINDOW_HOURS;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String describeCondition(boolean isNight, boolean raining, double clouds) {
        if (raining) {
            if (clouds > 0.75) {
                return isNight ? "Heavy Night Rain" : "Steady Rain";
            }
            return isNight ? "Passing Showers" : "Light Rain";
        }
        if (clouds > 0.85) {
            return isNight ? "Overcast Night" : "Overcast";
        }
        if (clouds > 0.6) {
            return isNight ? "Mostly Cloudy Night" : "Mostly Cloudy";
        }
        if (clouds > 0.4) {
            return isNight ? "Partly Cloudy Night" : "Partly Cloudy";
        }
        return isNight ? "Clear Night" : "Sunny";
    }
}
