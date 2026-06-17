package com.payroll.dao;
import com.payroll.model.Project;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
public class ProjectDAO {
    private final Connection conn;
    public ProjectDAO() { conn = DatabaseManager.getInstance().getConnection(); }
    public List<Project> findAll() throws SQLException {
        List<Project> list = new ArrayList<>();
        try (ResultSet rs = conn.prepareStatement("SELECT * FROM projects ORDER BY name").executeQuery())
        { while (rs.next()) list.add(map(rs)); } return list;
    }
    public void insert(Project p) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO projects(name,location,client,start_date,end_date,status,description) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,p.getName());ps.setString(2,p.getLocation());ps.setString(3,p.getClient());
            ps.setString(4,p.getStartDate()!=null?p.getStartDate().toString():null);
            ps.setString(5,p.getEndDate()!=null?p.getEndDate().toString():null);
            ps.setString(6,p.getStatus());ps.setString(7,p.getDescription());
            ps.executeUpdate();
            try(ResultSet k=ps.getGeneratedKeys()){if(k.next())p.setId(k.getInt(1));}
        }
    }
    public void update(Project p) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE projects SET name=?,location=?,client=?,start_date=?,end_date=?,status=?,description=? WHERE id=?")) {
            ps.setString(1,p.getName());ps.setString(2,p.getLocation());ps.setString(3,p.getClient());
            ps.setString(4,p.getStartDate()!=null?p.getStartDate().toString():null);
            ps.setString(5,p.getEndDate()!=null?p.getEndDate().toString():null);
            ps.setString(6,p.getStatus());ps.setString(7,p.getDescription());ps.setInt(8,p.getId());ps.executeUpdate();
        }
    }
    public void delete(int id) throws SQLException {
        try(PreparedStatement ps=conn.prepareStatement("DELETE FROM projects WHERE id=?")){ps.setInt(1,id);ps.executeUpdate();}
    }
    private Project map(ResultSet rs) throws SQLException {
        String sd=rs.getString("start_date"),ed=rs.getString("end_date");
        return new Project(rs.getInt("id"),rs.getString("name"),rs.getString("location"),rs.getString("client"),
                sd!=null?LocalDate.parse(sd):null,ed!=null?LocalDate.parse(ed):null,rs.getString("status"),rs.getString("description"));
    }
}
