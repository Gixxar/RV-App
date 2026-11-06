package com.poulton.rvapp;

import org.json.JSONObject;
import java.time.LocalDate;
import java.util.UUID;
import android.database.sqlite.SQLiteDatabase;

/** Atomic trip snapshots and service completion; IDs survive portable backups. */
final class TripWorkflow {
    static final String[] STATES={"Not started","Done","Not applicable","Needs attention"};
    static void put(JSONObject data,String key,Object value){try{data.put(key,value);}catch(Exception e){throw new IllegalArgumentException(e);}}
    static String identify(JSONObject data,String key){if(data.optString(key).isEmpty())put(data,key,UUID.randomUUID().toString());return data.optString(key);}
    static LogStore.Entry find(LogStore store,Module module,long id){for(LogStore.Entry e:store.list(module))if(e.id==id)return e;throw new IllegalArgumentException("Record no longer exists");}
    static LogStore.Entry trip(LogStore store,String key){for(LogStore.Entry e:store.list(Module.TRIPS))if(key.equals(e.data.optString("Trip ID")))return e;throw new IllegalArgumentException("Trip no longer exists");}
    static void advance(LogStore store,String key,String status){
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{
            LogStore.Entry trip=trip(store,key);String current=trip.data.optString("Status","Planned");
            if(!((current.equals("Planned")&&status.equals("In progress"))||(current.equals("In progress")&&status.equals("Arrived"))||(current.equals("Arrived")&&status.equals("Finished"))))throw new IllegalArgumentException("Trip status changed; reopen the trip");
            if(status.equals("In progress"))snapshot(store,trip,"Departure",Module.CHECKS);
            if(status.equals("Arrived"))snapshot(store,trip,"Arrival",Module.ARRIVAL);
            if(status.equals("Finished"))put(trip.data,"End date",LocalDate.now().toString());
            put(trip.data,"Status",status);store.save(Module.TRIPS,trip.id,trip.data);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private static void snapshot(LogStore store,LogStore.Entry trip,String phase,Module template){
        String flag=phase+" checklist created";if(trip.data.optString(flag).equals("yes"))return;
        java.util.List<LogStore.Entry> rows=store.list(template);java.util.Collections.reverse(rows);
        for(LogStore.Entry item:rows){JSONObject data=new JSONObject();put(data,"Item",item.data.optString("Item"));put(data,"Notes",item.data.optString("Notes"));put(data,"Trip ID",trip.data.optString("Trip ID"));put(data,"Phase",phase);put(data,"Status","Not started");store.save(Module.TRIP_CHECKS,0,data);}
        put(trip.data,flag,"yes");
    }
    static void complete(LogStore store,long id,JSONObject work){
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{
            LogStore.Entry entry=find(store,Module.REMINDERS,id);String key=identify(entry.data,"Reminder ID");
            if(entry.data.optBoolean("done"))throw new IllegalArgumentException("Service already completed");
            double current=store.latestMileage();
            if(work!=null){put(work,"Source reminder ID",key);put(work,"Trip ID",entry.data.optString("Trip ID"));store.save(Module.MAINTENANCE,0,work);}
            put(entry.data,"done",true);store.save(Module.REMINDERS,id,entry.data);
            int months=entry.data.optInt("Repeat months",0);double miles=entry.data.optDouble("Repeat miles",0);
            if(months>0||miles>0){JSONObject next=new JSONObject(entry.data.toString());next.remove("Reminder ID");put(next,"done",false);put(next,"Due date",months>0?LocalDate.now().plusMonths(months).toString():"");put(next,"Due miles",miles>0?String.valueOf(current+miles):"");store.save(Module.REMINDERS,0,next);}
            db.setTransactionSuccessful();
        }catch(org.json.JSONException e){throw new IllegalArgumentException(e);}finally{db.endTransaction();}
    }
}
