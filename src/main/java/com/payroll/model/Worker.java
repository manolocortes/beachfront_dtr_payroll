package com.payroll.model;
import javafx.beans.property.*;
public class Worker {
    private IntegerProperty id        = new SimpleIntegerProperty();
    private StringProperty  name      = new SimpleStringProperty();
    private StringProperty  position  = new SimpleStringProperty();
    private DoubleProperty  dailyRate = new SimpleDoubleProperty();
    private BooleanProperty active    = new SimpleBooleanProperty(true);
    private int sortOrder = 0;
    public Worker() {}
    public Worker(int id,String name,String position,double dailyRate,boolean active){
        this.id.set(id);this.name.set(name);this.position.set(position);
        this.dailyRate.set(dailyRate);this.active.set(active);
    }
    public int     getId()                  { return id.get(); }
    public void    setId(int v)             { id.set(v); }
    public IntegerProperty idProperty()     { return id; }
    public String  getName()                { return name.get(); }
    public void    setName(String v)        { name.set(v); }
    public StringProperty nameProperty()    { return name; }
    public String  getPosition()            { return position.get(); }
    public void    setPosition(String v)    { position.set(v); }
    public StringProperty positionProperty(){ return position; }
    public double  getDailyRate()           { return dailyRate.get(); }
    public void    setDailyRate(double v)   { dailyRate.set(v); }
    public DoubleProperty dailyRateProperty(){ return dailyRate; }
    public boolean isActive()               { return active.get(); }
    public void    setActive(boolean v)     { active.set(v); }
    public BooleanProperty activeProperty() { return active; }
    public int  getSortOrder()      { return sortOrder; }
    public void setSortOrder(int v) { sortOrder = v; }
    @Override public String toString()      { return name.get(); }
}