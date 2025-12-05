package edu.scu.csen275.group5.control;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Tracks weather telemetry data including rainfall and cloud cover.
 * Used for weather condition display in UI.
 */
public class WeatherTelemetry {
    private static final long ACTIVE_RAIN_WINDOW_MS = 60_000L;
    
    private double cloudCoverFraction = 0.35;
    private int lastTemperature = 70;
    private long lastRainTimestampMs = 0L;
    private int lastRainAmount = 0;
    private final Random random = new Random();

    public void recordRainfall(int amount) {
        lastRainAmount = amount;
        lastRainTimestampMs = System.currentTimeMillis();
        cloudCoverFraction = Math.min(1.0, cloudCoverFraction + 0.2);
    }

    public void recordTemperature(int temperature) {
        lastTemperature = temperature;
    }

    public void nudgeClouds(boolean isNight) {
        double target = isRainActive(System.currentTimeMillis())
                ? 0.85
                : (isNight ? 0.35 : 0.45 + random.nextDouble() * 0.25);
        cloudCoverFraction += (target - cloudCoverFraction) * 0.2;
        cloudCoverFraction = clamp(cloudCoverFraction, 0.05, 1.0);
    }

    public Map<String, Object> snapshot(boolean isNight, int hourOfDay) {
        long now = System.currentTimeMillis();
        boolean raining = isRainActive(now);
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
        weather.put("secondsSinceRain", lastRainTimestampMs == 0 ? -1
                : (int) Math.max(0, (now - lastRainTimestampMs) / 1000));
        weather.put("temperature", lastTemperature);
        weather.put("condition", describeCondition(isNight, raining, clouds));
        
        return weather;
    }

    private boolean isRainActive(long now) {
        return lastRainTimestampMs > 0 && (now - lastRainTimestampMs) <= ACTIVE_RAIN_WINDOW_MS;
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
