package edu.scu.csen275.group5.control;

import edu.scu.csen275.group5.core.Garden;
import edu.scu.csen275.group5.core.Plant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles all plant-related logging and status formatting.
 */
public class PlantLogger {
    private final Garden garden;
    private final GardenLogger logger;
    
    public PlantLogger(Garden garden, GardenLogger logger) {
        this.garden = garden;
        this.logger = logger;
    }
    
    /**
     * Log detailed status for all plants
     */
    @SuppressWarnings("unchecked")
    public void logDetailedPlantStatus(Map<String, Object> snapshot) {
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
    
    /**
     * Log ALERT for plants affected by parasite
     */
    public void logAffectedPlants(String parasiteName) {
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
    
    /**
     * Log ALERT for plants with health or status problems
     */
    @SuppressWarnings("unchecked")
    public void logPlantAlerts(Map<String, Object> snapshot) {
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
            
            // ALERT conditions
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
    
    /**
     * Describe health level in human terms
     */
    private String getHealthStatus(double healthPercent) {
        if (healthPercent >= 96) return "Healthy";
        if (healthPercent >= 80) return "Fair";
        if (healthPercent >= 40) return "Sick";
        if (healthPercent > 0) return "Dying";
        return "Dead";
    }
    
    /**
     * Describe water level in human terms
     */
    private String getWaterStatus(int currentWater, int requirement) {
        if (requirement == 0) return "N/A";
        double ratio = (double) currentWater / requirement;
        if (ratio >= 1.0) return "Good";
        if (ratio >= 0.5) return "OK";
        if (ratio > 0) return "Low";
        return "Dry";
    }
}
