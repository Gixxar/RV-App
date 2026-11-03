package com.poulton.rvapp;

/** Feature definitions shared by forms and storage. Keep persisted keys stable. */
public enum Module {
    PLACES("Places visited", new String[]{"Name", "Date", "Latitude", "Longitude", "Address", "Trip", "Notes"}),
    ROUTES("Routes travelled", new String[]{"Name", "Date", "Trip", "Route type", "Route points", "Notes"}),
    TRIPS("Trip logs", new String[]{"Name", "Date", "From", "To", "Start miles", "End miles", "Notes"}),
    CHECKS("Pre-departure checklist", new String[]{"Item", "Notes"}),
    FUEL("Fuel", new String[]{"Date", "Odometer miles", "Litres", "Cost GBP", "Station", "Notes"}),
    EXPENSES("Expenses", new String[]{"Date", "Category", "Cost GBP", "Description"}),
    MAINTENANCE("Maintenance", new String[]{"Work", "Date", "Odometer miles", "Cost GBP", "Provider", "Notes"}),
    CAMPSITES("Campsites", new String[]{"Name", "Address", "Arrival date", "Departure date", "Pitch", "Contact", "Facilities", "Notes"}),
    PHOTOS("Photos", new String[]{"Caption", "Date", "Trip or campsite", "Notes"}),
    MILEAGE("Mileage", new String[]{"Date", "Odometer miles", "Notes"}),
    REMINDERS("Service reminders", new String[]{"Service", "Due date", "Due miles", "Repeat months", "Repeat miles", "Notes"});

    public boolean required(String field) {
        if (field.equals(fields[0])) return true;
        if (this == PLACES && (field.equals("Latitude") || field.equals("Longitude"))) return true;
        if (this == ROUTES && (field.equals("Route type") || field.equals("Route points"))) return true;
        return (this == MILEAGE && field.equals("Odometer miles")) ||
               ((this == FUEL || this == EXPENSES) && field.equals("Cost GBP"));
    }
    public final String title;
    public final String[] fields;
    Module(String title, String[] fields) { this.title = title; this.fields = fields; }
}
