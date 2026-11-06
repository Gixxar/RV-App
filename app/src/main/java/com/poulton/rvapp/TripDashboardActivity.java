package com.poulton.rvapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import org.json.JSONObject;
import java.util.Locale;

/** Per-trip overview. Template checklists stay independent of historical snapshots. */
public final class TripDashboardActivity extends Activity {
    private LogStore store;private String key;private LinearLayout body;private String expandedPhase="";
    @Override public void onCreate(Bundle state){super.onCreate(state);store=new LogStore(this);key=getIntent().getStringExtra("trip");}
    @Override protected void onResume(){super.onResume();render();}
    private void label(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(AdventureStyle.INK);v.setTypeface(size>=20?AdventureStyle.DISPLAY:AdventureStyle.BODY);v.setPadding(0,12,0,12);body.addView(v);}
    private void button(String value,Runnable action){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setMinHeight((int)(52*getResources().getDisplayMetrics().density));b.setTextColor(AdventureStyle.INK);b.setTextSize(15);b.setTypeface(AdventureStyle.BODY);b.setBackground(AdventureStyle.pressable(this,AdventureStyle.CARD,AdventureStyle.BORDER));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,6,0,6);body.addView(b,lp);}
    private void render(){
        LogStore.Entry trip;try{trip=TripWorkflow.trip(store,key);}catch(RuntimeException e){finish();return;}
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int pad=(int)(20*getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);body.setBackgroundColor(AdventureStyle.PAPER);scroll.addView(body);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        label(trip.data.optString("Name"),28);String status=trip.data.optString("Status","Planned");label(status+" · "+trip.data.optString("Date"),16);label(trip.data.optString("From")+" → "+trip.data.optString("To"),18);
        double total=0;for(Module m:new Module[]{Module.FUEL,Module.EXPENSES,Module.MAINTENANCE})for(LogStore.Entry row:store.list(m))if(linked(row))total+=row.data.optDouble("Cost GBP",0);
        String distance="Mileage not yet recorded";if(!trip.data.optString("Start miles").isEmpty()&&!trip.data.optString("End miles").isEmpty())distance=String.format(Locale.UK,"%.1f miles",trip.data.optDouble("End miles")-trip.data.optDouble("Start miles"));label(distance+String.format(Locale.UK," · £%.2f recorded costs",total),16);
        String next=status.equals("Planned")?"In progress":status.equals("In progress")?"Arrived":status.equals("Arrived")?"Finished":null;
        if(next!=null)button(next.equals("In progress")?"Start trip & departure checklist":next.equals("Arrived")?"Arrive & setup checklist":"Finish trip",()->{
            int attention=0,open=0;for(LogStore.Entry e:store.list(Module.TRIP_CHECKS))if(linked(e)){String s=e.data.optString("Status");if(s.equals("Needs attention"))attention++;if(!s.equals("Done")&&!s.equals("Not applicable"))open++;}
            String message=open>0?open+" checklist items remain unresolved ("+attention+" need attention). Continue?":"Change trip status to "+next+"?";
            new AlertDialog.Builder(this).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->{try{TripWorkflow.advance(store,key,next);expandedPhase=next.equals("In progress")?"Departure":next.equals("Arrived")?"Arrival":"";render();}catch(RuntimeException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();render();}}).show();
        });
        for(String phase:new String[]{"Departure","Arrival"}){
            int all=0,done=0;for(LogStore.Entry e:store.list(Module.TRIP_CHECKS))if(linked(e)&&phase.equals(e.data.optString("Phase"))){all++;if(e.data.optString("Status").matches("Done|Not applicable"))done++;}
            final String section=phase;button(phase+" checklist · "+done+" / "+all+(expandedPhase.equals(phase)?" · hide":" · view"),()->{expandedPhase=expandedPhase.equals(section)?"":section;render();});
            if(all==0)label("Created when you "+(phase.equals("Departure")?"start the trip.":"mark arrival."),14);
            for(LogStore.Entry e:store.list(Module.TRIP_CHECKS))if(expandedPhase.equals(phase)&&linked(e)&&phase.equals(e.data.optString("Phase")))button(e.data.optString("Status")+" · "+e.data.optString("Item"),()->check(e));
        }
        label("Trip records",22);
        for(Module m:new Module[]{Module.FUEL,Module.EXPENSES,Module.CAMPSITES,Module.PHOTOS,Module.MAINTENANCE,Module.REMINDERS,Module.PLANS,Module.PLACES,Module.ROUTES}){
            int count=0;for(LogStore.Entry e:store.list(m))if(linked(e))count++;
            final Module module=m;button(module.title+" · "+count,()->startActivity(new Intent(this,MainActivity.class).putExtra("module",module.name()).putExtra("trip",key)));
        }
        label("Trip links use a permanent ID. Older records can be linked using ‘Choose trip’ in their editor. Planned routes can be attached below.",14);
        button("Link or unlink an existing record",this::linkExisting);
        button("Back to trips",this::finish);
    }
    private boolean linked(LogStore.Entry e){return key.equals(e.data.optString("Trip ID"));}
    private void check(LogStore.Entry row){
        EditText notes=new EditText(this);notes.setText(row.data.optString("Notes"));notes.setHint("Checklist notes");notes.setMinLines(2);
        final int[] chosen={java.util.Arrays.asList(TripWorkflow.STATES).indexOf(row.data.optString("Status"))};
        new AlertDialog.Builder(this).setTitle(row.data.optString("Item")).setView(notes).setSingleChoiceItems(TripWorkflow.STATES,chosen[0],(d,i)->chosen[0]=i).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{TripWorkflow.put(row.data,"Status",TripWorkflow.STATES[Math.max(0,chosen[0])]);TripWorkflow.put(row.data,"Notes",notes.getText().toString().trim());store.save(Module.TRIP_CHECKS,row.id,row.data);render();}).show();
    }
    private void linkExisting(){
        Module[] modules={Module.FUEL,Module.EXPENSES,Module.CAMPSITES,Module.PHOTOS,Module.MAINTENANCE,Module.REMINDERS,Module.PLANS,Module.PLACES,Module.ROUTES};String[] labels=new String[modules.length];for(int i=0;i<labels.length;i++)labels[i]=modules[i].title;
        new AlertDialog.Builder(this).setTitle("Choose record area").setItems(labels,(d,index)->{
            Module m=modules[index];java.util.List<LogStore.Entry> rows=store.list(m);String[] titles=new String[rows.size()];for(int i=0;i<titles.length;i++){LogStore.Entry e=rows.get(i);titles[i]=e.data.optString(m.fields[0])+" · "+e.data.optString("Date",e.data.optString("Address"))+ (linked(e)?" [linked here]":e.data.optString("Trip ID").isEmpty()?"":" [another trip]");}
            new AlertDialog.Builder(this).setTitle("Select a record").setItems(titles,(dd,i)->{LogStore.Entry e=rows.get(i);new AlertDialog.Builder(this).setMessage(linked(e)?"Unlink this record from this trip?":"Link this record to this trip? This replaces its previous trip link.").setNegativeButton("Cancel",null).setPositiveButton("Confirm",(ddd,w)->{TripWorkflow.put(e.data,"Trip ID",linked(e)?"":key);store.save(m,e.id,e.data);render();}).show();}).setNegativeButton("Close",null).show();
        }).setNegativeButton("Cancel",null).show();
    }
    @Override protected void onDestroy(){store.close();super.onDestroy();}
}
