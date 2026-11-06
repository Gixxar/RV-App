package com.poulton.rvapp;

import java.time.LocalDate;
import java.util.Map;

/** Rules shared by interactive forms and backup imports. */
public final class Rules {
    private Rules() {}
    public static String validate(String field, String value) {
        if (value.isEmpty()) return null;
        String dimensionError=VehicleProfile.error(field,value);if(dimensionError!=null)return dimensionError;
        if(field.equals("RV profile")){try{VehicleProfile.normalized(new org.json.JSONObject(value));}catch(Exception e){return "Invalid RV measurements";}}
        if(field.equals("Availability")&&!java.util.Arrays.asList(VehicleProfile.AVAILABILITY).contains(value))return "Choose a listed availability status";
        if (field.equals("Latitude") || field.equals("Longitude")) {
            try {
                double n=Double.parseDouble(value);double max=field.equals("Latitude")?90:180;
                if(!Double.isFinite(n)||Math.abs(n)>max)return "Use a coordinate between -"+max+" and "+max;
            } catch(NumberFormatException e){return "Enter a decimal coordinate";}
        }
        if (field.equals("Route points")) {
            try { GeoData.parse(value); } catch(Exception e){return "Import a GPX track or draw a route on the map";}
        }
        if(field.equals("Route stops")){try{RouteService.stops(value);}catch(Exception e){return "Choose 2–10 valid route stops";}}
        if (field.equals("Route type") && !value.equals(RouteService.TYPE) && !value.equals(RvRouting.TYPE) && !value.equals("GPX track") && !value.equals("GPX route") && !value.equals("Manual waypoints")) return "Invalid route type";
        if (field.toLowerCase(java.util.Locale.ROOT).contains("date")) {
            try { LocalDate parsed=LocalDate.parse(value);if((field.equals("Availability checked date")||field.equals("Last service date"))&&parsed.isAfter(LocalDate.now()))return "Use today or an earlier checked date"; } catch (RuntimeException e) { return "Use a valid date: YYYY-MM-DD"; }
        }
        if (isNumber(field)) {
            try {
                double n = Double.parseDouble(value);
                if (!Double.isFinite(n) || n < 0 || n > 1_000_000_000) return "Enter a number between 0 and 1,000,000,000";
            } catch (NumberFormatException e) { return "Enter a number using a decimal point"; }
        }
        if (field.equals("Repeat months") && !value.matches("[0-9]{1,3}")) return "Use whole months (0–999)";
        return null;
    }
    public static boolean isNumber(String field) {
        return field.contains("miles") || field.equals("Litres") || field.equals("Cost GBP") || field.equals("Repeat months") || field.equals("Drive minutes") || java.util.Arrays.asList(VehicleProfile.FIELDS).contains(field) || java.util.Arrays.asList(VehicleProfile.LIMITS).contains(field) || java.util.Arrays.asList(VehicleProfile.OPTIONAL).contains(field);
    }
    public static boolean due(String date, String miles, double current, LocalDate today) {
        return (!date.isEmpty() && !LocalDate.parse(date).isAfter(today)) || (!miles.isEmpty() && Double.parseDouble(miles) <= current);
    }
    public static String validateRecord(Module module, Map<String, String> values) {
        for (String field : module.fields) {
            String value = values.getOrDefault(field, "");
            if (module.required(field) && value.isEmpty()) return field + " is required";
            String error = validate(field,value);
            if (error != null) return field + ": " + error;
        }
        if(module==Module.TRIPS&&!values.getOrDefault("Status","").isEmpty()&&!java.util.Arrays.asList("Planned","In progress","Arrived","Finished").contains(values.get("Status")))return "Invalid trip status";
        if(module==Module.TRIP_CHECKS){if(values.getOrDefault("Trip ID","").isEmpty())return "Trip ID is required";if(!java.util.Arrays.asList("Departure","Arrival").contains(values.get("Phase")))return "Invalid checklist phase";if(!java.util.Arrays.asList(TripWorkflow.STATES).contains(values.get("Status")))return "Invalid checklist status";}
        if(module==Module.TRIPS&&both(values,"Date","End date")&&LocalDate.parse(values.get("End date")).isBefore(LocalDate.parse(values.get("Date"))))return "End date must be on or after start date";
        if(module==Module.PLANS){try{RouteService.validatePlan(values);}catch(Exception e){return "Calculate a valid road route before saving";}}
        if(module==Module.ROUTES && (RouteService.TYPE.equals(values.get("Route type"))||RvRouting.TYPE.equals(values.get("Route type"))))return "Save planned routes under Route plans";
        if(module==Module.CAMPSITES && ("Spaces reported".equals(values.get("Availability"))||"Fully booked".equals(values.get("Availability"))) && values.getOrDefault("Availability checked date","").isEmpty())return "Record the availability checked date";
        if (module == Module.TRIPS && both(values,"Start miles","End miles") &&
                Double.parseDouble(values.get("End miles")) < Double.parseDouble(values.get("Start miles"))) return "End miles must be at least start miles";
        if (module == Module.CAMPSITES && both(values,"Arrival date","Departure date") &&
                LocalDate.parse(values.get("Departure date")).isBefore(LocalDate.parse(values.get("Arrival date")))) return "Departure must be on or after arrival";
        if (module == Module.REMINDERS && !bothOrEither(values,"Due date","Due miles")) return "Enter a due date or due mileage";
        return null;
    }
    private static boolean both(Map<String,String> v, String a, String b) { return !v.getOrDefault(a,"").isEmpty() && !v.getOrDefault(b,"").isEmpty(); }
    private static boolean bothOrEither(Map<String,String> v, String a, String b) { return !v.getOrDefault(a,"").isEmpty() || !v.getOrDefault(b,"").isEmpty(); }
}
