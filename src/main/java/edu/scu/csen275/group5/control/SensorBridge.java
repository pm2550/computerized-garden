package edu.scu.csen275.group5.control;

import edu.scu.csen275.group5.modules.ModuleManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Bridge between garden sensors and automated control modules.
 * Monitors soil moisture, temperature, and pests, then triggers appropriate responses
 * via irrigation, heating, and pest control systems.
 */
public class SensorBridge {
    private static final double MOISTURE_LOW_THRESHOLD = 25.0;
    private static final double MOISTURE_RECOVERY_THRESHOLD = 45.0;
    private static final double PLANT_CRITICAL_RATIO = 0.25;
    private static final double PLANT_RECOVERY_RATIO = 0.75;
    private static final double MIN_CRITICAL_FRACTION = 0.35;
    private static final int TEMP_LOW_THRESHOLD_F = 52;
    private static final int TEMP_RECOVERY_THRESHOLD_F = 60;
    private static final int TEMP_TARGET_F = 65;
    private static final long PEST_SWEEP_COOLDOWN_MS = 30_000L;
    private static final String IRRIGATION = "irrigation";
    private static final String HEATING = "heating";
    private static final String PEST_CONTROL = "pest_control";

    private final ModuleManager modules;
    private final GardenLogger logger;
    private boolean irrigationAutoActive;
    private boolean heatingAutoActive;
    private boolean pestControlAutoActive;
    private long lastPestSweepMs;

    public SensorBridge(ModuleManager modules, GardenLogger logger) {
        this.modules = modules;
        this.logger = logger;
    }

    /**
     * Evaluates current garden state and triggers automated responses if needed.
     * @param snapshot Current garden state
     * @param minRain Minimum rainfall amount for irrigation
     * @param maxRain Maximum rainfall amount for irrigation
     * @return true if any module was activated
     */
    public boolean evaluateAndAct(Map<String, Object> snapshot, int minRain, int maxRain) {
        boolean acted = false;
        acted |= manageIrrigation(snapshot, minRain, maxRain);
        acted |= manageHeating(snapshot);
        acted |= managePests(snapshot);
        return acted;
    }

    private boolean manageIrrigation(Map<String, Object> snapshot, int minRain, int maxRain) {
        Map<String, Object> soil = soil(snapshot);
        double moisture = soil != null ? asDouble(soil.get("moisture"), Double.NaN) : Double.NaN;
        WaterDemand demand = assessPlantWater(snapshot);

        logger.log("DEBUG", String.format("Sensor check: moisture=%.1f%%, hasData=%b, avgRatio=%.2f, criticalFrac=%.2f, minRatio=%.2f",
                Double.isNaN(moisture) ? -1 : moisture, demand.hasData, 
                demand.hasData ? demand.avgRatio : -1,
                demand.hasData ? demand.criticalFraction : -1,
                demand.hasData ? demand.minRatio : -1));

        boolean soilDry = !Double.isNaN(moisture) && moisture < MOISTURE_LOW_THRESHOLD;
        boolean plantsDry = demand.hasData
                && (demand.criticalFraction >= MIN_CRITICAL_FRACTION || demand.minRatio < PLANT_CRITICAL_RATIO);

        if (soilDry || plantsDry) {
            modules.activateModule(IRRIGATION);
            irrigationAutoActive = true;
            int intensity = clamp((int) Math.round((soilDry ? (MOISTURE_LOW_THRESHOLD - moisture) : 10) * 4) + 35, 35, 100);
            modules.setModuleIntensity(IRRIGATION, intensity);
            double severity = demand.hasData
                    ? Math.min(1.0, Math.max(0.4, 1.0 - demand.avgRatio + 0.25))
                    : 0.8;
            int rainPulse = clamp((int) Math.round(maxRain * severity), minRain, maxRain);
            modules.handleRainfall(rainPulse);
            logger.log("SENSOR", String.format("Irrigation pulse (%du @%d%%) — soil %.1f%%, avg water %.0f%%, %.0f%% plants critical",
                    rainPulse, intensity, Double.isNaN(moisture) ? -1 : moisture,
                    demand.hasData ? demand.avgRatio * 100 : -1,
                    demand.hasData ? demand.criticalFraction * 100 : 0));
            return true;
        }

        boolean soilRecovered = Double.isNaN(moisture) || moisture >= MOISTURE_RECOVERY_THRESHOLD;
        boolean plantsRecovered = !demand.hasData || demand.avgRatio >= PLANT_RECOVERY_RATIO;
        if (irrigationAutoActive && soilRecovered && plantsRecovered) {
            modules.deactivateModule(IRRIGATION);
            irrigationAutoActive = false;
            logger.log("SENSOR", "Irrigation idle — soil and plants hydrated");
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private WaterDemand assessPlantWater(Map<String, Object> snapshot) {
        List<Integer> water = (List<Integer>) snapshot.getOrDefault("plantWater", Collections.emptyList());
        List<Integer> required = (List<Integer>) snapshot.getOrDefault("plantWaterRequirement", Collections.emptyList());
        int samples = Math.min(water.size(), required.size());
        if (samples == 0) {
            return WaterDemand.none();
        }
        double totalRatio = 0.0;
        double minRatio = Double.POSITIVE_INFINITY;
        int considered = 0;
        int critical = 0;
        for (int i = 0; i < samples; i++) {
            int requirement = Math.max(0, required.get(i));
            if (requirement <= 0) {
                continue;
            }
            int have = Math.max(0, water.get(i));
            double ratio = Math.max(0.0, Math.min(1.5, have / (double) requirement));
            totalRatio += ratio;
            minRatio = Math.min(minRatio, ratio);
            if (ratio < PLANT_CRITICAL_RATIO) {
                critical++;
            }
            considered++;
        }
        if (considered == 0) {
            return WaterDemand.none();
        }
        double avg = totalRatio / considered;
        double criticalFraction = critical / (double) considered;
        return new WaterDemand(true, avg, minRatio, criticalFraction);
    }

    private boolean manageHeating(Map<String, Object> snapshot) {
        double temperature = asDouble(snapshot.get("temperature"), Double.NaN);
        if (Double.isNaN(temperature)) {
            return false;
        }
        if (temperature < TEMP_LOW_THRESHOLD_F) {
            modules.activateModule(HEATING);
            heatingAutoActive = true;
            int intensity = clamp((int) Math.round((TEMP_LOW_THRESHOLD_F - temperature) * 6) + 40, 40, 100);
            modules.setModuleIntensity(HEATING, intensity);
            int target = Math.max(TEMP_TARGET_F, (int) Math.round(temperature + 5));
            modules.handleTemperatureChange(target);
            logger.log("SENSOR", String.format("Air temp %.0f°F low → heating target %d°F @%d%%",
                    temperature, target, intensity));
            return true;
        }
        if (heatingAutoActive && temperature >= TEMP_RECOVERY_THRESHOLD_F) {
            modules.deactivateModule(HEATING);
            heatingAutoActive = false;
            logger.log("SENSOR", String.format("Air temp stabilized at %.0f°F → heating idle", temperature));
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean managePests(Map<String, Object> snapshot) {
        Map<String, Object> soil = soil(snapshot);
        List<String> pests = soil != null
                ? (List<String>) soil.getOrDefault("pests", Collections.emptyList())
                : Collections.emptyList();
        long now = System.currentTimeMillis();
        if (pests != null && !pests.isEmpty()) {
            modules.activateModule(PEST_CONTROL);
            pestControlAutoActive = true;
            if (now - lastPestSweepMs >= PEST_SWEEP_COOLDOWN_MS) {
                for (String pest : pests) {
                    if (pest != null && !pest.isBlank()) {
                        modules.handleParasite(pest);
                    }
                }
                lastPestSweepMs = now;
                logger.log("SENSOR", "Detected pests → automated treatment for " + String.join(", ", pests));
                return true;
            }
        } else if (pestControlAutoActive && now - lastPestSweepMs >= PEST_SWEEP_COOLDOWN_MS) {
            modules.deactivateModule(PEST_CONTROL);
            pestControlAutoActive = false;
            logger.log("SENSOR", "Pest sensors clear → pest control idle");
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> soil(Map<String, Object> snapshot) {
        Object soil = snapshot.get("soil");
        if (soil instanceof Map<?, ?>) {
            return (Map<String, Object>) soil;
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return fallback;
    }

    private static final class WaterDemand {
        final boolean hasData;
        final double avgRatio;
        final double minRatio;
        final double criticalFraction;

        private WaterDemand(boolean hasData, double avgRatio, double minRatio, double criticalFraction) {
            this.hasData = hasData;
            this.avgRatio = avgRatio;
            this.minRatio = minRatio;
            this.criticalFraction = criticalFraction;
        }

        static WaterDemand none() {
            return new WaterDemand(false, 1.0, 1.0, 0.0);
        }
    }
}
