package com.poulton.rvapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.Locale;

/** Service planner backed by the same reminders used by Android notifications. */
public final class ServiceScheduleActivity extends Activity {
    private LogStore store;private LinearLayout body;private String filter="All active";
    @Override public void onCreate(Bundle state){super.onCreate(state);store=new LogStore(this);if(state!=null)filter=state.getString("filter","All active");}
    @Override protected void onResume(){super.onResume();render();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void text(String label,int size,int color){TextView v=new TextView(this);v.setText(label);v.setTextSize(size);v.setTextColor(color);v.setTypeface(size>=20?AdventureStyle.DISPLAY:AdventureStyle.BODY);v.setPadding(0,dp(6),0,dp(8));body.addView(v);}
    private void button(String label,Module color,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setMinHeight(dp(50));b.setTextColor(AdventureStyle.CARD);b.setBackground(AdventureStyle.vibrant(this,color));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));body.addView(b,lp);}
    private void render(){
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(20),dp(20),dp(24));body.setBackgroundColor(AdventureStyle.PAPER);scroll.addView(body);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        text("Service schedule",28,AdventureStyle.accent(Module.REMINDERS));double miles=store.latestMileage();LocalDate today=LocalDate.now();
        int due=0,soon=0,active=0;for(LogStore.Entry e:store.list(Module.REMINDERS)){int state=ServiceSchedule.state(e.data,miles,today);if(state<4)active++;if(state<2)due++;if(state==2)soon++;}
        LinearLayout page=body,banner=new LinearLayout(this);banner.setOrientation(LinearLayout.VERTICAL);banner.setPadding(dp(16),dp(10),dp(16),dp(10));banner.setBackground(AdventureStyle.vibrant(this,Module.REMINDERS));page.addView(banner,new LinearLayout.LayoutParams(-1,-2));body=banner;
        text(due+" due · "+soon+" due soon · "+active+" active",20,AdventureStyle.CARD);text(String.format(Locale.UK,"Highest logged mileage: %,.0f miles",miles),14,AdventureStyle.CARD);body=page;
        text("Date or mileage, whichever comes first. Due soon means within 30 days or 500 miles. Enter intervals from your RV and equipment service manuals.",14,AdventureStyle.TEXT);
        button("Add scheduled service",Module.REMINDERS,()->startActivity(new Intent(this,MainActivity.class).putExtra("new_service",true)));
        button("Back to logbook",Module.TRIPS,this::finish);
        String[] filters={"All active","Due & overdue","Due soon","Completed"};Spinner choice=new Spinner(this);choice.setContentDescription("Filter service schedule");choice.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,filters));choice.setSelection(Math.max(0,java.util.Arrays.asList(filters).indexOf(filter)));body.addView(choice);choice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,android.view.View v,int position,long id){if(!filter.equals(filters[position])){filter=filters[position];render();}}});
        int count=0;
        for(LogStore.Entry e:ServiceSchedule.ordered(store)){
            JSONObject d=e.data;int state=ServiceSchedule.state(d,miles,today);if(filter.equals("All active")?state==4:filter.equals("Due & overdue")?state>1:filter.equals("Due soon")?state!=2:state!=4)continue;count++;
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(12),dp(16),dp(12));Module color=state<2?Module.REMINDERS:state==2?Module.FUEL:state==4?Module.CHECKS:Module.PLANS;card.setBackground(AdventureStyle.panel(this,AdventureStyle.tint(color),AdventureStyle.BORDER));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(12),0,dp(4));body.addView(card,lp);LinearLayout parent=body;body=card;
            text(d.optString("Service"),21,AdventureStyle.accent(color));text(ServiceSchedule.status(d,miles,today)+(d.optString("Category").isEmpty()?"":" · "+d.optString("Category")),15,AdventureStyle.accent(color));
            if(state!=4){text(ServiceSchedule.remaining(d,miles,today),15,AdventureStyle.TEXT);text("Next due: "+(d.optString("Due date").isEmpty()?"date not set":d.optString("Due date"))+(d.optString("Due miles").isEmpty()?"":" · "+d.optString("Due miles")+" miles"),14,AdventureStyle.TEXT);}
            String interval="";if(d.optInt("Repeat months",0)>0)interval=d.optString("Repeat months")+" months";if(d.optDouble("Repeat miles",0)>0)interval+=(interval.isEmpty()?"":" or ")+d.optString("Repeat miles")+" miles";text(interval.isEmpty()?"One-time service":"Repeats every "+interval,14,AdventureStyle.MUTED);
            if(!d.optString("Last service date").isEmpty()||!d.optString("Last service miles").isEmpty())text("Last serviced: "+d.optString("Last service date")+(d.optString("Last service miles").isEmpty()?"":" · "+d.optString("Last service miles")+" miles"),14,AdventureStyle.TEXT);
            if(!d.optString("Notes").isEmpty())text(d.optString("Notes"),14,AdventureStyle.TEXT);
            if(state!=4)button("Record service",Module.MAINTENANCE,()->startActivity(new Intent(this,MainActivity.class).putExtra("record_service",e.id)));
            button("Edit service schedule",Module.PLANS,()->startActivity(new Intent(this,MainActivity.class).putExtra("edit_service",e.id)));
            String schedule=d.optString("Schedule ID");int history=0;
            for(LogStore.Entry work:store.list(Module.MAINTENANCE))if((!schedule.isEmpty()&&schedule.equals(work.data.optString("Schedule ID")))||(!d.optString("Reminder ID").isEmpty()&&d.optString("Reminder ID").equals(work.data.optString("Source reminder ID")))){if(history++==0)text("Recorded maintenance",18,AdventureStyle.TEXT);text(work.data.optString("Date")+" · "+work.data.optString("Work")+(work.data.optString("Provider").isEmpty()?"":" · "+work.data.optString("Provider")),14,AdventureStyle.TEXT);}
            body=parent;
        }
        if(count==0)text(filter.equals("All active")?"No scheduled services yet. Add a service and set its first due date or mileage.":"No services match this filter.",16,AdventureStyle.TEXT);
        text("Android checks approximately daily; notification permission and battery settings affect delivery. Photos are not included in record backups.",12,AdventureStyle.MUTED);
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("filter",filter);}
    @Override protected void onDestroy(){store.close();super.onDestroy();}
}
