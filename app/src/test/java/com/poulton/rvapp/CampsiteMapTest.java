package com.poulton.rvapp;

import android.app.*;
import android.content.*;
import android.net.Uri;
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
public class CampsiteMapTest {
    private Context context;
    private static final String URL="https://www.google.com/maps/place/Camp/data=!3d51.5!4d-0.1";
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("rv-logbook.db");}
    private static JSONObject camp(String name)throws Exception{return new JSONObject().put("Name",name);}
    private static List<JSONObject> read(String csv)throws Exception{return CampsiteImport.read(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),"Sites.csv").get(0).campsites;}
    @Test public void importsCoordinatesAndKeepsCampsitesSeparateFromVisits()throws Exception{
        try(LogStore store=new LogStore(context)){
            CampsiteImport.save(store,read("Title,URL,Latitude,Longitude\nCamp,"+URL+",-12.3,130.4\n"));
            JSONObject site=store.list(Module.CAMPSITES).get(0).data;
            assertEquals(-12.3,CampsiteLocations.position(site)[0],0);
            JSONObject payload=TravelMapActivity.payload(store);
            assertEquals(1,payload.getJSONArray("campsites").length());assertEquals(0,payload.getJSONArray("places").length());
            assertEquals(0,payload.getInt("missingCampsites"));assertEquals(0,store.list(Module.PLACES).size());
            Backup.restore(store,Backup.exportRecords(store));
            assertEquals(-12.3,CampsiteLocations.position(store.list(Module.CAMPSITES).get(0).data)[0],0);
        }
    }
    @Test public void retainsLinkCoordinatesAndCountsUnlocatedOlderImports()throws Exception{
        assertNull(CampsiteLocations.fromLink("https://www.google.com/maps/place/Camp/@51.5,-0.1,8z"));
        assertNull(CampsiteLocations.fromLink("https://www.google.com/maps/place/Camp/data=!3d999!4d0"));
        assertArrayEquals(new double[]{51.5,-0.1},CampsiteLocations.fromLink(URL),0);
        try(LogStore store=new LogStore(context)){
            store.save(Module.CAMPSITES,0,camp("Old import").put("Notes","Google Maps: "+URL));
            store.save(Module.CAMPSITES,0,camp("Missing").put("Notes","Google Maps: https://maps.app.goo.gl/unknown"));
            JSONObject payload=TravelMapActivity.payload(store);assertEquals(1,payload.getJSONArray("campsites").length());assertEquals(1,payload.getInt("missingCampsites"));
        }
    }
    @Test public void rejectsPartialOrInvalidImportedCoordinatesBeforeSaving()throws Exception{
        for(String pair:new String[]{"91,0","0,NaN","51,","x,0"}){
            try{read("Title,URL,Latitude,Longitude\nCamp,"+URL+","+pair+"\n");fail("Accepted invalid coordinates");}catch(IOException expected){}
        }
        Map<String,String> values=new HashMap<>();values.put("Name","Camp");values.put("Latitude","51");assertNotNull(Rules.validateRecord(Module.CAMPSITES,values));
        values.put("Longitude","-1");assertNull(Rules.validateRecord(Module.CAMPSITES,values));
    }
    @Test public void lookupUpdatesSameRecordWithoutOverwritingLocationsOrCreatingVisits()throws Exception{
        try(LogStore store=new LogStore(context)){
            store.save(Module.CAMPSITES,0,camp("Coast").put("Address","Kent").put("Pitch","42"));long id=store.list(Module.CAMPSITES).get(0).id;
            assertTrue(CampsiteLocations.saveSearch(store,id,"Coast Kent",51,-1));
            assertFalse(CampsiteLocations.saveSearch(store,id,"Coast Kent",52,-2));
            JSONObject data=store.list(Module.CAMPSITES).get(0).data;assertEquals("42",data.getString("Pitch"));assertEquals(51,CampsiteLocations.position(data)[0],0);
            assertEquals("Search result — check location",data.getString("Location source"));
            assertTrue(CampsiteLocations.missing(store).isEmpty());assertTrue(store.list(Module.PLACES).isEmpty());
            store.save(Module.CAMPSITES,0,camp("Renamed"));long other=store.list(Module.CAMPSITES).get(0).id;assertFalse(CampsiteLocations.saveSearch(store,other,"Original",0,0));
            store.delete(other);assertFalse(CampsiteLocations.saveSearch(store,other,"Renamed",0,0));
        }
    }
    @Test public void mapPinOpensCampsiteEditorAndPickerDoesNotDiscardDraft()throws Exception{
        long id;try(LogStore store=new LogStore(context)){store.save(Module.CAMPSITES,0,camp("Coast").put("Notes","My notes"));id=store.list(Module.CAMPSITES).get(0).id;}
        try(ActivityController<TravelMapActivity> c=Robolectric.buildActivity(TravelMapActivity.class).setup()){
            TravelMapActivity a=c.get();a.navigate(Uri.parse("poulton://edit?module=CAMPSITES&id="+id));
            Intent result=Shadows.shadowOf(a).getResultIntent();assertEquals("CAMPSITES",result.getStringExtra("module"));assertEquals(id,result.getLongExtra("id",0));
        }
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();a.onActivityResult(40,Activity.RESULT_OK,new Intent().putExtra("action","edit").putExtra("module","CAMPSITES").putExtra("id",id));
            LogbookTest.findButton(a.getWindow().getDecorView(),"Choose campsite location on map").performClick();
            org.robolectric.shadows.ShadowActivity.IntentForResult request=Shadows.shadowOf(a).getNextStartedActivityForResult();assertEquals(45,request.requestCode);
            a.onActivityResult(45,Activity.RESULT_OK,new Intent().putExtra("Latitude","51.2").putExtra("Longitude","-0.4"));
            LogbookTest.findButton(a.getWindow().getDecorView(),"Save").performClick();
            try(LogStore store=new LogStore(context)){JSONObject data=store.list(Module.CAMPSITES).get(0).data;assertEquals("My notes",data.getString("Notes"));assertEquals(51.2,CampsiteLocations.position(data)[0],0);assertTrue(store.list(Module.PLACES).isEmpty());}
        }
    }
}
