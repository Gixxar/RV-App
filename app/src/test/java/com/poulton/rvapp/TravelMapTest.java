package com.poulton.rvapp;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.webkit.WebView;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class TravelMapTest {
    private Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("rv-logbook.db");}
    private static InputStream xml(String value){return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));}
    private static String track(){return "[[[51.5,-0.1],[51.51,-0.11]],[[52.0,-1.0],[52.01,-1.01]]]";}
    @Test public void validatesSignedCoordinatesAndRouteSegments() throws Exception {
        assertNull(Rules.validate("Latitude","-45.3"));assertNull(Rules.validate("Longitude","-179.99"));
        assertNotNull(Rules.validate("Latitude","91"));assertNotNull(Rules.validate("Longitude","Infinity"));
        assertNotNull(Rules.validate("Route points","[[[51,0]]]"));assertNotNull(Rules.validate("Route points","[[[51,0],[999,1]]]"));
        JSONArray data=GeoData.parse(track());assertEquals(4,GeoData.pointCount(data));
        // Segment gaps are not counted as travelled miles.
        assertTrue(GeoData.miles(data)>1);assertTrue(GeoData.miles(data)<3);
    }
    @Test public void importsGpxWithNamespacesAndKeepsSegmentGaps() throws Exception {
        String doc="<gpx xmlns='http://www.topografix.com/GPX/1/1'><trk><name>Coast &amp; countryside</name><trkseg><trkpt lat='51.5' lon='-0.1'><time>2026-09-15T14:00:00Z</time></trkpt><trkpt lat='51.51' lon='-0.11'/></trkseg><trkseg><trkpt lat='52' lon='-1'/><trkpt lat='52.01' lon='-1.01'/></trkseg></trk></gpx>";
        JSONObject route=GpxImport.read(xml(doc));assertEquals("Coast & countryside",route.getString("Name"));
        assertEquals("2026-09-15",route.getString("Date"));assertEquals("GPX track",route.getString("Route type"));assertEquals(2,GeoData.parse(route.getString("Route points")).length());
    }
    @Test public void importsRouteFilesWithoutClaimingTheyAreRecordedTracks() throws Exception {
        JSONObject r=GpxImport.read(xml("<gpx><rte><name>My route</name><rtept lat='-10' lon='100'/><rtept lat='-11' lon='101'/></rte></gpx>"));
        assertEquals("GPX route",r.getString("Route type"));
    }
    @Test public void rejectsInvalidGpxAndExternalEntities() throws Exception {
        for(String doc:new String[]{"<html/>","<gpx/>","<gpx><trk><trkseg><trkpt lat='NaN' lon='0'/><trkpt lat='1' lon='1'/></trkseg></trk></gpx>","<!DOCTYPE gpx [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><gpx><trk><name>&x;</name></trk></gpx>"}){
            try{GpxImport.read(xml(doc));fail("Invalid GPX accepted");}catch(Exception expected){}
        }
        try{GpxImport.read(new ByteArrayInputStream(new byte[5*1024*1024+1]));fail("Oversized input accepted");}catch(IOException expected){}
    }
    @Test public void mapRecordsRoundTripThroughBackupAndStayLocal() throws Exception {
        try(LogStore s=new LogStore(context)){
            s.save(Module.PLACES,0,new JSONObject().put("Name","Coast").put("Latitude","51.5").put("Longitude","-0.1").put("Trip","Weekend"));
            s.save(Module.ROUTES,0,new JSONObject().put("Name","Weekend track").put("Trip","Weekend").put("Route type","GPX track").put("Route points",track()));
            JSONObject backup=Backup.exportRecords(s);Backup.restore(s,backup);
            JSONObject map=TravelMapActivity.payload(s);assertEquals(1,map.getJSONArray("places").length());assertEquals(1,map.getJSONArray("routes").length());
            assertEquals("Weekend",map.getJSONArray("routes").getJSONObject(0).getString("trip"));
            assertEquals(2,map.getJSONArray("routes").getJSONObject(0).getJSONArray("segments").length());
        }
    }
    @Test public void invalidMapBackupCannotReplaceExistingData() throws Exception {
        try(LogStore s=new LogStore(context)){
            s.save(Module.TRIPS,0,new JSONObject().put("Name","Keep"));JSONObject backup=Backup.exportRecords(s);
            backup.getJSONArray("records").put(new JSONObject().put("module","PLACES").put("data",new JSONObject().put("Name","Invalid").put("Latitude","100").put("Longitude","0")));
            try{Backup.restore(s,backup);fail("Invalid coordinates imported");}catch(JSONException expected){}
            assertEquals("Keep",s.list(Module.TRIPS).get(0).data.getString("Name"));
        }
    }
    @Test public void mapLoadsBundledPageAndReturnsValidatedPinToEditor() throws Exception {
        try(ActivityController<TravelMapActivity> c=Robolectric.buildActivity(TravelMapActivity.class).setup()){
            TravelMapActivity a=c.get();WebView web=findWeb(a.getWindow().getDecorView());assertNotNull(web);
            assertEquals("https://appassets.androidplatform.net/map/index.html",Shadows.shadowOf(web).getLastLoadedUrl());
            assertFalse(web.getSettings().getAllowFileAccess());assertFalse(web.getSettings().getAllowContentAccess());
            a.navigate(Uri.parse("poulton://pin?lat=-10.5&lon=23.4&name=Camp"));
            assertEquals(Activity.RESULT_OK,Shadows.shadowOf(a).getResultCode());
            Intent result=Shadows.shadowOf(a).getResultIntent();assertEquals("pin",result.getStringExtra("action"));assertEquals("Camp",result.getStringExtra("Name"));
        }
    }
    @Test public void mapRejectsBadCoordinatesAndUnknownWebsites() {
        try(ActivityController<TravelMapActivity> c=Robolectric.buildActivity(TravelMapActivity.class).setup()){
            TravelMapActivity a=c.get();a.navigate(Uri.parse("poulton://pin?lat=999&lon=0"));assertFalse(a.isFinishing());
            a.navigate(Uri.parse("https://example.com/"));assertFalse(a.isFinishing());assertNull(Shadows.shadowOf(a).getNextStartedActivity());
        }
    }
    @Test public void pickedMapPlaceAndDrawnRouteCanBeSavedFromTheMainEditor() throws Exception {
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();
            a.onActivityResult(40,Activity.RESULT_OK,new Intent().putExtra("action","pin").putExtra("Name","Coast").putExtra("Latitude","51.5").putExtra("Longitude","-0.1"));
            LogbookTest.findButton(a.getWindow().getDecorView(),"Save").performClick();
            try(LogStore s=new LogStore(context)){assertEquals(1,s.list(Module.PLACES).size());}
            a.onActivityResult(40,Activity.RESULT_OK,new Intent().putExtra("action","route").putExtra("Route points",track()));
            java.util.List<android.widget.EditText> inputs=new java.util.ArrayList<>();LogbookTest.findEdits(a.getWindow().getDecorView(),inputs);inputs.get(0).setText("Drawn journey");
            LogbookTest.findButton(a.getWindow().getDecorView(),"Save").performClick();
            try(LogStore s=new LogStore(context)){assertEquals(1,s.list(Module.ROUTES).size());assertEquals("Manual waypoints",s.list(Module.ROUTES).get(0).data.getString("Route type"));}
        }
    }
    private static WebView findWeb(android.view.View v){
        if(v instanceof WebView)return(WebView)v;
        if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){WebView found=findWeb(((android.view.ViewGroup)v).getChildAt(i));if(found!=null)return found;}
        return null;
    }
}
