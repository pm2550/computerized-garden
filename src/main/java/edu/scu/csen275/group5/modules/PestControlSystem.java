package edu.scu.csen275.group5.modules;

import edu.scu.csen275.group5.control.GardenLogger;
import edu.scu.csen275.group5.core.Garden;
import java.util.*;

/**
 * Pest control system module
 * responsible for parasite detection and treatment
 */
public class PestControlSystem implements GardenModule {
    private boolean active;
    private Garden garden;
    private GardenLogger logger;
    private String name = "Pest Control System";
    private Map<String, String> pesticideMap;

    public PestControlSystem(Garden garden, GardenLogger logger) {
        this.active = false;
        this.garden = garden;
        this.logger = logger;
        initializePesticideMap();
    }

    @Override
    public void activate() {
        this.active = true;
        logger.log("PEST_CONTROL", "System activated");
    }

    @Override
    public void deactivate() {
        this.active = false;
        logger.log("PEST_CONTROL", "System deactivated");
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public String getModuleName() {
        return name;
    }
    
    @Override
    public void update() {
        // Update logic for pest control (called each simulation step)
        if (active) {
            // Automatic pest detection and treatment can be implemented here if needed
        }
    }


    /**
     * Dealing with parasitic incidents
     * @param parasiteName
     */
    public void treatParasite(String parasiteName) {
        if (active) {
            String normalizedParasite = sanitizeParasiteName(parasiteName);
            String pesticide = selectPesticide(normalizedParasite);

            if (pesticide != null) {
                // Call the existing methods of the Garden class
                garden.treatParasite(normalizedParasite);
                logger.log("PARASITE", "Treated " + normalizedParasite + " with " + pesticide);
            } else {
                logger.log("PEST_CONTROL", "No suitable pesticide found for: " + normalizedParasite);
            }
        } else {
            logger.log("PEST_CONTROL", "Warning: Treatment attempted but system is inactive");
        }
    }

    /**
     * Introduce parasites (for testing)
     * @param parasiteName
     */
    public void introduceParasite(String parasiteName) {
        String normalizedParasite = sanitizeParasiteName(parasiteName);
        // Call the existing methods of the Garden class
        garden.triggerParasiteInfestation(normalizedParasite);
        logger.log("PARASITE", "Parasite introduced: " + normalizedParasite);
    }

    private void initializePesticideMap() {
        pesticideMap = new HashMap<>();
        // Match the parasite types in the existing code
        pesticideMap.put("aphids", "neem_oil");
        pesticideMap.put("spider_mites", "miticide");
        pesticideMap.put("whiteflies", "insecticidal_soap");
        pesticideMap.put("hornworms", "bacillus_thuringiensis");
        pesticideMap.put("slugs", "iron_phosphate");
        pesticideMap.put("beetles", "neem_oil");
        pesticideMap.put("fungus_gnats", "bacillus_thuringiensis");
        pesticideMap.put("mealybugs", "pyrethrin");
        pesticideMap.put("carrot_flies", "spinosad");
    }

    private String selectPesticide(String parasite) {
        return pesticideMap.get(parasite);
    }

    private String sanitizeParasiteName(String rawName) {
        if (rawName == null) return "";
        return rawName.trim().toLowerCase().replace(" ", "_");
    }
}