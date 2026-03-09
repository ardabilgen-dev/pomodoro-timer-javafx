module com.arda.demo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.desktop;


    opens com.arda.demo to javafx.fxml;
    exports com.arda.demo;
}