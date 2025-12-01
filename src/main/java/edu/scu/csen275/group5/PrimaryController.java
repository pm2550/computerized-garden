package edu.scu.csen275.group5;

import edu.scu.csen275.group5.control.GardenSimulationAPI;
import edu.scu.csen275.group5.ui.PlantVisualizer;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
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
import java.util.Map;

public class PrimaryController {

    @FXML
    private Label summaryLabel;

    @FXML
    private Label dayLabel;

    @FXML
    private Label clockLabel;

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

    private final ObservableList<PlantStatusRow> plantRows = FXCollections.observableArrayList();
    private final GardenSimulationAPI api = new GardenSimulationAPI();

    @FXML
    public void initialize() {
        configureTable();
        configureSpinners();
        configureLogArea();
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
        dayLabel.setText("Day " + ((Number) state.getOrDefault("day", 0)).intValue());
        clockLabel.setText("Sim hours: " + api.getHoursElapsed());

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
