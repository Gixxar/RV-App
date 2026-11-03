package com.poulton.rvapp;

import android.app.*;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class LogbookTest {
    Context context;
    @Before public void setUp() {
        context=RuntimeEnvironment.getApplication(); context.deleteDatabase("rv-logbook.db");
    }
    @Test public void recordsPersistAndTripStartCountsTowardsMileage() throws Exception {
        try (LogStore s=new LogStore(context)) {
            assertTrue(s.list(Module.CHECKS).size()>=10);
            s.save(Module.TRIPS,0,new JSONObject().put("Name","Cornwall").put("Start miles","52000"));
        }
        try (LogStore s=new LogStore(context)) {
            assertEquals("Cornwall",s.list(Module.TRIPS).get(0).data.getString("Name"));
            assertEquals(52000,s.latestMileage(),0);
            long id=s.list(Module.TRIPS).get(0).id;
            s.save(Module.TRIPS,id,new JSONObject().put("Name","Devon").put("End miles","52500"));
            assertEquals(1,s.list(Module.TRIPS).size()); assertEquals(52500,s.latestMileage(),0);
            s.delete(id); assertEquals(0,s.list(Module.TRIPS).size());
        }
    }
    @Test public void dueCountExcludesCompletedAndFutureServices() throws Exception {
        try (LogStore s=new LogStore(context)) {
            s.save(Module.MILEAGE,0,new JSONObject().put("Date","2026-11-03").put("Odometer miles","123"));
            s.save(Module.REMINDERS,0,new JSONObject().put("Service","Oil").put("Due miles","123"));
            s.save(Module.REMINDERS,0,new JSONObject().put("Service","Tyres").put("Due date","2000-01-01").put("done",true));
            s.save(Module.REMINDERS,0,new JSONObject().put("Service","Later").put("Due miles","124"));
            assertEquals(1,s.dueCount());
        }
    }
    @Test public void backupRoundTripPreservesRecordsButStripsPhotoPermissions() throws Exception {
        try (LogStore s=new LogStore(context)) {
            s.save(Module.PHOTOS,0,new JSONObject().put("Caption","Beach").put("uri","content://photos/private"));
            s.save(Module.MAINTENANCE,0,new JSONObject().put("Work","Oil change").put("Cost GBP","95.50"));
            JSONObject backup=Backup.exportRecords(s);
            assertFalse(backup.toString().contains("content://"));
            s.save(Module.TRIPS,0,new JSONObject().put("Name","Remove on restore"));
            Backup.restore(s,backup);
            assertEquals(0,s.list(Module.TRIPS).size());
            assertEquals("Beach",s.list(Module.PHOTOS).get(0).data.getString("Caption"));
            assertEquals(95.5,s.totalCost(),0.001);
        }
    }
    @Test public void invalidBackupLeavesExistingRecordsUntouched() throws Exception {
        try (LogStore s=new LogStore(context)) {
            s.save(Module.TRIPS,0,new JSONObject().put("Name","Keep me"));
            JSONObject bad=Backup.exportRecords(s);
            bad.getJSONArray("records").put(new JSONObject().put("module","MILEAGE").put("data",new JSONObject().put("Date","2026-11-03").put("Odometer miles","-1")));
            try { Backup.restore(s,bad); fail("Invalid backup accepted"); } catch (JSONException expected) { }
            assertEquals("Keep me",s.list(Module.TRIPS).get(0).data.getString("Name"));
        }
    }
    @Test public void validationRejectsBadRangesAndMissingDueTriggers() {
        Map<String,String> v=new HashMap<>(); v.put("Name","Test");v.put("Start miles","100");v.put("End miles","99");
        assertNotNull(Rules.validateRecord(Module.TRIPS,v));
        v.clear();v.put("Name","Site");v.put("Arrival date","2026-11-03");v.put("Departure date","2026-11-02");
        assertNotNull(Rules.validateRecord(Module.CAMPSITES,v));
        v.clear();v.put("Service","Oil");assertNotNull(Rules.validateRecord(Module.REMINDERS,v));
        assertNotNull(Rules.validate("Repeat months","1.5"));
    }
    @Test @Config(sdk={28,35}) public void dashboardAndEveryModuleCanOpen() {
        try (ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a=controller.get();
            for (Module m:Module.values()) {
                Button b=findButton(a.getWindow().getDecorView(),m==Module.REMINDERS?"Service reminders":m.title);
                assertNotNull(m.title,b);b.performClick();
                assertNotNull(findButton(a.getWindow().getDecorView(),"Add record"));
                findButton(a.getWindow().getDecorView(),"Home").performClick();
            }
        }
    }
    @Test public void tripFormSurvivesActivityRecreationAndSaves() {
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a=c.get();findButton(a.getWindow().getDecorView(),"Trip logs").performClick();
        findButton(a.getWindow().getDecorView(),"Add record").performClick();
        List<EditText> edits=new ArrayList<>();findEdits(a.getWindow().getDecorView(),edits);edits.get(0).setText("First journey");
        c.recreate(); a=c.get();
        edits.clear();findEdits(a.getWindow().getDecorView(),edits);assertEquals("First journey",edits.get(0).getText().toString());
        findButton(a.getWindow().getDecorView(),"Save").performClick();
        try (LogStore s=new LogStore(context)) { assertEquals(1,s.list(Module.TRIPS).size()); }
        c.pause().stop().destroy();
    }
    @Test public void dailyJobIsIdempotentAndNotificationClearsAfterCompletion() throws Exception {
        ServiceNotifications.schedule(context);ServiceNotifications.schedule(context);
        assertEquals(1,context.getSystemService(JobScheduler.class).getAllPendingJobs().size());
        try(LogStore s=new LogStore(context)) {
            s.save(Module.REMINDERS,0,new JSONObject().put("Service","Oil").put("Due date","2000-01-01"));
            ServiceNotifications.refresh(context);
            assertEquals(1,Shadows.shadowOf(context.getSystemService(NotificationManager.class)).size());
            LogStore.Entry e=s.list(Module.REMINDERS).get(0);e.data.put("done",true);s.save(Module.REMINDERS,e.id,e.data);
            ServiceNotifications.refresh(context);
            assertEquals(0,Shadows.shadowOf(context.getSystemService(NotificationManager.class)).size());
        }
    }
    @Test public void completingRepeatingServiceCreatesNextAndKeepsHistory() throws Exception {
        try(LogStore s=new LogStore(context)) {
            s.save(Module.MILEAGE,0,new JSONObject().put("Date","2026-11-03").put("Odometer miles","50000"));
            s.save(Module.REMINDERS,0,new JSONObject().put("Service","Oil").put("Due miles","50000").put("Repeat months","12").put("Repeat miles","6000"));
        }
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a=c.get();findButton(a.getWindow().getDecorView(),"1 service reminder").performClick();
            CheckBox check=findCheck(a.getWindow().getDecorView());assertNotNull(check);check.performClick();
            AlertDialog dialog=(AlertDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            try(LogStore s=new LogStore(context)) {
                assertEquals(2,s.list(Module.REMINDERS).size());
                LogStore.Entry next=s.list(Module.REMINDERS).get(0),old=s.list(Module.REMINDERS).get(1);
                assertFalse(next.data.optBoolean("done"));assertTrue(old.data.optBoolean("done"));
                assertEquals(56000,next.data.optDouble("Due miles"),0);
                assertEquals(java.time.LocalDate.now().plusMonths(12).toString(),next.data.optString("Due date"));
                assertEquals(0,s.dueCount());
            }
        }
    }
    @Test public void checklistTickPersistsAndResetRequiresConfirmation() throws Exception {
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a=c.get();findButton(a.getWindow().getDecorView(),Module.CHECKS.title).performClick();
            findCheck(a.getWindow().getDecorView()).performClick();
            try(LogStore s=new LogStore(context)) {assertTrue(s.list(Module.CHECKS).get(0).data.optBoolean("done"));}
            findButton(a.getWindow().getDecorView(),"Reset all").performClick();
            ((AlertDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            try(LogStore s=new LogStore(context)) {for(LogStore.Entry e:s.list(Module.CHECKS))assertFalse(e.data.optBoolean("done"));}
        }
    }
    static CheckBox findCheck(View v) {
        if(v instanceof CheckBox)return (CheckBox)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){CheckBox found=findCheck(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    static Button findButton(View v,String starts) {
        if(v instanceof Button && ((Button)v).getText().toString().startsWith(starts))return (Button)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button found=findButton(((ViewGroup)v).getChildAt(i),starts);if(found!=null)return found;}
        return null;
    }
    static void findEdits(View v,List<EditText> list) {
        if(v instanceof EditText)list.add((EditText)v);
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)findEdits(((ViewGroup)v).getChildAt(i),list);
    }
}
