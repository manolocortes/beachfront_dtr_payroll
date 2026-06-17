package com.payroll.dao;
import com.payroll.model.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
public class PayrollDAO {
    private final Connection conn;
    public PayrollDAO() { conn = DatabaseManager.getInstance().getConnection(); }

    public List<Payroll> findAll() throws SQLException {
        List<Payroll> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT p.*,pr.name as pname FROM payrolls p JOIN projects pr ON p.project_id=pr.id ORDER BY p.period_start DESC");
             ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapPayroll(rs)); }
        return list;
    }

    public Payroll findById(int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT p.*,pr.name as pname FROM payrolls p JOIN projects pr ON p.project_id=pr.id WHERE p.id=?")) {
            ps.setInt(1,id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { Payroll pay = mapPayroll(rs); pay.setEntries(findEntries(id)); return pay; }
            }
        } return null;
    }

    public void insert(Payroll p) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payrolls(project_id,period_start,period_end,pay_date,prepared_by,approved_by,status,total_amount) VALUES(?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            setParams(ps,p); ps.executeUpdate();
            try(ResultSet k=ps.getGeneratedKeys()){if(k.next())p.setId(k.getInt(1));}
        }
        for (PayrollEntry e : p.getEntries()) { e.setPayrollId(p.getId()); insertEntry(e); }
    }

    public void update(Payroll p) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE payrolls SET project_id=?,period_start=?,period_end=?,pay_date=?,prepared_by=?,approved_by=?,status=?,total_amount=? WHERE id=?")) {
            setParams(ps,p); ps.setInt(9,p.getId()); ps.executeUpdate();
        }
        deleteEntries(p.getId());
        for (PayrollEntry e : p.getEntries()) { e.setPayrollId(p.getId()); insertEntry(e); }
    }

    public void delete(int id) throws SQLException {
        try(PreparedStatement ps=conn.prepareStatement("DELETE FROM payrolls WHERE id=?")){ps.setInt(1,id);ps.executeUpdate();}
    }

    public List<PayrollEntry> findEntries(int payrollId) throws SQLException {
        List<PayrollEntry> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT pe.*,w.name as wname,w.position FROM payroll_entries pe JOIN workers w ON pe.worker_id=w.id WHERE pe.payroll_id=? ORDER BY w.name")) {
            ps.setInt(1,payrollId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapEntry(rs)); }
        } return list;
    }

    private void insertEntry(PayrollEntry e) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payroll_entries(payroll_id,worker_id,daily_rate,overtime_rate,gross_pay,net_pay,d1,d2,d3,d4,d5,d6,d7) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1,e.getPayrollId());ps.setInt(2,e.getWorkerId());ps.setDouble(3,e.getDailyRate());
            ps.setDouble(4,e.getOvertimeRate());ps.setDouble(5,e.getGrossPay());ps.setDouble(6,e.getNetPay());
            List<DayAttendance> days = e.getDayAttendance();
            for (int i=0;i<7;i++) { DayAttendance da=i<days.size()?days.get(i):new DayAttendance(i); ps.setString(7+i,da.serialize()); }
            ps.executeUpdate();
            try(ResultSet k=ps.getGeneratedKeys()){if(k.next())e.setId(k.getInt(1));}
        }
    }

    private void deleteEntries(int payrollId) throws SQLException {
        try(PreparedStatement ps=conn.prepareStatement("DELETE FROM payroll_entries WHERE payroll_id=?")){ps.setInt(1,payrollId);ps.executeUpdate();}
    }

    private void setParams(PreparedStatement ps, Payroll p) throws SQLException {
        ps.setInt(1,p.getProjectId());ps.setString(2,p.getPeriodStart().toString());ps.setString(3,p.getPeriodEnd().toString());
        ps.setString(4,p.getPayDate()!=null?p.getPayDate().toString():null);
        ps.setString(5,p.getPreparedBy());ps.setString(6,p.getApprovedBy());ps.setString(7,p.getStatus());ps.setDouble(8,p.getTotalAmount());
    }

    private Payroll mapPayroll(ResultSet rs) throws SQLException {
        String pd=rs.getString("pay_date");
        return new Payroll(rs.getInt("id"),rs.getInt("project_id"),rs.getString("pname"),
                LocalDate.parse(rs.getString("period_start")),LocalDate.parse(rs.getString("period_end")),
                pd!=null?LocalDate.parse(pd):null,rs.getString("prepared_by"),rs.getString("approved_by"),
                rs.getString("status"),rs.getDouble("total_amount"));
    }

    private PayrollEntry mapEntry(ResultSet rs) throws SQLException {
        PayrollEntry e = new PayrollEntry();
        e.setId(rs.getInt("id"));e.setPayrollId(rs.getInt("payroll_id"));e.setWorkerId(rs.getInt("worker_id"));
        e.setWorkerName(rs.getString("wname"));e.setPosition(rs.getString("position"));e.setDailyRate(rs.getDouble("daily_rate"));
        e.setOvertimeRate(rs.getDouble("overtime_rate"));e.setGrossPay(rs.getDouble("gross_pay"));e.setNetPay(rs.getDouble("net_pay"));
        List<DayAttendance> days = new ArrayList<>();
        for (int i=0;i<7;i++) days.add(DayAttendance.deserialize(i,rs.getString("d"+(i+1))));
        e.setDayAttendance(days);
        return e;
    }
}
