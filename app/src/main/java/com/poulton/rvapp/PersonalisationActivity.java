package com.poulton.rvapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.graphics.Bitmap;

public final class PersonalisationActivity extends Activity {
    private EditText name;
    private ImageView preview;
    private TextView status;
    private Button choose,remove;
    private boolean busy;
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(AdventureStyle.PAPER);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(20),dp(20),dp(28));scroll.addView(body);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        label(body,"Personalise my logbook",26);
        label(body,"Choose the name and RV picture shown on your home screen.",16);
        TextView nameLabel=label(body,"Your name or family name",16);
        name=new EditText(this);name.setId(View.generateViewId());nameLabel.setLabelFor(name.getId());name.setSingleLine(true);name.setTextSize(18);name.setTextColor(AdventureStyle.TEXT);name.setBackground(AdventureStyle.input(this));name.setPadding(dp(12),dp(12),dp(12),dp(12));name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});name.setText(saved==null?Personalisation.name(this):saved.getString("name",Personalisation.name(this)));body.addView(name,new LinearLayout.LayoutParams(-1,-2));
        addButton(body,"Save name",()->{try{Personalisation.saveName(this,name.getText().toString());setResult(RESULT_OK);toast("Name saved");}catch(RuntimeException e){name.setError(e.getMessage());}});
        label(body,"Your RV picture",20);
        preview=new ImageView(this);preview.setContentDescription("Your RV picture preview");preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackground(AdventureStyle.panel(this,AdventureStyle.CARD,AdventureStyle.BORDER));body.addView(preview,new LinearLayout.LayoutParams(-1,dp(220)));
        status=label(body,"",14);
        choose=addButton(body,"Choose RV picture",()->{try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),1);}catch(RuntimeException e){toast("No picture picker available");}});
        remove=addButton(body,"Remove RV picture",()->{Personalisation.removePhoto(this);setResult(RESULT_OK);refreshPhoto();});
        label(body,"Your picture is saved on this device. Choose a landscape photo for the best fit. Personalisation stays on this device; record backups do not include it.",14);
        addButton(body,"Done",this::finish);
        refreshPhoto();
    }
    private void refreshPhoto(){Bitmap bitmap=Personalisation.photo(this);if(bitmap!=null)preview.setImageBitmap(bitmap);else preview.setImageResource(R.drawable.ic_rv);remove.setEnabled(bitmap!=null&&!busy);status.setText(bitmap==null?"No personal RV picture selected":"Your RV picture is saved");}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=1||result!=RESULT_OK||data==null||data.getData()==null)return;
        busy=true;choose.setEnabled(false);remove.setEnabled(false);status.setText("Saving your picture…");
        android.content.Context context=getApplicationContext();android.net.Uri uri=data.getData();
        worker.execute(()->{String error=null;try{Personalisation.importPhoto(context,uri);}catch(Exception e){error="Could not use that picture. Choose another image.";}final String message=error;runOnUiThread(()->{if(isFinishing()||isDestroyed())return;busy=false;choose.setEnabled(true);refreshPhoto();if(message==null){setResult(RESULT_OK);toast("RV picture saved");}else{status.setText(message);toast(message);}});});
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("name",name.getText().toString());}
    @Override protected void onResume(){super.onResume();if(preview!=null&&!busy)refreshPhoto();}
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
    private TextView label(LinearLayout body,String text,int size){TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(AdventureStyle.TEXT);view.setPadding(0,dp(10),0,dp(10));body.addView(view);return view;}
    private Button addButton(LinearLayout body,String title,Runnable action){Button button=new Button(this);button.setText(title);button.setAllCaps(false);button.setTextColor(AdventureStyle.CARD);button.setBackground(AdventureStyle.vibrant(this,null));button.setMinHeight(dp(48));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(8),0,dp(8));body.addView(button,lp);button.setOnClickListener(v->action.run());return button;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
