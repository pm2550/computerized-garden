package edu.scu.csen275.group5.control;

import edu.scu.csen275.group5.core.Garden;
import edu.scu.csen275.group5.core.GardenConfig;
import edu.scu.csen275.group5.core.Plant;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main API for the garden simulation. Scripts call these methods to simulate
 * weather and pest events. The UI also hooks into this to show what's happening.
 * 
 * Required by project spec - implements all methods from the Gardening System API doc.
 */
public class GardenSimulationAPI {
    private static final int AUTO_SLICES_PER_HOUR = 6; // 10 simulated minutes per slice

    public interface SimulationObserver {
        // UI or external system can watch garden changes through this
        void onStateChanged(Map<String, Object> stateSnapshot);
        void onLogAppended(String logEntry);
    }

    private final Garden garden;
    private final Path configPath;
    private final GardenLogger logger;
    private final List<SimulationObserver> observers;  // who's watching the simulation
    private final AtomicInteger hoursElapsed;  // tracks simulation time (1 hour = 1 day)
    private final Random random;

    private boolean initialized;
    private int minWaterRequirement;  // computed from plants after init
    private int maxWaterRequirement;
    private boolean autoEventsEnabled;
    private AutoEventConfig autoEventConfig;
    private final WeatherTelemetry weatherTelemetry;
    private final AtomicInteger slicesProcessedThisHour;

    public GardenSimulationAPI() {
        this(Paths.get("garden_config.txt"), Paths.get("log.txt"));
    }

    public GardenSimulationAPI(Path configPath, Path logPath) {
        this.garden = new Garden();
        this.configPath = Objects.requireNonNull(configPath, "configPath");
        this.logger = new GardenLogger(Objects.requireNonNull(logPath, "logPath"));
        this.observers = new CopyOnWriteArrayList<>();  // thread-safe for observers
        this.hoursElapsed = new AtomicInteger();
        this.random = new Random();
        this.minWaterRequirement = 5;   // defaults until we know actual plant needs
        this.maxWaterRequirement = 20;
        this.autoEventConfig = AutoEventConfig.defaultConfig();
        this.autoEventsEnabled = true;
    this.weatherTelemetry = new WeatherTelemetry();
    this.slicesProcessedThisHour = new AtomicInteger();

        // wire up logger so we can push log lines to observers (for UI display)
        this.logger.addListener(this::fanOutLogEntry);
    }

    /**
     * Initializes garden from config file. Call this first before running anything.
     * Starts the simulation clock at hour 0.
     */
    public synchronized void initializeGarden() {
        garden.reset();
        GardenConfig config = new GardenConfig(configPath.toString());
        boolean loaded = config.loadConfig();
        if (loaded) {
            config.applyToGarden(garden);
        } else {
            // missing config? fall back to hard-coded default plants
            logger.log("CONFIG", "Config file missing or invalid. Falling back to default templates.");
        }

        Map<String, Map<String, Object>> definitions = config.getConfigs();
        if (definitions.isEmpty()) {
            seedDefaultGarden();
        } else {
            seedFromConfig(definitions);
        }

        initialized = true;
        hoursElapsed.set(0);
        refreshWaterStats();  // figure out min/max water needs from actual plants
        logger.log("INIT", "Garden initialized with " + garden.getPlants().size() + " plants");
        dispatchState(captureState());
    }

    /**
     * Returns plant data in the format specified by the API doc.
     * Returns:
     *   {
     *     plants = [Rose, Tomato, ...],
     *     waterRequirement = [10, 15, ...],
     *     parasites = [[pest1, pest2], [pest1], ...]
     *   }
     */
    public synchronized Map<String, Object> getPlants() {
        ensureInitialized();
        Map<String, Object> payload = new LinkedHashMap<>();
        List<String> plantNames = new ArrayList<>();
        List<Integer> waterNeeds = new ArrayList<>();
        List<List<String>> parasiteMatrix = new ArrayList<>();

        for (Plant plant : garden.getPlants()) {
            plantNames.add(plant.getName());
            waterNeeds.add(plant.getWaterRequirement());
            parasiteMatrix.add(new ArrayList<>(plant.getVulnerableParasites()));
        }

        payload.put("plants", plantNames);
        payload.put("waterRequirement", waterNeeds);
        payload.put("parasites", parasiteMatrix);
        return Collections.unmodifiableMap(payload);
    }

    /**
     * Simulates rainfall. Amount is clamped to match plant water requirements
     * (spec says range depends on getPlants().waterRequirement values).
     */
    public synchronized void rain(int rainfallAmount) {
        ensureInitialized();
        int validated = clampRainfall(rainfallAmount);
        if (validated != rainfallAmount) {
            logger.log("RAIN", "Requested " + rainfallAmount + " units. Clamped to " + validated + ".");
        }
        garden.applyRainfall(validated);
        weatherTelemetry.recordRain(validated);
        logger.log("RAIN", "Manual rainfall applied: " + validated + " units");
        refreshAndBroadcastState();
    }

    /**
     * Sets daily temperature. Spec says range is 40-120°F.
     */
    public synchronized void temperature(int temperatureFahrenheit) {
        ensureInitialized();
        garden.applyTemperature(temperatureFahrenheit);
        weatherTelemetry.recordTemperature(temperatureFahrenheit);
        refreshAndBroadcastState();
    }

    /**
     * Releases a parasite into the garden. Only affects plants vulnerable to it
     * (as defined in getPlants().parasites).
     */
    public synchronized void parasite(String parasiteName) {
        ensureInitialized();
        String cleaned = sanitizeParasiteName(parasiteName);
        if (cleaned.isEmpty()) {
            logger.log("PARASITE", "Ignored parasite event because name was empty");
            return;
        }
        applyParasiteSilently(cleaned, false);
        refreshAndBroadcastState();
    }

    /**
     * Advances the simulation by one hour automatically (timer driven).
     */
    public synchronized void advanceHourAutomatically() {
        advanceHourWithReason("Timer auto advance");
    }

    /**
     * Advances the simulation by one hour due to user input.
     */
    public synchronized void advanceHourManually() {
        advanceHourWithReason("Next hour button");
    }

    private void advanceHourWithReason(String reason) {
        ensureInitialized();
        closeHour(reason);
    }

    public synchronized void setAutoEventsEnabled(boolean enabled) {
        this.autoEventsEnabled = enabled;
        logger.log("AUTO", enabled ? "Automated weather/events enabled" : "Automated weather/events disabled");
    }

    public synchronized boolean isAutoEventsEnabled() {
        return autoEventsEnabled;
    }

    public synchronized void updateAutoEventConfig(AutoEventConfig config) {
        if (config != null) {
            this.autoEventConfig = config;
        }
    }

    private void refreshAndBroadcastState() {
        refreshWaterStats();
        Map<String, Object> snapshot = captureState();
        logPlantAlerts(snapshot);
        dispatchState(snapshot);
    }

    /**
     * Logs current garden state to log.txt (per API spec).
     * Shows alive/dead counts and notifies observers.
     */
    public synchronized void getState() {
        ensureInitialized();
        Map<String, Object> snapshot = captureState();
        
        // Log summary first
        logger.log("STATE", summarize(snapshot));
        
        // Log detailed plant status
        logDetailedPlantStatus(snapshot);
        
        dispatchState(snapshot);
    }

    // extra method for UI - returns state map instead of just logging
    public synchronized Map<String, Object> currentState() {
        ensureInitialized();
        return captureState();
    }

    public void addObserver(SimulationObserver observer) {
        observers.add(observer);
    }

    // UI uses this to show recent log tail
    public List<String> recentLogEntries() {
        return logger.recentEntries();
    }

    // tells scripts what rainfall range is valid (based on actual plants)
    public int getMinWaterRequirement() {
        return minWaterRequirement;
    }

    public int getMaxWaterRequirement() {
        return maxWaterRequirement;
    }

    public int getHoursElapsed() {
        return hoursElapsed.get();
    }

    // populate garden from config file plant definitions
    private void seedFromConfig(Map<String, Map<String, Object>> definitions) {
        definitions.forEach((type, config) -> {
            int instances = ((Integer) config.getOrDefault("instances", 2));
            for (int i = 1; i <= instances; i++) {
                String name = type + "-" + String.format("%03d", i);
                Plant planted = garden.plantNew(name, type);
                if (planted != null) {
                    logger.log("PLANT", "Seeded " + planted.getName() + " (" + type + ")");
                }
            }
        });
    }

    // fallback if config file is missing - creates a default garden
    private void seedDefaultGarden() {
        List<String> defaults = new ArrayList<>(garden.getAvailablePlantTypes());
        Collections.sort(defaults);
        for (String type : defaults) {
            for (int i = 1; i <= 2; i++) {
                String name = type + "-" + String.format("%03d", i);
                Plant planted = garden.plantNew(name, type);
                if (planted != null) {
                    logger.log("PLANT", "Seeded " + planted.getName() + " (" + type + ")");
                }
            }
        }
    }

    // pushes state update to observers
    private void closeHour(String reason) {
        runRemainingSlicesForHour();
    garden.advanceDay();
    slicesProcessedThisHour.set(0);
        int hour = hoursElapsed.incrementAndGet();
        logger.log("HOUR", reason + ". Hour " + hour + " closed.");
        refreshAndBroadcastState();
    }

    private void dispatchState(Map<String, Object> snapshot) {
        for (SimulationObserver observer : observers) {
            observer.onStateChanged(snapshot);
        }
    }

    // broadcast log line to all observers (UI will display it)
    private void fanOutLogEntry(String entry) {
        for (SimulationObserver observer : observers) {
            observer.onLogAppended(entry);
        }
    }

    private Map<String, Object> captureState() {
        Map<String, Object> snapshot = garden.getState();
        int hourOfDay = getCurrentHourOfDay();
        boolean night = isNightHour(hourOfDay);
        snapshot.put("hoursElapsed", hoursElapsed.get());
        snapshot.put("weather", weatherTelemetry.snapshot(night, hourOfDay));
        return snapshot;
    }

    // update rainfall range based on actual plants in garden
    private void refreshWaterStats() {
        List<Plant> plants = garden.getPlants();
        if (plants.isEmpty()) {
            minWaterRequirement = 5;
            maxWaterRequirement = 20;
            return;
        }

        minWaterRequirement = plants.stream()
                .mapToInt(Plant::getWaterRequirement)
                .min()
                .orElse(5);
        maxWaterRequirement = plants.stream()
                .mapToInt(Plant::getWaterRequirement)
                .max()
                .orElse(20);
    }

    public synchronized void processAutoSlices(int slices) {
        ensureInitialized();
        if (slices <= 0) {
            return;
        }
        for (int i = 0; i < slices; i++) {
            if (slicesProcessedThisHour.get() >= AUTO_SLICES_PER_HOUR) {
                break;
            }
            runAutoEventsSlice();
            slicesProcessedThisHour.incrementAndGet();
        }
    }

    private void runRemainingSlicesForHour() {
        if (!initialized) {
            return;
        }
    int remaining = AUTO_SLICES_PER_HOUR - slicesProcessedThisHour.get();
        if (remaining > 0) {
            processAutoSlices(remaining);
        }
    }

    private void runAutoEventsSlice() {
        if (autoEventConfig == null) {
            autoEventConfig = AutoEventConfig.defaultConfig();
        }
        int hourOfDay = getCurrentHourOfDay();
        boolean isNight = isNightHour(hourOfDay);

        StringBuilder summary = new StringBuilder();

        if (autoEventsEnabled && autoEventConfig.getRainChance() > 0.0 && random.nextDouble() < autoEventConfig.getRainChance()) {
            int rainAmount = generateRandomRainAmount();
            int applied = applyRainSilently(rainAmount);
            if (applied > 0) {
                summary.append(String.format("rain=%du ", applied));
            }
        }

        if (autoEventsEnabled && autoEventConfig.getTemperatureChance() > 0.0 && random.nextDouble() < autoEventConfig.getTemperatureChance()) {
            int temp = computeDiurnalTemperatureWithVariation();
            applyTemperatureSilently(temp);
            summary.append(String.format("temp=%d°F(time) ", temp));
        }

        if (autoEventsEnabled && autoEventConfig.getParasiteChance() > 0.0 && random.nextDouble() < autoEventConfig.getParasiteChance()) {
            String parasite = pickRandomParasite();
            if (!parasite.isEmpty()) {
                if (applyParasiteSilently(parasite, true)) {
                    summary.append(String.format("parasite=%s ", parasite));
                }
            }
        }

        weatherTelemetry.nudgeClouds(random, isNight);
        if (summary.length() > 0) {
            refreshAndBroadcastState();
            logger.log("AUTO", "Automated slice -> " + summary.toString().trim());
        } else {
            dispatchState(captureState());
        }
    }

    private int generateRandomRainAmount() {
        int min = Math.max(1, minWaterRequirement);
        int max = Math.max(min, maxWaterRequirement);
        return randomBetween(min, max);
    }

    private int computeDiurnalTemperatureWithVariation() {
        int base = computeDiurnalTemperature();
        int jitter = autoEventConfig != null ? autoEventConfig.getTemperatureJitter() : 2;
        if (jitter > 0) {
            base += randomBetween(-jitter, jitter);
        }
        return Math.max(40, Math.min(120, base));
    }

    private int computeDiurnalTemperature() {
        int hourOfDay = getCurrentHourOfDay();
        int min = Math.max(40, autoEventConfig.getMinTemperature());
        int max = Math.min(120, autoEventConfig.getMaxTemperature());
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }

        double normalizedHour = hourOfDay / 24.0;
        double sineWave = Math.sin(2 * Math.PI * normalizedHour - Math.PI / 2); // peak at midday
        double baseNormalized = (sineWave + 1) / 2.0; // map -1..1 to 0..1
        return (int) Math.round(min + (max - min) * baseNormalized);
    }

    private int getCurrentHourOfDay() {
    int totalSlices = hoursElapsed.get() * AUTO_SLICES_PER_HOUR + slicesProcessedThisHour.get();
        int hour = (totalSlices / AUTO_SLICES_PER_HOUR) % 24;
        if (hour < 0) {
            hour += 24;
        }
        return hour;
    }

    private boolean isNightHour(int hourOfDay) {
        return hourOfDay < 6 || hourOfDay >= 20;
    }

    private int randomBetween(int min, int max) {
        if (min >= max) {
            return min;
        }
        return min + random.nextInt((max - min) + 1);
    }

    private int applyRainSilently(int requested) {
        int validated = clampRainfall(requested);
        garden.applyRainfall(validated);
        weatherTelemetry.recordRain(validated);
        logger.log("RAIN", "Automated rainfall applied: " + validated + " units");
        return validated;
    }

    private void applyTemperatureSilently(int requested) {
        int clamped = Math.max(40, Math.min(120, requested));
        garden.applyTemperature(clamped);
        weatherTelemetry.recordTemperature(clamped);
    }

    private boolean applyParasiteSilently(String parasiteName, boolean enforceLimit) {
        String cleaned = sanitizeParasiteName(parasiteName);
        if (cleaned.isEmpty()) {
            return false;
        }
        if (enforceLimit && !canIntroduceParasite(cleaned)) {
            logger.log("AUTO", "Skipped parasite event for " + cleaned + " (pest load capped)");
            return false;
        }
        logAffectedPlants(cleaned);
        garden.triggerParasiteInfestation(cleaned);
        return true;
    }

    private boolean canIntroduceParasite(String parasiteName) {
        List<String> existing = garden.getSoil().getCurrentPests();
        if (existing.contains(parasiteName)) {
            return true;
        }
        int maxConcurrent = autoEventConfig != null ? autoEventConfig.getMaxConcurrentParasites() : 2;
        return existing.size() < Math.max(1, maxConcurrent);
    }

    private String pickRandomParasite() {
        Set<String> parasites = new HashSet<>();
        for (Plant plant : garden.getPlants()) {
            parasites.addAll(plant.getVulnerableParasites());
        }
        if (parasites.isEmpty()) {
            return "";
        }
        int index = random.nextInt(parasites.size());
        int i = 0;
        for (String parasite : parasites) {
            if (i == index) {
                return parasite;
            }
            i++;
        }
        return "";
    }

    public static class AutoEventConfig {
        private final double rainChance;
        private final double temperatureChance;
        private final double parasiteChance;
        private final int minTemperature;
        private final int maxTemperature;
        private final int temperatureJitter;
        private final int maxConcurrentParasites;

        private AutoEventConfig(double rainChance, double temperatureChance, double parasiteChance,
                                int minTemperature, int maxTemperature,
                                int temperatureJitter, int maxConcurrentParasites) {
            this.rainChance = rainChance;
            this.temperatureChance = temperatureChance;
            this.parasiteChance = parasiteChance;
            this.minTemperature = minTemperature;
            this.maxTemperature = maxTemperature;
            this.temperatureJitter = temperatureJitter;
            this.maxConcurrentParasites = maxConcurrentParasites;
        }

        public static AutoEventConfig defaultConfig() {
            return new AutoEventConfig(0.35, 0.9, 0.04, 55, 95, 3, 2);
        }

        public double getRainChance() {
            return rainChance;
        }

        public double getTemperatureChance() {
            return temperatureChance;
        }

        public double getParasiteChance() {
            return parasiteChance;
        }

        public int getMinTemperature() {
            return minTemperature;
        }

        public int getMaxTemperature() {
            return maxTemperature;
        }

        public int getTemperatureJitter() {
            return temperatureJitter;
        }

        public int getMaxConcurrentParasites() {
            return maxConcurrentParasites;
        }
    }

    // clamp rain amount to valid range (per spec: based on plant water needs)
    private int clampRainfall(int requested) {
        if (requested < minWaterRequirement) {
            return minWaterRequirement;
        }
        if (requested > maxWaterRequirement) {
            return maxWaterRequirement;
        }
        return requested;
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Call initializeGarden() before running the simulation");
        }
    }

    // normalize parasite names so "Aphids", "aphids", "APHIDS" all match
    private String sanitizeParasiteName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase().replace(" ", "_");
    }

    // format state snapshot into readable log line
    private String summarize(Map<String, Object> snapshot) {
        int day = ((Number) snapshot.getOrDefault("day", 0)).intValue();
        int alive = ((Number) snapshot.getOrDefault("alivePlants", 0)).intValue();
        int total = ((Number) snapshot.getOrDefault("totalPlants", 0)).intValue();
        return "Day " + day + ": " + alive + "/" + total + " plants alive.";
    }
    
    // log detailed status for each plant
    @SuppressWarnings("unchecked")
    private void logDetailedPlantStatus(Map<String, Object> snapshot) {
        List<String> names = (List<String>) snapshot.get("plants");
        List<String> types = (List<String>) snapshot.get("plantTypes");
        List<Double> health = (List<Double>) snapshot.get("plantHealth");
        List<Integer> water = (List<Integer>) snapshot.get("plantWater");
        List<Integer> waterReq = (List<Integer>) snapshot.get("plantWaterRequirement");
        List<Boolean> alive = (List<Boolean>) snapshot.get("plantAlive");
        
        if (names == null || names.isEmpty()) {
            return;
        }
        
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String type = types != null && i < types.size() ? types.get(i) : "Unknown";
            double healthValue = health != null && i < health.size() ? health.get(i) : 0.0;
            int waterValue = water != null && i < water.size() ? water.get(i) : 0;
            int waterReqValue = waterReq != null && i < waterReq.size() ? waterReq.get(i) : 0;
            boolean isAlive = alive != null && i < alive.size() && alive.get(i);
            
            String status = isAlive ? "ALIVE" : "DEAD";
            String healthStatus = getHealthStatus(healthValue);
            String waterStatus = getWaterStatus(waterValue, waterReqValue);
            
            String detail = String.format("%s (%s) - %s | Health: %.1f%% (%s) | Water: %d/%d (%s)",
                name, type, status, healthValue, healthStatus, waterValue, waterReqValue, waterStatus);
            
            logger.log("PLANT_STATUS", detail);
        }
    }
    
    // describe health level in human terms
    private String getHealthStatus(double healthPercent) {
        if (healthPercent >= 80) return "Healthy";
        if (healthPercent >= 50) return "Fair";
        if (healthPercent >= 20) return "Sick";
        if (healthPercent > 0) return "Dying";
        return "Dead";
    }
    
    // describe water level in human terms
    private String getWaterStatus(int currentWater, int requirement) {
        if (requirement == 0) return "N/A";
        double ratio = (double) currentWater / requirement;
        if (ratio >= 1.0) return "Good";
        if (ratio >= 0.5) return "OK";
        if (ratio > 0) return "Low";
        return "Dry";
    }
    
    // log ALERT for plants affected by parasite
    private void logAffectedPlants(String parasiteName) {
        List<Plant> allPlants = garden.getPlants();
        List<Plant> affected = new ArrayList<>();
        
        for (Plant plant : allPlants) {
            if (plant.isAlive() && plant.getVulnerableParasites().contains(parasiteName)) {
                affected.add(plant);
            }
        }
        
        if (affected.isEmpty()) {
            logger.log("PARASITE", "No plants are vulnerable to " + parasiteName);
            return;
        }
        
        // Log ALERT for each affected plant
        for (Plant plant : affected) {
            String alertMsg = String.format("ALERT: %s (%s) under attack by %s | Health: %.1f%%",
                plant.getName(), plant.getType(), parasiteName, plant.getHealth());
            logger.log("ALERT", alertMsg);
        }
    }

    private static class WeatherTelemetry {
        private static final long ACTIVE_RAIN_WINDOW_MS = 60_000L;
        private double cloudCoverFraction = 0.35;
        private int lastTemperature = 70;
        private long lastRainTimestampMs = 0L;
        private int lastRainAmount = 0;

        void recordRain(int amount) {
            lastRainAmount = amount;
            lastRainTimestampMs = System.currentTimeMillis();
            cloudCoverFraction = Math.min(1.0, cloudCoverFraction + 0.2);
        }

        void recordTemperature(int temperature) {
            lastTemperature = temperature;
        }

        void nudgeClouds(Random random, boolean isNight) {
            double target = isRainActive(System.currentTimeMillis())
                    ? 0.85
                    : (isNight ? 0.35 : 0.45 + random.nextDouble() * 0.25);
            cloudCoverFraction += (target - cloudCoverFraction) * 0.2;
            cloudCoverFraction = clamp(cloudCoverFraction, 0.05, 1.0);
        }

        Map<String, Object> snapshot(boolean isNight, int hourOfDay) {
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
            if (value < min) {
                return min;
            }
            if (value > max) {
                return max;
            }
            return value;
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
    
    // log ALERT for plants with health or status problems
    @SuppressWarnings("unchecked")
    private void logPlantAlerts(Map<String, Object> snapshot) {
        List<String> names = (List<String>) snapshot.get("plants");
        List<String> types = (List<String>) snapshot.get("plantTypes");
        List<Double> health = (List<Double>) snapshot.get("plantHealth");
        List<Integer> water = (List<Integer>) snapshot.get("plantWater");
        List<Integer> waterReq = (List<Integer>) snapshot.get("plantWaterRequirement");
        List<Boolean> alive = (List<Boolean>) snapshot.get("plantAlive");
        List<Boolean> infested = (List<Boolean>) snapshot.get("plantInfested");
        
        if (names == null || names.isEmpty()) {
            return;
        }
        
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String type = types != null && i < types.size() ? types.get(i) : "Unknown";
            double healthValue = health != null && i < health.size() ? health.get(i) : 0.0;
            int waterValue = water != null && i < water.size() ? water.get(i) : 0;
            int waterReqValue = waterReq != null && i < waterReq.size() ? waterReq.get(i) : 0;
            boolean isAlive = alive != null && i < alive.size() && alive.get(i);
            boolean isInfested = infested != null && i < infested.size() && infested.get(i);
            
            // ALERT conditions:
            // 1. Plant just died (health = 0)
            // 2. Plant is dying (health < 20% but still alive)
            // 3. Plant is critically low on water (water < 20% of requirement)
            // 4. Plant is infested
            
            if (!isAlive && healthValue == 0.0) {
                String alertMsg = String.format("ALERT: %s (%s) has DIED", name, type);
                logger.log("ALERT", alertMsg);
            } else if (isAlive) {
                List<String> problems = new ArrayList<>();
                
                if (healthValue < 20.0) {
                    problems.add(String.format("Health CRITICAL: %.1f%%", healthValue));
                } else if (healthValue < 50.0) {
                    problems.add(String.format("Health LOW: %.1f%%", healthValue));
                }
                
                if (waterReqValue > 0) {
                    double waterRatio = (double) waterValue / waterReqValue;
                    if (waterRatio < 0.2) {
                        problems.add(String.format("Water CRITICAL: %d/%d", waterValue, waterReqValue));
                    }
                }
                
                if (isInfested) {
                    problems.add("INFESTED by parasites");
                }
                
                if (!problems.isEmpty()) {
                    String alertMsg = String.format("ALERT: %s (%s) - %s", 
                        name, type, String.join(" | ", problems));
                    logger.log("ALERT", alertMsg);
                }
            }
        }
    }
}
