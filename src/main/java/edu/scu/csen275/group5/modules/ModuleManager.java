package edu.scu.csen275.group5.modules;

import edu.scu.csen275.group5.control.GardenLogger;
import edu.scu.csen275.group5.core.Garden;
import java.util.*;

/**
 * Module Centralized manager
 * responsible for the coordination and management of all functional modules
 */
public class ModuleManager {
    private static ModuleManager instance;
    private Map<String, GardenModule> modules;
    private Map<String, ControllableModule> controllableModules;
    private Garden garden;

    private ModuleManager(Garden garden) {
        this.garden = garden;
        this.modules = new HashMap<>();
        this.controllableModules = new HashMap<>();
        initializeModules();
    }

    public static synchronized ModuleManager getInstance(Garden garden) {
        if (instance == null) {
            instance = new ModuleManager(garden);
        }
        return instance;
    }

    public static ModuleManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ModuleManager not initialized. Call getInstance(Garden) first.");
        }
        return instance;
    }

    private void initializeModules() {
        // Create and register all functional modules
        registerModule(new IrrigationSystem(garden), "irrigation");
        registerModule(new HeatingSystem(garden), "heating");
        registerModule(new PestControlSystem(garden), "pest_control");

        GardenLogger.log("MODULE_MANAGER", "All garden modules initialized successfully");
    }

    private void registerModule(GardenModule module, String name) {
        modules.put(name, module);
        if (module instanceof ControllableModule) {
            controllableModules.put(name, (ControllableModule) module);
        }
    }

    // API - For use by the GardenSimulationAPI
    public void handleRainfall(int amount) {
        IrrigationSystem irrigation = (IrrigationSystem) modules.get("irrigation");
        if (irrigation != null) {
            irrigation.simulateRainfall(amount);
        }
    }

    public void handleTemperatureChange(int temperature) {
        HeatingSystem heating = (HeatingSystem) modules.get("heating");
        if (heating != null) {
            heating.setTargetTemperature(temperature);
            heating.update();
        }
    }

    public void handleParasite(String parasiteName) {
        PestControlSystem pestControl = (PestControlSystem) modules.get("pest_control");
        if (pestControl != null) {
            pestControl.treatParasite(parasiteName);
        }
    }

    public void setModuleIntensity(String moduleName, int intensity) {
        ControllableModule module = controllableModules.get(moduleName);
        if (module != null) {
            module.setIntensity(intensity);
        }
    }

    public void activateModule(String moduleName) {
        GardenModule module = modules.get(moduleName);
        if (module != null) {
            module.activate();
        }
    }

    public void deactivateModule(String moduleName) {
        GardenModule module = modules.get(moduleName);
        if (module != null) {
            module.deactivate();
        }
    }

    public Map<String, Object> getModuleStatus() {
        Map<String, Object> status = new HashMap<>();
        for (Map.Entry<String, GardenModule> entry : modules.entrySet()) {
            Map<String, Object> moduleInfo = new HashMap<>();
            GardenModule module = entry.getValue();

            moduleInfo.put("active", module.isActive());
            moduleInfo.put("name", module.getModuleName());

            if (module instanceof ControllableModule) {
                ControllableModule controllable = (ControllableModule) module;
                moduleInfo.put("intensity", controllable.getCurrentIntensity());

                if (module instanceof HeatingSystem) {
                    moduleInfo.put("targetTemperature", ((HeatingSystem) module).getTargetTemperature());
                }
            }

            status.put(entry.getKey(), moduleInfo);
        }
        return status;
    }

    public void updateAllModules() {
        for (GardenModule module : modules.values()) {
            module.update();
        }
    }
}