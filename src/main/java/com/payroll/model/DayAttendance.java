package com.payroll.model;

import javafx.beans.property.*;

public class DayAttendance {
    private final int dayIndex;
    private BooleanProperty am      = new SimpleBooleanProperty(false);
    private BooleanProperty pm      = new SimpleBooleanProperty(false);
    private DoubleProperty  otHours = new SimpleDoubleProperty(0);

    public DayAttendance(int dayIndex) { this.dayIndex = dayIndex; }

    public DayAttendance(int dayIndex, boolean am, boolean pm, double otHours) {
        this.dayIndex = dayIndex;
        this.am.set(am); this.pm.set(pm); this.otHours.set(otHours);
    }

    public int    getDayIndex()             { return dayIndex; }
    public boolean isAm()                  { return am.get(); }
    public void   setAm(boolean v)         { am.set(v); }
    public BooleanProperty amProperty()    { return am; }
    public boolean isPm()                  { return pm.get(); }
    public void   setPm(boolean v)         { pm.set(v); }
    public BooleanProperty pmProperty()    { return pm; }
    public double getOtHours()             { return otHours.get(); }
    public void   setOtHours(double v)     { otHours.set(v); }
    public DoubleProperty otHoursProperty(){ return otHours; }

    public double daysContribution() {
        if (am.get() && pm.get()) return 1.0;
        if (am.get() || pm.get()) return 0.5;
        return 0.0;
    }

    public String serialize() {
        return (am.get() ? "1" : "0") + "," + (pm.get() ? "1" : "0") + "," + otHours.get();
    }

    public static DayAttendance deserialize(int idx, String s) {
        if (s == null || s.isBlank()) return new DayAttendance(idx);
        try {
            String[] p = s.split(",");
            boolean a = "1".equals(p[0]);
            boolean pm = p.length > 1 && "1".equals(p[1]);
            double ot  = p.length > 2 ? Double.parseDouble(p[2]) : 0;
            return new DayAttendance(idx, a, pm, ot);
        } catch (Exception e) { return new DayAttendance(idx); }
    }
}
