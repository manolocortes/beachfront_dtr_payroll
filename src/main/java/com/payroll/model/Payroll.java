package com.payroll.model;
import javafx.beans.property.*;
import java.time.LocalDate;
import java.util.*;
public class Payroll {
    private IntegerProperty id          = new SimpleIntegerProperty();
    private IntegerProperty projectId   = new SimpleIntegerProperty();
    private StringProperty  projectName = new SimpleStringProperty();
    private ObjectProperty<LocalDate> periodStart = new SimpleObjectProperty<>();
    private ObjectProperty<LocalDate> periodEnd   = new SimpleObjectProperty<>();
    private ObjectProperty<LocalDate> payDate     = new SimpleObjectProperty<>();
    private StringProperty  preparedBy  = new SimpleStringProperty();
    private StringProperty  approvedBy  = new SimpleStringProperty();
    private StringProperty  status      = new SimpleStringProperty("DRAFT");
    private DoubleProperty  totalAmount = new SimpleDoubleProperty();
    private List<PayrollEntry> entries  = new ArrayList<>();
    public Payroll() {}
    public Payroll(int id,int projectId,String projectName,LocalDate periodStart,LocalDate periodEnd,
                   LocalDate payDate,String preparedBy,String approvedBy,String status,double totalAmount){
        this.id.set(id);this.projectId.set(projectId);this.projectName.set(projectName);
        this.periodStart.set(periodStart);this.periodEnd.set(periodEnd);this.payDate.set(payDate);
        this.preparedBy.set(preparedBy);this.approvedBy.set(approvedBy);
        this.status.set(status);this.totalAmount.set(totalAmount);
    }
    public int    getId()                   { return id.get(); }
    public void   setId(int v)              { id.set(v); }
    public IntegerProperty idProperty()     { return id; }
    public int    getProjectId()            { return projectId.get(); }
    public void   setProjectId(int v)       { projectId.set(v); }
    public String getProjectName()          { return projectName.get(); }
    public void   setProjectName(String v)  { projectName.set(v); }
    public StringProperty projectNameProperty(){ return projectName; }
    public LocalDate getPeriodStart()       { return periodStart.get(); }
    public void   setPeriodStart(LocalDate v){ periodStart.set(v); }
    public ObjectProperty<LocalDate> periodStartProperty(){ return periodStart; }
    public LocalDate getPeriodEnd()         { return periodEnd.get(); }
    public void   setPeriodEnd(LocalDate v) { periodEnd.set(v); }
    public ObjectProperty<LocalDate> periodEndProperty()  { return periodEnd; }
    public LocalDate getPayDate()           { return payDate.get(); }
    public void   setPayDate(LocalDate v)   { payDate.set(v); }
    public ObjectProperty<LocalDate> payDateProperty()    { return payDate; }
    public String getPreparedBy()           { return preparedBy.get(); }
    public void   setPreparedBy(String v)   { preparedBy.set(v); }
    public StringProperty preparedByProperty(){ return preparedBy; }
    public String getApprovedBy()           { return approvedBy.get(); }
    public void   setApprovedBy(String v)   { approvedBy.set(v); }
    public StringProperty approvedByProperty(){ return approvedBy; }
    public String getStatus()               { return status.get(); }
    public void   setStatus(String v)       { status.set(v); }
    public StringProperty statusProperty()  { return status; }
    public double getTotalAmount()          { return totalAmount.get(); }
    public void   setTotalAmount(double v)  { totalAmount.set(v); }
    public DoubleProperty totalAmountProperty(){ return totalAmount; }
    public List<PayrollEntry> getEntries()  { return entries; }
    public void setEntries(List<PayrollEntry> v){ entries = v; }
}
