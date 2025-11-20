module edu.scu.csen275.group5 {
    requires javafx.controls;
    requires javafx.fxml;

    opens edu.scu.csen275.group5 to javafx.fxml;
    exports edu.scu.csen275.group5;
}
