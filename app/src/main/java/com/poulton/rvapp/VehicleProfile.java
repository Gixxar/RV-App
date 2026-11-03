package com.poulton.rvapp;

import org.json.*;
import java.util.*;

/** Metric RV measurements and comparisons against user-recorded campsite limits. */
public final class VehicleProfile {
    private VehicleProfile() {}
    public static final String[] FIELDS={"Length m","Height m","Width m","Weight kg"};
    public static final String[] OPTIONAL={"Axle count","Max axle load kg"};
    public static final String[] LIMITS={"Max length m","Max height m","Max width m","Max weight kg"};
    public static final String[] AVAILABILITY={"Not checked","Enquiry sent","Spaces reported","Fully booked"};
    public static final String RV_NOTICE="Uses your dimensions with mapped truck restrictions. Map data may be missing or wrong; truck rules can differ from RV rules. Enter optional axle details to refine axle checks. Check signs, clearances and campsite access before driving.";
    public static JSONObject current(LogStore store){List<LogStore.Entry> rows=store.list(Module.VEHICLE);return rows.isEmpty()?new JSONObject():rows.get(0).data;}
    public static String error(String field,String value){
        if(field.equals("Axle count")){if(value.isEmpty())return null;try{int n=Integer.parseInt(value);return n>=2&&n<=20?null:"Use a whole axle count from 2 to 20";}catch(Exception e){return "Use a whole axle count from 2 to 20";}}
        if(field.equals("Max axle load kg")){if(value.isEmpty())return null;try{double n=Double.parseDouble(value);return Double.isFinite(n)&&n>0&&n<=40000?null:"Use a value above 0 and up to 40,000 kg";}catch(Exception e){return "Enter a load in kilograms";}}
        int i=Arrays.asList(FIELDS).indexOf(field);if(i<0)i=Arrays.asList(LIMITS).indexOf(field);if(i<0)return null;
        if(value.isEmpty())return null;
        try{double n=Double.parseDouble(value);double max=i==0?50:i==3?100000:10;if(!Double.isFinite(n)||n<=0||n>max)return "Use a value above 0 and up to "+(int)max+(i==3?" kg":" metres");}catch(Exception e){return "Enter a number using a decimal point";}return null;
    }
    public static JSONObject normalized(JSONObject profile)throws JSONException{
        JSONObject result=new JSONObject();for(String field:FIELDS){if(!profile.has(field)||!(profile.get(field) instanceof String)||profile.getString(field).trim().isEmpty())throw new JSONException("Enter all four RV measurements");String value=profile.getString(field).trim();String error=error(field,value);if(error!=null)throw new JSONException(field+": "+error);result.put(field,Double.toString(Double.parseDouble(value)));}for(String field:OPTIONAL){if(profile.has(field)){if(!(profile.get(field) instanceof String))throw new JSONException("Invalid axle field type");String value=profile.getString(field).trim();if(!value.isEmpty()){String error=error(field,value);if(error!=null)throw new JSONException(error);result.put(field,field.equals("Axle count")?Integer.toString(Integer.parseInt(value)):Double.toString(Double.parseDouble(value)));}}}return result;
    }
    public static boolean complete(JSONObject profile){try{normalized(profile);return true;}catch(Exception e){return false;}}
    public static boolean same(JSONObject a,JSONObject b){try{return normalized(a).toString().equals(normalized(b).toString());}catch(Exception e){return false;}}
    public static String axleNotice(JSONObject profile){return (profile.optString("Axle count").isEmpty()?"Axle count not entered: truck default 5 used. ":"Axles: "+profile.optString("Axle count")+". ")+(profile.optString("Max axle load kg").isEmpty()?"Axle-load limits not checked.":"Max axle load: "+profile.optString("Max axle load kg")+" kg.");}
    public static String summary(JSONObject profile){try{JSONObject p=normalized(profile);return String.format(Locale.UK,"L %.2f m · H %.2f m · W %.2f m · %,.0f kg",p.getDouble(FIELDS[0]),p.getDouble(FIELDS[1]),p.getDouble(FIELDS[2]),p.getDouble(FIELDS[3]));}catch(Exception e){return "Add your RV length, height, width and loaded weight";}}
    public static final class Fit {public final int status;public final String message;Fit(int status,String message){this.status=status;this.message=message;}}
    // 0 needs confirmation, 1 within all entered limits, 2 exceeds at least one entered limit.
    public static Fit fit(JSONObject site,JSONObject profile){
        if(!complete(profile))return new Fit(0,"RV fit unknown · add your RV dimensions");
        ArrayList<String> missing=new ArrayList<>(),over=new ArrayList<>(),boundary=new ArrayList<>();
        for(int i=0;i<FIELDS.length;i++){
            String value=site.optString(LIMITS[i]).trim(),label=FIELDS[i].replace(" m","").replace(" kg","").toLowerCase(Locale.ROOT);
            if(value.isEmpty()||error(LIMITS[i],value)!=null){missing.add(label);continue;}
            double actual=profile.optDouble(FIELDS[i]),limit=Double.parseDouble(value);
            if(actual>limit+1e-9)over.add(label);else if(Math.abs(actual-limit)<1e-9)boundary.add(label);
        }
        if(!over.isEmpty())return new Fit(2,"Exceeds recorded site limit: "+String.join(", ",over));
        if(!boundary.isEmpty())return new Fit(0,"At recorded limit: "+String.join(", ",boundary)+" · confirm clearance with campsite");
        if(!missing.isEmpty())return new Fit(0,"RV fit partly checked · confirm "+String.join(", ",missing)+" limits");
        return new Fit(1,"Within all four recorded limits · confirm access and pitch space");
    }
    public static String availability(JSONObject site){
        String status=site.optString("Availability","Not checked");if(status.isEmpty())status="Not checked";
        String checked=site.optString("Availability checked date");
        return "Pitch availability: "+status+(checked.isEmpty()?"":" · recorded "+checked)+". Contact campsite for current availability.";
    }
}
