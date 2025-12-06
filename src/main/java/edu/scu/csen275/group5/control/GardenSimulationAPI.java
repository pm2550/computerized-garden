package edu.scu.csen275.group5.control;

import edu.scu.csen275.group5.core.Garden;
import edu.scu.csen275.group5.core.GardenConfig;
import edu.scu.csen275.group5.core.Plant;
import edu.scu.csen275.group5.modules.ModuleManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main API for the garden simulation. Scripts call these methods to simulate
 * weather and pest events. The UI also hooks into this to show what's happening.
 * 
 * SINGLETON: Ensures garden state persists across scene changes in UI.
 */
public class GardenSimulationAPI {

    public interface SimulationObserver {
        // UI or external system can watch garden changes through this
        void onStateChanged(Map<String, Object> stateSnapshot);
        void onLogAppended(String logEntry);
    }

    // Singleton instance
    private static GardenSimulationAPI instance;

    // Core components
    private final Garden garden;
    private final GardenLogger logger;
    private final ModuleManager moduleManager;
    private final SensorBridge sensorBridge;
    
    // New refactored components
    private final SimulationTimeManager timeManager;
    private final WeatherSimulator weatherSimulator;
    private final AutoEventGenerator eventGenerator;
    private final WeatherTelemetry weatherTelemetry;
    private final SliceProcessor sliceProcessor;
    private final StateManager stateManager;
    private final PlantLogger plantLogger;
    
    // Configuration and state
    private final Path configPath;
    private final List<SimulationObserver> observers;
    private boolean initialized;
    private boolean autoEventsEnabled;

    /**
     * Get singleton instance with default paths
     */
    public static synchronized GardenSimulationAPI getInstance() {
        if (instance == null) {
            instance = new GardenSimulationAPI(Paths.get("garden_config.txt"), Paths.get("log.txt"));
        }
        return instance;
    }

    /**
     * Get singleton instance with custom paths (for testing)
     */
    public static synchronized GardenSimulationAPI getInstance(Path configPath, Path logPath) {
        if (instance == null) {
            instance = new GardenSimulationAPI(configPath, logPath);
        }
        return instance;
    }

    /**
     * Private constructor - use getInstance() instead
     */
    private GardenSimulationAPI(Path configPath, Path logPath) {
        this.garden = new Garden();
        this.configPath = Objects.requireNonNull(configPath, "configPath");
        this.logger = new GardenLogger(Objects.requireNonNull(logPath, "logPath"));
        this.observers = new CopyOnWriteArrayList<>();
        
        // Initialize new components
        this.timeManager = new SimulationTimeManager();
        this.weatherSimulator = new WeatherSimulator();
        this.eventGenerator = new AutoEventGenerator(weatherSimulator);
        this.weatherTelemetry = new WeatherTelemetry();
        this.stateManager = new StateManager(garden, timeManager, weatherSimulator, weatherTelemetry);
        this.plantLogger = new PlantLogger(garden, logger);
        this.sliceProcessor = new SliceProcessor(garden, timeManager, weatherSimulator,
                                                  eventGenerator, weatherTelemetry, logger);
        
        this.autoEventsEnabled = true;
        
        this.moduleManager = ModuleManager.getInstance(garden, logger);
        this.sensorBridge = new SensorBridge(moduleManager, logger);

        // Wire up logger for observers
        this.logger.addListener(this::fanOutLogEntry);
    }

    /**
     * Reset singleton instance - FOR TESTING ONLY
     * This allows tests to create fresh instances with custom paths
     */
    static synchronized void resetInstance() {
        instance = null;
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
        timeManager.reset(); // Reset time to hour 0
        stateManager.refreshWaterStats();  // figure out min/max water needs from actual plants
        logger.log("INIT", "Garden initialized with " + garden.getPlants().size() + " plants");
        dispatchState(stateManager.captureState());
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
        // Use StateManager to get current water requirement range and clamp
        int minWater = stateManager.getMinWaterRequirement();
        int maxWater = stateManager.getMaxWaterRequirement();
        int validated = Math.max(minWater, Math.min(maxWater, rainfallAmount));
        
        if (validated != rainfallAmount) {
            logger.log("RAIN", "Requested " + rainfallAmount + " units. Clamped to " + validated + ".");
        }
    garden.applyRainfall(validated);
    weatherTelemetry.recordRainfall(validated, timeManager.getHoursElapsed());
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
        logger.log("TEMPERATURE", "Manual temperature set to " + temperatureFahrenheit + "°F");
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
        // Apply parasite directly (manual trigger, no limit check)
        plantLogger.logAffectedPlants(cleaned);
        garden.triggerParasiteInfestation(cleaned);
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

    public synchronized void updateAutoEventConfig(AutoEventGenerator.AutoEventConfig config) {
        if (config != null) {
            eventGenerator.setConfig(config);
        }
    }

    private void refreshAndBroadcastState() {
        stateManager.refreshWaterStats();
        Map<String, Object> snapshot = stateManager.captureState();
        if (sensorBridge != null) {
            boolean acted = sensorBridge.evaluateAndAct(snapshot, 
                stateManager.getMinWaterRequirement(), 
                stateManager.getMaxWaterRequirement());
            if (acted) {
                snapshot = stateManager.captureState();
            }
        } else {
            logger.log("DEBUG", "WARNING: sensorBridge is null!");
        }
        plantLogger.logPlantAlerts(snapshot);
        dispatchState(snapshot);
    }

    /**
     * Logs current garden state to log.txt (per API spec).
     * Shows alive/dead counts and notifies observers.
     */
    public synchronized void getState() {
        ensureInitialized();
        Map<String, Object> snapshot = stateManager.captureState();
        
        // Log summary first
        logger.log("STATE", stateManager.summarize(snapshot));
        
        // Log detailed plant status
        plantLogger.logDetailedPlantStatus(snapshot);
        
        dispatchState(snapshot);
    }

    // extra method for UI - returns state map instead of just logging
    public synchronized Map<String, Object> currentState() {
        ensureInitialized();
        return stateManager.captureState();
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
        return stateManager.getMinWaterRequirement();
    }

    public int getMaxWaterRequirement() {
        return stateManager.getMaxWaterRequirement();
    }

    public int getHoursElapsed() {
        return timeManager.getHoursElapsed();
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
        // Process remaining slices
        int remaining = timeManager.getRemainingSlices();
        for (int i = 0; i < remaining; i++) {
            processSlice();
        }
        
        timeManager.advanceHour();
        int hour = timeManager.getHoursElapsed();
        
        // Advance day counter every 24 hours
        if (hour % 24 == 0) {
            garden.advanceDay();
        }
        
        logger.log("HOUR", reason + ". Hour " + hour + " closed.");
        refreshAndBroadcastState();
    }
    
    /**
     * Process one simulation slice - delegates to SliceProcessor component
     */
    private void processSlice() {
        // Update slice processor with current water requirements from StateManager
        sliceProcessor.updateWaterRequirements(
            stateManager.getMinWaterRequirement(), 
            stateManager.getMaxWaterRequirement()
        );
        
        // Process slice (delegates to component)
        String eventSummary = autoEventsEnabled 
            ? sliceProcessor.processSliceWithAutoEvents()
            : null;
        
        if (!autoEventsEnabled) {
            sliceProcessor.processSliceWithoutAutoEvents();
        }
        
        // Log auto events if any occurred
        if (eventSummary != null) {
            logger.log("AUTO", eventSummary);
        }
        
        // Log affected plants if parasite was introduced
        if (eventSummary != null && eventSummary.contains("pest=")) {
            String parasite = extractParasiteName(eventSummary);
            if (parasite != null) {
                plantLogger.logAffectedPlants(parasite);
            }
        }
        
        // Check sensors and refresh state
        refreshAndBroadcastState();
    }
    
    /**
     * Extract parasite name from event summary string
     */
    private String extractParasiteName(String summary) {
        int pestIdx = summary.indexOf("pest=");
        if (pestIdx < 0) return null;
        
        int start = pestIdx + 5; // "pest=".length()
        int end = summary.indexOf(' ', start);
        if (end < 0) end = summary.length();
        
        return summary.substring(start, end).trim();
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

    /**
     * Process multiple slices for UI/timer (public API)
     */
    public synchronized void processAutoSlices(int slices) {
        ensureInitialized();
        if (slices <= 0) return;
        
        for (int i = 0; i < slices && !timeManager.isHourComplete(); i++) {
            processSlice();
        }
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
}
