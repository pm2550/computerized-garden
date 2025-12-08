package edu.scu.csen275.group5.core;

import java.util.*;

/**
 * Core simulation layer representing the garden ecosystem.
 * Manages plants, soil, growth rules, and time progression.
 * Responsible for determining plant survival based on environmental conditions.
 */
public class Garden {
    private List<Plant> plants;
    private Soil soil;
    private int simulationDay;  // Current day in simulation
    private int airTemperature;  // Current air temperature in Fahrenheit
    private Map<String, PlantTemplate> plantTemplates;  // Predefined plant types
    private List<GardenEvent> eventHistory;  // Log of all events

    /**
     * Represents a plant template with default properties
     */
    public static class PlantTemplate {
        public String type;
        public int waterRequirement;
        public int optimalTempMin;
        public int optimalTempMax;
        public int minTempTolerance;
        public int maxTempTolerance;
        public List<String> vulnerableParasites;

        public PlantTemplate(String type, int waterRequirement,
                           int optimalTempMin, int optimalTempMax,
                           int minTempTolerance, int maxTempTolerance) {
            this.type = type;
            this.waterRequirement = waterRequirement;
            this.optimalTempMin = optimalTempMin;
            this.optimalTempMax = optimalTempMax;
            this.minTempTolerance = minTempTolerance;
            this.maxTempTolerance = maxTempTolerance;
            this.vulnerableParasites = new ArrayList<>();
        }
    }

    /**
     * Records an event that occurred in the garden
     */
    public static class GardenEvent {
        public int day;
        public String eventType;  // "INITIALIZATION", "RAIN", "TEMPERATURE", "PARASITE", etc.
        public String description;
        public long timestamp;

        public GardenEvent(int day, String eventType, String description) {
            this.day = day;
            this.eventType = eventType;
            this.description = description;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("[Day %d] %s: %s", day, eventType, description);
        }
    }

    public Garden() {
        this.plants = new ArrayList<>();
        this.soil = new Soil();
        this.simulationDay = 0;
        this.airTemperature = 70;  // Start at pleasant 70°F
        this.plantTemplates = new HashMap<>();
        this.eventHistory = new ArrayList<>();
        initializeDefaultPlantTemplates();
    }

    /**
     * Initialize default plant templates (Rose, Tomato, Lettuce, Sunflower)
     */
    private void initializeDefaultPlantTemplates() {
        // Rose: moderate water, warm temperature, vulnerable to aphids and spider mites
        PlantTemplate rose = new PlantTemplate("Rose", 10, 65, 75, 40, 95);
        rose.vulnerableParasites.addAll(Arrays.asList("aphids", "spider_mites"));
        plantTemplates.put("Rose", rose);

        // Tomato: high water, warm temperature, vulnerable to hornworms and spider mites
        PlantTemplate tomato = new PlantTemplate("Tomato", 15, 70, 85, 50, 100);
        tomato.vulnerableParasites.addAll(Arrays.asList("hornworms", "spider_mites", "whiteflies"));
        plantTemplates.put("Tomato", tomato);

        // Lettuce: moderate water, cool temperature, vulnerable to aphids
        PlantTemplate lettuce = new PlantTemplate("Lettuce", 8, 55, 70, 35, 80);
        lettuce.vulnerableParasites.addAll(Arrays.asList("aphids", "slugs"));
        plantTemplates.put("Lettuce", lettuce);

        // Sunflower: low-moderate water, warm temperature, vulnerable to beetles
        PlantTemplate sunflower = new PlantTemplate("Sunflower", 12, 70, 85, 45, 100);
        sunflower.vulnerableParasites.addAll(Arrays.asList("beetles", "aphids"));
        plantTemplates.put("Sunflower", sunflower);
    }

    /**
     * Add a custom plant template to the garden
     * @param type plant type name
     * @param template the PlantTemplate
     */
    public void addPlantTemplate(String type, PlantTemplate template) {
        plantTemplates.put(type, template);
    }

    /**
     * Plant a new plant in the garden
     * @param name unique name for the plant
     * @param type type of plant (must exist in templates)
     * @return the created Plant object, or null if type not found
     */
    public Plant plantNew(String name, String type) {
        if (!plantTemplates.containsKey(type)) {
            recordEvent("PLANT_ERROR", "Cannot plant " + name + ": unknown type '" + type + "'");
            return null;
        }

        PlantTemplate template = plantTemplates.get(type);
        Plant plant = new Plant(name, type,
                template.waterRequirement,
                template.optimalTempMin, template.optimalTempMax,
                template.minTempTolerance, template.maxTempTolerance);

        // Set vulnerable parasites
        for (String parasite : template.vulnerableParasites) {
            plant.addVulnerableParasite(parasite);
        }

        plants.add(plant);
        recordEvent("PLANTING", name + " (" + type + ") has been planted");
        return plant;
    }

    /**
     * Apply rainfall to the garden
     * Distributes water to soil, which makes it available to plants
     * @param rainfallAmount amount of water from rain
     */
    public void applyRainfall(int rainfallAmount) {
        soil.addWater(rainfallAmount);
        applyExcessRainfallToPlants(rainfallAmount);
        
        recordEvent("RAIN", "Rainfall applied: " + rainfallAmount + " units. Soil moisture: " + 
                   String.format("%.1f%%", soil.getMoisture()));
    }

    private void applyExcessRainfallToPlants(int rainfallAmount) {
        if (rainfallAmount <= 0) {
            return;
        }
        int maxRequirement = plants.stream()
                .filter(Plant::isAlive)
                .mapToInt(Plant::getWaterRequirement)
                .max()
                .orElse(0);
        if (maxRequirement <= 0 || rainfallAmount <= maxRequirement) {
            return;
        }
        double excessMultiplier = ((double) rainfallAmount / maxRequirement) - 1.0;
        if (excessMultiplier <= 0) {
            return;
        }

        List<String> damaged = new ArrayList<>();
        for (Plant plant : plants) {
            if (!plant.isAlive()) {
                continue;
            }
            double extraWater = plant.getWaterRequirement() * excessMultiplier;
            int extraUnits = (int) Math.ceil(extraWater);
            if (extraUnits <= 0) {
                continue;
            }
            double beforeHealth = plant.getHealth();
            plant.addWater(extraUnits);
            if (plant.getHealth() < beforeHealth) {
                damaged.add(String.format("%s(%.0f→%.0f%%)",
                        plant.getName(), beforeHealth, plant.getHealth()));
            }
        }

        if (!damaged.isEmpty()) {
            recordEvent("RAIN_DAMAGE", "Excess rainfall harmed " + damaged.size() + " plant(s): " +
                    String.join(", ", damaged));
        }
    }

    /**
     * Apply temperature stress to the garden
     * @param temperature air temperature in Fahrenheit (range: 40-120)
     */
    public void applyTemperature(int temperature) {
        // Validate input
        if (temperature < 40 || temperature > 120) {
            recordEvent("TEMPERATURE_ERROR", "Invalid temperature: " + temperature + "°F (range: 40-120)");
            return;
        }

        this.airTemperature = temperature;
        soil.updateTemperature(temperature);

        // Apply temperature stress to all plants
        int plantsAffected = 0;
        for (Plant plant : plants) {
            if (plant.isAlive()) {
                plant.applyTemperatureStress(temperature);
                plantsAffected++;
            }
        }

        recordEvent("TEMPERATURE", "Temperature set to " + temperature + "°F. Affected " + 
                   plantsAffected + " plant(s)");
    }

    /**
     * Trigger a parasite infestation in the garden
     * The parasite will infect plants that are vulnerable to it
     * @param parasiteName name of the parasite
     */
    public void triggerParasiteInfestation(String parasiteName) {
        soil.addPest(parasiteName);
        
        List<String> infectedPlants = new ArrayList<>();
        for (Plant plant : plants) {
            if (plant.isAlive() && plant.getVulnerableParasites().contains(parasiteName)) {
                plant.infectParasite(parasiteName);
                infectedPlants.add(plant.getName());
            }
        }

        if (infectedPlants.isEmpty()) {
            recordEvent("PARASITE", "Parasite '" + parasiteName + "' introduced but no vulnerable plants");
        } else {
            recordEvent("PARASITE", "Parasite '" + parasiteName + "' infested: " + 
                       String.join(", ", infectedPlants));
        }
    }

    /**
     * Treat parasites in the garden
     * Removes parasites from vulnerable plants
     * @param parasiteName name of the parasite to treat
     */
    public void treatParasite(String parasiteName) {
        System.out.println("DEBUG: treatParasite called for: " + parasiteName);
        soil.removePest(parasiteName);
        
        int treatedPlants = 0;
        for (Plant plant : plants) {
            if (plant.isAlive() && plant.isInfested() && 
                plant.getVulnerableParasites().contains(parasiteName)) {
                System.out.println("DEBUG: Treating plant " + plant.getName() + " for " + parasiteName);
                plant.treatParasite();
                treatedPlants++;
            }
        }
        System.out.println("DEBUG: Total treated plants: " + treatedPlants);

        recordEvent("TREATMENT", "Treated parasite '" + parasiteName + "': " + 
                   treatedPlants + " plant(s) treated");
    }

    /**
     * Advance the simulation by one full day (24 simulated hours = 144 slices)
     */
    public void advanceDay() {
        simulationDay++;
        
        // Advance each plant's age
        for (Plant plant : plants) {
            if (plant.isAlive()) {
                plant.advanceDay();
            }
        }

        // Advance soil (daily processes like evaporation)
        soil.advanceDay();

        recordEvent("DAY_ADVANCE", "Day " + simulationDay + " completed");
    }
    
    /**
     * Advance the simulation by one slice (10 simulated minutes)
     */
    public void advanceSlice() {
        // First let plants absorb water from soil BEFORE consuming
        for (Plant plant : plants) {
            if (plant.isAlive()) {
                int currentWater = plant.getCurrentWater();
                int waterReq = plant.getWaterRequirement();
                // Target: keep plants at 100-120% of requirement for health buffer
                double targetWater = waterReq * 1.1; // 110% of requirement
                
                if (currentWater < targetWater && soil.getMoisture() > 10.0) {
                    // Plant needs water and soil has some moisture
                    double needed = targetWater - currentWater;
                    // Each plant can absorb based on soil moisture
                    // Higher moisture = easier absorption, using doubles for precision
                    double absorptionRate = Math.min(1.0, soil.getMoisture() / 50.0); // 0.2-1.0
                    double canAbsorb = absorptionRate * 3.0; // 0.6-3.0 units per slice
                    double absorbed = Math.min(canAbsorb, needed);
                    
                    if (absorbed > 0.01) { // Use small threshold for double precision
                        plant.addWater((int)Math.ceil(absorbed)); // Round up to ensure absorption
                        // Remove absorbed water from soil (moderate depletion)
                        soil.addWater(-absorbed * 0.3); // Less soil depletion than plant gain
                    }
                }
            }
        }
        
        // Then process plant water consumption
        for (Plant plant : plants) {
            if (plant.isAlive()) {
                plant.advanceSlice();
            }
        }
    }

    /**
     * Get the current state of the garden
     * @return a map describing the garden state
     */
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        
        state.put("day", simulationDay);
        state.put("temperature", airTemperature);
        
        // Plant status
        List<String> plantNames = new ArrayList<>();
        List<String> plantTypes = new ArrayList<>();
        List<Double> plantHealth = new ArrayList<>();
        List<Boolean> plantAlive = new ArrayList<>();
        List<Integer> plantWaterLevels = new ArrayList<>();
        List<Integer> plantWaterRequirements = new ArrayList<>();
        List<Integer> plantAges = new ArrayList<>();
        List<Boolean> plantInfested = new ArrayList<>();
        List<String> plantStatus = new ArrayList<>();

        for (Plant plant : plants) {
            plantNames.add(plant.getName());
            plantTypes.add(plant.getType());
            plantHealth.add(plant.getHealth());
            plantAlive.add(plant.isAlive());
            plantWaterLevels.add(plant.getCurrentWater());
            plantWaterRequirements.add(plant.getWaterRequirement());
            plantAges.add(plant.getAge());
            plantInfested.add(plant.isInfested());
            plantStatus.add(String.format("%s: Health=%.1f%%, Water=%d, Age=%d, %s",
                    plant.getName(), plant.getHealth(), plant.getCurrentWater(),
                    plant.getAge(), plant.isAlive() ? "ALIVE" : "DEAD"));
        }

        state.put("plants", plantNames);
        state.put("plantTypes", plantTypes);
        state.put("plantHealth", plantHealth);
        state.put("plantAlive", plantAlive);
        state.put("plantWater", plantWaterLevels);
        state.put("plantWaterRequirement", plantWaterRequirements);
        state.put("plantAge", plantAges);
        state.put("plantInfested", plantInfested);
        state.put("plantDetails", plantStatus);

        // Soil status
        Map<String, Object> soilStatus = new LinkedHashMap<>();
        soilStatus.put("moisture", soil.getMoisture());
        soilStatus.put("nutrients", soil.getNutrients());
        soilStatus.put("pH", soil.getPH());
        soilStatus.put("temperature", soil.getTemperature());
        soilStatus.put("pests", soil.getCurrentPests());
        state.put("soil", soilStatus);

        // Statistics
    int aliveCount = (int) plants.stream().filter(Plant::isAlive).count();
    int deadCount = plants.size() - aliveCount;
    state.put("totalPlants", plants.size());
    state.put("alivePlants", aliveCount);
    state.put("deadPlants", deadCount);

        return state;
    }

    /**
     * Record an event in the garden's history
     */
    private void recordEvent(String eventType, String description) {
        eventHistory.add(new GardenEvent(simulationDay, eventType, description));
    }

    // Getters
    public List<Plant> getPlants() { return new ArrayList<>(plants); }
    public Soil getSoil() { return soil; }
    public int getSimulationDay() { return simulationDay; }
    public int getAirTemperature() { return airTemperature; }
    public List<GardenEvent> getEventHistory() { return new ArrayList<>(eventHistory); }
    public Set<String> getAvailablePlantTypes() { return plantTemplates.keySet(); }

    /**
     * Reset the garden to initial state
     */
    public void reset() {
        plants.clear();
        soil = new Soil();
        simulationDay = 0;
        airTemperature = 70;
        eventHistory.clear();
        recordEvent("RESET", "Garden has been reset");
    }

    @Override
    public String toString() {
        return String.format("Garden - Day %d, Plants: %d alive/%d total, Temperature: %d°F",
                simulationDay, 
                plants.stream().filter(Plant::isAlive).count(),
                plants.size(),
                airTemperature);
    }
}
