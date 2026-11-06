package com.poulton.rvapp;

import android.content.*;
import android.app.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.time.LocalDate;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ServiceScheduleTest {
    private Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("rv-logbook.db");}
    @Test public void dueAndSoonUseEitherTriggerWithExactBoundaries()throws Exception{
        LocalDate today=LocalDate.of(2026,11,6);JSONObject d=new JSONObject().put("Due date","2027-01-01").put("Due miles","1000");
        assertEquals("Overdue",ServiceSchedule.status(d,1001,today));assertEquals("Due now",ServiceSchedule.status(d,1000,today));assertEquals("Due soon",ServiceSchedule.status(d,500,today));assertEquals("Scheduled",ServiceSchedule.status(d,499,today));
        d.put("Due date",today.plusDays(30).toString());assertEquals("Due soon",ServiceSchedule.status(d,0,today));d.put("Due date",today.minusDays(1).toString());assertEquals("Overdue",ServiceSchedule.status(d,0,today));d.put("done",true);assertEquals("Completed",ServiceSchedule.status(d,2000,today));
    }
    @Test public void calculationHandlesMonthEndsAndPreservesManualFirstDue()throws Exception{
        JSONObject d=new JSONObject().put("Last service date","2024-01-31").put("Last service miles","10000").put("Repeat months","1").put("Repeat miles","5000");ServiceSchedule.calculate(d,false);assertEquals("2024-02-29",d.getString("Due date"));assertEquals(15000,d.getDouble("Due miles"),0);
        d.put("Due date","2024-04-01").put("Due miles","17000");ServiceSchedule.calculate(d,false);assertEquals("2024-04-01",d.getString("Due date"));ServiceSchedule.calculate(d,true);assertEquals("2024-02-29",d.getString("Due date"));assertEquals(15000,d.getDouble("Due miles"),0);
    }
    @Test public void historicCompletionUsesActualDateAndMileageAndHistorySurvivesBackup()throws Exception{
        try(LogStore s=new LogStore(context)){
            s.save(Module.MILEAGE,0,new JSONObject().put("Date","2026-01-01").put("Odometer miles","20000"));JSONObject reminder=new JSONObject().put("Service","Engine oil").put("Due date","2025-01-01").put("Repeat months","12").put("Repeat miles","5000");s.save(Module.REMINDERS,0,reminder);long id=s.list(Module.REMINDERS).get(0).id;
            LocalDate serviced=LocalDate.now().minusMonths(3);TripWorkflow.complete(s,id,new JSONObject().put("Work","Oil changed").put("Date",serviced.toString()).put("Odometer miles","18000").put("Cost GBP","100").put("Provider","Garage"));
            JSONObject next=s.list(Module.REMINDERS).get(0).data;assertEquals(serviced.plusMonths(12).toString(),next.getString("Due date"));assertEquals(23000,next.getDouble("Due miles"),0);assertEquals(serviced.toString(),next.getString("Last service date"));assertEquals(18000,next.getDouble("Last service miles"),0);assertEquals(reminder.getString("Schedule ID"),next.getString("Schedule ID"));
            Backup.restore(s,Backup.exportRecords(s));assertEquals(next.getString("Schedule ID"),s.list(Module.MAINTENANCE).get(0).data.getString("Schedule ID"));assertEquals(2,s.list(Module.REMINDERS).size());
        }
    }
    @Test public void lastServiceMileageIsAReadingButFutureDueMileageIsNot()throws Exception{
        try(LogStore s=new LogStore(context)){s.save(Module.REMINDERS,0,new JSONObject().put("Service","Oil").put("Due miles","30000").put("Last service miles","20000"));assertEquals(20000,s.latestMileage(),0);assertEquals(0,s.dueCount());s.save(Module.MILEAGE,0,new JSONObject().put("Date",LocalDate.now().toString()).put("Odometer miles","31000"));assertEquals(31000,s.latestMileage(),0);assertEquals(1,s.dueCount());}
    }
    @Test public void futureServiceCompletionIsRejectedWithoutPartialWrites()throws Exception{
        try(LogStore s=new LogStore(context)){
            s.save(Module.REMINDERS,0,new JSONObject().put("Service","Inspect brakes").put("Due miles","100"));long id=s.list(Module.REMINDERS).get(0).id;
            try{TripWorkflow.complete(s,id,new JSONObject().put("Work","Brake inspection").put("Date",LocalDate.now().plusDays(1).toString()));fail();}catch(IllegalArgumentException expected){}
            assertFalse(s.list(Module.REMINDERS).get(0).data.optBoolean("done"));assertTrue(s.list(Module.MAINTENANCE).isEmpty());assertEquals(1,s.list(Module.REMINDERS).size());
        }
    }
    @Test public void newScheduleFormCalculatesAndPersistsThroughRotation()throws Exception{
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class,new Intent(context,MainActivity.class).putExtra("new_service",true)).setup()){
            MainActivity a=c.get();set(a,"Service","Generator service");set(a,"Last service date","2026-01-31");set(a,"Last service miles","1000");set(a,"Repeat months","6");set(a,"Repeat miles","2000");set(a,"Category","Generator");c.recreate();a=c.get();LogbookTest.findButton(a.getWindow().getDecorView(),"Save").performClick();assertTrue(a.isFinishing());
            try(LogStore s=new LogStore(context)){JSONObject d=s.list(Module.REMINDERS).get(0).data;assertEquals("2026-07-31",d.getString("Due date"));assertEquals(3000,d.getDouble("Due miles"),0);assertEquals("Generator",d.getString("Category"));assertFalse(d.getString("Schedule ID").isEmpty());assertEquals(1,s.dueCount());}
        }
    }
    @Test public void oldRemindersRemainVisibleAndScheduleFilterSurvivesRotation()throws Exception{
        try(LogStore s=new LogStore(context)){s.save(Module.REMINDERS,0,new JSONObject().put("Service","Existing reminder").put("Due miles","100"));s.save(Module.REMINDERS,0,new JSONObject().put("Service","Old completed").put("Due miles","1").put("done",true));}
        try(ActivityController<ServiceScheduleActivity> c=Robolectric.buildActivity(ServiceScheduleActivity.class).setup()){
            ServiceScheduleActivity a=c.get();assertTrue(hasText(a.getWindow().getDecorView(),"Existing reminder"));assertFalse(hasText(a.getWindow().getDecorView(),"Old completed"));spinner(a.getWindow().getDecorView()).setSelection(3);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();c.recreate();a=c.get();assertTrue(hasText(a.getWindow().getDecorView(),"Old completed"));assertFalse(hasText(a.getWindow().getDecorView(),"Existing reminder"));
        }
    }
    @Test @Config(sdk=35,qualifiers="w393dp-h852dp-port-xhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void serviceScheduleRendersAtPhoneSize()throws Exception{
        try(LogStore s=new LogStore(context)){s.save(Module.REMINDERS,0,new JSONObject().put("Service","Engine oil & filter").put("Category","Engine").put("Due date",LocalDate.now().minusDays(10).toString()).put("Repeat months","12").put("Repeat miles","6000"));s.save(Module.REMINDERS,0,new JSONObject().put("Service","Generator service").put("Category","Generator").put("Due date",LocalDate.now().plusDays(20).toString()).put("Repeat months","6"));}
        try(ActivityController<ServiceScheduleActivity> c=Robolectric.buildActivity(ServiceScheduleActivity.class).setup()){
            View root=c.get().getWindow().getDecorView();root.measure(View.MeasureSpec.makeMeasureSpec(786,1073741824),View.MeasureSpec.makeMeasureSpec(1704,1073741824));root.layout(0,0,786,1704);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(786,1704,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));java.io.File f=new java.io.File("build/reports/tests/service-schedule.png");f.getParentFile().mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(f)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}assertNotNull(LogbookTest.findButton(root,"Add scheduled service"));assertTrue(hasText(root,"Overdue"));
        }
    }
    private static void set(Activity a,String name,String value){View root=a.getWindow().getDecorView();TextView label=label(root,name);assertNotNull(name,label);((EditText)root.findViewById(label.getLabelFor())).setText(value);}
    private static TextView label(View v,String name){if(v instanceof TextView&&((TextView)v).getText().toString().replace(" *","").equals(name)&&v.getLabelFor()!=View.NO_ID)return(TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=label(((ViewGroup)v).getChildAt(i),name);if(found!=null)return found;}return null;}
    private static boolean hasText(View v,String value){if(v instanceof TextView&&((TextView)v).getText().toString().startsWith(value))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasText(((ViewGroup)v).getChildAt(i),value))return true;return false;}
    private static Spinner spinner(View v){if(v instanceof Spinner)return(Spinner)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Spinner s=spinner(((ViewGroup)v).getChildAt(i));if(s!=null)return s;}return null;}
}
