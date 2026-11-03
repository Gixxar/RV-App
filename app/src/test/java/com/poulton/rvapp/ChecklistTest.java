package com.poulton.rvapp;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.widget.CheckBox;
import android.view.*;
import android.app.AlertDialog;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ChecklistTest {
    private Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("rv-logbook.db");}
    @Test public void freshListsMatchAllPosterItemsInOrder(){
        try(LogStore s=new LogStore(context)){
            assertArrayEquals(ChecklistTemplates.PRE_TRIP,items(s,Module.CHECKS));assertEquals(24,s.list(Module.CHECKS).size());
            assertArrayEquals(ChecklistTemplates.ARRIVAL,items(s,Module.ARRIVAL));assertEquals(12,s.list(Module.ARRIVAL).size());
        }
    }
    @Test public void tickingAndResettingOneListDoesNotAffectTheOther() throws Exception {
        try(LogStore s=new LogStore(context)){
            for(Module module:new Module[]{Module.CHECKS,Module.ARRIVAL}){LogStore.Entry e=s.list(module).get(0);e.data.put("done",true);s.save(module,e.id,e.data);}
            s.resetChecklist(Module.CHECKS);assertFalse(s.list(Module.CHECKS).get(0).data.optBoolean("done"));assertTrue(s.list(Module.ARRIVAL).get(0).data.optBoolean("done"));
        }
        try(LogStore s=new LogStore(context)){assertTrue(s.list(Module.ARRIVAL).get(0).data.optBoolean("done"));s.resetChecklist(Module.ARRIVAL);assertFalse(s.list(Module.ARRIVAL).get(0).data.optBoolean("done"));}
    }
    @Test public void upgradeReplacesUntouchedStartersButPreservesCustomCheckedAndNotedRecords() throws Exception {
        try(SQLiteDatabase db=context.openOrCreateDatabase("rv-logbook.db",0,null)){
            db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, module TEXT NOT NULL, data TEXT NOT NULL)");
            db.execSQL("CREATE INDEX entries_module ON entries(module)");
            raw(db,Module.CHECKS,new JSONObject().put("Item","Check tyres and lights"));
            raw(db,Module.CHECKS,new JSONObject().put("Item","Check doors and roof vents").put("done",true));
            raw(db,Module.CHECKS,new JSONObject().put("Item","Water Hose").put("Notes","My blue hose").put("done",true));
            raw(db,Module.CHECKS,new JSONObject().put("Item","My custom check"));
            raw(db,Module.TRIPS,new JSONObject().put("Name","Preserve trip"));db.setVersion(1);
        }
        try(LogStore s=new LogStore(context)){
            assertEquals(2,s.getReadableDatabase().getVersion());List<String> checks=Arrays.asList(items(s,Module.CHECKS));
            assertFalse(checks.contains("Check tyres and lights"));assertTrue(checks.contains("My custom check"));assertTrue(checks.contains("Check doors and roof vents"));assertEquals(26,checks.size());
            LogStore.Entry hose=s.list(Module.CHECKS).stream().filter(e->e.data.optString("Item").equals("Water Hose")).findFirst().get();
            assertTrue(hose.data.optBoolean("done"));assertEquals("My blue hose",hose.data.optString("Notes"));
            assertEquals(12,s.list(Module.ARRIVAL).size());assertEquals("Preserve trip",s.list(Module.TRIPS).get(0).data.optString("Name"));
        }
    }
    @Test public void reopeningDoesNotReseedDeletedOrEditedPosterItems() throws Exception {
        try(LogStore s=new LogStore(context)){s.delete(s.list(Module.ARRIVAL).get(0).id);LogStore.Entry e=s.list(Module.CHECKS).get(0);e.data.put("Item","My oil check");s.save(Module.CHECKS,e.id,e.data);}
        try(LogStore s=new LogStore(context)){assertEquals(11,s.list(Module.ARRIVAL).size());assertEquals(24,s.list(Module.CHECKS).size());assertEquals("My oil check",s.list(Module.CHECKS).get(0).data.optString("Item"));}
    }
    @Test public void restoringOlderBackupAddsPosterListsAndPreservesItsTripAndCustomCheck() throws Exception {
        JSONObject backup=new JSONObject().put("format","poulton-rv-logbook").put("version",1).put("records",new JSONArray()
            .put(row(Module.CHECKS,new JSONObject().put("Item","Check tyres and lights")))
            .put(row(Module.CHECKS,new JSONObject().put("Item","Custom restored check").put("done",true)))
            .put(row(Module.TRIPS,new JSONObject().put("Name","Restored trip"))));
        try(LogStore s=new LogStore(context)){Backup.restore(s,backup);assertEquals(25,s.list(Module.CHECKS).size());assertEquals(12,s.list(Module.ARRIVAL).size());assertEquals("Restored trip",s.list(Module.TRIPS).get(0).data.optString("Name"));assertFalse(Arrays.asList(items(s,Module.CHECKS)).contains("Check tyres and lights"));}
    }
    @Test public void currentBackupPreservesIntentionalDeletionsAndChecklistCompletion() throws Exception {
        try(LogStore s=new LogStore(context)){
            s.delete(s.list(Module.CHECKS).get(0).id);LogStore.Entry arrival=s.list(Module.ARRIVAL).get(0);arrival.data.put("done",true);s.save(Module.ARRIVAL,arrival.id,arrival.data);
            JSONObject backup=Backup.exportRecords(s);assertEquals(1,backup.getInt("checklistTemplatesVersion"));Backup.restore(s,backup);
            assertEquals(23,s.list(Module.CHECKS).size());assertTrue(s.list(Module.ARRIVAL).get(0).data.optBoolean("done"));
        }
    }
    @Test public void arrivalChecklistScreenTicksEditsAndResetsIndependently() throws Exception {
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();LogbookTest.findButton(a.getWindow().getDecorView(),Module.ARRIVAL.title).performClick();
            CheckBox first=check(a.getWindow().getDecorView());assertEquals("Arrived Safely",first.getText().toString());first.performClick();
            try(LogStore s=new LogStore(context)){assertTrue(s.list(Module.ARRIVAL).get(0).data.optBoolean("done"));assertFalse(s.list(Module.CHECKS).get(0).data.optBoolean("done"));}
            LogbookTest.findButton(a.getWindow().getDecorView(),"Reset all").performClick();
            ((AlertDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            try(LogStore s=new LogStore(context)){assertFalse(s.list(Module.ARRIVAL).get(0).data.optBoolean("done"));assertEquals(24,s.list(Module.CHECKS).size());}
        }
    }
    private static String[] items(LogStore s,Module m){return s.list(m).stream().map(e->e.data.optString("Item")).toArray(String[]::new);}
    private static JSONObject row(Module m,JSONObject d)throws JSONException{return new JSONObject().put("module",m.name()).put("data",d);}
    private static void raw(SQLiteDatabase db,Module m,JSONObject d){ContentValues v=new ContentValues();v.put("module",m.name());v.put("data",d.toString());db.insertOrThrow("entries",null,v);}
    private static CheckBox check(View v){if(v instanceof CheckBox)return(CheckBox)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){CheckBox c=check(((ViewGroup)v).getChildAt(i));if(c!=null)return c;}return null;}
}
