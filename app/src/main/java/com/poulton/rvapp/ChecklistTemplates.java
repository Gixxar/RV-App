package com.poulton.rvapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** The two checklists transcribed from the user's Poulton RV poster. */
public final class ChecklistTemplates {
    public static final int VERSION=1;
    public static final String[] PRE_TRIP={
        "Engine Oil Level", "Coolant Level", "Transmission Fluid", "Power Steering Fluid",
        "Brake Fluid", "Wiper Fluid", "Battery Condition", "Tire Pressure / Condition",
        "All Lights (Interior/Exterior)", "Horn", "Windshield Wipers", "Mirrors",
        "Awning Secure", "Slide Outs Function", "Steps Operate", "Entry Door Lock",
        "Propane Level", "Generator Oil Level", "Sewer Hose", "Water Hose",
        "Surge Protector", "Fire Extinguisher", "First Aid Kit", "Emergency Kit / Tools"
    };
    public static final String[] ARRIVAL={
        "Arrived Safely", "Site Level", "Parking Brake Set", "Slides Out / In (if used)",
        "Awning Out / In", "Water Connected", "Sewer Connected", "Power Connected (50 Amp)",
        "Generator (if used)", "Satellite / TV Setup", "Camp Chairs / Table Out", "Site Secured"
    };
    private static final Set<String> LEGACY=new HashSet<>(Arrays.asList(
        "Check tyres and lights", "Secure cupboards and loose items", "Disconnect shore power and water",
        "Retract steps and awning", "Check doors and roof vents", "Retract levelling jacks and check warning lights",
        "Retract slide-outs and secure travel locks", "Check engine oil, coolant and air pressure",
        "Check tyre pressures to vehicle specification", "Secure LPG and appliances for travel", "Walk around RV and check clearance"
    ));
    private ChecklistTemplates(){}
    public static void apply(SQLiteDatabase db,boolean replaceOldStarters){
        if(replaceOldStarters){
            List<Long> remove=new ArrayList<>();
            try(Cursor c=db.query("entries",new String[]{"id","data"},"module=?",new String[]{Module.CHECKS.name()},null,null,null)){
                while(c.moveToNext()){
                    JSONObject data=parse(c.getString(1));boolean originalFields=true;
                    for(Iterator<String> keys=data.keys();keys.hasNext();)if(!Arrays.asList("Item","Notes","done").contains(keys.next()))originalFields=false;
                    if(originalFields&&LEGACY.contains(data.optString("Item"))&&data.optString("Notes").trim().isEmpty()&&!data.optBoolean("done"))remove.add(c.getLong(0));
                }
            }
            for(long id:remove)db.delete("entries","id=?",new String[]{String.valueOf(id)});
        }
        seed(db,Module.CHECKS,PRE_TRIP);seed(db,Module.ARRIVAL,ARRIVAL);
    }
    private static JSONObject parse(String value){try{return new JSONObject(value);}catch(JSONException e){throw new IllegalStateException("Invalid stored checklist record",e);}}
    private static void seed(SQLiteDatabase db,Module module,String[] items){
        Set<String> existing=new HashSet<>();
        try(Cursor c=db.query("entries",new String[]{"data"},"module=?",new String[]{module.name()},null,null,null)){
            while(c.moveToNext())existing.add(parse(c.getString(0)).optString("Item").trim().toLowerCase(Locale.ROOT));
        }
        // Other modules keep newest-first order; reverse inserts keep poster order for checklists.
        for(int i=items.length-1;i>=0;i--)if(existing.add(items[i].toLowerCase(Locale.ROOT))){
            JSONObject data=new JSONObject();try{data.put("Item",items[i]);data.put("done",false);}catch(JSONException e){throw new IllegalStateException(e);}
            ContentValues row=new ContentValues();row.put("module",module.name());row.put("data",data.toString());db.insertOrThrow("entries",null,row);
        }
    }
}
