package edu.scu.csen275.group5;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

import java.io.IOException;

public class SecondaryController {

    @FXML
    private TextArea helpArea;

    @FXML
    public void initialize() {
        helpArea.setText(buildHelpText());
        helpArea.setWrapText(true);
        helpArea.setEditable(false);
    }

    @FXML
    private void switchToPrimary() throws IOException {
        App.setRoot("primary");
    }

    private String buildHelpText() {
    return "Computerized Garden Help / Log Guide\n" +
                "══════════════════════════════════════\n\n" +

                "Buttons at a glance\n" +
                "  • Initialize Garden – reads garden_config.txt, seeds plants, and unlocks every other control. Mandatory first step.\n" +
                "  • Rain / Temperature / Parasite – call the GardenSimulationAPI handlers directly; each click advances one simulated hour and writes [RAIN]/[TEMPERATURE]/[PARASITE].\n" +
                "  • Snapshot – calls getState(), dumps a [STATE] block plus per-plant lines for inspection. Use it whenever you need a checkpoint.\n" +
                "  • Next Hour – manual advancement with zero random weather/parasite noise. Helpful before turning in a deterministic trace.\n" +
                "  • Auto Weather & Events toggle – when on, AnimationTimer keeps marching slices with natural rain, diurnal temps, and parasite drift. Turn off for hands-on demos.\n" +
                "  • Time Speed menu – pick 1× / 2× / 4× (or custom) to control how many real seconds equal one simulated hour; countdown label mirrors this.\n" +
                "  • Help / Log Guide – opens this note.\n\n" +

                "Log reader basics\n" +
                "  • File: log.txt at the project root. The UI tail shows ~250 recent lines; open the file in an editor for full history.\n" +
                "  • Format: YYYY-MM-DD HH:MM:SS [TAG] Message.\n" +
                "  • Tags: [INIT], [PLANT], [RAIN], [TEMPERATURE], [PARASITE], [DAY], [STATE], [PLANT_STATUS], [ALERT]. Red alerts mean pests are chewing on something.\n\n" +

                "Operating tips\n" +
                "  • Take at least one Snapshot per 24 simulated hours to capture alive counts and soil stats for later review.\n" +
                "  • Mix Auto mode for long burns with manual buttons when you want to inject specific weather. Switching off Auto pauses the timer immediately.\n" +
                "  • Need precise values? Open the Developer Console (Ctrl+D) for spinners that call the same handlers with exact amounts.\n" +
                "  • If the log tail stops moving, scroll to the bottom or reopen log.txt—writes continue even when the UI pane hits its cap.\n\n" +

                "Resetting\n" +
                "  • Re-click Initialize Garden anytime you want a clean slate. It rebuilds plants, resets timers, and clears auto progression state.\n\n" +

                "Legacy log cheat sheet\n" +
                "  • Log file lives at project root as log.txt. Format stays TIMESTAMP [TAG] Message.\n" +
                "  • Tag reference:\n" +
                "      [INIT]          Garden initialization\n" +
                "      [PLANT]         Plant seeding\n" +
                "      [RAIN]          Rainfall events\n" +
                "      [TEMPERATURE]   Temperature changes\n" +
                "      [PARASITE]      Parasite release\n" +
                "      [DAY]           Day/hour progression\n" +
                "      [STATE]         Summary (alive/total)\n" +
                "      [PLANT_STATUS]  Detailed per-plant info\n" +
                "      [ALERT]         🔴 Plants under attack\n" +
                "  • Snapshot button reminder:\n" +
                "      – Writes one [STATE] summary plus every plant's health%, water, alive/dead flag.\n" +
                "      – Trigger it at least once per simulated day so later comparisons stay easy.\n" +
                "  • Notes:\n" +
                "      – UI tail is short; open log.txt for full context.\n" +
                "      – Timing controls: auto timer maps real seconds to sim hours, Next Hour skips instantly, Time Speed values (1× default, 2×–32× boost or custom) adjust cadence.\n";
    }
}