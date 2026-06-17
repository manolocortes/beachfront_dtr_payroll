module com.payroll {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires kernel;
    requires layout;
    requires io;

    opens com.payroll to javafx.fxml;
    opens com.payroll.controller to javafx.fxml;
    opens com.payroll.model to javafx.base;

    exports com.payroll;
    exports com.payroll.controller;
    exports com.payroll.model;
    exports com.payroll.dao;
    exports com.payroll.service;
}
