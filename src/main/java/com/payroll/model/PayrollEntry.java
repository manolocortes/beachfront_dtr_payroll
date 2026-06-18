package com.payroll.model;

import javafx.beans.property.*;
import java.util.ArrayList;
import java.util.List;

public class PayrollEntry {
    private IntegerProperty id            = new SimpleIntegerProperty();
    private IntegerProperty payrollId     = new SimpleIntegerProperty();
    private IntegerProperty workerId      = new SimpleIntegerProperty();
    private StringProperty  workerName    = new SimpleStringProperty();
    private StringProperty  position      = new SimpleStringProperty();
    private DoubleProperty  dailyRate     = new SimpleDoubleProperty();
    private List<DayAttendance> dayAttendance = new ArrayList<>();
    private DoubleProperty  overtimeRate  = new SimpleDoubleProperty();
    private DoubleProperty  grossPay      = new SimpleDoubleProperty();
    private DoubleProperty  netPay        = new SimpleDoubleProperty();
    private int sortOrder = 0;

    public PayrollEntry()  { initDays(); }

    public PayrollEntry(int workerId, String workerName, String position, double dailyRate) {
        this.workerId.set(workerId); this.workerName.set(workerName);
        this.position.set(position); this.dailyRate.set(dailyRate);
        initDays();
    }

    private void initDays() {
        dayAttendance.clear();
        for (int i = 0; i < 7; i++) dayAttendance.add(new DayAttendance(i));
    }

    public void calculate() {
        double days  = dayAttendance.stream().mapToDouble(DayAttendance::effectiveDaysContribution).sum();
        double otH   = dayAttendance.stream().mapToDouble(DayAttendance::getOtHours).sum();
        double gross = dailyRate.get() * days + otH * overtimeRate.get();
        grossPay.set(gross);
        netPay.set(gross);
    }

    /** Effective days worked after undertime deductions. */
    public double getDaysWorked()      { return dayAttendance.stream().mapToDouble(DayAttendance::effectiveDaysContribution).sum(); }
    public double getOvertimeHours()   { return dayAttendance.stream().mapToDouble(DayAttendance::getOtHours).sum(); }
    public double getUndertimeHours()  { return dayAttendance.stream().mapToDouble(DayAttendance::getUtHours).sum(); }

    public int    getId()              { return id.get(); }
    public void   setId(int v)         { id.set(v); }
    public IntegerProperty idProperty(){ return id; }

    public int    getPayrollId()           { return payrollId.get(); }
    public void   setPayrollId(int v)      { payrollId.set(v); }

    public int    getWorkerId()            { return workerId.get(); }
    public void   setWorkerId(int v)       { workerId.set(v); }

    public String getWorkerName()          { return workerName.get(); }
    public void   setWorkerName(String v)  { workerName.set(v); }
    public StringProperty workerNameProperty() { return workerName; }

    public String getPosition()            { return position.get(); }
    public void   setPosition(String v)    { position.set(v); }
    public StringProperty positionProperty(){ return position; }

    public double getDailyRate()           { return dailyRate.get(); }
    public void   setDailyRate(double v)   { dailyRate.set(v); }
    public DoubleProperty dailyRateProperty(){ return dailyRate; }

    public List<DayAttendance> getDayAttendance()            { return dayAttendance; }
    public void setDayAttendance(List<DayAttendance> v)      { dayAttendance = v; }

    public double getOvertimeRate()          { return overtimeRate.get(); }
    public void   setOvertimeRate(double v)  { overtimeRate.set(v); }
    public DoubleProperty overtimeRateProperty() { return overtimeRate; }

    public double getGrossPay()              { return grossPay.get(); }
    public void   setGrossPay(double v)     { grossPay.set(v); }
    public DoubleProperty grossPayProperty(){ return grossPay; }

    public double getNetPay()               { return netPay.get(); }
    public void   setNetPay(double v)       { netPay.set(v); }
    public DoubleProperty netPayProperty()  { return netPay; }

    public int  getSortOrder()       { return sortOrder; }
    public void setSortOrder(int v)  { sortOrder = v; }
}
