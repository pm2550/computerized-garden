# Computerized Garden Control Room

This module delivers the "control room" portion of the Computerized Garden project: a JavaFX dashboard, a public automation API, and a persistent logging layer that lets human gardeners and automated scripts cooperate.

## Highlights

- **JavaFX Control Room** – the `primary.fxml` scene surfaces plant health, live logs, and buttons for rain, temperature, parasite, and state snapshot events.
- **`GardenSimulationAPI`** – the automation surface expected by grading scripts. It seeds the garden from `garden_config.txt`, enforces input bounds, advances the simulation clock, and keeps observers in sync.
- **Structured Logging** – every event writes to `log.txt` using the format `YYYY-MM-DD HH:MM:SS [TAG] Message`. The UI streams the last ~250 lines while the full file is suitable for grading audits.

## Running the UI

```bash
mvn clean javafx:run
```

1. Click **Initialize Garden** to load templates from `garden_config.txt` and seed the garden.
2. Use the Rain / Temperature / Parasite controls to simulate the hourly events described in the spec. Each call advances the clock by one simulated day/hour.
3. Press **Snapshot** anytime to force a logged state summary.
4. The **Help / Log Guide** button opens a short reference explaining `log.txt` expectations.

## API

```java
GardenSimulationAPI api = new GardenSimulationAPI();
api.initializeGarden();
Map<String, Object> definition = api.getPlants();
api.rain(12);
api.temperature(78);
api.parasite("aphids");
api.getState(); // writes snapshot to log and notifies observers
```

Key behaviors:

- `rain(int amount)` clamps the amount between the smallest and largest `waterRequirement` reported by `getPlants()`.
- `temperature(int degreesF)` validates the `40–120°F` range.
- `parasite(String name)` normalizes identifiers (lower-case, underscores) before releasing pests.
- After every environmental call the API advances the simulation day (one hour in the monitoring harness) and logs a `[DAY]` entry.

### Observers & Logs

The UI registers as a `SimulationObserver` to receive both state snapshots and streaming log lines. Scripts may do the same:

```java
api.addObserver(new SimulationObserver() {
    public void onStateChanged(Map<String, Object> state) { /* react */ }
    public void onLogAppended(String entry) { System.out.println(entry); }
});
```

`GardenLogger` keeps the tail buffer and ensures `log.txt` is created automatically (or reused) at the project root.

## Configuration

`garden_config.txt` now accepts an `instances` field per plant template:

```
[Rose]
instances=3
waterRequirement=10
...
```

During `initializeGarden()` the API seeds `instances` copies of each template (falling back to two of each default type if the config is missing).

## Testing

Automated tests (JUnit 5) cover the API contract at a basic level. Run them with:

```bash
mvn test
```

They create temporary config/log files, seed plants, and verify that environment calls stay within expected bounds without mutating the real `log.txt`.

## Logging Manual (also shown in the Help view)

- File: `log.txt` (project root).
- Format: `TIMESTAMP [TAG] message`.
- Important tags: `INIT`, `PLANT`, `RAIN`, `TEMPERATURE`, `PARASITE`, `DAY`, `STATE`.
- Use the UI Snapshot button (or call `getState()`) to inject a `[STATE]` summary every 24 hours of simulation time.
- `ALERT` status in the plant table hints at living plants fighting active parasites—cross-check with log entries.

