package com.poulton.rvapp;

import android.content.*;
import android.app.AlertDialog;
import android.widget.EditText;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class TripWorkflowTest {
    private Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("rv-logbook.db");}
    private String addTrip(LogStore s,String name)throws Exception{JSONObject d=new JSONObject().put("Name",name);s.save(Module.TRIPS,0,d);return d.getString("Trip ID");}
    @Test public void snapshotsAreFreshIndependentAndDoNotResetTemplates()throws Exception{
        try(LogStore s=new LogStore(context)){
            LogStore.Entry template=s.list(Module.CHECKS).get(0);template.data.put("done",true).put("Notes","Check when cold");s.save(Module.CHECKS,template.id,template.data);
            String a=addTrip(s,"Same name"),b=addTrip(s,"Same name");assertNotEquals(a,b);TripWorkflow.advance(s,a,"In progress");assertEquals(24,s.list(Module.TRIP_CHECKS).size());
            LogStore.Entry item=s.list(Module.TRIP_CHECKS).get(0);assertEquals("Not started",item.data.getString("Status"));item.data.put("Status","Done");s.save(Module.TRIP_CHECKS,item.id,item.data);
            TripWorkflow.advance(s,b,"In progress");assertEquals(48,s.list(Module.TRIP_CHECKS).size());assertTrue(s.list(Module.CHECKS).get(0).data.getBoolean("done"));
            try{TripWorkflow.advance(s,a,"In progress");fail();}catch(IllegalArgumentException expected){}assertEquals(48,s.list(Module.TRIP_CHECKS).size());
            TripWorkflow.advance(s,a,"Arrived");assertEquals(60,s.list(Module.TRIP_CHECKS).size());TripWorkflow.advance(s,a,"Finished");assertEquals("Finished",TripWorkflow.trip(s,a).data.getString("Status"));assertFalse(TripWorkflow.trip(s,a).data.getString("End date").isEmpty());
        }
    }
    @Test public void backupAndRenamePreserveTripLinksAndCheckNotes()throws Exception{
        try(LogStore s=new LogStore(context)){
            String key=addTrip(s,"Trip");TripWorkflow.advance(s,key,"In progress");LogStore.Entry check=s.list(Module.TRIP_CHECKS).get(0);check.data.put("Status","Needs attention").put("Notes","Replace hose");s.save(Module.TRIP_CHECKS,check.id,check.data);
            s.save(Module.EXPENSES,0,new JSONObject().put("Date","2026-11-06").put("Cost GBP","25").put("Trip ID",key));
            Backup.restore(s,Backup.exportRecords(s));LogStore.Entry trip=TripWorkflow.trip(s,key);trip.data.put("Name","Renamed");s.save(Module.TRIPS,trip.id,trip.data);
            assertEquals(key,s.list(Module.EXPENSES).get(0).data.getString("Trip ID"));assertEquals("Needs attention",s.list(Module.TRIP_CHECKS).get(0).data.getString("Status"));assertEquals("Replace hose",s.list(Module.TRIP_CHECKS).get(0).data.getString("Notes"));assertEquals(24,s.list(Module.TRIP_CHECKS).size());
        }
    }
    @Test public void completionAndMaintenanceAreAtomicAndRetryDoesNotDuplicate()throws Exception{
        try(LogStore s=new LogStore(context)){
            String key=addTrip(s,"Service trip");s.save(Module.MILEAGE,0,new JSONObject().put("Date","2026-11-06").put("Odometer miles","1000"));s.save(Module.REMINDERS,0,new JSONObject().put("Service","Oil").put("Due miles","1000").put("Repeat miles","500").put("Repeat months","12").put("Trip ID",key));
            LogStore.Entry reminder=s.list(Module.REMINDERS).get(0);TripWorkflow.complete(s,reminder.id,new JSONObject().put("Work","Oil changed").put("Date","2026-11-06").put("Cost GBP","45"));
            assertEquals(1,s.list(Module.MAINTENANCE).size());assertEquals(2,s.list(Module.REMINDERS).size());assertEquals("1500.0",s.list(Module.REMINDERS).get(0).data.getString("Due miles"));assertNotEquals(reminder.data.getString("Reminder ID"),s.list(Module.REMINDERS).get(0).data.getString("Reminder ID"));
            assertEquals(key,s.list(Module.MAINTENANCE).get(0).data.getString("Trip ID"));assertEquals(reminder.data.getString("Reminder ID"),s.list(Module.MAINTENANCE).get(0).data.getString("Source reminder ID"));
            try{TripWorkflow.complete(s,reminder.id,new JSONObject().put("Work","Duplicate"));fail();}catch(IllegalArgumentException expected){}assertEquals(1,s.list(Module.MAINTENANCE).size());assertEquals(2,s.list(Module.REMINDERS).size());
            Backup.restore(s,Backup.exportRecords(s));assertEquals(reminder.data.getString("Reminder ID"),s.list(Module.MAINTENANCE).get(0).data.getString("Source reminder ID"));
        }
    }
    @Test public void scopedRecordSurvivesRotationAndStaysLinked()throws Exception{
        String key;try(LogStore s=new LogStore(context)){key=addTrip(s,"Test trip");}
        Intent intent=new Intent(context,MainActivity.class).putExtra("module","EXPENSES").putExtra("trip",key);
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class,intent).setup()){
            MainActivity a=c.get();LogbookTest.findButton(a.getWindow().getDecorView(),"Add record").performClick();java.util.List<EditText> edits=new java.util.ArrayList<>();LogbookTest.findEdits(a.getWindow().getDecorView(),edits);edits.get(2).setText("12");c.recreate();a=c.get();LogbookTest.findButton(a.getWindow().getDecorView(),"Save").performClick();
            try(LogStore s=new LogStore(context)){assertEquals(key,s.list(Module.EXPENSES).get(0).data.getString("Trip ID"));}
        }
    }
    @Test public void tripDashboardStartsTripAndOpensSnapshotEditor()throws Exception{
        String key;try(LogStore s=new LogStore(context)){key=addTrip(s,"Dashboard trip");}
        try(ActivityController<TripDashboardActivity> c=Robolectric.buildActivity(TripDashboardActivity.class,new Intent(context,TripDashboardActivity.class).putExtra("trip",key)).setup()){
            TripDashboardActivity a=c.get();LogbookTest.findButton(a.getWindow().getDecorView(),"Start trip").performClick();((AlertDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertNotNull(LogbookTest.findButton(a.getWindow().getDecorView(),"Not started"));LogbookTest.findButton(a.getWindow().getDecorView(),"Not started").performClick();assertNotNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
        }
    }
}
