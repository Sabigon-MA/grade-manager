module com.grademanager {
    requires javafx.controls;
    requires javafx.fxml;
    opens com.grademanager to javafx.fxml;
    exports com.grademanager;
}
