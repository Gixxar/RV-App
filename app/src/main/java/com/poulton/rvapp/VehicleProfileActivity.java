package com.poulton.rvapp;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import org.json.*;
import java.util.*;

/** Single saved RV profile, stored with the rest of the logbook and its backups. */
public final class VehicleProfileActivity extends Activity {
    private final Map<String,EditText> inputs=new LinkedHashMap<>();
    private long id;
    private boolean dirty;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);JSONObject data=new JSONObject();try(LogStore store=new LogStore(this)){List<LogStore.Entry> rows=store.list(Module.VEHICLE);if(!rows.isEmpty()){id=rows.get(0).id;data=rows.get(0).data;}}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(AdventureStyle.PAPER);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(18),dp(20),dp(24));scroll.addView(body);
        text(body,"My RV dimensions",26);text(body,"Use metres and kilograms. Measure travelling height including roof equipment, overall travelling length and width, and the fully loaded travelling weight. Check mirrors and any tow vehicle separately. Your previously stated 11 m length is suggested below; confirm it before saving.",15);
        for(String field:Module.VEHICLE.fields){TextView label=text(body,field.equals("Weight kg")?"Loaded travelling weight (kg)":field.equals("Axle count")?"Axle count (optional)":field.equals("Max axle load kg")?"Maximum loaded axle (kg, optional)":field.replace(" m"," (metres)"),14);EditText input=new EditText(this);input.setId(View.generateViewId());label.setLabelFor(input.getId());input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});input.setBackground(AdventureStyle.input(this));input.setTextColor(AdventureStyle.TEXT);input.setTextSize(17);input.setPadding(dp(14),dp(12),dp(14),dp(12));input.setMinHeight(dp(52));input.setText(state!=null?state.getString(field,""):data.optString(field,id==0&&field.equals("Length m")?"11":""));body.addView(input);inputs.put(field,input);input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){dirty=true;}public void afterTextChanged(Editable e){}});}
        text(body,"Used by RV routing and comparisons with recorded campsite limits. A fit result does not confirm that pitches are available. Updating these values requires recalculating existing RV routes. Optional axle count refines routing; if blank the provider uses 5. Axle-load limits are not checked unless you enter the maximum loaded axle weight. "+VehicleProfile.RV_NOTICE,14);
        if(id!=0)button(body,"Remove saved profile",()->new AlertDialog.Builder(this).setMessage("Remove your saved RV dimensions? Existing plans keep the measurements used when calculated.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{try(LogStore store=new LogStore(this)){store.delete(id);}dirty=false;setResult(RESULT_OK);finish();}).show());
        LinearLayout actions=new LinearLayout(this);actions.setPadding(dp(16),dp(8),dp(16),dp(8));actions.setBackgroundColor(AdventureStyle.CARD);root.addView(actions);Button cancel=button(actions,"Cancel",this::onBackPressed),save=button(actions,"Save RV",this::save);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.setMargins(0,0,dp(10),0);cancel.setLayoutParams(left);save.setLayoutParams(new LinearLayout.LayoutParams(0,-2,2));save.setTextColor(AdventureStyle.CARD);save.setBackground(AdventureStyle.vibrant(this,Module.VEHICLE));dirty=state!=null&&state.getBoolean("dirty");
    }
    private void save(){
        JSONObject data=new JSONObject();boolean invalid=false;
        for(Map.Entry<String,EditText> e:inputs.entrySet()){String value=e.getValue().getText().toString().trim();String error=value.isEmpty()&&java.util.Arrays.asList(VehicleProfile.FIELDS).contains(e.getKey())?"Required":VehicleProfile.error(e.getKey(),value);if(error!=null){e.getValue().setError(error);invalid=true;}try{data.put(e.getKey(),value);}catch(JSONException ex){return;}}
        if(invalid)return;
        try(LogStore store=new LogStore(this)){VehicleProfile.normalized(data);store.save(Module.VEHICLE,id,data);dirty=false;setResult(RESULT_OK);finish();}catch(Exception e){Toast.makeText(this,"Could not save your RV. Please retry.",Toast.LENGTH_LONG).show();}
    }
    private TextView text(LinearLayout body,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(size>20?AdventureStyle.INK:AdventureStyle.TEXT);t.setPadding(0,dp(10),0,dp(8));body.addView(t);return t;}
    private Button button(LinearLayout parent,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(AdventureStyle.accent(Module.VEHICLE));b.setBackground(AdventureStyle.pressable(this,AdventureStyle.tint(Module.VEHICLE),AdventureStyle.BORDER));parent.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->action.run());return b;}
    private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density);}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);for(Map.Entry<String,EditText> e:inputs.entrySet())state.putString(e.getKey(),e.getValue().getText().toString());state.putBoolean("dirty",dirty);}
    @Override public void onBackPressed(){if(dirty)new AlertDialog.Builder(this).setMessage("Discard unsaved RV measurements?").setNegativeButton("Keep editing",null).setPositiveButton("Discard",(d,w)->finish()).show();else finish();}
}
