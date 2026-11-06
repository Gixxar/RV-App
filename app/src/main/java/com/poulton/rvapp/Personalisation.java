package com.poulton.rvapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.util.AtomicFile;
import java.io.*;

/** Local identity and a bounded, private copy of the chosen RV picture. */
final class Personalisation {
    static final String DEFAULT_NAME="The Poultons";
    private Personalisation() {}
    static String name(Context c){return c.getSharedPreferences("personalisation",0).getString("name",DEFAULT_NAME);}
    static boolean customName(Context c){return !DEFAULT_NAME.equals(name(c));}
    static void saveName(Context c,String name){
        name=name.trim();
        if(name.isEmpty()||name.length()>60)throw new IllegalArgumentException("Enter a name of 1–60 characters");
        if(!c.getSharedPreferences("personalisation",0).edit().putString("name",name).commit())throw new IllegalStateException("Could not save your name. Please retry.");
    }
    static File photoFile(Context c){return new File(c.getFilesDir(),"my-rv.jpg");}
    static Bitmap photo(Context c){return BitmapFactory.decodeFile(photoFile(c).getPath());}
    static void removePhoto(Context c){new AtomicFile(photoFile(c)).delete();}
    static void importPhoto(Context c,Uri uri)throws IOException {
        Bitmap bitmap;
        if(Build.VERSION.SDK_INT>=28){
            bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(),uri),(decoder,info,source)->{
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                float scale=Math.min(1f,1600f/Math.max(w,h));
                decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }else{
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
            if(options.outWidth<=0||options.outHeight<=0)throw new IOException("Choose a supported image");
            options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;
            try(InputStream in=c.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}
        }
        if(bitmap==null)throw new IOException("Choose a supported image");
        AtomicFile file=new AtomicFile(photoFile(c));FileOutputStream out=null;
        try{out=file.startWrite();if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("Could not save picture");file.finishWrite(out);}
        catch(IOException|RuntimeException e){if(out!=null)file.failWrite(out);throw e;}
        finally{bitmap.recycle();}
    }
}
