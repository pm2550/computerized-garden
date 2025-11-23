package edu.scu.csen275.group5.core;

import java.util.*;

/**
 * Example usage of the core simulation layer.
 * Demonstrates how to initialize a garden, run a simulation, and retrieve state.
 * This is NOT part of the API - it's just for testing the core logic.
 */
public class SimulationExample {
    
    public static void main(String[] args) {
        System.out.println("=== Computerized Garden - Core Simulation Example ===\n");
        
        // Create garden
        Garden garden = new Garden();
        System.out.println("Garden created\n");
        
        // Load configuration
        GardenConfig config = new GardenConfig("garden_config.txt");
        if (config.loadConfig()) {
            config.applyToGarden(garden);
            System.out.println("Configuration loaded successfully");
            System.out.println("Available plant types: " + garden.getAvailablePlantTypes() + "\n");
        } else {
            System.out.println("Using default plant templates\n");
        }
        
        // Plant some flowers
        System.out.println("--- Planting Phase ---");
        garden.plantNew("Rose-001", "Rose");
        garden.plantNew("Rose-002", "Rose");
        garden.plantNew("Tomato-001", "Tomato");
        garden.plantNew("Lettuce-001", "Lettuce");
        System.out.println();
        
        // Initial state
        printGardenState(garden);
        
        // Simulate 24 days
        System.out.println("\n=== Simulating 24 Days ===\n");
        
        Random rand = new Random(42);  // Fixed seed for reproducibility
        
        for (int day = 1; day <= 24; day++) {
            System.out.println("--- Day " + day + " ---");
            
            // Randomly trigger environmental events
            int eventType = rand.nextInt(3);
            
            switch (eventType) {
                case 0:  // Rain
                    int rainfall = 5 + rand.nextInt(15);  // 5-20 units
                    System.out.println("Event: Rain (" + rainfall + " units)");
                    garden.applyRainfall(rainfall);
                    break;
                    
                case 1:  // Temperature
                    int temp = 50 + rand.nextInt(71);  // 50-120°F
                    System.out.println("Event: Temperature (" + temp + "°F)");
                    garden.applyTemperature(temp);
                    break;
                    
                case 2:  // Parasite
                    String[] parasites = {"aphids", "spider_mites", "hornworms", "beetles", "slugs"};
                    String parasite = parasites[rand.nextInt(parasites.length)];
                    System.out.println("Event: Parasite Infestation (" + parasite + ")");
                    garden.triggerParasiteInfestation(parasite);
                    break;
            }
            
            // Advance simulation day
            garden.advanceDay();
            
            // Print current state
            Map<String, Object> state = garden.getState();
            System.out.println("  Alive: " + state.get("alivePlants") + "/" + state.get("totalPlants"));
            
            @SuppressWarnings("unchecked")
            List<Double> healthList = (List<Double>) state.get("plantHealth");
            @SuppressWarnings("unchecked")
            List<String> nameList = (List<String>) state.get("plants");
            
            for (int i = 0; i < nameList.size(); i++) {
                double health = healthList.get(i);
                String status = health <= 0 ? "DEAD" : String.format("Health: %.1f%%", health);
                System.out.println("    " + nameList.get(i) + ": " + status);
            }
            
            System.out.println();
        }
        
        // Final assessment
        System.out.println("\n=== Final Assessment (Day 24) ===");
        printGardenState(garden);
        
        // Print event history
        System.out.println("\n=== Event Log ===");
        for (Garden.GardenEvent event : garden.getEventHistory()) {
            System.out.println(event);
        }
    }
    
    private static void printGardenState(Garden garden) {
        Map<String, Object> state = garden.getState();
        
        System.out.println("Current Day: " + state.get("day"));
        System.out.println("Temperature: " + state.get("temperature") + "°F");
        
        System.out.println("\nPlant Status:");
        @SuppressWarnings("unchecked")
        List<String> details = (List<String>) state.get("plantDetails");
        for (String detail : details) {
            System.out.println("  " + detail);
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> soilStatus = (Map<String, Object>) state.get("soil");
        System.out.println("\nSoil Status:");
        System.out.println("  Moisture: " + String.format("%.1f%%", soilStatus.get("moisture")));
        System.out.println("  Nutrients: " + String.format("%.1f%%", soilStatus.get("nutrients")));
        System.out.println("  pH: " + soilStatus.get("pH"));
        System.out.println("  Temperature: " + String.format("%.1f°F", soilStatus.get("temperature")));
        @SuppressWarnings("unchecked")
        List<String> pests = (List<String>) soilStatus.get("pests");
        System.out.println("  Pests: " + (pests.isEmpty() ? "None" : pests));
        
        System.out.println("\nSummary:");
        System.out.println("  Total Plants: " + state.get("totalPlants"));
        System.out.println("  Alive: " + state.get("alivePlants"));
        System.out.println("  Dead: " + state.get("deadPlants"));
    }
}
