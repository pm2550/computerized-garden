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
        return "Computerized Garden Log Guide\n" +
                "═════════════════════════════════\n\n" +
                
                "📄 Log File: log.txt (project root)\n" +
                "Format: TIMESTAMP [TAG] MESSAGE\n\n" +
                
                "🏷️ Log Tags:\n" +
                "  [INIT]          Garden initialization\n" +
                "  [PLANT]         Plant seeding\n" +
                "  [RAIN]          Rainfall events\n" +
                "  [TEMPERATURE]   Temperature changes\n" +
                "  [PARASITE]      Parasite release\n" +
                "  [DAY]           Day/hour progression\n" +
                "  [STATE]         Summary (alive/total)\n" +
                "  [PLANT_STATUS]  Detailed per-plant info\n" +
                "  [ALERT]         🔴 Plants under attack\n\n" +
                
                "📊 Snapshot Button:\n" +
                "  • Logs summary + each plant's status\n" +
                "  • Shows: health%, water, alive/dead\n" +
                "  • Use every 24 sim-hours as required\n\n" +
                
                "📝 Notes:\n" +
                "  • UI shows recent lines only\n" +
                "  • Open log.txt for full history\n\n" +
                "⏱️ Timer Controls:\n" +
                "  • Auto timer advances hours using real seconds\n" +
                "  • 'Next Hour' skips immediately when needed\n" +
                "  • Speed buttons (1x default, 2x–32x boost) or custom entry adjust pace\n";
    }
}