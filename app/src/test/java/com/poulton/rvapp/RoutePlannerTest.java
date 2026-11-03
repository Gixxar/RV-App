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
import org.robolectric.shadows.ShadowAlertDialog;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class RoutePlannerTest {
    private Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("rv-logbook.db");}
    static JSONArray stops()throws Exception{
        return new JSONArray().put(new JSONObject().put("name","Start A").put("lat",52.517037).put("lon",13.38886))
            .put(new JSONObject().put("name","Stop B").put("lat",52.529407).put("lon",13.397634))
            .put(new JSONObject().put("name","Destination C").put("lat",52.523219).put("lon",13.428555));
    }
    static JSONObject fixture()throws Exception{
        try(InputStream in=RoutePlannerTest.class.getResourceAsStream("/route-response.json")){assertNotNull(in);return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
    }
    private static InputStream input(JSONObject json){return new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8));}
    static JSONObject plan()throws Exception{return RouteService.response(input(fixture()),stops()).put("Name","Berlin road plan").put("Trip","Weekend").put("Date","2026-12-01").put("Notes","Confirm campsite entrance");}
    @Test public void requestKeepsStopOrderAndConvertsGeoJsonToMapCoordinates()throws Exception{
        String url=RouteService.url(stops()).toString();assertTrue(url.startsWith("https://router.project-osrm.org/route/v1/driving/13.38886,52.517037;13.397634,52.529407;13.428555,52.523219?"));assertFalse(url.contains("Start A"));assertTrue(url.contains("radiuses=500;500;500"));
        JSONObject route=plan();assertEquals(RouteService.TYPE,route.getString("Route type"));assertEquals(stops().toString(),route.getString("Route stops"));
        JSONArray line=GeoData.parse(route.getString("Route points")).getJSONArray(0),original=fixture().getJSONArray("routes").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
        assertEquals(original.getJSONArray(0).getDouble(1),line.getJSONArray(0).getDouble(0),0);assertEquals(original.getJSONArray(0).getDouble(0),line.getJSONArray(0).getDouble(1),0);
        assertTrue(route.getDouble("Distance miles")>2);assertTrue(route.getDouble("Drive minutes")>5);
        RouteService.UriParts leg=RouteService.leg(stops(),1);assertEquals("52.529407,13.397634",leg.origin);assertEquals("52.523219,13.428555",leg.destination);
    }
    @Test public void stopValidationRejectsMissingInvalidRepeatedAndExcessStops()throws Exception{
        for(String value:new String[]{"[]","[{}]","[{},{}]","[{\"lat\":100,\"lon\":0,\"name\":\"Bad\"},{\"lat\":0,\"lon\":0,\"name\":\"Other\"}]"})try{RouteService.stops(value);fail(value);}catch(JSONException expected){}
        JSONArray duplicate=new JSONArray().put(stops().get(0)).put(stops().get(0));try{RouteService.stops(duplicate.toString());fail("Duplicate consecutive stops accepted");}catch(JSONException expected){}
        JSONArray many=new JSONArray();for(int i=0;i<11;i++)many.put(new JSONObject().put("name","Stop").put("lat",i).put("lon",0));try{RouteService.stops(many.toString());fail("Too many stops accepted");}catch(JSONException expected){}
    }
    @Test public void responseRejectsServiceFailuresInvalidGeometryAndBadEstimates()throws Exception{
        for(int scenario=0;scenario<7;scenario++){
            JSONObject bad=fixture(),route=bad.getJSONArray("routes").getJSONObject(0);
            switch(scenario){case 0:bad.put("code","NoRoute");break;case 1:route.put("duration",-1);break;case 2:route.put("distance",0);break;case 3:route.put("legs",new JSONArray());break;case 4:route.getJSONObject("geometry").put("type","Polygon");break;case 5:route.getJSONObject("geometry").put("coordinates",new JSONArray("[[999,0],[0,0]]"));break;default:bad.getJSONArray("waypoints").getJSONObject(0).put("distance",501);}
            try{RouteService.response(input(bad),stops());fail("Bad response accepted: "+scenario);}catch(IOException|JSONException expected){}
        }
        try{RouteService.response(new ByteArrayInputStream(new byte[4*1024*1024+1]),stops());fail("Oversized response accepted");}catch(IOException expected){}
    }
    @Test public void plansRoundTripThroughBackupWithoutBecomingTravelledRoutes()throws Exception{
        try(LogStore store=new LogStore(context)){
            store.save(Module.PLANS,0,plan());JSONObject backup=Backup.exportRecords(store);Backup.restore(store,backup);
            assertEquals(stops().toString(),store.list(Module.PLANS).get(0).data.getString("Route stops"));
            JSONObject map=TravelMapActivity.payload(store);assertEquals(1,map.getJSONArray("plans").length());assertEquals(0,map.getJSONArray("routes").length());assertEquals(0,map.getJSONArray("places").length());assertTrue(map.getJSONArray("plans").getJSONObject(0).getString("type").contains("RV suitability not checked"));
            backup.getJSONArray("records").getJSONObject(0).getJSONObject("data").put("Drive minutes","-1");
            try{Backup.restore(store,backup);fail("Invalid plan replaced data");}catch(JSONException expected){}
            assertEquals("Berlin road plan",store.list(Module.PLANS).get(0).data.getString("Name"));
        }
    }
    @Test public void mapStopPickerReturnsCoordinatesWithoutSavingAVisit()throws Exception{
        Intent intent=new Intent(context,TravelMapActivity.class).putExtra("pick_stop",true);
        try(ActivityController<TravelMapActivity> c=Robolectric.buildActivity(TravelMapActivity.class,intent).setup()){
            TravelMapActivity a=c.get();a.navigate(android.net.Uri.parse("poulton://pin?lat=52&lon=13&name=NotVisited"));assertFalse(a.isFinishing());
            a.navigate(android.net.Uri.parse("poulton://stop?lat=52.5&lon=13.4&name=Camp"));assertEquals(Activity.RESULT_OK,Shadows.shadowOf(a).getResultCode());assertEquals("stop",Shadows.shadowOf(a).getResultIntent().getStringExtra("action"));
            try(LogStore store=new LogStore(context)){assertTrue(store.list(Module.PLACES).isEmpty());}
        }
    }
    @Test public void newPlanCanPickOrderedStopsCalculateAndSaveLinkedToTrip()throws Exception{
        try(LogStore store=new LogStore(context)){store.save(Module.TRIPS,0,new JSONObject().put("Name","Weekend"));}
        try(ActivityController<RoutePlannerActivity> c=Robolectric.buildActivity(RoutePlannerActivity.class).setup()){
            RoutePlannerActivity a=c.get();assertFalse(button(a,"Save plan").isEnabled());button(a,"Add intermediate stop").performClick();
            for(int i=0;i<3;i++)pick(a,i,stops().getJSONObject(i));
            List<EditText> edits=new ArrayList<>();LogbookTest.findEdits(a.getWindow().getDecorView(),edits);edits.get(0).setText("My road plan");findSpinner(a.getWindow().getDecorView()).setSelection(1);
            a.acceptRoute(plan());assertTrue(button(a,"Save plan").isEnabled());button(a,"Save plan").performClick();assertTrue(a.isFinishing());
            try(LogStore store=new LogStore(context)){assertEquals(1,store.list(Module.PLANS).size());assertEquals("My road plan",store.list(Module.PLANS).get(0).data.getString("Name"));assertEquals("Weekend",store.list(Module.PLANS).get(0).data.getString("Trip"));assertTrue(store.list(Module.ROUTES).isEmpty());}
        }
    }
    @Test public void changingStopsInvalidatesPreviewAndCancelKeepsSavedPlan()throws Exception{
        long id;try(LogStore store=new LogStore(context)){store.save(Module.PLANS,0,plan());id=store.list(Module.PLANS).get(0).id;}
        try(ActivityController<RoutePlannerActivity> c=Robolectric.buildActivity(RoutePlannerActivity.class,new Intent(context,RoutePlannerActivity.class).putExtra("id",id)).setup()){
            RoutePlannerActivity a=c.get();assertTrue(button(a,"Save plan").isEnabled());button(a,"Add intermediate stop").performClick();assertFalse(button(a,"Save plan").isEnabled());a.onBackPressed();
            ((AlertDialog)ShadowAlertDialog.getLatestAlertDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
            try(LogStore store=new LogStore(context)){assertEquals(3,new JSONArray(store.list(Module.PLANS).get(0).data.getString("Route stops")).length());}
        }
    }
    @Test public void unsavedStopsAndTextSurviveRecreationAndNavigationUsesOneLeg()throws Exception{
        try(ActivityController<RoutePlannerActivity> c=Robolectric.buildActivity(RoutePlannerActivity.class).setup()){
            RoutePlannerActivity a=c.get();button(a,"Add intermediate stop").performClick();for(int i=0;i<3;i++)pick(a,i,stops().getJSONObject(i));a.acceptRoute(plan());
            List<EditText> edits=new ArrayList<>();LogbookTest.findEdits(a.getWindow().getDecorView(),edits);edits.get(0).setText("Keep draft");c.recreate();a=c.get();edits.clear();LogbookTest.findEdits(a.getWindow().getDecorView(),edits);assertEquals("Keep draft",edits.get(0).getText().toString());
            a.acceptRoute(plan());button(a,"Navigate a leg in Maps").performClick();AlertDialog dialog=(AlertDialog)ShadowAlertDialog.getLatestAlertDialog();dialog.getListView().performItemClick(null,1,1);Shadows.shadowOf(Looper.getMainLooper()).idle();Intent nav=Shadows.shadowOf(a).getNextStartedActivity();assertNotNull(nav);assertEquals("1",nav.getData().getQueryParameter("api"));assertEquals("52.529407,13.397634",nav.getData().getQueryParameter("origin"));assertEquals("52.523219,13.428555",nav.getData().getQueryParameter("destination"));
        }
    }
    @Test @Config(sdk=35,qualifiers="w393dp-h852dp-port-xhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void nativeLayoutKeepsSaveActionsVisibleAndRendersPreview()throws Exception{
        long id;try(LogStore store=new LogStore(context)){store.save(Module.PLANS,0,plan());id=store.list(Module.PLANS).get(0).id;}
        try(ActivityController<RoutePlannerActivity> c=Robolectric.buildActivity(RoutePlannerActivity.class,new Intent(context,RoutePlannerActivity.class).putExtra("id",id)).setup().visible()){
            RoutePlannerActivity a=c.get();View root=a.findViewById(android.R.id.content);root.measure(View.MeasureSpec.makeMeasureSpec(786,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1704,View.MeasureSpec.EXACTLY));root.layout(0,0,786,1704);
            Button save=button(a,"Save plan");assertTrue(save.isEnabled());int[] xy=new int[2];save.getLocationInWindow(xy);assertTrue(xy[1]+save.getHeight()<=1704);assertTrue(save.getHeight()>=96);
            render(root,"route-planner-top.png");ScrollView scroll=findScroll(root);scroll.scrollTo(0,scroll.getChildAt(0).getHeight());render(root,"route-planner-bottom.png");
        }
    }
    private static void render(View view,String file)throws Exception{
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(786,1704,android.graphics.Bitmap.Config.ARGB_8888);view.draw(new android.graphics.Canvas(bitmap));File dir=new File("build/reports/tests");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,file))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
    }
    private static Button button(Activity a,String prefix){Button b=LogbookTest.findButton(a.getWindow().getDecorView(),prefix);assertNotNull(prefix,b);return b;}
    private static void pick(RoutePlannerActivity a,int index,JSONObject point)throws Exception{
        ArrayList<Button> maps=new ArrayList<>();buttons(a.getWindow().getDecorView(),maps);maps.get(index).performClick();assertEquals(60,Shadows.shadowOf(a).getNextStartedActivityForResult().requestCode);
        a.onActivityResult(60,Activity.RESULT_OK,new Intent().putExtra("Name",point.getString("name")).putExtra("Latitude",String.valueOf(point.getDouble("lat"))).putExtra("Longitude",String.valueOf(point.getDouble("lon"))));
    }
    private static void buttons(View view,List<Button> result){if(view instanceof Button&&((Button)view).getText().toString().equals("Map"))result.add((Button)view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)buttons(((ViewGroup)view).getChildAt(i),result);}
    private static Spinner findSpinner(View view){if(view instanceof Spinner)return(Spinner)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){Spinner result=findSpinner(((ViewGroup)view).getChildAt(i));if(result!=null)return result;}return null;}
    private static ScrollView findScroll(View view){if(view instanceof ScrollView)return(ScrollView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){ScrollView result=findScroll(((ViewGroup)view).getChildAt(i));if(result!=null)return result;}return null;}
}
