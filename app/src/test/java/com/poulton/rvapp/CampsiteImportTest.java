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
import org.robolectric.shadows.ShadowAlertDialog;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CampsiteImportTest {
    private Context context;
    private static final String A="https://www.google.com/maps/place/Coast/data=!4m2!3m1!1sabc";
    private static final String B="https://maps.app.goo.gl/CountryCamp";
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("rv-logbook.db");}
    private static InputStream input(String csv){return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));}
    private static CampsiteImport.SavedList read(String csv)throws Exception{return CampsiteImport.read(input(csv),"Campsites.csv").get(0);}
    private static String sample(){return "Title,Note,URL,Tags,Comment\r\nCoast,Sea view,"+A+",UK,\r\nCountry,Quiet,"+B+",,\r\n";}
    private static byte[] zip(Map<String,byte[]> files)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)){for(Map.Entry<String,byte[]> file:files.entrySet()){zip.putNextEntry(new ZipEntry(file.getKey()));zip.write(file.getValue());zip.closeEntry();}}
        return out.toByteArray();
    }
    @Test public void preservesQuotedNamesMultilineNotesUnicodeAndOptionalAddress()throws Exception{
        CampsiteImport.SavedList list=read("\ufeffTitle,Note,URL,Tags,Comment,Address\r\n\"Café, Coast\",\"Sea view\nSay \"\"hello\"\"\","+A+",Dog friendly,Booked,\"High St, Kent\"\r\n");
        assertEquals("Campsites",list.name);assertEquals(1,list.campsites.size());assertEquals(0,list.skipped);
        JSONObject record=list.campsites.get(0);assertEquals("Café, Coast",record.getString("Name"));assertEquals("High St, Kent",record.getString("Address"));
        assertTrue(record.getString("Notes").contains("Sea view\nSay \"hello\""));assertTrue(record.getString("Notes").contains("Tags: Dog friendly"));assertTrue(record.getString("Notes").contains("Booked"));
        assertEquals(A,CampsiteImport.link(record));assertFalse(record.has("Arrival date"));
    }
    @Test public void archiveExposesSeparateListsWithoutExtractingFiles()throws Exception{
        Map<String,byte[]> files=new LinkedHashMap<>();files.put("Takeout/index.html","<html>Export</html>".getBytes(StandardCharsets.UTF_8));
        files.put("../../Saved/UK.csv",sample().getBytes(StandardCharsets.UTF_8));files.put("Takeout/Saved/France.csv",("Title,URL\nFrench camp,"+B).getBytes(StandardCharsets.UTF_8));
        List<CampsiteImport.SavedList> lists=CampsiteImport.read(new ByteArrayInputStream(zip(files)),"takeout.zip");
        assertEquals(2,lists.size());assertEquals("UK",lists.get(0).name);assertEquals(2,lists.get(0).campsites.size());assertEquals("France",lists.get(1).name);
    }
    @Test public void skipsWebLinksBlankTitlesAndUnsafeSchemes()throws Exception{
        CampsiteImport.SavedList list=read("Title,URL\nCamp,"+A+"\nWeb,https://example.com\n,https://maps.app.goo.gl/x\nBad,javascript:alert(1)\nSpoof,https://www.google.com.evil.test/maps/x\n");
        assertEquals(1,list.campsites.size());assertEquals(4,list.skipped);
        assertNull(CampsiteImport.mapsUrl("https://google.com@evil.test/maps/x"));assertNull(CampsiteImport.mapsUrl("https://www.google.com/search?q=camp"));
        assertNull(CampsiteImport.mapsUrl("https://maps.app.goo.gl:443/x"));assertEquals("https://maps.google.co.uk/?cid=123",CampsiteImport.mapsUrl("http://maps.google.co.uk/?cid=123"));
    }
    @Test public void rejectsMalformedFilesAndLimitsBeforeAnyWrite()throws Exception{
        String[] invalid={"", "<html>Shared list</html>","Name,Link\nCamp,"+A,"Title,URL,URL\nCamp,"+A+","+A,"Title,URL\n\"Unclosed,"+A,"Title,URL\nBad\"quote,"+A,"Title,URL\n\"Bad\"x,"+A,"Title,URL\nCamp,"+A+",extra","Title,URL\n"+"a".repeat(16001)+","+A};
        for(String csv:invalid){try{read(csv);fail("Invalid CSV accepted: "+csv.substring(0,Math.min(csv.length(),40)));}catch(IOException expected){}}
        StringBuilder big=new StringBuilder("Title,URL\n");for(int i=0;i<2001;i++)big.append("Camp,").append(A).append('\n');
        try{read(big.toString());fail("Row limit ignored");}catch(IOException expected){}
        try{CampsiteImport.read(new ByteArrayInputStream(new byte[CampsiteImport.MAX_BYTES+1]),"big.csv");fail("File limit ignored");}catch(IOException expected){}
        try{CampsiteImport.read(new ByteArrayInputStream(new byte[]{(byte)0xff,(byte)0xfe,0,0}),"utf16.csv");fail("Bad encoding accepted");}catch(IOException expected){}
        Map<String,byte[]> files=new LinkedHashMap<>();files.put("large.txt",new byte[20*1024*1024+1]);
        try{CampsiteImport.read(new ByteArrayInputStream(zip(files)),"large.zip");fail("ZIP expansion limit ignored");}catch(IOException expected){}
    }
    @Test public void importIsAdditiveDeduplicatesAndSurvivesEditAndBackup()throws Exception{
        CampsiteImport.SavedList list=read(sample());
        try(LogStore store=new LogStore(context)){
            store.save(Module.TRIPS,0,new JSONObject().put("Name","Keep trip"));store.save(Module.CAMPSITES,0,new JSONObject().put("Name","Existing camp"));
            List<JSONObject> candidates=new ArrayList<>(list.campsites);candidates.add(list.campsites.get(0));
            assertEquals(1,CampsiteImport.preview(candidates,store).duplicates);assertEquals(2,CampsiteImport.save(store,candidates));
            assertEquals("Coast",store.list(Module.CAMPSITES).get(0).data.getString("Name"));
            LogStore.Entry edited=store.list(Module.CAMPSITES).get(0);edited.data.put("Name","Renamed coast").put("Pitch","12");store.save(Module.CAMPSITES,edited.id,edited.data);
            JSONObject backup=Backup.exportRecords(store);Backup.restore(store,backup);
            assertEquals(0,CampsiteImport.save(store,list.campsites));assertEquals(3,store.list(Module.CAMPSITES).size());
            assertEquals("12",store.list(Module.CAMPSITES).get(0).data.getString("Pitch"));assertEquals("Keep trip",store.list(Module.TRIPS).get(0).data.getString("Name"));
            assertEquals(0,store.list(Module.PLACES).size());assertEquals(0,store.list(Module.ROUTES).size());
        }
    }
    @Test public void failedInsertRollsBackEntireImport()throws Exception{
        try(LogStore store=new LogStore(context)){
            store.getWritableDatabase().execSQL("CREATE TRIGGER fail_coast BEFORE INSERT ON entries WHEN NEW.module='CAMPSITES' AND NEW.data LIKE '%Coast%' BEGIN SELECT RAISE(ABORT,'test failure'); END");
            try{CampsiteImport.save(store,read(sample()).campsites);fail("Failure ignored");}catch(android.database.sqlite.SQLiteException expected){}
            assertTrue(store.list(Module.CAMPSITES).isEmpty());
        }
    }
    @Test public void previewCancellationAndSelectionOnlySaveChosenCampsites()throws Exception{
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();LogbookTest.findButton(a.getWindow().getDecorView(),"Campsites").performClick();
            LogbookTest.findButton(a.getWindow().getDecorView(),"Import Google Maps list").performClick();
            AlertDialog help=(AlertDialog)ShadowAlertDialog.getLatestAlertDialog();help.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            org.robolectric.shadows.ShadowActivity.IntentForResult pick=Shadows.shadowOf(a).getNextStartedActivityForResult();assertEquals(42,pick.requestCode);assertEquals(Intent.ACTION_OPEN_DOCUMENT,pick.intent.getAction());
            a.onActivityResult(42,Activity.RESULT_CANCELED,null);
            a.previewCampsiteList(read(sample()));AlertDialog preview=(AlertDialog)ShadowAlertDialog.getLatestAlertDialog();preview.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            try(LogStore store=new LogStore(context)){assertTrue(store.list(Module.CAMPSITES).isEmpty());}
            a.previewCampsiteList(read(sample()));preview=(AlertDialog)ShadowAlertDialog.getLatestAlertDialog();
            // Bind the row so the native adapter applies its initially checked state.
            android.view.View row=preview.getListView().getAdapter().getView(1,null,preview.getListView());
            assertTrue(preview.getListView().isItemChecked(1));
            preview.getListView().performItemClick(row,1,1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals("Import 1",preview.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
            preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            awaitCampCount(1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            try(LogStore store=new LogStore(context)){assertEquals("Coast",store.list(Module.CAMPSITES).get(0).data.getString("Name"));}
            // The database commit can finish just before the UI refresh is posted.
            long uiDeadline=System.currentTimeMillis()+5000;android.widget.Button open;
            do{Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();open=LogbookTest.findButton(a.getWindow().getDecorView(),"Open in Google Maps");if(open==null)Thread.sleep(10);}while(open==null&&System.currentTimeMillis()<uiDeadline);
            assertNotNull("Import UI did not finish refreshing",open);open.performClick();
            assertEquals(Uri.parse(A),Shadows.shadowOf(a).getNextStartedActivity().getData());
        }
    }
    @Test public void filePickerResultLoadsAReviewBeforeSavingAnything()throws Exception{
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();LogbookTest.findButton(a.getWindow().getDecorView(),"Campsites").performClick();
            Uri uri=Uri.parse("content://test/camps.csv");Shadows.shadowOf(context.getContentResolver()).registerInputStream(uri,input(sample()));
            a.onActivityResult(42,Activity.RESULT_OK,new Intent().setData(uri));
            long until=System.currentTimeMillis()+5000;AlertDialog dialog;
            do{Thread.sleep(10);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();dialog=(AlertDialog)ShadowAlertDialog.getLatestAlertDialog();}while(dialog.getListView()==null&&System.currentTimeMillis()<until);
            assertNotNull(dialog.getListView());assertEquals(2,dialog.getListView().getCount());
            try(LogStore store=new LogStore(context)){assertTrue(store.list(Module.CAMPSITES).isEmpty());}
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        }
    }
    private void awaitCampCount(int expected)throws Exception{
        long until=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<until){try(LogStore store=new LogStore(context)){if(store.list(Module.CAMPSITES).size()==expected)return;}Thread.sleep(10);}
        fail("Background import did not finish");
    }
}
