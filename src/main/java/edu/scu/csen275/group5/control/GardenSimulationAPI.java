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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main API for the garden simulation. Scripts call these methods to simulate
 * weather and pest events. The UI also hooks into this to show what's happening.
 * 
 * Required by project spec - implements all methods from the Gardening System API doc.
 */
public class GardenSimulationAPI {

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

    private boolean initialized;
    private int minWaterRequirement;  // computed from plants after init
    private int maxWaterRequirement;

    public GardenSimulationAPI() {
        this(Paths.get("garden_config.txt"), Paths.get("log.txt"));
    }

    public GardenSimulationAPI(Path configPath, Path logPath) {
        this.garden = new Garden();
        this.configPath = Objects.requireNonNull(configPath, "configPath");
        this.logger = new GardenLogger(Objects.requireNonNull(logPath, "logPath"));
        this.observers = new CopyOnWriteArrayList<>();  // thread-safe for observers
        this.hoursElapsed = new AtomicInteger();
        this.minWaterRequirement = 5;   // defaults until we know actual plant needs
        this.maxWaterRequirement = 20;

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
        notifyState();
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
        closeHour("Rainfall " + validated + " units applied");
    }

    /**
     * Sets daily temperature. Spec says range is 40-120°F.
     */
    public synchronized void temperature(int temperatureFahrenheit) {
        ensureInitialized();
        garden.applyTemperature(temperatureFahrenheit);
        closeHour("Temperature set to " + temperatureFahrenheit + "°F");
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
        garden.triggerParasiteInfestation(cleaned);
        closeHour("Parasite '" + cleaned + "' released");
    }

    /**
     * Logs current garden state to log.txt (per API spec).
     * Shows alive/dead counts and notifies observers.
     */
    public synchronized void getState() {
        ensureInitialized();
        Map<String, Object> snapshot = captureState();
        logger.log("STATE", summarize(snapshot));
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

    // advances day counter and pushes state update to observers
    private void closeHour(String reason) {
        garden.advanceDay();
        int hour = hoursElapsed.incrementAndGet();
        logger.log("DAY", reason + ". Hour " + hour + " closed.");
        refreshWaterStats();
        notifyState();
    }

    private void notifyState() {
        if (observers.isEmpty()) {
            return;
        }
        dispatchState(captureState());
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
        return garden.getState();
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
}
