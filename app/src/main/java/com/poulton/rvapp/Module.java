package com.poulton.rvapp;

/** Feature definitions shared by forms and storage. Keep persisted keys stable. */
public enum Module {
    VEHICLE("My RV", new String[]{"Length m", "Height m", "Width m", "Weight kg", "Axle count", "Max axle load kg"}),
    PLANS("Route plans", new String[]{"Name", "Date", "Trip", "Route stops", "Route type", "Route points", "Distance miles", "Drive minutes", "RV profile", "Notes"}),
    PLACES("Places visited", new String[]{"Name", "Date", "Latitude", "Longitude", "Address", "Trip", "Notes"}),
    ROUTES("Routes travelled", new String[]{"Name", "Date", "Trip", "Route type", "Route points", "Notes"}),
    TRIPS("Trip logs", new String[]{"Name", "Date", "From", "To", "Start miles", "End miles", "Notes"}),
    CHECKS("Pre-trip inspection checklist", new String[]{"Item", "Notes"}),
    ARRIVAL("Arrival / setup checklist", new String[]{"Item", "Notes"}),
    FUEL("Fuel", new String[]{"Date", "Odometer miles", "Litres", "Cost GBP", "Station", "Notes"}),
    EXPENSES("Expenses", new String[]{"Date", "Category", "Cost GBP", "Description"}),
    MAINTENANCE("Maintenance", new String[]{"Work", "Date", "Odometer miles", "Cost GBP", "Provider", "Notes"}),
    CAMPSITES("Campsites", new String[]{"Name", "Address", "Arrival date", "Departure date", "Pitch", "Contact", "Facilities", "Max length m", "Max height m", "Max width m", "Max weight kg", "Availability", "Availability checked date", "Notes"}),
    PHOTOS("Photos", new String[]{"Caption", "Date", "Trip or campsite", "Notes"}),
    MILEAGE("Mileage", new String[]{"Date", "Odometer miles", "Notes"}),
    REMINDERS("Service reminders", new String[]{"Service", "Due date", "Due miles", "Repeat months", "Repeat miles", "Notes"});

    public boolean isChecklist() { return this == CHECKS || this == ARRIVAL; }
    public boolean required(String field) {
        if(this==VEHICLE)return java.util.Arrays.asList(VehicleProfile.FIELDS).contains(field);
        if (field.equals(fields[0])) return true;
        if (this == PLACES && (field.equals("Latitude") || field.equals("Longitude"))) return true;
        if ((this == ROUTES || this == PLANS) && (field.equals("Route type") || field.equals("Route points"))) return true;
        return (this == MILEAGE && field.equals("Odometer miles")) ||
               ((this == FUEL || this == EXPENSES) && field.equals("Cost GBP"));
    }
    public final String title;
    public final String[] fields;
    Module(String title, String[] fields) { this.title = title; this.fields = fields; }
}
