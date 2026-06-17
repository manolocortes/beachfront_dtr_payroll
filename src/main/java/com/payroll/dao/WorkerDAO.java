package com.payroll.dao;
import com.payroll.model.Worker;
import java.sql.*;
import java.util.*;
public class WorkerDAO {
    private final Connection conn;
    public WorkerDAO() { conn = DatabaseManager.getInstance().getConnection(); }
    public List<Worker> findAll() throws SQLException {
        List<Worker> list = new ArrayList<>();
        try (ResultSet rs = conn.prepareStatement("SELECT * FROM workers ORDER BY name").executeQuery())
        { while (rs.next()) list.add(map(rs)); } return list;
    }
    public List<Worker> findActive() throws SQLException {
        List<Worker> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM workers WHERE active=1 ORDER BY name"); ResultSet rs = ps.executeQuery())
        { while (rs.next()) list.add(map(rs)); } return list;
    }
    public void insert(Worker w) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO workers(name,position,daily_rate,active) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,w.getName());ps.setString(2,w.getPosition());ps.setDouble(3,w.getDailyRate());ps.setInt(4,w.isActive()?1:0);
            ps.executeUpdate();
            try(ResultSet k=ps.getGeneratedKeys()){if(k.next())w.setId(k.getInt(1));}
        }
    }
    public void update(Worker w) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE workers SET name=?,position=?,daily_rate=?,active=? WHERE id=?")) {
            ps.setString(1,w.getName());ps.setString(2,w.getPosition());ps.setDouble(3,w.getDailyRate());ps.setInt(4,w.isActive()?1:0);ps.setInt(5,w.getId());ps.executeUpdate();
        }
    }
    public void delete(int id) throws SQLException {
        try(PreparedStatement ps=conn.prepareStatement("DELETE FROM workers WHERE id=?")){ps.setInt(1,id);ps.executeUpdate();}
    }
    private Worker map(ResultSet rs) throws SQLException {
        return new Worker(rs.getInt("id"),rs.getString("name"),rs.getString("position"),rs.getDouble("daily_rate"),rs.getInt("active")==1);
    }
}
