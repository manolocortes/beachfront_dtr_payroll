package com.payroll.model;
import javafx.beans.property.*;
import java.time.LocalDate;
public class Project {
    private IntegerProperty id          = new SimpleIntegerProperty();
    private StringProperty  name        = new SimpleStringProperty();
    private StringProperty  location    = new SimpleStringProperty();
    private StringProperty  client      = new SimpleStringProperty();
    private ObjectProperty<LocalDate> startDate = new SimpleObjectProperty<>();
    private ObjectProperty<LocalDate> endDate   = new SimpleObjectProperty<>();
    private StringProperty  status      = new SimpleStringProperty("ACTIVE");
    private StringProperty  description = new SimpleStringProperty();
    public Project() {}
    public Project(int id,String name,String location,String client,LocalDate startDate,LocalDate endDate,String status,String description){
        this.id.set(id);this.name.set(name);this.location.set(location);this.client.set(client);
        this.startDate.set(startDate);this.endDate.set(endDate);this.status.set(status);this.description.set(description);
    }
    public int    getId()                   { return id.get(); }
    public void   setId(int v)              { id.set(v); }
    public IntegerProperty idProperty()     { return id; }
    public String getName()                 { return name.get(); }
    public void   setName(String v)         { name.set(v); }
    public StringProperty nameProperty()    { return name; }
    public String getLocation()             { return location.get(); }
    public void   setLocation(String v)     { location.set(v); }
    public StringProperty locationProperty(){ return location; }
    public String getClient()               { return client.get(); }
    public void   setClient(String v)       { client.set(v); }
    public StringProperty clientProperty()  { return client; }
    public LocalDate getStartDate()         { return startDate.get(); }
    public void   setStartDate(LocalDate v) { startDate.set(v); }
    public ObjectProperty<LocalDate> startDateProperty(){ return startDate; }
    public LocalDate getEndDate()           { return endDate.get(); }
    public void   setEndDate(LocalDate v)   { endDate.set(v); }
    public ObjectProperty<LocalDate> endDateProperty()  { return endDate; }
    public String getStatus()               { return status.get(); }
    public void   setStatus(String v)       { status.set(v); }
    public StringProperty statusProperty()  { return status; }
    public String getDescription()          { return description.get(); }
    public void   setDescription(String v)  { description.set(v); }
    public StringProperty descriptionProperty(){ return description; }
    @Override public String toString()      { return name.get(); }
}
