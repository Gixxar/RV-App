package com.poulton.rvapp;

import android.app.*;
import android.content.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class VehicleProfileTest {
    private Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("rv-logbook.db");}
    static JSONObject profile()throws Exception{return new JSONObject().put("Length m","11").put("Height m","3.4").put("Width m","2.5").put("Weight kg","14000").put("Axle count","2").put("Max axle load kg","8000");}
    static JSONObject site()throws Exception{return new JSONObject().put("Name","Fits site").put("Max length m","12").put("Max height m","4").put("Max width m","3").put("Max weight kg","18000");}
    private static InputStream stream(JSONObject json){return new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8));}
    private static JSONObject response()throws Exception{try(InputStream in=VehicleProfileTest.class.getResourceAsStream("/rv-route-response.json")){assertNotNull(in);return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}
    static JSONObject rvPlan()throws Exception{return RvRouting.response(stream(response()),RoutePlannerTest.stops(),profile()).put("Name","RV plan");}
    @Test public void validatesFourMeasurementsAndOptionalAxleDetails()throws Exception{
        assertTrue(VehicleProfile.complete(profile()));assertFalse(VehicleProfile.complete(new JSONObject().put("Length m","11")));
        for(String value:new String[]{"0","-1","NaN","Infinity","100001"})assertNotNull(VehicleProfile.error("Weight kg",value));
        assertNotNull(VehicleProfile.error("Height m","10.1"));assertNotNull(VehicleProfile.error("Length m","50.1"));assertNotNull(VehicleProfile.error("Axle count","1"));assertNotNull(VehicleProfile.error("Axle count","2.5"));assertNotNull(VehicleProfile.error("Max axle load kg","40001"));
        JSONObject same=profile().put("Length m","11.00");assertTrue(VehicleProfile.same(same,profile()));
        assertEquals(11,VehicleProfile.normalized(profile()).getDouble("Length m"),0);
    }
    @Test public void sendsMetresAndMetricTonnesWithRestrictionsEnabled()throws Exception{
        JSONObject request=RvRouting.request(RoutePlannerTest.stops(),profile());assertEquals("truck",request.getString("costing"));JSONObject truck=request.getJSONObject("costing_options").getJSONObject("truck");
        assertEquals(3.4,truck.getDouble("height"),0);assertEquals(11,truck.getDouble("length"),0);assertEquals(2.5,truck.getDouble("width"),0);assertEquals(14,truck.getDouble("weight"),0);assertEquals(8,truck.getDouble("axle_load"),0);assertEquals(2,truck.getInt("axle_count"));assertFalse(truck.getBoolean("ignore_restrictions"));assertFalse(truck.getBoolean("ignore_access"));
        JSONObject unknown=profile();unknown.remove("Axle count");unknown.remove("Max axle load kg");JSONObject defaults=RvRouting.request(RoutePlannerTest.stops(),unknown).getJSONObject("costing_options").getJSONObject("truck");assertEquals(5,defaults.getInt("axle_count"));assertEquals(0,defaults.getDouble("axle_load"),0);assertTrue(VehicleProfile.axleNotice(unknown).contains("default 5"));
    }
    @Test public void rejectsAdjustedOrCarResponsesAndKeepsProfileSnapshot()throws Exception{
        JSONObject plan=rvPlan();assertEquals(RvRouting.TYPE,plan.getString("Route type"));assertTrue(VehicleProfile.same(profile(),new JSONObject(plan.getString("RV profile"))));
        JSONObject bad=response().put("warnings",new JSONArray().put(new JSONObject().put("code",500).put("text","height clamped")));
        try{RvRouting.response(stream(bad),RoutePlannerTest.stops(),profile());fail("Adjusted settings accepted");}catch(IOException expected){}
        bad=response();bad.getJSONArray("routes").getJSONObject(0).put("weight_name","auto");try{RvRouting.response(stream(bad),RoutePlannerTest.stops(),profile());fail("Car route accepted");}catch(IOException expected){}
    }
    @Test public void campsiteFitDistinguishesMissingExceededAndBoundaryLimits()throws Exception{
        assertEquals(1,VehicleProfile.fit(site(),profile()).status);assertEquals(0,VehicleProfile.fit(site(),new JSONObject()).status);
        JSONObject partial=site();partial.remove("Max width m");assertEquals(0,VehicleProfile.fit(partial,profile()).status);assertTrue(VehicleProfile.fit(partial,profile()).message.contains("width"));
        partial.put("Max length m","10");assertEquals(2,VehicleProfile.fit(partial,profile()).status);
        assertEquals(0,VehicleProfile.fit(site().put("Max height m","3.4"),profile()).status);
        assertEquals(2,VehicleProfile.fit(site().put("Max weight kg","13000"),profile()).status);
        assertEquals(0,VehicleProfile.fit(new JSONObject().put("Name","Imported campsite"),profile()).status);
    }
    @Test public void pitchAvailabilityIsSeparateFromVehicleFitAndNeedsCheckedDate()throws Exception{
        JSONObject booked=site().put("Availability","Fully booked").put("Availability checked date","2026-01-01");assertEquals(1,VehicleProfile.fit(booked,profile()).status);assertTrue(VehicleProfile.availability(booked).contains("Fully booked"));assertTrue(VehicleProfile.availability(booked).contains("Contact campsite"));
        Map<String,String> values=new HashMap<>();values.put("Name","Site");values.put("Availability","Spaces reported");assertNotNull(Rules.validateRecord(Module.CAMPSITES,values));values.put("Availability checked date","2026-01-01");assertNull(Rules.validateRecord(Module.CAMPSITES,values));
        assertNotNull(Rules.validate("Availability checked date",java.time.LocalDate.now().plusDays(1).toString()));assertNotNull(Rules.validate("Max height m","0"));
    }
    @Test public void profileLimitsAndRvPlansSurviveBackupAndInvalidBackupIsAtomic()throws Exception{
        try(LogStore store=new LogStore(context)){
            store.save(Module.VEHICLE,0,profile());store.save(Module.CAMPSITES,0,site());store.save(Module.PLANS,0,rvPlan());JSONObject backup=Backup.exportRecords(store);Backup.restore(store,backup);
            assertTrue(VehicleProfile.same(profile(),VehicleProfile.current(store)));assertEquals("12",store.list(Module.CAMPSITES).get(0).data.getString("Max length m"));assertEquals(RvRouting.TYPE,store.list(Module.PLANS).get(0).data.getString("Route type"));
            backup.getJSONArray("records").put(new JSONObject().put("module","VEHICLE").put("data",profile()));try{Backup.restore(store,backup);fail("Duplicate profiles accepted");}catch(JSONException expected){}
            assertEquals(1,store.list(Module.VEHICLE).size());assertEquals(1,store.list(Module.PLANS).size());
        }
    }
    @Test public void profileFormRequiresMeasurementsThenUpdatesSingleton()throws Exception{
        try(ActivityController<VehicleProfileActivity> c=Robolectric.buildActivity(VehicleProfileActivity.class).setup()){
            VehicleProfileActivity a=c.get();List<EditText> edits=new ArrayList<>();LogbookTest.findEdits(a.getWindow().getDecorView(),edits);assertEquals("11",edits.get(0).getText().toString());assertEquals("",edits.get(1).getText().toString());button(a,"Save RV").performClick();assertFalse(a.isFinishing());
            edits.get(1).setText("3.4");edits.get(2).setText("2.5");edits.get(3).setText("14000");button(a,"Save RV").performClick();assertTrue(a.isFinishing());
        }
        try(ActivityController<VehicleProfileActivity> c=Robolectric.buildActivity(VehicleProfileActivity.class).setup()){
            List<EditText> edits=new ArrayList<>();LogbookTest.findEdits(c.get().getWindow().getDecorView(),edits);edits.get(2).setText("2.6");c.recreate();edits.clear();LogbookTest.findEdits(c.get().getWindow().getDecorView(),edits);assertEquals("2.6",edits.get(2).getText().toString());button(c.get(),"Save RV").performClick();
        }
        try(LogStore store=new LogStore(context)){assertEquals(1,store.list(Module.VEHICLE).size());assertEquals(2.6,VehicleProfile.current(store).getDouble("Width m"),0);}
    }
    @Test public void rvProfileChangeInvalidatesSavedRouteAndMapsHandoffWarns()throws Exception{
        long id;try(LogStore store=new LogStore(context)){store.save(Module.VEHICLE,0,profile());store.save(Module.PLANS,0,rvPlan());id=store.list(Module.PLANS).get(0).id;}
        try(ActivityController<RoutePlannerActivity> c=Robolectric.buildActivity(RoutePlannerActivity.class,new Intent(context,RoutePlannerActivity.class).putExtra("id",id)).setup()){
            RoutePlannerActivity a=c.get();assertTrue(button(a,"Save plan").isEnabled());button(a,"Navigate a leg").performClick();AlertDialog warning=(AlertDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertEquals("Google Maps uses car routing",org.robolectric.Shadows.shadowOf(warning).getTitle());assertNull(Shadows.shadowOf(a).getNextStartedActivity());warning.dismiss();
            try(LogStore store=new LogStore(context)){LogStore.Entry p=store.list(Module.VEHICLE).get(0);store.save(Module.VEHICLE,p.id,profile().put("Height m","3.6"));}
            a.onActivityResult(70,Activity.RESULT_OK,null);assertFalse(button(a,"Save plan").isEnabled());
            try(LogStore store=new LogStore(context)){assertEquals(3.4,new JSONObject(store.list(Module.PLANS).get(0).data.getString("RV profile")).getDouble("Height m"),0);}
        }
    }
    @Test public void campsiteFiltersDoNotTreatUnknownLimitsAsFits()throws Exception{
        try(LogStore store=new LogStore(context)){store.save(Module.VEHICLE,0,profile());store.save(Module.CAMPSITES,0,site());store.save(Module.CAMPSITES,0,site().put("Name","Too short site").put("Max length m","9"));store.save(Module.CAMPSITES,0,new JSONObject().put("Name","Unknown site"));}
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();button(a,"Campsites").performClick();spinner(a.getWindow().getDecorView()).setSelection(1);Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(hasText(a.getWindow().getDecorView(),"Fits site"));assertFalse(hasText(a.getWindow().getDecorView(),"Unknown site"));assertFalse(hasText(a.getWindow().getDecorView(),"Too short site"));
            spinner(a.getWindow().getDecorView()).setSelection(3);Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(hasText(a.getWindow().getDecorView(),"Unknown site"));assertFalse(hasText(a.getWindow().getDecorView(),"Fits site"));
        }
    }
    @Test @Config(sdk=35,qualifiers="w393dp-h852dp-port-xhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void profileLayoutKeepsSaveVisible()throws Exception{
        try(ActivityController<VehicleProfileActivity> c=Robolectric.buildActivity(VehicleProfileActivity.class).setup().visible()){
            View root=c.get().findViewById(android.R.id.content);root.measure(View.MeasureSpec.makeMeasureSpec(786,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1704,View.MeasureSpec.EXACTLY));root.layout(0,0,786,1704);Button save=button(c.get(),"Save RV");int[] xy=new int[2];save.getLocationInWindow(xy);assertTrue(xy[1]+save.getHeight()<=1704);
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(786,1704,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));File dir=new File("build/reports/tests");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"rv-profile.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
        }
    }
    private static Button button(Activity a,String prefix){Button b=LogbookTest.findButton(a.getWindow().getDecorView(),prefix);assertNotNull(prefix,b);return b;}
    private static Spinner spinner(View v){if(v instanceof Spinner)return(Spinner)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Spinner s=spinner(((ViewGroup)v).getChildAt(i));if(s!=null)return s;}return null;}
    private static boolean hasText(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasText(((ViewGroup)v).getChildAt(i),text))return true;return false;}
}
