package edu.scu.csen275.group5.core;

import java.io.*;
import java.util.*;

/**
 * Configuration manager for loading plant definitions from a config file.
 * Provides centralized configuration for the garden ecosystem.
 */
public class GardenConfig {
    private String configPath;
    private Map<String, Map<String, Object>> plantConfigs;

    public GardenConfig(String configPath) {
        this.configPath = configPath;
        this.plantConfigs = new HashMap<>();
    }

    /**
     * Load configuration from a file in the format:
     * [plantType]
     * instances=3
     * waterRequirement=15
     * optimalTempMin=70
     * optimalTempMax=85
     * minTempTolerance=50
     * maxTempTolerance=100
     * parasites=hornworms,spider_mites,whiteflies
     */
    public boolean loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader(configPath))) {
            String line;
            String currentPlant = null;
            Map<String, Object> currentConfig = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Plant type header [Rose]
                if (line.startsWith("[") && line.endsWith("]")) {
                    currentPlant = line.substring(1, line.length() - 1);
                    currentConfig = new HashMap<>();
                    plantConfigs.put(currentPlant, currentConfig);
                } else if (currentConfig != null && line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    
                    // Parse values appropriately
                    try {
                        if (key.equals("parasites")) {
                            List<String> parasites = new ArrayList<>();
                            for (String token : value.split(",")) {
                                String trimmed = token.trim();
                                if (!trimmed.isEmpty()) {
                                    parasites.add(trimmed);
                                }
                            }
                            currentConfig.put(key, parasites);
                        } else {
                            currentConfig.put(key, Integer.parseInt(value));
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Error parsing value for " + key + ": " + value);
                    }
                }
            }

            return true;
        } catch (IOException e) {
            System.err.println("Error loading config from " + configPath + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Apply loaded configuration to a garden
     * @param garden the Garden instance to configure
     */
    public void applyToGarden(Garden garden) {
        for (String plantType : plantConfigs.keySet()) {
            Map<String, Object> config = plantConfigs.get(plantType);
            
            try {
                int waterReq = ((Integer) config.getOrDefault("waterRequirement", 10));
                int optTempMin = ((Integer) config.getOrDefault("optimalTempMin", 65));
                int optTempMax = ((Integer) config.getOrDefault("optimalTempMax", 75));
                int minTempTol = ((Integer) config.getOrDefault("minTempTolerance", 40));
                int maxTempTol = ((Integer) config.getOrDefault("maxTempTolerance", 95));

                Garden.PlantTemplate template = new Garden.PlantTemplate(
                        plantType, waterReq, optTempMin, optTempMax, minTempTol, maxTempTol);

                @SuppressWarnings("unchecked")
                List<String> parasites = (List<String>) config.getOrDefault("parasites", new ArrayList<>());
                template.vulnerableParasites.addAll(parasites);

                garden.addPlantTemplate(plantType, template);
            } catch (Exception e) {
                System.err.println("Error applying config for " + plantType + ": " + e.getMessage());
            }
        }
    }

    public Map<String, Map<String, Object>> getConfigs() {
        return new HashMap<>(plantConfigs);
    }
}
