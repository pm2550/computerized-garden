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
                "--------------------------------\n" +
                "• File: log.txt (project root).\n" +
                "• Every API call writes TIMESTAMP [TAG] MESSAGE.\n" +
                "• Tags: INIT, PLANT, RAIN, TEMPERATURE, PARASITE, DAY, STATE.\n" +
                "• Use the control room ‘Snapshot’ button to dump a summarized state entry.\n" +
                "• Lines flagged ALERT indicate living plants battling parasites.\n" +
                "• The UI shows the most recent lines; open the file for the full history.\n" +
                "• Keep log.txt with submissions so the TA can audit your run.";
    }
}