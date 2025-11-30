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
    private String name = "Pest Control System";
    private Map<String, String> pesticideMap;

    public PestControlSystem(Garden garden) {
        this.active = false;
        this.garden = garden;
        initializePesticideMap();
    }

    @Override
    public void activate() {
        this.active = true;
        GardenLogger.log("PEST_CONTROL", "System activated");
    }

    @Override
    public void deactivate() {
        this.active = false;
        GardenLogger.log("PEST_CONTROL", "System deactivated");
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public String getModuleName() {
        return name;
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
                GardenLogger.log("PARASITE", "Treated " + normalizedParasite + " with " + pesticide);
            } else {
                GardenLogger.log("PEST_CONTROL", "No suitable pesticide found for: " + normalizedParasite);
            }
        } else {
            GardenLogger.log("PEST_CONTROL", "Warning: Treatment attempted but system is inactive");
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
        GardenLogger.log("PARASITE", "Parasite introduced: " + normalizedParasite);
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