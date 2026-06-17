package com.payroll.controller;

import com.payroll.dao.*;
import com.payroll.model.*;
import com.payroll.service.PayrollPdfService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.StringConverter;

import java.io.File;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainController implements Initializable {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    // -- Nav ---------------------------------------------------------------
    @FXML private ToggleButton btnProjects, btnWorkers, btnPayroll, btnHistory;
    @FXML private StackPane contentStack;
    @FXML private VBox projectsPane, workersPane, payrollPane, historyPane;

    // -- Projects --------------------------------------------------------------
    @FXML private TableView<Project> projectTable;
    @FXML private TableColumn<Project,String> colProjId, colProjName, colProjClient,
            colProjLocation, colProjStart, colProjEnd, colProjStatus, colProjActions;
    @FXML private TextField projectSearch;

    // -- Workers ---------------------------------------------------------------
    @FXML private TableView<Worker> workerTable;
    @FXML private TableColumn<Worker,String> colWId, colWName, colWPosition,
            colWRate, colWStatus, colWActions;
    @FXML private TextField workerSearch;
    @FXML private CheckBox showInactiveWorkers;

    // -- Payroll ---------------------------------------------------------------
    @FXML private ComboBox<Project> payrollProject;
    @FXML private DatePicker payrollPeriodStart, payrollPeriodEnd, payrollPayDate;
    @FXML private TextField payrollPreparedBy, payrollApprovedBy;
    @FXML private VBox attendanceContainer;
    @FXML private Label totalNetLabel, workerCountLabel, emptyStateLabel;

    // -- History ---------------------------------------------------------------
    @FXML private TableView<Payroll> historyTable;
    @FXML private TableColumn<Payroll,String> colHisId, colHisProject, colHisPeriod,
            colHisPayDate, colHisTotal, colHisPrepared, colHisStatus, colHisActions;
    @FXML private ComboBox<String> historyProjectFilter;
    @FXML private TextField historySearch;

    // -- Status --------------------------------------------------------------
    @FXML private Label statusLabel, dbPathLabel;

    // -- State -----------------------------------------------------------------
    private final WorkerDAO  workerDAO  = new WorkerDAO();
    private final ProjectDAO projectDAO = new ProjectDAO();
    private final PayrollDAO payrollDAO = new PayrollDAO();
    private final ProjectWorkerDAO projectWorkerDAO = new ProjectWorkerDAO();
    private final PayrollPdfService pdfService = new PayrollPdfService();

    private ObservableList<Worker>  allWorkers  = FXCollections.observableArrayList();
    private ObservableList<Project> allProjects = FXCollections.observableArrayList();
    private ObservableList<Payroll> allPayrolls = FXCollections.observableArrayList();
    private final List<PayrollEntry> currentEntries = new ArrayList<>();
    private Payroll editingPayroll = null;
    private final ToggleGroup navGroup = new ToggleGroup();

    // -- Init --------------------------------------------------------------
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        btnProjects.setToggleGroup(navGroup);
        btnWorkers.setToggleGroup(navGroup);
        btnPayroll.setToggleGroup(navGroup);
        btnHistory.setToggleGroup(navGroup);
        setupProjectTable(); setupWorkerTable(); setupHistoryTable();
        dbPathLabel.setText("Database: " + new File("payroll.db").getAbsolutePath());
        loadAll();
        showProjects();
    }

    // -- Navigation --------------------------------------------------------
    @FXML public void showProjects()  { switchPane(projectsPane);  refreshProjects(); }
    @FXML public void showWorkers()   { switchPane(workersPane);   refreshWorkers(); }
    @FXML public void showPayroll()   { switchPane(payrollPane);   prepareNewPayroll(); }
    @FXML public void showHistory()   { switchPane(historyPane);   refreshHistory(); }

    private void switchPane(VBox t) {
        for (VBox p : List.of(projectsPane,workersPane,payrollPane,historyPane))
            p.setVisible(p == t);
    }

    private void loadAll() {
        try {
            allWorkers.setAll(workerDAO.findAll());
            allProjects.setAll(projectDAO.findAll());
            allPayrolls.setAll(payrollDAO.findAll());
        } catch (SQLException e) { showError("Database Error", e.getMessage()); }
    }

    // -- Projects --------------------------------------------------------------
    private void setupProjectTable() {
        colProjId.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getId())));
        colProjName.setCellValueFactory(d -> d.getValue().nameProperty());
        colProjClient.setCellValueFactory(d -> d.getValue().clientProperty());
        colProjLocation.setCellValueFactory(d -> d.getValue().locationProperty());
        colProjStart.setCellValueFactory(d -> new SimpleStringProperty(fmt(d.getValue().getStartDate())));
        colProjEnd.setCellValueFactory(d -> new SimpleStringProperty(fmt(d.getValue().getEndDate())));
        colProjStatus.setCellValueFactory(d -> d.getValue().statusProperty());
        colProjStatus.setCellFactory(col -> new BadgeCell<>());
        colProjActions.setCellFactory(col -> new ProjectActionCell(
                p -> openProjectDialog(p), p -> deleteProject(p), p -> openAssignWorkersDialog(p)));
    }

    private void refreshProjects() {
        FilteredList<Project> fl = new FilteredList<>(allProjects);
        projectSearch.textProperty().addListener((o,a,b) ->
                fl.setPredicate(p -> b==null||b.isEmpty()
                        ||p.getName().toLowerCase().contains(b.toLowerCase())
                        ||(p.getClient()!=null && p.getClient().toLowerCase().contains(b.toLowerCase()))));
        projectTable.setItems(fl);
        projectTable.setPlaceholder(new Label("No projects yet. Click \"New Project\" to add your first one."));
    }

    @FXML private void openNewProject() { openProjectDialog(null); }

    private void openProjectDialog(Project existing) {
        boolean isNew = existing == null;
        Project p = isNew ? new Project() : existing;
        Dialog<ButtonType> dlg = dlg(isNew ? "New Project" : "Edit Project");
        GridPane g = grid();
        TextField fName = tf(p.getName()), fClient = tf(p.getClient()), fLoc = tf(p.getLocation());
        DatePicker dpS = new DatePicker(p.getStartDate()), dpE = new DatePicker(p.getEndDate());
        ComboBox<String> cbSt = new ComboBox<>(FXCollections.observableArrayList("ACTIVE","COMPLETED","ON_HOLD"));
        cbSt.setValue(nvl(p.getStatus(),"ACTIVE"));
        cbSt.setMaxWidth(Double.MAX_VALUE);
        TextArea fDesc = new TextArea(nvl(p.getDescription())); fDesc.setPrefRowCount(2);
        fDesc.setPromptText("Optional notes about this project…");
        g.addRow(0,lbl("Project Name*"),fName);
        g.addRow(1,lbl("Client"),fClient);
        g.addRow(2,lbl("Location"),fLoc);
        g.addRow(3,lbl("Start Date"),dpS);
        g.addRow(4,lbl("End Date"),dpE);
        g.addRow(5,lbl("Status"),cbSt);
        g.addRow(6,lbl("Notes"),fDesc);
        dlg.getDialogPane().setContent(g);
        Platform_runLaterFocus(fName);
        dlg.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            if (fName.getText().isBlank()) { showError("Missing Information","Please enter a project name."); return; }
            p.setName(fName.getText().trim()); p.setClient(fClient.getText().trim());
            p.setLocation(fLoc.getText().trim()); p.setStartDate(dpS.getValue());
            p.setEndDate(dpE.getValue()); p.setStatus(cbSt.getValue()); p.setDescription(fDesc.getText().trim());
            try { if (isNew) { projectDAO.insert(p); allProjects.add(p); } else projectDAO.update(p);
                  projectTable.refresh(); refreshPayrollProjectCombo();
                  setStatus("Project saved: " + p.getName());
            } catch (SQLException e) { showError("Could Not Save Project", e.getMessage()); }
        });
    }

    private void deleteProject(Project p) {
        if (!confirm("Delete project \"" + p.getName() + "\"?\n\nThis cannot be undone.")) return;
        try { projectDAO.delete(p.getId()); allProjects.remove(p); setStatus("Project deleted."); }
        catch (SQLException e) { showError("Could Not Delete Project", e.getMessage()); }
    }

    /**
     * Lets the user pick which workers are part of this project's regular crew.
     * Assigned workers are auto-suggested when creating a new payroll for this project.
     */
    private void openAssignWorkersDialog(Project p) {
        Set<Integer> assigned;
        try { assigned = projectWorkerDAO.findWorkerIdsForProject(p.getId()); }
        catch (SQLException e) { showError("Could Not Load Assignments", e.getMessage()); return; }

        List<Worker> all = allWorkers.stream()
                .sorted(Comparator.comparing(Worker::isActive, Comparator.reverseOrder())
                        .thenComparing(Worker::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (all.isEmpty()) {
            showError("No Workers Available", "Add workers in the Workers tab first.");
            return;
        }

        Dialog<ButtonType> dlg = dlg("Assign Workers \u2014 " + p.getName());
        dlg.setHeaderText("Choose the regular crew for \"" + p.getName() + "\".\nThese workers will be suggested automatically when you create a payroll for this project.");

        TextField search = new TextField();
        search.setPromptText("Search workers by name or position\u2026");
        search.getStyleClass().add("search-field");

        Map<Integer, CheckBox> checkboxes = new LinkedHashMap<>();
        VBox listBox = new VBox(2);
        for (Worker w : all) {
            CheckBox cb = new CheckBox(w.getName() + "  \u2014  " + nvl(w.getPosition(),"No position")
                    + "  (\u20b1" + String.format("%,.2f", w.getDailyRate()) + "/day)"
                    + (w.isActive() ? "" : "  [INACTIVE]"));
            cb.setSelected(assigned.contains(w.getId()));
            cb.getStyleClass().add("checklist-item");
            if (!w.isActive()) cb.getStyleClass().add("checklist-item-inactive");
            checkboxes.put(w.getId(), cb);
            listBox.getChildren().add(cb);
        }

        ScrollPane scroll = new ScrollPane(listBox);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(440, 360);
        scroll.getStyleClass().add("checklist-scroll");

        // Live filter
        search.textProperty().addListener((o,a,b) -> {
            String q = b == null ? "" : b.toLowerCase().trim();
            for (Worker w : all) {
                CheckBox cb = checkboxes.get(w.getId());
                boolean match = q.isEmpty()
                        || w.getName().toLowerCase().contains(q)
                        || (w.getPosition()!=null && w.getPosition().toLowerCase().contains(q));
                cb.setVisible(match); cb.setManaged(match);
            }
        });

        Label countLabel = new Label();
        Runnable updateCount = () -> {
            long c = checkboxes.values().stream().filter(CheckBox::isSelected).count();
            countLabel.setText(c + " of " + all.size() + " selected");
        };
        checkboxes.values().forEach(cb -> cb.selectedProperty().addListener((o,a,b) -> updateCount.run()));
        updateCount.run();

        Button selectAllBtn = new Button("Select All");
        selectAllBtn.getStyleClass().add("btn-link");
        selectAllBtn.setOnAction(e -> checkboxes.values().forEach(cb -> { if (cb.isVisible()) cb.setSelected(true); }));

        Button selectNoneBtn = new Button("Select None");
        selectNoneBtn.getStyleClass().add("btn-link");
        selectNoneBtn.setOnAction(e -> checkboxes.values().forEach(cb -> { if (cb.isVisible()) cb.setSelected(false); }));

        Button activeOnlyBtn = new Button("Select Active Workers Only");
        activeOnlyBtn.getStyleClass().add("btn-link");
        activeOnlyBtn.setOnAction(e -> all.forEach(w -> checkboxes.get(w.getId()).setSelected(w.isActive())));

        HBox quickActions = new HBox(6, selectAllBtn, selectNoneBtn, activeOnlyBtn);
        quickActions.setAlignment(Pos.CENTER_LEFT);

        HBox topRow = new HBox(10, search, countLabel);
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);

        VBox content = new VBox(10, topRow, quickActions, scroll);
        content.setPadding(new Insets(14));
        content.setPrefWidth(480);
        dlg.getDialogPane().setContent(content);

        dlg.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            Set<Integer> selected = new HashSet<>();
            checkboxes.forEach((id, cb) -> { if (cb.isSelected()) selected.add(id); });
            try {
                projectWorkerDAO.setAssignedWorkers(p.getId(), selected);
                setStatus("Updated crew for " + p.getName() + ": " + selected.size() + " worker(s) assigned.");
            } catch (SQLException e) { showError("Could Not Save Assignments", e.getMessage()); }
        });
    }

    // -- Workers ---------------------------------------------------------------
    private void setupWorkerTable() {
        colWId.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getId())));
        colWName.setCellValueFactory(d -> d.getValue().nameProperty());
        colWPosition.setCellValueFactory(d -> d.getValue().positionProperty());
        colWRate.setCellValueFactory(d -> new SimpleStringProperty(String.format("\u20b1 %,.2f", d.getValue().getDailyRate())));
        colWStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isActive()?"ACTIVE":"INACTIVE"));
        colWStatus.setCellFactory(col -> new BadgeCell<>());
        colWActions.setCellFactory(col -> new ActionCell<>(w -> openWorkerDialog(w), w -> deleteWorker(w)));
    }

    private void refreshWorkers() {
        FilteredList<Worker> fl = new FilteredList<>(allWorkers);
        Runnable f = () -> fl.setPredicate(w -> {
            boolean ok = showInactiveWorkers.isSelected() || w.isActive();
            String q = workerSearch.getText();
            return ok && (q==null||q.isEmpty()||w.getName().toLowerCase().contains(q.toLowerCase())
                    ||(w.getPosition()!=null && w.getPosition().toLowerCase().contains(q.toLowerCase())));
        });
        workerSearch.textProperty().addListener((o,a,b)->f.run());
        showInactiveWorkers.selectedProperty().addListener((o,a,b)->f.run());
        f.run(); workerTable.setItems(fl);
        workerTable.setPlaceholder(new Label("No workers yet. Click \"Add Worker\" to add your first one."));
    }

    @FXML private void openNewWorker() { openWorkerDialog(null); }

    private void openWorkerDialog(Worker existing) {
        boolean isNew = existing == null;
        Worker w = isNew ? new Worker() : existing;
        Dialog<ButtonType> dlg = dlg(isNew ? "Add Worker" : "Edit Worker");
        GridPane g = grid();
        TextField fName=tf(w.getName()), fPos=tf(w.getPosition()),
                  fRate=tf(w.getDailyRate()==0?"":String.valueOf(w.getDailyRate()));
        CheckBox cbAct = new CheckBox("Active (available for payroll)"); cbAct.setSelected(isNew||w.isActive());
        g.addRow(0,lbl("Full Name*"),fName);
        g.addRow(1,lbl("Position"),fPos);
        g.addRow(2,lbl("Daily Rate (\u20b1)*"),fRate);
        g.addRow(3,new Label(""),cbAct);
        dlg.getDialogPane().setContent(g);
        Platform_runLaterFocus(fName);
        dlg.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            if (fName.getText().isBlank()) { showError("Missing Information","Please enter the worker's name."); return; }
            double rate;
            try { rate = Double.parseDouble(fRate.getText().trim()); }
            catch (NumberFormatException ex) { showError("Invalid Rate","Daily rate must be a number, e.g. 650.00"); return; }
            if (rate < 0) { showError("Invalid Rate","Daily rate cannot be negative."); return; }
            w.setName(fName.getText().trim()); w.setPosition(fPos.getText().trim());
            w.setDailyRate(rate); w.setActive(cbAct.isSelected());
            try { if (isNew) { workerDAO.insert(w); allWorkers.add(w); } else workerDAO.update(w);
                  workerTable.refresh(); setStatus("Worker saved: " + w.getName());
            } catch (SQLException e) { showError("Could Not Save Worker", e.getMessage()); }
        });
    }

    private void deleteWorker(Worker w) {
        if (!confirm("Delete worker \"" + w.getName() + "\"?\n\nThis cannot be undone.")) return;
        try { workerDAO.delete(w.getId()); allWorkers.remove(w); setStatus("Worker deleted."); }
        catch (SQLException e) { showError("Could Not Delete Worker", e.getMessage()); }
    }

    // -- Payroll form ----------------------------------------------------------
    private void prepareNewPayroll() {
        editingPayroll = null;
        currentEntries.clear();
        attendanceContainer.getChildren().clear();
        payrollPeriodStart.setValue(LocalDate.now().withDayOfMonth(1));
        payrollPeriodEnd.setValue(LocalDate.now());
        payrollPayDate.setValue(LocalDate.now());
        payrollPreparedBy.clear(); payrollApprovedBy.clear();
        refreshPayrollProjectCombo(); refreshTotals();
        updateEmptyState();
    }

    private void refreshPayrollProjectCombo() {
        payrollProject.setItems(FXCollections.observableArrayList(
                allProjects.stream().filter(p -> "ACTIVE".equals(p.getStatus())).toList()));
        payrollProject.setConverter(new StringConverter<>() {
            @Override public String toString(Project p)   { return p==null?"":p.getName(); }
            @Override public Project fromString(String s) { return null; }
        });
        if (allProjects.stream().noneMatch(p -> "ACTIVE".equals(p.getStatus())))
            payrollProject.setPromptText("No active projects — add one in the Projects tab");
        else
            payrollProject.setPromptText("Select a project…");

        // Avoid stacking duplicate listeners across repeated refreshes
        payrollProject.getSelectionModel().selectedItemProperty().removeListener(projectSelectionListener);
        payrollProject.getSelectionModel().selectedItemProperty().addListener(projectSelectionListener);
    }

    /**
     * When the user picks a project for a brand-new (empty) payroll, offer to
     * auto-load that project's assigned crew so they don't have to add workers manually.
     */
    private final javafx.beans.value.ChangeListener<Project> projectSelectionListener = (obs, oldVal, newVal) -> {
        if (newVal == null || editingPayroll != null || !currentEntries.isEmpty()) return;
        try {
            List<Worker> crew = projectWorkerDAO.findWorkersForProject(newVal.getId())
                    .stream().filter(Worker::isActive).toList();
            if (crew.isEmpty()) return;
            if (confirm("\"" + newVal.getName() + "\" has " + crew.size() + " worker(s) assigned to it.\n\n"
                    + "Add them to this payroll now? You can still add, remove, or edit workers afterwards.")) {
                crew.forEach(this::addEntry);
                setStatus("Loaded " + crew.size() + " worker(s) from " + newVal.getName() + "'s assigned crew.");
            }
        } catch (SQLException e) { showError("Could Not Load Crew", e.getMessage()); }
    };

    /**
     * Checklist dialog letting the user select multiple workers at once to add
     * to the current payroll. Supports search filtering and select-all/none.
     */
    @FXML private void addWorkerToPayroll() {
        List<Worker> available = allWorkers.stream()
                .filter(w -> currentEntries.stream().noneMatch(e -> e.getWorkerId()==w.getId()))
                .sorted(Comparator.comparing(Worker::isActive, Comparator.reverseOrder())
                        .thenComparing(Worker::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (available.isEmpty()) {
            if (allWorkers.isEmpty()) showError("No Workers Available","Add workers in the Workers tab first.");
            else showInfo("All Workers Added","Every worker is already on this payroll.");
            return;
        }

        Dialog<ButtonType> dlg = dlg("Add Workers to Payroll");
        dlg.setHeaderText("Select one or more workers to add to this payroll.");

        TextField search = new TextField();
        search.setPromptText("Search by name or position\u2026");
        search.getStyleClass().add("search-field");

        Map<Integer, CheckBox> checkboxes = new LinkedHashMap<>();
        VBox listBox = new VBox(2);
        for (Worker w : available) {
            CheckBox cb = new CheckBox(w.getName() + "  \u2014  " + nvl(w.getPosition(),"No position")
                    + "  (\u20b1" + String.format("%,.2f", w.getDailyRate()) + "/day)"
                    + (w.isActive() ? "" : "  [INACTIVE]"));
            cb.getStyleClass().add("checklist-item");
            if (!w.isActive()) cb.getStyleClass().add("checklist-item-inactive");
            checkboxes.put(w.getId(), cb);
            listBox.getChildren().add(cb);
        }

        ScrollPane scroll = new ScrollPane(listBox);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(440, 360);
        scroll.getStyleClass().add("checklist-scroll");

        search.textProperty().addListener((o,a,b) -> {
            String q = b == null ? "" : b.toLowerCase().trim();
            for (Worker w : available) {
                CheckBox cb = checkboxes.get(w.getId());
                boolean match = q.isEmpty()
                        || w.getName().toLowerCase().contains(q)
                        || (w.getPosition()!=null && w.getPosition().toLowerCase().contains(q));
                cb.setVisible(match); cb.setManaged(match);
            }
        });

        Label countLabel = new Label();
        Runnable updateCount = () -> {
            long c = checkboxes.values().stream().filter(CheckBox::isSelected).count();
            countLabel.setText(c + " selected");
        };
        checkboxes.values().forEach(cb -> cb.selectedProperty().addListener((o,a,b) -> updateCount.run()));
        updateCount.run();

        Button selectAllBtn = new Button("Select All");
        selectAllBtn.getStyleClass().add("btn-link");
        selectAllBtn.setOnAction(e -> checkboxes.values().forEach(cb -> { if (cb.isVisible()) cb.setSelected(true); }));

        Button selectNoneBtn = new Button("Select None");
        selectNoneBtn.getStyleClass().add("btn-link");
        selectNoneBtn.setOnAction(e -> checkboxes.values().forEach(cb -> { if (cb.isVisible()) cb.setSelected(false); }));

        HBox quickActions = new HBox(6, selectAllBtn, selectNoneBtn);
        quickActions.setAlignment(Pos.CENTER_LEFT);

        HBox topRow = new HBox(10, search, countLabel);
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);

        VBox content = new VBox(10, topRow, quickActions, scroll);
        content.setPadding(new Insets(14));
        content.setPrefWidth(480);
        dlg.getDialogPane().setContent(content);
        Platform_runLaterFocus(search);

        dlg.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            List<Worker> toAdd = available.stream()
                    .filter(w -> checkboxes.get(w.getId()).isSelected())
                    .toList();
            if (toAdd.isEmpty()) return;
            toAdd.forEach(this::addEntry);
            setStatus("Added " + toAdd.size() + " worker" + (toAdd.size()==1?"":"s") + " to this payroll.");
        });
    }

    /**
     * Adds this project's assigned crew (or, if none assigned, all active workers)
     * to the current payroll in one click.
     */
    @FXML private void importProjectWorkers() {
        Project proj = payrollProject.getValue();
        List<Worker> source;
        String sourceDesc;
        try {
            if (proj != null) {
                List<Worker> crew = projectWorkerDAO.findWorkersForProject(proj.getId())
                        .stream().filter(Worker::isActive).toList();
                if (!crew.isEmpty()) {
                    source = crew;
                    sourceDesc = "\"" + proj.getName() + "\"'s assigned crew";
                } else {
                    source = allWorkers.stream().filter(Worker::isActive).toList();
                    sourceDesc = "all active workers";
                }
            } else {
                source = allWorkers.stream().filter(Worker::isActive).toList();
                sourceDesc = "all active workers";
            }
        } catch (SQLException e) { showError("Could Not Load Crew", e.getMessage()); return; }

        if (source.isEmpty()) { showError("No Workers Available","Add workers in the Workers tab first."); return; }
        List<Worker> toAdd = source.stream()
                .filter(w -> currentEntries.stream().noneMatch(e -> e.getWorkerId()==w.getId()))
                .toList();
        if (toAdd.isEmpty()) { showInfo("Already Added","All of " + sourceDesc + " are already on this payroll."); return; }
        toAdd.forEach(this::addEntry);
        setStatus("Added " + toAdd.size() + " worker(s) from " + sourceDesc + ".");
    }

    /** Saves the current payroll's worker list as the project's default assigned crew. */
    @FXML private void saveAsDefaultCrew() {
        Project proj = payrollProject.getValue();
        if (proj == null) { showError("No Project Selected","Select a project first."); return; }
        if (currentEntries.isEmpty()) { showError("No Workers Added","Add workers to this payroll first."); return; }
        if (!confirm("Save the current " + currentEntries.size() + " worker(s) as the default crew for \""
                + proj.getName() + "\"?\n\nThis will replace any existing crew assignment for this project.")) return;
        try {
            Set<Integer> ids = new HashSet<>();
            currentEntries.forEach(e -> ids.add(e.getWorkerId()));
            projectWorkerDAO.setAssignedWorkers(proj.getId(), ids);
            setStatus("Saved default crew for " + proj.getName() + " (" + ids.size() + " worker(s)).");
            showInfo("Default Crew Saved", "These " + ids.size() + " worker(s) will now be suggested automatically "
                    + "whenever you create a new payroll for \"" + proj.getName() + "\".");
        } catch (SQLException e) { showError("Could Not Save Crew", e.getMessage()); }
    }

    private void addEntry(Worker w) {
        if (currentEntries.stream().anyMatch(e -> e.getWorkerId()==w.getId())) {
            showInfo("Already Added", w.getName()+" is already on this payroll."); return;
        }
        PayrollEntry entry = new PayrollEntry(w.getId(),w.getName(),w.getPosition(),w.getDailyRate());
        entry.setOvertimeRate(Math.round((w.getDailyRate()/8.0*1.25)*100.0)/100.0);
        currentEntries.add(entry);
        attendanceContainer.getChildren().add(buildCard(entry));
        refreshTotals();
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = currentEntries.isEmpty();
        if (emptyStateLabel != null) emptyStateLabel.setVisible(empty);
        if (emptyStateLabel != null) emptyStateLabel.setManaged(empty);
    }

    /** Builds the per-worker attendance card with a friendly 7-day grid. */
    private VBox buildCard(PayrollEntry entry) {
        VBox card = new VBox(8);
        card.getStyleClass().add("attendance-card");

        // Header row
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label nameLbl = new Label(entry.getWorkerName()); nameLbl.getStyleClass().add("card-name");
        Label posLbl  = new Label(nvl(entry.getPosition(),"\u2014")); posLbl.getStyleClass().add("card-pos");
        Label rateLbl = new Label("\u20b1"+String.format("%,.2f",entry.getDailyRate())+" / day"); rateLbl.getStyleClass().add("card-rate");
        Region sp = new Region(); HBox.setHgrow(sp,Priority.ALWAYS);
        Button removeBtn = new Button("Remove"); removeBtn.getStyleClass().add("btn-danger-sm");
        removeBtn.setOnAction(e -> {
            if (!confirm("Remove " + entry.getWorkerName() + " from this payroll?")) return;
            currentEntries.remove(entry);
            attendanceContainer.getChildren().remove(card);
            refreshTotals();
            updateEmptyState();
        });
        header.getChildren().addAll(nameLbl, posLbl, rateLbl, sp, removeBtn);

        // Helper text
        Label helper = new Label("Check AM and/or PM for each day worked. Add overtime hours if applicable.");
        helper.getStyleClass().add("card-helper");

        // Attendance grid
        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(6); grid.setPadding(new Insets(4,0,4,0));
        grid.add(new Label(""), 0, 0);
        grid.add(rowLbl("Morning (AM)"),    0, 1);
        grid.add(rowLbl("Afternoon (PM)"),  0, 2);
        grid.add(rowLbl("Overtime (hrs)"),  0, 3);

        String[] dayLabels = {"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
        List<DayAttendance> days = entry.getDayAttendance();

        for (int d = 0; d < 7; d++) {
            DayAttendance da = days.get(d);
            int col = d + 1;

            VBox dayHeader = new VBox(2);
            dayHeader.setAlignment(Pos.CENTER);
            Label dayName = new Label(dayLabels[d]);
            dayName.getStyleClass().add("day-header");
            Label dayNum = new Label("Day " + (d+1));
            dayNum.getStyleClass().add("day-subheader");
            dayHeader.getChildren().addAll(dayName, dayNum);
            dayHeader.setMinWidth(76);
            grid.add(dayHeader, col, 0);

            CheckBox amCb = new CheckBox(); amCb.setSelected(da.isAm());
            amCb.getStyleClass().add("attendance-checkbox");
            GridPane.setHalignment(amCb, HPos.CENTER);
            amCb.selectedProperty().addListener((o,a,b) -> { da.setAm(b); entry.calculate(); refreshTotals(); updateTotals(card,entry); });
            grid.add(amCb, col, 1);

            CheckBox pmCb = new CheckBox(); pmCb.setSelected(da.isPm());
            pmCb.getStyleClass().add("attendance-checkbox");
            GridPane.setHalignment(pmCb, HPos.CENTER);
            pmCb.selectedProperty().addListener((o,a,b) -> { da.setPm(b); entry.calculate(); refreshTotals(); updateTotals(card,entry); });
            grid.add(pmCb, col, 2);

            Spinner<Double> otSp = new Spinner<>(0.0,24.0,da.getOtHours(),0.5);
            otSp.setEditable(true); otSp.setPrefWidth(76);
            otSp.getStyleClass().add("ot-spinner");
            otSp.valueProperty().addListener((o,a,b) -> { da.setOtHours(b==null?0:b); entry.calculate(); refreshTotals(); updateTotals(card,entry); });
            GridPane.setHalignment(otSp, HPos.CENTER);
            grid.add(otSp, col, 3);
        }

        // Quick action: mark whole week present
        HBox quickActions = new HBox(8);
        quickActions.setAlignment(Pos.CENTER_LEFT);
        Button fullWeekBtn = new Button("Mark Full Week (Mon\u2013Fri)");
        fullWeekBtn.getStyleClass().add("btn-link");
        fullWeekBtn.setOnAction(e -> {
            for (int d = 0; d < 5; d++) { days.get(d).setAm(true); days.get(d).setPm(true); }
            entry.calculate();
            attendanceContainer.getChildren().set(attendanceContainer.getChildren().indexOf(card), buildCard(entry));
            refreshTotals();
        });
        Button clearBtn = new Button("Clear All");
        clearBtn.getStyleClass().add("btn-link");
        clearBtn.setOnAction(e -> {
            for (DayAttendance da : days) { da.setAm(false); da.setPm(false); da.setOtHours(0); }
            entry.calculate();
            attendanceContainer.getChildren().set(attendanceContainer.getChildren().indexOf(card), buildCard(entry));
            refreshTotals();
        });
        quickActions.getChildren().addAll(fullWeekBtn, clearBtn);

        // Totals row
        HBox totRow = new HBox(20); totRow.setAlignment(Pos.CENTER_RIGHT); totRow.getStyleClass().add("card-totals");
        Label dL = new Label("Days: 0");  dL.getStyleClass().add("card-total-lbl"); dL.setId("days");
        Label oL = new Label("Overtime: 0h"); oL.getStyleClass().add("card-total-lbl"); oL.setId("ot");
        Label nL = new Label("Pay: \u20b10.00");  nL.getStyleClass().add("card-total-net"); nL.setId("net");
        totRow.getChildren().addAll(dL, oL, nL);

        card.getChildren().addAll(header, helper, grid, quickActions, totRow);
        entry.calculate(); updateTotals(card, entry);
        return card;
    }

    private Label rowLbl(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("attendance-row-label");
        l.setMinWidth(120);
        return l;
    }

    private void updateTotals(VBox card, PayrollEntry e) {
        for (var node : card.getChildren()) {
            if (node instanceof HBox hb) {
                for (var child : hb.getChildren()) {
                    if (child instanceof Label l) {
                        switch (nvl(l.getId(),"")) {
                            case "days" -> l.setText(String.format("Days: %.1f", e.getDaysWorked()));
                            case "ot"   -> l.setText(String.format("Overtime: %.1fh", e.getOvertimeHours()));
                            case "net"  -> l.setText(String.format("Pay: \u20b1%,.2f", e.getNetPay()));
                        }
                    }
                }
            }
        }
    }

    private void refreshTotals() {
        double net = currentEntries.stream().mapToDouble(PayrollEntry::getNetPay).sum();
        totalNetLabel.setText(String.format("\u20b1 %,.2f", net));
        if (workerCountLabel != null)
            workerCountLabel.setText(currentEntries.size() + (currentEntries.size()==1 ? " worker" : " workers"));
    }

    @FXML private void savePayrollDraft() { savePayroll("DRAFT"); }
    @FXML private void finalizePayroll()  { savePayroll("FINALIZED"); }

    private void savePayroll(String status) {
        if (!validateForm()) return;
        double total = currentEntries.stream().mapToDouble(PayrollEntry::getNetPay).sum();
        Payroll p = editingPayroll != null ? editingPayroll : new Payroll();
        p.setProjectId(payrollProject.getValue().getId());
        p.setProjectName(payrollProject.getValue().getName());
        p.setPeriodStart(payrollPeriodStart.getValue());
        p.setPeriodEnd(payrollPeriodEnd.getValue());
        p.setPayDate(payrollPayDate.getValue());
        p.setPreparedBy(payrollPreparedBy.getText().trim());
        p.setApprovedBy(payrollApprovedBy.getText().trim());
        p.setStatus(status); p.setTotalAmount(total);
        p.setEntries(List.copyOf(currentEntries));
        try {
            if (editingPayroll==null) { payrollDAO.insert(p); allPayrolls.add(0,p); editingPayroll=p; }
            else { payrollDAO.update(p); int i=allPayrolls.indexOf(p); if(i>=0) allPayrolls.set(i,p); }
            String friendly = status.equals("DRAFT") ? "saved as a draft" : "finalized";
            setStatus("Payroll " + friendly + " \u2014 #" + p.getId());
            showInfo("Payroll Saved", "This payroll has been " + friendly + ".\n\nProject: " + p.getProjectName()
                    + "\nTotal pay: \u20b1" + String.format("%,.2f", total));
        } catch (SQLException e) { showError("Could Not Save Payroll",e.getMessage()); }
    }

    @FXML private void exportPayrollPdf() {
        if (!validateForm()) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Payroll PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Document","*.pdf"));
        String projName = payrollProject.getValue()!=null ? sanitize(payrollProject.getValue().getName()) : "Project";
        fc.setInitialFileName(projName + "_DTR_" + payrollPeriodStart.getValue() + ".pdf");
        File f = fc.showSaveDialog(payrollPane.getScene().getWindow());
        if (f==null) return;
        Payroll p = new Payroll();
        p.setId(editingPayroll!=null?editingPayroll.getId():0);
        p.setProjectName(payrollProject.getValue().getName());
        p.setPeriodStart(payrollPeriodStart.getValue()); p.setPeriodEnd(payrollPeriodEnd.getValue());
        p.setPayDate(payrollPayDate.getValue());
        p.setPreparedBy(payrollPreparedBy.getText().trim()); p.setApprovedBy(payrollApprovedBy.getText().trim());
        p.setEntries(List.copyOf(currentEntries));
        try { pdfService.generate(p, f.getAbsolutePath(), null); setStatus("PDF saved: "+f.getName()); showInfo("PDF Exported","Saved to:\n"+f.getAbsolutePath()); }
        catch (Exception e) { showError("Could Not Export PDF",e.getMessage()); }
    }

    private boolean validateForm() {
        if (payrollProject.getValue()==null) { showError("Project Required","Please select a project for this payroll."); return false; }
        if (payrollPeriodStart.getValue()==null || payrollPeriodEnd.getValue()==null) { showError("Pay Period Required","Please set both the start and end dates for the pay period."); return false; }
        if (payrollPeriodEnd.getValue().isBefore(payrollPeriodStart.getValue())) { showError("Invalid Pay Period","Period end date cannot be before the start date."); return false; }
        if (currentEntries.isEmpty()) { showError("No Workers Added","Add at least one worker to this payroll before saving."); return false; }
        return true;
    }

    // -- History ---------------------------------------------------------------
    private void setupHistoryTable() {
        colHisId.setCellValueFactory(d -> new SimpleStringProperty("#"+d.getValue().getId()));
        colHisProject.setCellValueFactory(d -> d.getValue().projectNameProperty());
        colHisPeriod.setCellValueFactory(d -> new SimpleStringProperty(fmt(d.getValue().getPeriodStart())+" \u2013 "+fmt(d.getValue().getPeriodEnd())));
        colHisPayDate.setCellValueFactory(d -> new SimpleStringProperty(fmt(d.getValue().getPayDate())));
        colHisTotal.setCellValueFactory(d -> new SimpleStringProperty(String.format("\u20b1 %,.2f",d.getValue().getTotalAmount())));
        colHisPrepared.setCellValueFactory(d -> d.getValue().preparedByProperty());
        colHisStatus.setCellValueFactory(d -> d.getValue().statusProperty());
        colHisStatus.setCellFactory(col -> new BadgeCell<>());
        colHisActions.setCellFactory(col -> new TableCell<>() {
            final Button vBtn=btn("View / Edit","btn-edit"), pBtn=btn("Export PDF","btn-pdf"), dBtn=btn("Delete","btn-danger");
            final HBox box = new HBox(6,vBtn,pBtn,dBtn);
            { box.setAlignment(Pos.CENTER);
              vBtn.setOnAction(e -> loadPayrollForEdit(getTableView().getItems().get(getIndex())));
              pBtn.setOnAction(e -> exportHistoryPdf(getTableView().getItems().get(getIndex())));
              dBtn.setOnAction(e -> deletePayroll(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(String item, boolean empty) { super.updateItem(item,empty); setGraphic(empty?null:box); }
        });
    }

    private void refreshHistory() {
        ObservableList<String> names = FXCollections.observableArrayList("All Projects");
        allProjects.forEach(p -> names.add(p.getName()));
        historyProjectFilter.setItems(names); historyProjectFilter.setValue("All Projects");
        FilteredList<Payroll> fl = new FilteredList<>(allPayrolls);
        Runnable f = () -> fl.setPredicate(pay -> {
            String pf = historyProjectFilter.getValue();
            boolean mp = pf==null||"All Projects".equals(pf)||pay.getProjectName().equals(pf);
            String q  = historySearch.getText();
            boolean mq = q==null||q.isEmpty()||pay.getProjectName().toLowerCase().contains(q.toLowerCase());
            return mp && mq;
        });
        historyProjectFilter.valueProperty().addListener((o,a,b)->f.run());
        historySearch.textProperty().addListener((o,a,b)->f.run());
        f.run(); historyTable.setItems(fl);
        historyTable.setPlaceholder(new Label("No payrolls yet. Create one in the Payroll tab."));
    }

    private void loadPayrollForEdit(Payroll p) {
        try {
            Payroll full = payrollDAO.findById(p.getId());
            editingPayroll = full;
            refreshPayrollProjectCombo();
            allProjects.stream().filter(pr -> pr.getId()==full.getProjectId()).findFirst()
                    .ifPresent(pr -> payrollProject.setValue(pr));
            payrollPeriodStart.setValue(full.getPeriodStart()); payrollPeriodEnd.setValue(full.getPeriodEnd());
            payrollPayDate.setValue(full.getPayDate());
            payrollPreparedBy.setText(nvl(full.getPreparedBy())); payrollApprovedBy.setText(nvl(full.getApprovedBy()));
            currentEntries.clear(); currentEntries.addAll(full.getEntries());
            attendanceContainer.getChildren().clear();
            currentEntries.forEach(e -> attendanceContainer.getChildren().add(buildCard(e)));
            refreshTotals(); updateEmptyState();
            switchPane(payrollPane); btnPayroll.setSelected(true);
            setStatus("Editing payroll #"+full.getId());
        } catch (SQLException e) { showError("Could Not Load Payroll",e.getMessage()); }
    }

    private void exportHistoryPdf(Payroll p) {
        try {
            Payroll full = payrollDAO.findById(p.getId());
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Payroll PDF"); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Document","*.pdf"));
            fc.setInitialFileName(sanitize(full.getProjectName())+"_DTR_"+full.getPeriodStart()+".pdf");
            File f = fc.showSaveDialog(historyPane.getScene().getWindow());
            if (f==null) return;
            pdfService.generate(full, f.getAbsolutePath(), null);
            setStatus("PDF saved: "+f.getName()); showInfo("PDF Exported","Saved to:\n"+f.getAbsolutePath());
        } catch (Exception e) { showError("Could Not Export PDF",e.getMessage()); }
    }

    private void deletePayroll(Payroll p) {
        if (!confirm("Delete payroll #"+p.getId()+" for "+p.getProjectName()+"?\n\nThis cannot be undone.")) return;
        try { payrollDAO.delete(p.getId()); allPayrolls.remove(p); historyTable.refresh(); setStatus("Payroll deleted."); }
        catch (SQLException e) { showError("Could Not Delete Payroll",e.getMessage()); }
    }

    // -- Helpers -------------------------------------------------------------
    private void setStatus(String m)    { statusLabel.setText(m); }
    private String fmt(LocalDate d)     { return d!=null?d.format(DATE_FMT):"\u2014"; }
    private String nvl(String s)        { return s!=null?s:""; }
    private String nvl(String s,String def){ return (s!=null&&!s.isEmpty())?s:def; }

    private String sanitize(String s) {
        if (s==null) return "Project";
        return s.replaceAll("[/\\\\:*?\"<>|]","_").trim();
    }

    private Label lbl(String t) { Label l=new Label(t); l.getStyleClass().add("dialog-label"); return l; }
    private TextField tf(String v) { TextField tf=new TextField(nvl(v)); tf.setMinWidth(280); return tf; }
    private Button btn(String t,String cls){ Button b=new Button(t); b.getStyleClass().add(cls); return b; }

    private Dialog<ButtonType> dlg(String title) {
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle(title);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        d.getDialogPane().getStyleClass().add("app-dialog");
        return d;
    }
    private GridPane grid() {
        GridPane g=new GridPane(); g.setHgap(12); g.setVgap(10); g.setPadding(new Insets(16));
        g.getColumnConstraints().addAll(new ColumnConstraints(130), new ColumnConstraints(320)); return g;
    }
    private void showError(String t,String m){ Alert a=new Alert(Alert.AlertType.ERROR);a.setTitle(t);a.setHeaderText(t);a.setContentText(m);a.showAndWait(); }
    private void showInfo(String t,String m) { Alert a=new Alert(Alert.AlertType.INFORMATION);a.setTitle(t);a.setHeaderText(t);a.setContentText(m);a.showAndWait(); }
    private boolean confirm(String m){ Alert a=new Alert(Alert.AlertType.CONFIRMATION);a.setTitle("Please Confirm");a.setHeaderText(null);a.setContentText(m);return a.showAndWait().filter(b->b==ButtonType.OK).isPresent(); }

    private void Platform_runLaterFocus(TextField field) {
        javafx.application.Platform.runLater(field::requestFocus);
    }

    // -- Inner cell types --------------------------------------------------
    static class BadgeCell<T> extends TableCell<T,String> {
        @Override protected void updateItem(String item,boolean empty) {
            super.updateItem(item,empty);
            if (empty||item==null){setGraphic(null);return;}
            Label b=new Label(item);
            b.getStyleClass().add(switch(item.toUpperCase()){
                case "ACTIVE","FINALIZED"->"badge-active"; case "DRAFT"->"badge-draft";
                case "COMPLETED"->"badge-final"; default->"badge-inactive";
            });
            setGraphic(b); setText(null);
        }
    }

    static class ActionCell<T> extends TableCell<T,String> {
        private final java.util.function.Consumer<T> onEdit, onDelete;
        private final Button editBtn=new Button("Edit"), delBtn=new Button("Delete");
        private final HBox box=new HBox(6,editBtn,delBtn);
        ActionCell(java.util.function.Consumer<T> onEdit, java.util.function.Consumer<T> onDelete){
            this.onEdit=onEdit; this.onDelete=onDelete;
            editBtn.getStyleClass().add("btn-edit"); delBtn.getStyleClass().add("btn-danger");
            box.setAlignment(Pos.CENTER);
            editBtn.setOnAction(e->onEdit.accept(getTableView().getItems().get(getIndex())));
            delBtn.setOnAction(e->onDelete.accept(getTableView().getItems().get(getIndex())));
        }
        @Override protected void updateItem(String item,boolean empty){super.updateItem(item,empty);setGraphic(empty?null:box);}
    }

    static class ProjectActionCell extends TableCell<Project,String> {
        private final java.util.function.Consumer<Project> onEdit, onDelete, onAssign;
        private final Button editBtn=new Button("Edit"), delBtn=new Button("Delete"), assignBtn=new Button("Workers");
        private final HBox box=new HBox(6,assignBtn,editBtn,delBtn);
        ProjectActionCell(java.util.function.Consumer<Project> onEdit, java.util.function.Consumer<Project> onDelete, java.util.function.Consumer<Project> onAssign){
            this.onEdit=onEdit; this.onDelete=onDelete; this.onAssign=onAssign;
            assignBtn.getStyleClass().add("btn-secondary-sm");
            editBtn.getStyleClass().add("btn-edit"); delBtn.getStyleClass().add("btn-danger");
            box.setAlignment(Pos.CENTER);
            assignBtn.setOnAction(e->onAssign.accept(getTableView().getItems().get(getIndex())));
            editBtn.setOnAction(e->onEdit.accept(getTableView().getItems().get(getIndex())));
            delBtn.setOnAction(e->onDelete.accept(getTableView().getItems().get(getIndex())));
        }
        @Override protected void updateItem(String item,boolean empty){super.updateItem(item,empty);setGraphic(empty?null:box);}
    }
}
