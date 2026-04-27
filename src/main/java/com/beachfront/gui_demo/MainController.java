package com.beachfront.gui_demo;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    // ─── Navigation ────────────────────────────────────────────────────────────
    @FXML private ToggleButton btnProjects, btnWorkers, btnPayroll, btnHistory;
    @FXML private StackPane contentStack;
    @FXML private VBox projectsPane, workersPane, payrollPane, historyPane;

    // ─── Projects ──────────────────────────────────────────────────────────────
    @FXML private TableView<Project> projectTable;
    @FXML private TableColumn<Project, String> colProjId, colProjName, colProjClient,
            colProjLocation, colProjStart, colProjEnd, colProjStatus, colProjActions;
    @FXML private TextField projectSearch;

    // ─── Workers ───────────────────────────────────────────────────────────────
    @FXML private TableView<Worker> workerTable;
    @FXML private TableColumn<Worker, String> colWId, colWName, colWPosition,
            colWRate, colWContact, colWAddress, colWStatus, colWActions;
    @FXML private TextField workerSearch;
    @FXML private CheckBox showInactiveWorkers;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

    }
}
