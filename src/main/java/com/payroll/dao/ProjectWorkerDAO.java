package com.payroll.dao;
import com.payroll.model.Worker;
import java.sql.*;
import java.util.*;

/** Manages the many-to-many assignment of workers to projects. */
public class ProjectWorkerDAO {
    private final Connection conn;
    public ProjectWorkerDAO() { conn = DatabaseManager.getInstance().getConnection(); }

    /** Returns all workers assigned to the given project, ordered by name. */
    public List<Worker> findWorkersForProject(int projectId) throws SQLException {
        List<Worker> list = new ArrayList<>();
        String sql = "SELECT w.*, pw.sort_order FROM workers w JOIN project_workers pw ON w.id=pw.worker_id WHERE pw.project_id=? ORDER BY pw.sort_order ASC, w.name ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Worker(rs.getInt("id"), rs.getString("name"), rs.getString("position"),
                            rs.getDouble("daily_rate"), rs.getInt("active")==1));
                }
            }
        }
        return list;
    }

    /** Returns the set of worker IDs assigned to the given project. */
    public Set<Integer> findWorkerIdsForProject(int projectId) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT worker_id FROM project_workers WHERE project_id=?")) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt("worker_id"));
            }
        }
        return ids;
    }

    /** Replaces the full set of assigned workers for a project (Set version, unordered). */
    public void setAssignedWorkers(int projectId, Set<Integer> workerIds) throws SQLException {
        setAssignedWorkersOrdered(projectId, new ArrayList<>(workerIds));
    }

    /** Replaces the assigned workers for a project preserving the given list order. */
    public void setAssignedWorkersOrdered(int projectId, List<Integer> orderedIds) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM project_workers WHERE project_id=?")) {
            del.setInt(1, projectId); del.executeUpdate();
        }
        if (orderedIds.isEmpty()) return;
        try (PreparedStatement ins = conn.prepareStatement("INSERT INTO project_workers(project_id, worker_id, sort_order) VALUES (?,?,?)")) {
            for (int i = 0; i < orderedIds.size(); i++) {
                ins.setInt(1, projectId); ins.setInt(2, orderedIds.get(i)); ins.setInt(3, i);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    /** Adds a single worker to a project (no-op if already assigned). */
    public void assignWorker(int projectId, int workerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO project_workers(project_id, worker_id) VALUES (?,?)")) {
            ps.setInt(1, projectId);
            ps.setInt(2, workerId);
            ps.executeUpdate();
        }
    }

    /** Removes a single worker from a project. */
    public void unassignWorker(int projectId, int workerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM project_workers WHERE project_id=? AND worker_id=?")) {
            ps.setInt(1, projectId);
            ps.setInt(2, workerId);
            ps.executeUpdate();
        }
    }
}
