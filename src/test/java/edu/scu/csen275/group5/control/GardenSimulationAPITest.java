package edu.scu.csen275.group5.control;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GardenSimulationAPITest {

    private Path tempDir;
    private Path configPath;
    private Path logPath;
    private GardenSimulationAPI api;

    @BeforeEach
    void setUp() throws IOException {
        // Reset singleton before each test
        GardenSimulationAPI.resetInstance();
        
        tempDir = Files.createTempDirectory("garden-api-test");
        configPath = tempDir.resolve("garden.conf");
        logPath = tempDir.resolve("log.txt");
        writeConfig();
        api = GardenSimulationAPI.getInstance(configPath, logPath);
    }

    @Test
    void initializeGardenSeedsConfiguredInstances() {
        api.initializeGarden();
        Map<String, Object> plants = api.getPlants();
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) plants.get("plants");
        assertEquals(3, names.size());
        assertTrue(names.contains("Rose-001"));
        assertTrue(names.contains("Rose-002"));
        assertTrue(names.contains("Tomato-001"));
    }

    @Test
    void rainEventClampsWithoutAdvancingClock() {
        api.initializeGarden();
        int before = api.getHoursElapsed();
        api.rain(api.getMaxWaterRequirement() + 50);
        assertEquals(before, api.getHoursElapsed());
        boolean clampLogged = api.recentLogEntries().stream()
                .anyMatch(line -> line.contains("Clamped"));
        assertTrue(clampLogged, "Clamp log entry missing");
    }

    @Test
    void getPlantsReturnsParasiteMatrix() {
        api.initializeGarden();
        Map<String, Object> plants = api.getPlants();
    @SuppressWarnings("unchecked")
    List<List<String>> parasites = (List<List<String>>) plants.get("parasites");
    assertFalse(parasites.isEmpty());
    boolean containsAphids = parasites.stream().flatMap(List::stream).anyMatch("aphids"::equals);
    assertTrue(containsAphids, "Expected aphids parasite entry");
    }

    private void writeConfig() throws IOException {
        String cfg = "[Rose]\n" +
                "instances=2\n" +
                "waterRequirement=10\n" +
                "optimalTempMin=60\n" +
                "optimalTempMax=80\n" +
                "minTempTolerance=40\n" +
                "maxTempTolerance=95\n" +
                "parasites=aphids\n" +
                "\n" +
                "[Tomato]\n" +
                "instances=1\n" +
                "waterRequirement=15\n" +
                "optimalTempMin=70\n" +
                "optimalTempMax=90\n" +
                "minTempTolerance=45\n" +
                "maxTempTolerance=105\n" +
                "parasites=hornworms";
        Files.writeString(configPath, cfg);
    }
}
