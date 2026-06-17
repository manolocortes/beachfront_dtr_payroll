package com.payroll.dao;
import java.sql.*;
import java.io.File;
public class DatabaseManager {
    private static DatabaseManager instance;
    private static final String DB_FILE;
    static {
        // Store DB in user home directory so it works when installed in read-only locations
        String appDir = System.getProperty("user.home") + File.separator + ".payroll-app";
        new File(appDir).mkdirs();
        DB_FILE = appDir + File.separator + "payroll.db";
    }
    private Connection connection;
    private DatabaseManager() {}
    public static DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }
    public void initialize() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE);
        connection.createStatement().execute("PRAGMA foreign_keys = ON");
        System.out.println("DB path: " + new File(DB_FILE).getAbsolutePath());
        createTables();
        // Print all columns in both tables for debug
        try (ResultSet rs = connection.createStatement().executeQuery("PRAGMA table_info(project_workers)")) {
            System.out.print("project_workers cols: ");
            while (rs.next()) System.out.print(rs.getString("name") + " ");
            System.out.println();
        }
        try (ResultSet rs = connection.createStatement().executeQuery("PRAGMA table_info(payroll_entries)")) {
            System.out.print("payroll_entries cols: ");
            while (rs.next()) System.out.print(rs.getString("name") + " ");
            System.out.println();
        }
    }
    public Connection getConnection() { return connection; }
    private void createTables() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS workers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, position TEXT, daily_rate REAL NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1)");
            s.execute("CREATE TABLE IF NOT EXISTS projects (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, location TEXT, client TEXT, start_date TEXT, end_date TEXT, status TEXT NOT NULL DEFAULT 'ACTIVE', description TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS payrolls (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, period_start TEXT NOT NULL, period_end TEXT NOT NULL, pay_date TEXT, prepared_by TEXT, approved_by TEXT, status TEXT NOT NULL DEFAULT 'DRAFT', total_amount REAL NOT NULL DEFAULT 0, FOREIGN KEY (project_id) REFERENCES projects(id))");
            s.execute("CREATE TABLE IF NOT EXISTS payroll_entries (id INTEGER PRIMARY KEY AUTOINCREMENT, payroll_id INTEGER NOT NULL, worker_id INTEGER NOT NULL, daily_rate REAL NOT NULL DEFAULT 0, overtime_rate REAL NOT NULL DEFAULT 0, gross_pay REAL NOT NULL DEFAULT 0, net_pay REAL NOT NULL DEFAULT 0, d1 TEXT DEFAULT '0,0,0', d2 TEXT DEFAULT '0,0,0', d3 TEXT DEFAULT '0,0,0', d4 TEXT DEFAULT '0,0,0', d5 TEXT DEFAULT '0,0,0', d6 TEXT DEFAULT '0,0,0', d7 TEXT DEFAULT '0,0,0', FOREIGN KEY (payroll_id) REFERENCES payrolls(id) ON DELETE CASCADE, FOREIGN KEY (worker_id) REFERENCES workers(id))");

            // Junction table: which workers are assigned to which project
            s.execute("CREATE TABLE IF NOT EXISTS project_workers (project_id INTEGER NOT NULL, worker_id INTEGER NOT NULL, PRIMARY KEY (project_id, worker_id), FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE, FOREIGN KEY (worker_id) REFERENCES workers(id) ON DELETE CASCADE)");

            // Migrate older databases that may still have the old workers columns
            migrateWorkersTable(s);
            // Add sort_order to project_workers if missing
            migrateProjectWorkersSortOrder(s);
            // Add sort_order column to payroll_entries if missing (older DBs)
            migratePayrollEntriesSortOrder(s);
        }
    }

    /** Drops contact_number/address columns from older DB versions by rebuilding the table if needed. */
    private void migrateWorkersTable(Statement s) throws SQLException {
        boolean hasOldCols = false;
        try (ResultSet rs = s.executeQuery("PRAGMA table_info(workers)")) {
            while (rs.next()) {
                String col = rs.getString("name");
                if ("contact_number".equals(col) || "address".equals(col)) { hasOldCols = true; break; }
            }
        }
        if (hasOldCols) {
            s.execute("ALTER TABLE workers RENAME TO workers_old");
            s.execute("CREATE TABLE workers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, position TEXT, daily_rate REAL NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1)");
            s.execute("INSERT INTO workers (id,name,position,daily_rate,active) SELECT id,name,position,daily_rate,active FROM workers_old");
            s.execute("DROP TABLE workers_old");
        }
    }

    /** Adds sort_order to project_workers if missing. */
    private void migrateProjectWorkersSortOrder(Statement s) throws SQLException {
        boolean hasCol = false;
        try (ResultSet rs = s.executeQuery("PRAGMA table_info(project_workers)")) {
            while (rs.next()) {
                if ("sort_order".equals(rs.getString("name"))) { hasCol = true; break; }
            }
        }
        if (!hasCol) {
            s.execute("PRAGMA foreign_keys = OFF");
            s.execute("ALTER TABLE project_workers ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
            s.execute("UPDATE project_workers SET sort_order = worker_id");
            s.execute("PRAGMA foreign_keys = ON");
        }
    }

    /** Adds sort_order column to payroll_entries if it does not exist yet. */
    private void migratePayrollEntriesSortOrder(Statement s) throws SQLException {
        boolean hasCol = false;
        try (ResultSet rs = s.executeQuery("PRAGMA table_info(payroll_entries)")) {
            while (rs.next()) {
                if ("sort_order".equals(rs.getString("name"))) { hasCol = true; break; }
            }
        }
        if (!hasCol) {
            s.execute("PRAGMA foreign_keys = OFF");
            s.execute("ALTER TABLE payroll_entries ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
            s.execute("PRAGMA foreign_keys = ON");
        }
    }

    public void close() {
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException e) { e.printStackTrace(); }
    }
}
