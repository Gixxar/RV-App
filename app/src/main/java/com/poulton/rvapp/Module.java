package com.poulton.rvapp;

/** Feature definitions shared by forms and storage. Keep persisted keys stable. */
public enum Module {
    TRIPS("Trip logs", new String[]{"Name", "Date", "From", "To", "Start miles", "End miles", "Notes"}),
    CHECKS("Pre-departure checklist", new String[]{"Item", "Notes"}),
    FUEL("Fuel", new String[]{"Date", "Odometer miles", "Litres", "Cost GBP", "Station", "Notes"}),
    EXPENSES("Expenses", new String[]{"Date", "Category", "Cost GBP", "Description"}),
    MAINTENANCE("Maintenance", new String[]{"Work", "Date", "Odometer miles", "Cost GBP", "Provider", "Notes"}),
    CAMPSITES("Campsites", new String[]{"Name", "Address", "Arrival date", "Departure date", "Pitch", "Contact", "Facilities", "Notes"}),
    PHOTOS("Photos", new String[]{"Caption", "Date", "Trip or campsite", "Notes"}),
    MILEAGE("Mileage", new String[]{"Date", "Odometer miles", "Notes"}),
    REMINDERS("Service reminders", new String[]{"Service", "Due date", "Due miles", "Notes"});

    public final String title;
    public final String[] fields;
    Module(String title, String[] fields) { this.title = title; this.fields = fields; }
}
