package edu.scu.csen275.group5;

import edu.scu.csen275.group5.control.GardenSimulationAPI;
import edu.scu.csen275.group5.ui.PlantVisualizer;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PrimaryController {

    @FXML
    private Label summaryLabel;

    @FXML
    private Label dayLabel;

    @FXML
    private Label clockLabel;

    @FXML
    private Label timerStatusLabel;

    @FXML
    private Label rainGuidanceLabel;
    
    @FXML
    private GridPane gardenGrid;  // the garden map view
    
    @FXML
    private StackPane gardenBackground;  // container for background image

    @FXML
    private TableView<PlantStatusRow> plantTable;

    @FXML
    private TableColumn<PlantStatusRow, String> nameColumn;

    @FXML
    private TableColumn<PlantStatusRow, String> typeColumn;

    @FXML
    private TableColumn<PlantStatusRow, String> healthColumn;

    @FXML
    private TableColumn<PlantStatusRow, String> statusColumn;

    @FXML
    private TextArea logArea;

    @FXML
    private Spinner<Integer> rainSpinner;

    @FXML
    private Spinner<Integer> temperatureSpinner;

    @FXML
    private TextField parasiteField;

    @FXML
    private Button initButton;

    @FXML
    private Button nextHourButton;

    @FXML
    private ComboBox<String> speedComboBox;

    private final ObservableList<PlantStatusRow> plantRows = FXCollections.observableArrayList();
    private final GardenSimulationAPI api = new GardenSimulationAPI();

    private static final double SIM_HOUR_SECONDS = 3600.0; // 1 simulated hour = 3600 simulated seconds
    private static final double BASE_HOUR_SECONDS = SIM_HOUR_SECONDS; // 1x = 3600 real seconds per sim hour
    private AnimationTimer hourTimer;
    private double speedMultiplier = 1.0;
    private double remainingSeconds = BASE_HOUR_SECONDS;
    private long lastTickNanos = 0L;
    private boolean timerActive = false;
    private int lastKnownSimHour = 0;
    private String lastSpeedSelection = "1x";
    private boolean suppressSpeedSelectionUpdate = false;

    @FXML
    public void initialize() {
        configureTable();
        configureSpinners();
        configureLogArea();
        configureTimerControls();
        hookObservers();
        hydrateLogTail();
        setGardenBackground();  // set the background image
    }
    
    // sets the garden background image
    private void setGardenBackground() {
        if (gardenBackground != null) {
            try {
                String imageUrl = getClass().getResource("garden.png").toExternalForm();
                String style = String.format(
                    "-fx-background-image: url('%s'); " +
                    "-fx-background-size: 100%% auto; " +
                    "-fx-background-repeat: no-repeat; " +  
                    "-fx-background-position: center center; " +  
                    "-fx-border-color: #4caf50; " +
                    "-fx-border-width: 2;",
                    imageUrl
                );
                gardenBackground.setStyle(style);
            } catch (Exception e) {
                System.err.println("Could not load garden.png: " + e.getMessage());
                // fallback to green background
                gardenBackground.setStyle("-fx-background-color: #e8f5e9; -fx-border-color: #4caf50; -fx-border-width: 2;");
            }
        }
    }

    @FXML
    private void handleInitializeGarden() {
        try {
            api.initializeGarden();
            applyState(api.currentState());
            updateRainGuidance();
            summaryLabel.setText("Garden online — ready for events");
            initButton.setDisable(true);
            startHourTimer();
        } catch (Exception ex) {
            appendLog("Initialization failed: " + ex.getMessage());
            summaryLabel.setText("Init error: " + ex.getMessage());
        }
    }

    @FXML
    private void handleRainEvent() {
        try {
            api.rain(rainSpinner.getValue());
        } catch (Exception ex) {
            appendLog("Rain event failed: " + ex.getMessage());
        }
    }

    @FXML
    private void handleTemperatureEvent() {
        try {
            api.temperature(temperatureSpinner.getValue());
        } catch (Exception ex) {
            appendLog("Temperature event failed: " + ex.getMessage());
        }
    }

    @FXML
    private void handleParasiteEvent() {
        try {
            api.parasite(parasiteField.getText());
            parasiteField.clear();
        } catch (Exception ex) {
            appendLog("Parasite event failed: " + ex.getMessage());
        }
    }

    @FXML
    private void handleNextHour() {
        try {
            api.advanceHourManually();
            resetTimerCountdown();
        } catch (Exception ex) {
            appendLog("Next hour failed: " + ex.getMessage());
        }
    }

    @FXML
    private void handleStateSnapshot() {
        try {
            api.getState();
        } catch (Exception ex) {
            appendLog("State snapshot failed: " + ex.getMessage());
        }
    }

    @FXML
    private void handleOpenHelp() throws IOException {
        App.setRoot("secondary");
    }

    private void hookObservers() {
        api.addObserver(new GardenSimulationAPI.SimulationObserver() {
            @Override
            public void onStateChanged(Map<String, Object> stateSnapshot) {
                Platform.runLater(() -> applyState(stateSnapshot));
            }

            @Override
            public void onLogAppended(String logEntry) {
                Platform.runLater(() -> appendLog(logEntry));
            }
        });
    }

    private void configureTable() {
        plantTable.setItems(plantRows);
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        healthColumn.setCellValueFactory(new PropertyValueFactory<>("health"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        plantTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        healthColumn.setText("Health %");
        healthColumn.setMinWidth(80);
        healthColumn.setPrefWidth(80);
    }

    private void configureSpinners() {
        rainSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 20, 10));
        temperatureSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(40, 120, 72));
        rainSpinner.setEditable(true);
        temperatureSpinner.setEditable(true);
    }

    private void configureLogArea() {
        logArea.setEditable(false);
        logArea.setWrapText(true);
    }

    private void configureTimerControls() {
        if (timerStatusLabel != null) {
            timerStatusLabel.setText("Timer: waiting for init");
        }
        if (nextHourButton != null) {
            nextHourButton.setDisable(true);
        }
        if (speedComboBox != null) {
            speedComboBox.setEditable(true);
            speedComboBox.setDisable(true);
            suppressSpeedSelectionUpdate = true;
            speedComboBox.getItems().setAll("1x", "2x", "4x", "8x", "16x", "32x");
            speedComboBox.setValue("1x");
            suppressSpeedSelectionUpdate = false;
            speedComboBox.valueProperty().addListener((obs, oldVal, newVal) -> onSpeedSelectionChanged(oldVal, newVal));
        }
        speedMultiplier = 1.0;
        lastSpeedSelection = "1x";
        updateTimerLabel();
    }

    private void onSpeedSelectionChanged(String oldValue, String newValue) {
        if (suppressSpeedSelectionUpdate) {
            return;
        }
        if (newValue == null || newValue.isBlank()) {
            revertSpeedSelection();
            return;
        }

        double parsed;
        try {
            parsed = parseSpeedInput(newValue);
        } catch (NumberFormatException ex) {
            appendLog("Invalid speed value: " + newValue + ". Keeping " + lastSpeedSelection);
            revertSpeedSelection();
            return;
        }

        if (parsed <= 0) {
            appendLog("Speed multiplier must be greater than 0");
            revertSpeedSelection();
            return;
        }

        lastSpeedSelection = formatSpeedDisplay(parsed);
        applySpeedMultiplier(parsed);
        updateSpeedComboDisplay(lastSpeedSelection);
    }

    private void revertSpeedSelection() {
        updateSpeedComboDisplay(lastSpeedSelection);
    }

    private void updateSpeedComboDisplay(String displayValue) {
        if (speedComboBox == null) {
            return;
        }
        suppressSpeedSelectionUpdate = true;
        speedComboBox.setValue(displayValue);
        suppressSpeedSelectionUpdate = false;
    }

    private double parseSpeedInput(String rawValue) {
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("x")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return Double.parseDouble(normalized);
    }

    private String formatSpeedDisplay(double multiplier) {
        if (Math.abs(multiplier - Math.rint(multiplier)) < 0.01) {
            return String.format("%.0fx", Math.rint(multiplier));
        }
        return String.format("%.2fx", multiplier);
    }

    private void setSpeedControlDisabled(boolean disabled) {
        if (speedComboBox != null) {
            speedComboBox.setDisable(disabled);
        }
    }

    private void applySpeedMultiplier(double multiplier) {
        if (multiplier <= 0) {
            appendLog("Speed multiplier must be greater than 0");
            return;
        }
        double previousMultiplier = speedMultiplier;
        double previousHourDuration = getHourDurationSeconds();
        speedMultiplier = multiplier;
        if (timerActive) {
            double newHourDuration = getHourDurationSeconds();
            if (previousMultiplier > 0 && previousHourDuration > 0) {
                double remainingFraction = Math.max(0.0, Math.min(1.0, remainingSeconds / previousHourDuration));
                remainingSeconds = newHourDuration * remainingFraction;
            } else {
                remainingSeconds = newHourDuration;
            }
            lastTickNanos = 0L;
        }
        updateTimerLabel();
    }

    private void startHourTimer() {
        stopHourTimer();
        timerActive = true;
        setSpeedControlDisabled(false);
        if (nextHourButton != null) {
            nextHourButton.setDisable(false);
        }
        lastKnownSimHour = api.getHoursElapsed();
        resetTimerCountdown();
        hourTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                handleTimerTick(now);
            }
        };
        hourTimer.start();
    }

    private void stopHourTimer() {
        timerActive = false;
        if (hourTimer != null) {
            hourTimer.stop();
            hourTimer = null;
        }
        lastTickNanos = 0L;
        if (nextHourButton != null) {
            nextHourButton.setDisable(true);
        }
        setSpeedControlDisabled(true);
        updateTimerLabel();
    }

    private void resetTimerCountdown() {
        remainingSeconds = getHourDurationSeconds();
        lastTickNanos = 0L;
        updateTimerLabel();
    }

    private void onHourAdvanced() {
        if (!timerActive) {
            return;
        }
        remainingSeconds = getHourDurationSeconds();
        updateTimerLabel();
    }

    private void updateTimerLabel() {
        if (timerStatusLabel == null) {
            return;
        }
        if (!timerActive) {
            timerStatusLabel.setText("Timer paused");
            updateClockDisplay();
            return;
        }
        timerStatusLabel.setText(String.format("Timer running @ %sx", formatSpeedLabel()));
        updateClockDisplay();
    }

    private void updateClockDisplay() {
        updateClockDisplay(lastKnownSimHour);
    }

    private void updateClockDisplay(int hoursElapsed) {
        if (clockLabel == null) {
            return;
        }
        double hourDuration = getHourDurationSeconds();
        double progressFraction = 0.0;
        if (timerActive && hourDuration > 0) {
            double elapsedCurrentHour = hourDuration - remainingSeconds;
            progressFraction = Math.max(0.0, Math.min(1.0, elapsedCurrentHour / hourDuration));
        }
        double totalSimSeconds = Math.max(0.0,
                hoursElapsed * SIM_HOUR_SECONDS + (progressFraction * SIM_HOUR_SECONDS));
        long totalSecondsFloor = (long) Math.floor(totalSimSeconds);
        long days = totalSecondsFloor / (24 * 3600);
        long remainder = totalSecondsFloor % (24 * 3600);
        long hours = remainder / 3600;
        remainder %= 3600;
        long minutes = remainder / 60;
        long seconds = remainder % 60;
        clockLabel.setText(String.format("Elapsed  |  %dd %02dh %02dm %02ds", days, hours, minutes, seconds));
    }

    private String formatSpeedLabel() {
        if (Math.abs(speedMultiplier - Math.rint(speedMultiplier)) < 0.01) {
            return String.format("%.0f", speedMultiplier);
        }
        return String.format("%.1f", speedMultiplier);
    }

    private double getHourDurationSeconds() {
        double effectiveMultiplier = speedMultiplier <= 0 ? 1.0 : speedMultiplier;
        return BASE_HOUR_SECONDS / effectiveMultiplier;
    }

    private void handleTimerTick(long now) {
        if (!timerActive) {
            return;
        }
        if (lastTickNanos == 0L) {
            lastTickNanos = now;
            return;
        }

        double deltaSeconds = (now - lastTickNanos) / 1_000_000_000.0;
        lastTickNanos = now;
        remainingSeconds -= deltaSeconds;

        while (timerActive && remainingSeconds <= 0) {
            remainingSeconds += getHourDurationSeconds();
            try {
                api.advanceHourAutomatically();
            } catch (Exception ex) {
                appendLog("Auto hour failed: " + ex.getMessage());
                stopHourTimer();
                break;
            }
        }

        updateTimerLabel();
    }

    private void hydrateLogTail() {
        List<String> history = api.recentLogEntries();
        if (!history.isEmpty()) {
            logArea.clear();
            history.forEach(line -> {
                // Add red warning emoji for ALERT lines
                if (line.contains("[ALERT]")) {
                    line = "🔴 " + line;
                }
                logArea.appendText(line + System.lineSeparator());
            });
        }
    }

    private void applyState(Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            return;
        }

    summaryLabel.setText(String.format("%d/%d plants alive",
        ((Number) state.getOrDefault("alivePlants", 0)).intValue(),
        ((Number) state.getOrDefault("totalPlants", 0)).intValue()));
    int hoursElapsed = api.getHoursElapsed();
    int derivedDay = Math.max(0, hoursElapsed) / 24;
    dayLabel.setText(String.format("Day %d", derivedDay));
    if (hoursElapsed != lastKnownSimHour) {
        lastKnownSimHour = hoursElapsed;
        onHourAdvanced();
    }
    updateClockDisplay(hoursElapsed);

        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) state.getOrDefault("plants", new ArrayList<>());
        @SuppressWarnings("unchecked")
        List<String> types = (List<String>) state.getOrDefault("plantTypes", new ArrayList<>());
        @SuppressWarnings("unchecked")
        List<Double> health = (List<Double>) state.getOrDefault("plantHealth", new ArrayList<>());
        @SuppressWarnings("unchecked")
        List<Integer> water = (List<Integer>) state.getOrDefault("plantWater", new ArrayList<>());
        @SuppressWarnings("unchecked")
        List<Integer> waterReq = (List<Integer>) state.getOrDefault("plantWaterRequirement", new ArrayList<>());
        @SuppressWarnings("unchecked")
        List<Boolean> alive = (List<Boolean>) state.getOrDefault("plantAlive", new ArrayList<>());
        @SuppressWarnings("unchecked")
        List<Boolean> infested = (List<Boolean>) state.getOrDefault("plantInfested", new ArrayList<>());

        // update table
        plantRows.clear();
        for (int i = 0; i < names.size(); i++) {
            String plantName = names.get(i);
            String type = safeGet(types, i);
            double healthValue = i < health.size() ? health.get(i) : 0.0;
            int waterValue = i < water.size() ? water.get(i) : 0;
            boolean aliveFlag = i < alive.size() && Boolean.TRUE.equals(alive.get(i));
            boolean infestedFlag = i < infested.size() && Boolean.TRUE.equals(infested.get(i));
            plantRows.add(new PlantStatusRow(plantName, type, healthValue, waterValue, aliveFlag, infestedFlag));
        }
        
        // update garden map grid
        updateGardenMap(names, types, health, water, waterReq, alive);
    }
    
    // rebuild the garden map grid with plant tiles
    private void updateGardenMap(List<String> names, List<String> types, List<Double> health,
                                   List<Integer> water, List<Integer> waterReq, List<Boolean> alive) {
        gardenGrid.getChildren().clear();
        
        // limit to reasonable number for fixed view - max 40 plants recommended
        int maxPlants = Math.min(names.size(), 40);
        int plantsPerRow = 6;  // 6 columns for better fit in fixed container
        
        for (int i = 0; i < maxPlants; i++) {
            String name = names.get(i);
            String type = safeGet(types, i);
            double healthValue = i < health.size() ? health.get(i) : 0.0;
            int waterValue = i < water.size() ? water.get(i) : 0;
            int waterReqValue = i < waterReq.size() ? waterReq.get(i) : 10;
            boolean aliveFlag = i < alive.size() && Boolean.TRUE.equals(alive.get(i));
            
            StackPane tile = PlantVisualizer.createPlantTile(name, type, healthValue, 
                                                               waterValue, waterReqValue, aliveFlag);
            
            int row = i / plantsPerRow;
            int col = i % plantsPerRow;
            gardenGrid.add(tile, col, row);
        }
        
        // warn if too many plants for optimal display
        if (names.size() > maxPlants) {
            System.err.println("WARNING: Garden has " + names.size() + " plants. Only showing first " + maxPlants + " for optimal fixed-size display.");
        }
    }

    private String safeGet(List<String> list, int index) {
        return index < list.size() ? list.get(index) : "-";
    }

    private void appendLog(String line) {
        // Add red warning emoji for ALERT lines
        if (line.contains("[ALERT]")) {
            line = "🔴 " + line;
        }
        logArea.appendText(line + System.lineSeparator());
        trimLogArea();
    }

    private void trimLogArea() {
        String text = logArea.getText();
        String[] lines = text.split("\\R");
        if (lines.length <= 400) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = lines.length - 400; i < lines.length; i++) {
            builder.append(lines[i]).append(System.lineSeparator());
        }
        logArea.setText(builder.toString());
    }

    private void updateRainGuidance() {
        rainGuidanceLabel.setText(String.format("Rain range: %d - %d units", api.getMinWaterRequirement(), api.getMaxWaterRequirement()));
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) rainSpinner.getValueFactory();
        factory.setMin(api.getMinWaterRequirement());
        factory.setMax(api.getMaxWaterRequirement());
        factory.setValue(api.getMinWaterRequirement());
    }

    public static class PlantStatusRow {
        private final SimpleStringProperty name;
        private final SimpleStringProperty type;
        private final SimpleStringProperty health;
        private final SimpleStringProperty water;
        private final SimpleStringProperty status;

        public PlantStatusRow(String name, String type, double health, int water, boolean alive, boolean infested) {
            this.name = new SimpleStringProperty(name);
            this.type = new SimpleStringProperty(type);
            this.health = new SimpleStringProperty(String.format("%.1f%%", health));
            this.water = new SimpleStringProperty(String.valueOf(water));
            String tag = alive ? (infested ? "ALERT" : "Stable") : "DEAD";
            this.status = new SimpleStringProperty(tag);
        }

        public String getName() {
            return name.get();
        }

        public String getType() {
            return type.get();
        }

        public String getHealth() {
            return health.get();
        }

        public String getWater() {
            return water.get();
        }

        public String getStatus() {
            return status.get();
        }
    }
}
