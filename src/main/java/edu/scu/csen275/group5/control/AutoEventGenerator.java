package edu.scu.csen275.group5.control;

import java.util.*;

/**
 * Generates automatic weather and pest events for the garden simulation.
 * Controls random rain, temperature changes, and parasite infestations.
 */
public class AutoEventGenerator {
    private static final List<String> ALL_PARASITES = Arrays.asList(
        "aphids", "spider_mites", "whiteflies", "hornworms", 
        "slugs", "beetles", "fungus_gnats", "mealybugs", "carrot_flies"
    );
    
    private static final int DEFAULT_RAIN_COOLDOWN_HOURS = 3;

    private final WeatherSimulator weatherSim;
    private final Set<String> recentParasites;
    private final Random random;
    
    private AutoEventConfig config;
    private int lastParasiteHour;
    private int nextRainEligibleHour;
    private int rainCooldownHours;
    
    public AutoEventGenerator(WeatherSimulator weatherSim) {
        this.weatherSim = weatherSim;
        this.recentParasites = new HashSet<>();
        this.random = new Random();
        this.lastParasiteHour = -999;
        this.nextRainEligibleHour = Integer.MIN_VALUE;
        applyConfig(AutoEventConfig.defaultConfig());
    }
    
    /**
     * Update configuration
     */
    public void setConfig(AutoEventConfig config) {
        if (config == null) {
            return;
        }
        applyConfig(config);
    }
    
    public AutoEventConfig getConfig() {
        return config;
    }
    
    /**
     * Generate events for a slice
     * @param hourOfDay current hour (0-23)
     * @param currentHour total hours elapsed
     * @return events to apply
     */
    public AutoEvents generateEvents(int hourOfDay, int currentHour) {
        AutoEvents events = new AutoEvents();
        
        // Rain event with cooldown
        boolean rainReady = currentHour >= nextRainEligibleHour;
        if (rainReady && weatherSim.shouldOccur(config.getRainChance())) {
            events.rainfall = weatherSim.generateRainfall(5, 15);
            nextRainEligibleHour = currentHour + rainCooldownHours;
        }
        
        // Temperature event
        if (weatherSim.shouldOccur(config.getTemperatureChance())) {
            events.temperature = weatherSim.computeTemperatureWithVariation(hourOfDay);
        } else {
            // Apply base temperature if no random event
            events.temperature = weatherSim.computeDiurnalTemperature(hourOfDay);
        }
        
        // Parasite event (with cooldown and limits)
        if (weatherSim.shouldOccur(config.getParasiteChance()) && 
            !weatherSim.isNightHour(hourOfDay)) {
            
            // Cooldown: at least 3 hours between parasites
            if (currentHour - lastParasiteHour >= 3) {
                String parasite = pickRandomParasite();
                if (parasite != null && canIntroduceParasite(parasite)) {
                    events.parasite = parasite;
                    recentParasites.add(parasite);
                    lastParasiteHour = currentHour;
                    
                    // Clear recent history if too many
                    if (recentParasites.size() > 5) {
                        recentParasites.clear();
                    }
                }
            }
        }
        
        return events;
    }
    
    /**
     * Pick a random parasite that hasn't appeared recently
     */
    private String pickRandomParasite() {
        List<String> available = new ArrayList<>();
        for (String p : ALL_PARASITES) {
            if (!recentParasites.contains(p)) {
                available.add(p);
            }
        }
        
        if (available.isEmpty()) {
            // All parasites used recently, clear and start over
            recentParasites.clear();
            available.addAll(ALL_PARASITES);
        }
        
        return available.isEmpty() ? null : available.get(random.nextInt(available.size()));
    }
    
    /**
     * Check if a parasite can be introduced (not too recent)
     */
    private boolean canIntroduceParasite(String parasiteName) {
        return !recentParasites.contains(parasiteName);
    }
    
    /**
     * Reset state (for new simulation)
     */
    public void reset() {
        recentParasites.clear();
        lastParasiteHour = -999;
        nextRainEligibleHour = Integer.MIN_VALUE;
    }

    private void applyConfig(AutoEventConfig config) {
        this.config = config;
        int requestedCooldown = config.getRainCooldownHours();
        this.rainCooldownHours = requestedCooldown > 0 ? requestedCooldown : DEFAULT_RAIN_COOLDOWN_HOURS;
    }
    
    /**
     * Container for generated events
     */
    public static class AutoEvents {
        public Integer rainfall;      // null if no rain
        public Integer temperature;   // always set
        public String parasite;        // null if no parasite
        
        public boolean hasRain() {
            return rainfall != null;
        }
        
        public boolean hasParasite() {
            return parasite != null;
        }
    }
    
    /**
     * Configuration for auto event probabilities
     */
    public static class AutoEventConfig {
        private final double rainChance;
        private final double temperatureChance;
        private final double parasiteChance;
        private final int rainCooldownHours;
        
        public AutoEventConfig(double rainChance, double temperatureChance, double parasiteChance) {
            this(rainChance, temperatureChance, parasiteChance, DEFAULT_RAIN_COOLDOWN_HOURS);
        }

        public AutoEventConfig(double rainChance, double temperatureChance, double parasiteChance, int rainCooldownHours) {
            this.rainChance = rainChance;
            this.temperatureChance = temperatureChance;
            this.parasiteChance = parasiteChance;
            this.rainCooldownHours = rainCooldownHours;
        }
        
        public static AutoEventConfig defaultConfig() {
            return new AutoEventConfig(0.04, 0.3, 0.15, DEFAULT_RAIN_COOLDOWN_HOURS);
        }
        
        public double getRainChance() { return rainChance; }
        public double getTemperatureChance() { return temperatureChance; }
        public double getParasiteChance() { return parasiteChance; }
        public int getRainCooldownHours() { return rainCooldownHours; }
    }
}
