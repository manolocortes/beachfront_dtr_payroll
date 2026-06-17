package com.payroll;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.payroll.dao.DatabaseManager;
import java.io.*;
import java.time.LocalDateTime;

public class Main extends Application {

    private static final String LOG_FILE = System.getProperty("user.home")
            + File.separator + ".payroll-app" + File.separator + "startup.log";

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseManager.getInstance().initialize();
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/MainView.fxml"));
        Scene scene = new Scene(root, 1280, 820);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        stage.setTitle("Payroll Management System");
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        // Ensure log directory exists
        new File(System.getProperty("user.home") + File.separator + ".payroll-app").mkdirs();
        try {
            launch(args);
        } catch (Throwable t) {
            writeLog(t);
        }
    }

    public static void writeLog(Throwable t) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            pw.println("=== " + LocalDateTime.now() + " ===");
            t.printStackTrace(pw);
            pw.println();
        } catch (IOException ignored) {}
    }
}