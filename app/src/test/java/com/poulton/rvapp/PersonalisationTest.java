package com.poulton.rvapp;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PersonalisationTest {
    private Context context;
    @Before public void before(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("personalisation",0).edit().clear().commit();Personalisation.removePhoto(context);}
    @Test public void nameSurvivesRelaunchAndBlankCannotEraseIt(){
        try(ActivityController<PersonalisationActivity> c=Robolectric.buildActivity(PersonalisationActivity.class).setup()){
            edit(c.get()).setText("  The Smiths  ");button(c.get(),"Save name").performClick();assertEquals("The Smiths",Personalisation.name(context));
            edit(c.get()).setText("  ");button(c.get(),"Save name").performClick();assertNotNull(edit(c.get()).getError());assertEquals("The Smiths",Personalisation.name(context));
        }
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            assertTrue(text(c.get().getWindow().getDecorView(),"The Smiths"));
            button(c.get(),"Personalise my logbook").performClick();assertEquals(PersonalisationActivity.class.getName(),Shadows.shadowOf(c.get()).getNextStartedActivity().getComponent().getClassName());
        }
    }
    @Test public void unsavedNameSurvivesRecreationAndPickerOnlyRequestsImages(){
        try(ActivityController<PersonalisationActivity> c=Robolectric.buildActivity(PersonalisationActivity.class).setup()){
            edit(c.get()).setText("Our Adventures");c.recreate();assertEquals("Our Adventures",edit(c.get()).getText().toString());
            button(c.get(),"Choose RV picture").performClick();Intent intent=Shadows.shadowOf(c.get()).getNextStartedActivity();assertEquals(Intent.ACTION_OPEN_DOCUMENT,intent.getAction());assertEquals("image/*",intent.getType());
            c.get().onActivityResult(1,Activity.RESULT_CANCELED,null);assertEquals("Our Adventures",edit(c.get()).getText().toString());
        }
    }
    @Test public void imageIsCopiedResizedAndKeptWhenReplacementFails()throws Exception{
        File source=new File(context.getCacheDir(),"large-rv.png");Bitmap b=Bitmap.createBitmap(2400,1200,Bitmap.Config.ARGB_8888);b.eraseColor(Color.BLUE);try(FileOutputStream out=new FileOutputStream(source)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
        Personalisation.importPhoto(context,Uri.fromFile(source));assertTrue(source.delete());Bitmap photo=Personalisation.photo(context);assertNotNull(photo);assertEquals(1600,photo.getWidth());assertEquals(800,photo.getHeight());photo.recycle();
        try{Personalisation.importPhoto(context,Uri.fromFile(source));fail("missing image accepted");}catch(IOException expected){}
        assertNotNull(Personalisation.photo(context));
        try(ActivityController<PersonalisationActivity> c=Robolectric.buildActivity(PersonalisationActivity.class).setup()){assertTrue(button(c.get(),"Remove RV picture").isEnabled());button(c.get(),"Remove RV picture").performClick();assertNull(Personalisation.photo(context));assertFalse(button(c.get(),"Remove RV picture").isEnabled());}
    }
    private static EditText edit(Activity a){List<EditText> edits=new ArrayList<>();LogbookTest.findEdits(a.getWindow().getDecorView(),edits);return edits.get(0);}
    private static Button button(Activity a,String label){Button b=LogbookTest.findButton(a.getWindow().getDecorView(),label);assertNotNull(label,b);return b;}
    private static boolean text(View v,String value){if(v instanceof TextView&&((TextView)v).getText().toString().equals(value))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(text(((ViewGroup)v).getChildAt(i),value))return true;return false;}
}
