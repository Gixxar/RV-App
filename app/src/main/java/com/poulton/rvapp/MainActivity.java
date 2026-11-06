package com.poulton.rvapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.Manifest;
import android.os.Build;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.view.WindowInsets;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.OutputStream;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MainActivity extends Activity {
    private LogStore store;
    private LinearLayout content,appRoot;
    private String currentScreen="Overview";
    private String scopedTrip="";
    private long completingReminder;
    private int campsiteFilter;
    private Module selected;
    private JSONObject draft;
    private long editId;
    private final Map<String, EditText> inputs = new LinkedHashMap<>();
    private TextView photoLabel;
    private final java.util.concurrent.ExecutorService imports=java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new LogStore(this);if(state!=null)campsiteFilter=state.getInt("campsiteFilter",0);
        scopedTrip=getIntent().getStringExtra("trip")==null?"":getIntent().getStringExtra("trip");
        if(state!=null)completingReminder=state.getLong("completingReminder");
        ServiceNotifications.schedule(this);
        ServiceNotifications.refresh(this);
        if (state != null && state.containsKey("module")) {
            selected = Module.valueOf(state.getString("module"));
            if (state.containsKey("draft")) {
                try { editId = state.getLong("editId"); editor(new JSONObject(state.getString("draft")), editId); }
                catch (Exception e) { list(selected); }
            } else list(selected);
        } else if(state!=null && "Checklists".equals(state.getString("section"))) checksHub();
        else if(state!=null && "Records".equals(state.getString("section"))) recordsHub();
        else if(state!=null && "Backup & restore".equals(state.getString("section"))) backups();
        else if(getIntent().getBooleanExtra("new_service",false)){selected=Module.REMINDERS;editor(new JSONObject(),0);}
        else if(getIntent().hasExtra("record_service")||getIntent().hasExtra("edit_service")){
            try{long id=getIntent().getLongExtra(getIntent().hasExtra("record_service")?"record_service":"edit_service",0);LogStore.Entry e=TripWorkflow.find(store,Module.REMINDERS,id);if(getIntent().hasExtra("record_service"))recordService(e);else{selected=Module.REMINDERS;editor(e.data,e.id);}}catch(RuntimeException e){toast("Service no longer exists");finish();}
        }
        else if (getIntent().getBooleanExtra("show_reminders",false)) list(Module.REMINDERS);
        else if(getIntent().hasExtra("module"))list(Module.valueOf(getIntent().getStringExtra("module")));
        else home();
    }
    @Override protected void onResume() {
        super.onResume(); ServiceNotifications.refresh(this);
    }
    private void screen(String title) {
        boolean dashboard=title.equals("Overview");
        boolean form=title.startsWith("Add: ")||title.startsWith("Edit: ");
        currentScreen=dashboard?"Overview":form?(editId==0?"New ":"Edit ")+singular(selected):title;
        appRoot=new LinearLayout(this);appRoot.setOrientation(LinearLayout.VERTICAL);appRoot.setBackgroundColor(AdventureStyle.PAPER);setContentView(appRoot);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(16),dp(6),dp(16),dp(6));toolbar.setBackground(AdventureStyle.vibrant(this,selected));appRoot.addView(toolbar);
        if(!dashboard){Button back=new Button(this);back.setText("‹");back.setTextSize(28);back.setTextColor(AdventureStyle.CARD);back.setBackgroundColor(android.graphics.Color.TRANSPARENT);back.setContentDescription("Back");toolbar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));back.setOnClickListener(v->{if(draft!=null)discardEditor();else if(!scopedTrip.isEmpty())finish();else home();});}
        ImageView crest=new ImageView(this);crest.setImageResource(Personalisation.customName(this)?R.drawable.ic_rv:R.drawable.poulton_crest);crest.setContentDescription("RV logbook");crest.setScaleType(ImageView.ScaleType.FIT_CENTER);toolbar.addView(crest,new LinearLayout.LayoutParams(dp(30),dp(42)));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);brand.setPadding(dp(10),0,0,0);toolbar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView name=new TextView(this);name.setText(Personalisation.name(this));name.setTypeface(AdventureStyle.DISPLAY);name.setTextColor(AdventureStyle.CARD);name.setTextSize(17);brand.addView(name);
        TextView sub=new TextView(this);sub.setText(R.string.app_subtitle);sub.setTextColor(0xffe1ebff);sub.setTextSize(12);brand.addView(sub);brand.setContentDescription("Edit logbook name and RV picture");if(!form)brand.setOnClickListener(v->openPersonalisation());
        View divider=new View(this);divider.setBackgroundColor(AdventureStyle.BORDER);appRoot.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);appRoot.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(20),dp(20),dp(28));scroll.addView(content);
        TextView heading=text(currentScreen,28);heading.setTypeface(AdventureStyle.DISPLAY);heading.setTextColor(AdventureStyle.accent(selected));heading.setPadding(0,0,0,dp(8));if(Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);
        if(!form)bottomNavigation(dashboard);
        appRoot.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
    }
    private String singular(Module module){
        switch(module){case VEHICLE:return "RV profile";case PLANS:return "route plan";case TRIPS:return "trip";case CHECKS:case ARRIVAL:return "check";case FUEL:return "fuel entry";case EXPENSES:return "expense";case MAINTENANCE:return "maintenance record";case CAMPSITES:return "campsite";case PHOTOS:return "photo";case MILEAGE:return "mileage reading";case REMINDERS:return "service reminder";case PLACES:return "visited place";default:return "route";}
    }
    private void bottomNavigation(boolean dashboard){
        LinearLayout nav=new LinearLayout(this);nav.setBackgroundColor(AdventureStyle.CARD);nav.setPadding(dp(10),dp(8),dp(10),dp(8));appRoot.addView(nav);
        if(!scopedTrip.isEmpty()){navItem(nav,"Back to trip",Module.TRIPS,false,this::finish);return;}
        navItem(nav,"Home",Module.TRIPS,dashboard,this::home);
        navItem(nav,"Map",Module.PLACES,false,()->startActivityForResult(new Intent(this,TravelMapActivity.class),40));
        navItem(nav,"Checks",Module.CHECKS,(selected!=null&&selected.isChecklist())||currentScreen.equals("Checklists"),this::checksHub);
        navItem(nav,"Records",Module.MAINTENANCE,!dashboard&&(selected==null||!selected.isChecklist())&&!currentScreen.equals("Checklists"),this::recordsHub);
    }
    private void navItem(LinearLayout row,String title,Module icon,boolean active,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(11);b.setTypeface(AdventureStyle.DISPLAY);b.setTextColor(AdventureStyle.accent(icon));b.setSelected(active);b.setMinHeight(dp(52));b.setPadding(dp(3),dp(4),dp(3),dp(4));b.setBackground(AdventureStyle.pressable(this,active?AdventureStyle.tint(icon):AdventureStyle.CARD,0));
        android.graphics.drawable.Drawable symbol=new NavigationIcon(title,AdventureStyle.accent(icon));symbol.setBounds(0,0,dp(21),dp(21));b.setCompoundDrawables(null,symbol,null,null);b.setCompoundDrawablePadding(dp(4));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);row.addView(b,lp);b.setOnClickListener(v->action.run());
    }
    private void section(String label,int color){TextView t=text(label,20);t.setTypeface(AdventureStyle.DISPLAY);t.setTextColor(color);t.setPadding(0,dp(22),0,dp(10));if(Build.VERSION.SDK_INT>=28)t.setAccessibilityHeading(true);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTypeface(AdventureStyle.BODY);t.setTextColor(AdventureStyle.TEXT);t.setLineSpacing(dp(3),1);t.setPadding(0,dp(6),0,dp(8));content.addView(t);return t;}
    private Button makeButton(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(AdventureStyle.DISPLAY);b.setMinHeight(dp(48));b.setPadding(dp(14),dp(10),dp(14),dp(10));
        boolean secondary=label.startsWith("Edit")||label.startsWith("Choose")||label.startsWith("Pick")||label.startsWith("Cancel")||label.startsWith("Reset")||label.startsWith("Open")||label.startsWith("Notification")||label.startsWith("Import")||label.startsWith("Restore")||label.startsWith("Backup")||label.startsWith("View");
        boolean danger=label.startsWith("Delete");b.setTextColor(danger?AdventureStyle.DANGER:secondary?AdventureStyle.accent(selected):AdventureStyle.CARD);b.setBackground(secondary||danger?AdventureStyle.pressable(this,danger?0xfffde8ec:AdventureStyle.tint(selected),AdventureStyle.BORDER):AdventureStyle.vibrant(this,selected));b.setOnClickListener(v->action.run());return b;
    }
    private void button(String label,Runnable action){Button b=makeButton(label,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));content.addView(b,lp);}
    private void home(){
        selected=null;draft=null;screen("Overview");
        TextView intro=text("Journeys, checklists and vehicle care.",14);intro.setTextColor(AdventureStyle.MUTED);
        android.graphics.Bitmap rvPhoto=Personalisation.photo(this);
        ImageView hero=new ImageView(this);if(rvPhoto!=null)hero.setImageBitmap(rvPhoto);else hero.setImageResource(Personalisation.customName(this)?R.drawable.ic_rv:R.drawable.adventure_header);hero.setContentDescription(Personalisation.name(this)+" · RV picture · tap to personalise");hero.setAdjustViewBounds(true);hero.setScaleType(ImageView.ScaleType.FIT_CENTER);hero.setBackground(AdventureStyle.panel(this,AdventureStyle.CARD,0));hero.setClipToOutline(true);LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,rvPhoto!=null||Personalisation.customName(this)?dp(220):-2);heroLp.setMargins(0,dp(8),0,dp(12));content.addView(hero,heroLp);hero.setOnClickListener(v->openPersonalisation());
        button("Personalise my logbook",this::openPersonalisation);
        LinearLayout stats=new LinearLayout(this);stats.setBaselineAligned(false);content.addView(stats);stat(stats,String.format(Locale.UK,"%,.0f",store.latestMileage()),"Odometer · miles");stat(stats,String.format(Locale.UK,"£%,.2f",store.totalCost()),"Recorded costs");
        int due=store.dueCount();Button service=makeButton(due==0?"Service reminders · all up to date":due+" service reminder(s) due · review",()->list(Module.REMINDERS));service.setTextColor(AdventureStyle.accent(Module.REMINDERS));service.setTextSize(14);service.setBackground(AdventureStyle.pressable(this,AdventureStyle.tint(Module.REMINDERS),0));LinearLayout.LayoutParams serviceLp=new LinearLayout.LayoutParams(-1,-2);serviceLp.setMargins(0,dp(8),0,0);content.addView(service,serviceLp);
        section("Your next journey",AdventureStyle.accent(Module.TRIPS));LinearLayout quick=new LinearLayout(this);quick.setBaselineAligned(false);content.addView(quick);tile(quick,Module.TRIPS,true);
        Button map=makeButton("Travel map\nRoutes & visited places",()->startActivityForResult(new Intent(this,TravelMapActivity.class),40));styleTile(map,Module.PLACES);LinearLayout.LayoutParams mapLp=new LinearLayout.LayoutParams(0,-2,1);mapLp.setMargins(dp(5),dp(4),0,dp(4));quick.addView(map,mapLp);
        button("Service schedule",()->startActivity(new Intent(this,ServiceScheduleActivity.class)));
        button("Plan a route",()->openPlanner(0));
        section("Ready for the road",AdventureStyle.GREEN);checklistProgress(Module.CHECKS,"Pre-trip inspection");checklistProgress(Module.ARRIVAL,"Arrival / setup");
        section("Your logbook",AdventureStyle.accent(Module.PLANS));
        moduleGrid(new Module[]{Module.PLANS,Module.MAINTENANCE,Module.FUEL,Module.EXPENSES,Module.CAMPSITES,Module.PHOTOS,Module.MILEAGE,Module.PLACES,Module.ROUTES});
        section("Your data",AdventureStyle.accent(Module.PLACES));button("My RV dimensions",this::openVehicle);button("Backup & restore",this::backups);TextView footer=text("Saved on this device · Miles · Litres · GBP",12);footer.setTextColor(AdventureStyle.MUTED);footer.setGravity(android.view.Gravity.CENTER);
    }
    private void checksHub(){selected=null;draft=null;screen("Checklists");TextView note=text("One step closer to your next adventure.",14);note.setTextColor(AdventureStyle.MUTED);checklistProgress(Module.CHECKS,"Pre-trip inspection");checklistProgress(Module.ARRIVAL,"Arrival / setup");}
    private void recordsHub(){selected=null;draft=null;screen("Records");TextView note=text("Your trips, running costs and vehicle history.",14);note.setTextColor(AdventureStyle.MUTED);section("Journeys & places",AdventureStyle.accent(Module.TRIPS));moduleGrid(new Module[]{Module.TRIPS,Module.PLANS,Module.CAMPSITES,Module.PLACES,Module.ROUTES,Module.PHOTOS});section("Vehicle care",AdventureStyle.accent(Module.MAINTENANCE));button("Service schedule",()->startActivity(new Intent(this,ServiceScheduleActivity.class)));moduleGrid(new Module[]{Module.MAINTENANCE,Module.REMINDERS,Module.MILEAGE});section("Running costs",AdventureStyle.accent(Module.EXPENSES));moduleGrid(new Module[]{Module.FUEL,Module.EXPENSES});section("Settings & data",AdventureStyle.accent(Module.PLANS));button("Personalise my logbook",this::openPersonalisation);button("My RV dimensions",this::openVehicle);button("Backup & restore",this::backups);text("Version 1.13 · "+Personalisation.name(this)+" · RV Logbook",12);}
    private void moduleGrid(Module[] areas){int columns=getResources().getConfiguration().fontScale>1.2f||getResources().getConfiguration().screenWidthDp<360?1:2;for(int i=0;i<areas.length;i+=columns){LinearLayout row=new LinearLayout(this);row.setBaselineAligned(false);content.addView(row);tile(row,areas[i],true);if(columns==2&&i+1<areas.length)tile(row,areas[i+1],false);}}
    private void checklistProgress(Module module,String label){
        int total=store.list(module).size();long done=store.list(module).stream().filter(e->e.data.optBoolean("done")).count();
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(5),dp(14),dp(14));card.setBackground(AdventureStyle.pressable(this,AdventureStyle.tint(module),AdventureStyle.BORDER));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));content.addView(card,lp);
        Button b=makeButton(module.title+"\n"+done+" of "+total+" completed",()->list(module));b.setTextColor(AdventureStyle.accent(module));b.setTextSize(15);b.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);b.setPadding(0,dp(6),0,dp(10));b.setBackgroundColor(android.graphics.Color.TRANSPARENT);card.addView(b,new LinearLayout.LayoutParams(-1,-2));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(total,1));bar.setProgress((int)done);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(AdventureStyle.accent(module)));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(AdventureStyle.SOFT));card.addView(bar,new LinearLayout.LayoutParams(-1,dp(5)));card.setOnClickListener(v->list(module));
    }
    private void renderChecklist(Module module){
        if(store.list(module).isEmpty())text("No checks yet. Add a record above.",16);
        for(LogStore.Entry entry:store.list(module)){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(9),dp(5),dp(9),dp(5));card.setBackground(AdventureStyle.panel(this,AdventureStyle.tint(module),AdventureStyle.BORDER));
            LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);cardLp.setMargins(0,dp(4),0,dp(4));content.addView(card,cardLp);
            LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);card.addView(row);
            CheckBox check=new CheckBox(this);check.setText(entry.data.optString("Item"));check.setTextSize(16);check.setTextColor(AdventureStyle.TEXT);check.setButtonTintList(android.content.res.ColorStateList.valueOf(AdventureStyle.accent(module)));check.setMinHeight(dp(52));check.setChecked(entry.data.optBoolean("done"));check.setTextColor(entry.data.optBoolean("done")?AdventureStyle.MUTED:AdventureStyle.TEXT);row.addView(check,new LinearLayout.LayoutParams(0,-2,1));
            check.setOnCheckedChangeListener((v,done)->{put(entry.data,"done",done);store.save(module,entry.id,entry.data);check.setTextColor(done?AdventureStyle.MUTED:AdventureStyle.TEXT);});
            Button edit=makeButton("Edit",()->editor(entry.data,entry.id));edit.setContentDescription("Edit "+entry.data.optString("Item"));row.addView(edit,new LinearLayout.LayoutParams(dp(64),-2));
            if(!entry.data.optString("Notes").isEmpty()){
                TextView notes=new TextView(this);notes.setText(entry.data.optString("Notes"));notes.setTextSize(14);notes.setTextColor(AdventureStyle.TEXT);notes.setPadding(dp(8),0,0,dp(5));card.addView(notes);
            }
        }
    }
    private void stat(LinearLayout row,String value,String label){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackground(AdventureStyle.vibrant(this,row.getChildCount()==0?Module.MILEAGE:Module.EXPENSES));
        TextView l=new TextView(this);l.setText(label);l.setTextSize(12);l.setTextColor(AdventureStyle.CARD);card.addView(l);
        TextView n=new TextView(this);n.setText(value);n.setTextSize(24);n.setTypeface(AdventureStyle.DISPLAY);n.setTextColor(AdventureStyle.CARD);n.setPadding(0,dp(5),0,0);card.addView(n);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(row.getChildCount()==0?0:dp(5),0,row.getChildCount()==0?dp(5):0,0);row.addView(card,lp);
    }
    private void styleTile(Button b,Module module){
        b.setTextColor(AdventureStyle.CARD);b.setBackground(AdventureStyle.vibrant(this,module));b.setTextSize(14);b.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);b.setMinHeight(dp(96));b.setPadding(dp(12),dp(12),dp(10),dp(12));
        android.graphics.drawable.Drawable icon=new LogbookIcon(module,AdventureStyle.CARD);icon.setBounds(0,0,dp(23),dp(23));b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(dp(10));
        String value=b.getText().toString();int line=value.indexOf('\n');if(line>=0){android.text.SpannableString caption=new android.text.SpannableString(value);caption.setSpan(new android.text.style.RelativeSizeSpan(.85f),line+1,value.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);caption.setSpan(new android.text.style.ForegroundColorSpan(AdventureStyle.CARD),line+1,value.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);b.setText(caption);}
    }
    private void tile(LinearLayout row,Module module,boolean left){Button b=makeButton(module.title+"\n"+store.list(module).size()+" entries",()->list(module));styleTile(b,module);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(left?0:dp(5),dp(5),left?dp(5):0,dp(5));row.addView(b,lp);}
    private void emptyState(Module module){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(android.view.Gravity.CENTER);card.setPadding(dp(24),dp(28),dp(24),dp(28));card.setBackground(AdventureStyle.panel(this,AdventureStyle.CARD,AdventureStyle.BORDER));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(16),0,0);content.addView(card,lp);
        ImageView image=new ImageView(this);image.setImageDrawable(new LogbookIcon(module));image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(image,new LinearLayout.LayoutParams(dp(40),dp(40)));
        TextView title=new TextView(this);title.setText(R.string.no_entries);title.setTextSize(19);title.setTypeface(AdventureStyle.DISPLAY);title.setTextColor(AdventureStyle.INK);title.setPadding(0,dp(14),0,dp(6));card.addView(title);
        TextView detail=new TextView(this);detail.setText(getString(R.string.empty_detail,singular(module)));detail.setTextSize(14);detail.setTextColor(AdventureStyle.MUTED);detail.setGravity(android.view.Gravity.CENTER);card.addView(detail);
    }
    private void list(Module module) {
        selected = module; draft = null; screen(module.title); JSONObject rv=VehicleProfile.current(store);java.util.List<LogStore.Entry> rows=new java.util.ArrayList<>(store.list(module));if(!scopedTrip.isEmpty())rows.removeIf(e->!scopedTrip.equals(e.data.optString("Trip ID")));if(module==Module.CAMPSITES&&campsiteFilter!=0)rows.removeIf(e->{int fit=VehicleProfile.fit(e.data,rv).status;return campsiteFilter==1?fit!=1:campsiteFilter==2?fit!=2:fit!=0;});TextView count=text(rows.size()+" "+(module.isChecklist()?"checks":"entries"),14);count.setTextColor(AdventureStyle.MUTED);button(module==Module.PLANS?"Plan a route":"Add record", () -> {if(module==Module.PLANS)openPlanner(0);else editor(new JSONObject(),0);});
        if(!scopedTrip.isEmpty())button("Back to trip",this::finish);
        if(module==Module.ROUTES)button("Plan a route",()->openPlanner(0));
        if(module==Module.CAMPSITES){button("My RV dimensions",this::openVehicle);text(VehicleProfile.summary(rv),14);button("Import Google Maps list",this::campsiteImportHelp);button("View campsites on map",()->startActivityForResult(new Intent(this,TravelMapActivity.class),40));Spinner filter=new Spinner(this);filter.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"All campsites","Within recorded RV limits","Exceeds recorded RV limits","Needs confirmation"}));filter.setSelection(campsiteFilter);content.addView(filter);filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(position!=campsiteFilter){campsiteFilter=position;list(Module.CAMPSITES);}}});text("Fit uses recorded site limits only. Missing limits need confirmation. Pitch availability is recorded manually; contact the campsite for current spaces.",14);}
        if(module == Module.PLACES || module == Module.ROUTES){
            button("View travel map",()->startActivityForResult(new Intent(this,TravelMapActivity.class),40));
            if(module==Module.ROUTES)button("Import GPX track",this::chooseGpx);
        }
        if(module.isChecklist()){
            text("From your RV poster. Add vehicle-specific checks and follow your RV manual.",15);
            button(module==Module.CHECKS?"Reset all checks for next trip":"Reset all checks for next arrival",()->new AlertDialog.Builder(this).setMessage("Mark every item in this checklist as incomplete?").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{store.resetChecklist(module);list(module);}).show());
            renderChecklist(module);return;
        }
        if (module == Module.REMINDERS) {
            button("Service schedule",()->startActivity(new Intent(this,ServiceScheduleActivity.class)));
            text("Due when either date or mileage is reached. Mileage uses your highest logged reading. Android checks approximately daily; battery settings may delay alerts.",16);
            button("Notification settings",this::notificationSettings);
        }
        if(rows.isEmpty()){if(module==Module.CAMPSITES&&!store.list(module).isEmpty())text("No campsites match this fit filter. Choose All campsites or add site limits.",16);else emptyState(module);}
        for (LogStore.Entry e : rows) {
            LinearLayout parent = content;
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(12),dp(16),dp(12));
            card.setBackground(AdventureStyle.panel(this,AdventureStyle.tint(module),AdventureStyle.BORDER));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1,-2); cardLp.setMargins(0,dp(12),0,dp(4)); parent.addView(card,cardLp); content=card;
            LinearLayout recordContent=content;
            TextView recordTitle=new TextView(this);recordTitle.setText(e.data.optString(module.fields[0],module.title));recordTitle.setTypeface(AdventureStyle.DISPLAY);recordTitle.setTextSize(18);recordTitle.setTextColor(AdventureStyle.accent(module));recordTitle.setPadding(0,dp(2),0,dp(8));recordContent.addView(recordTitle);
            if(module==Module.CAMPSITES){VehicleProfile.Fit fit=VehicleProfile.fit(e.data,rv);TextView fitView=text(fit.message,15);fitView.setTextColor(fit.status==2?AdventureStyle.DANGER:fit.status==1?AdventureStyle.GREEN:AdventureStyle.MUTED);text(VehicleProfile.availability(e.data),14);}
            if(module==Module.PLANS)text(RvRouting.description(e.data),14);
            StringBuilder label = new StringBuilder();
            if (module == Module.REMINDERS && !e.data.optBoolean("done") && Rules.due(e.data.optString("Due date"),e.data.optString("Due miles"),store.latestMileage(),LocalDate.now())) label.append("DUE • ");
            for (String field : module.fields) if (!(module==Module.CAMPSITES && field.equals("Notes")) && !Module.hidden(field) && !field.equals(module.fields[0]) && !field.equals("Route points") && !field.equals("Route stops") && !field.equals("RV profile") && !e.data.optString(field).isEmpty()) label.append(field).append(": ").append(e.data.optString(field)).append("\n");
            if(module==Module.ROUTES)try{org.json.JSONArray segments=GeoData.parse(e.data.optString("Route points"));label.append(GeoData.pointCount(segments)).append(" points · ").append(String.format(Locale.UK,"%.1f",GeoData.miles(segments))).append(" path miles\n");}catch(Exception ignored){}
            if (module == Module.REMINDERS) {
                CheckBox check = new CheckBox(this); check.setText(R.string.service_completed); check.setTextColor(AdventureStyle.TEXT);check.setButtonTintList(android.content.res.ColorStateList.valueOf(AdventureStyle.accent(module))); check.setMinHeight(dp(48)); check.setChecked(e.data.optBoolean("done"));
                if (module == Module.REMINDERS && e.data.optBoolean("done")) check.setEnabled(false);
                content.addView(check);
                check.setOnCheckedChangeListener((v,done)->{
                    if (module == Module.REMINDERS && done) {
                        check.setOnCheckedChangeListener(null); check.setChecked(false);
                        new AlertDialog.Builder(this).setTitle("Complete service?").setMessage("Mark this reminder completed? A repeat will use the actual service date/mileage when recording work, or today and your current logged mileage when completing without a work record. Choose Record work to save a linked maintenance entry and complete this reminder together.")
                            .setNegativeButton("Cancel",(d,w)->list(module)).setNeutralButton("Record work",(d,w)->recordService(e)).setPositiveButton("Complete",(d,w)->completeReminder(e)).setOnCancelListener(d->list(module)).show();
                    } else { put(e.data,"done",done); store.save(module,e.id,e.data); ServiceNotifications.refresh(this); list(module); }
                });
            }
            if(label.length()>0){TextView detail=text(label.toString().trim(),14);detail.setTextColor(AdventureStyle.MUTED);}
            if (module == Module.PHOTOS && !e.data.optString("uri").isEmpty()) button("Open photo", () -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(e.data.optString("uri")),"image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
                catch (RuntimeException ex) { toast("Photo unavailable. Edit this record to choose it again."); }
            });
            if(module==Module.CAMPSITES){text(CampsiteLocations.position(e.data)==null?"Map location not set · open the map to locate campsites":e.data.optString("Location source","Saved map location"),14);String maps=CampsiteImport.link(e.data);if(maps!=null)button("Open in Google Maps",()->openWeb(maps));}
            if(module==Module.TRIPS){String key=TripWorkflow.identify(e.data,"Trip ID");store.save(module,e.id,e.data);button("Open trip dashboard",()->startActivity(new Intent(this,TripDashboardActivity.class).putExtra("trip",key)));}
            if(module==Module.MAINTENANCE&&!e.data.optString("Source reminder ID").isEmpty())text("Linked to completed service reminder",14);
            if(module==Module.REMINDERS&&e.data.optBoolean("done")){for(LogStore.Entry work:store.list(Module.MAINTENANCE))if(!e.data.optString("Reminder ID").isEmpty()&&e.data.optString("Reminder ID").equals(work.data.optString("Source reminder ID")))text("Maintenance recorded: "+work.data.optString("Work")+" · "+work.data.optString("Date"),14);}
            button("Edit record", () -> {if(module==Module.PLANS)openPlanner(e.id);else editor(e.data,e.id);});
            content = parent;
        }
    }
    private void editor(JSONObject data, long id) {
        try { draft = new JSONObject(data.toString()); } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        if(id==0&&!scopedTrip.isEmpty()&&!draft.has("Trip ID"))put(draft,"Trip ID",scopedTrip);
        editId = id; inputs.clear(); screen((id == 0 ? "Add: " : "Edit: ") + selected.title);
        if(selected==Module.PLACES){
            text("Choose a place on the travel map, or enter decimal latitude and longitude below.",16);
            button("Choose place on map",this::openMapFromEditor);
        }
        if(selected==Module.ROUTES){
            text(draft.optString("Route type").isEmpty()?"Use Import GPX or Draw route on the travel map to attach a path.":draft.optString("Route type")+" · path attached",16);
            button("Open travel map",this::openMapFromEditor);
            button("Import GPX file",()->confirmGpxReplace());
        }
        if(selected==Module.REMINDERS){
            text("Set the last service date/mileage and repeat intervals, or enter the first due date/mileage directly. Date or mileage, whichever comes first. Use your manufacturer’s intervals; leave unknown history blank.",14);
            button("Choose service name",()->new AlertDialog.Builder(this).setTitle("Service name only — set your own intervals").setItems(ServiceSchedule.SUGGESTIONS,(d,i)->inputs.get("Service").setText(ServiceSchedule.SUGGESTIONS[i])).setNegativeButton("Cancel",null).show());
        }
        if(selected==Module.CAMPSITES){
            text("Campsites with coordinates appear on the map. Use Locate campsites on the map to search imported names, or choose the entrance yourself. Saving a location does not record a visit.",14);
            button("Choose campsite location on map",()->{collect();Intent intent=new Intent(this,TravelMapActivity.class).putExtra("pick_stop",true).putExtra("campsite_location",true);double[] point=CampsiteLocations.position(draft);if(point!=null)intent.putExtra("initial_lat",point[0]).putExtra("initial_lon",point[1]);startActivityForResult(intent,45);});
        }
        if(selected==Module.CAMPSITES)text("Enter the campsite’s maximum accepted RV dimensions and weight from its operator or listing. Leave unknown limits blank. Confirm entrance, turning space and pitch/awning room separately. Availability is a manual report, not a live booking check.",14);
        for (String field : selected.fields) {
            if(Module.hidden(field)||field.equals("Status")||field.equals("Route points")||field.equals("Route type"))continue;
            TextView fieldLabel=text(field + (selected.required(field) ? " *" : ""),13);fieldLabel.setTypeface(AdventureStyle.DISPLAY);
            EditText input = new EditText(this);input.setId(View.generateViewId());fieldLabel.setLabelFor(input.getId()); input.setSingleLine(!field.equals("Notes"));
            if (Rules.isNumber(field)) input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            if(field.equals("Latitude")||field.equals("Longitude"))input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
            if (field.toLowerCase(java.util.Locale.ROOT).contains("date")) input.setHint("YYYY-MM-DD");
            input.setTextColor(AdventureStyle.TEXT);input.setHintTextColor(0xff756b59);input.setMinHeight(dp(52));
            input.setBackground(AdventureStyle.input(this));input.setPadding(dp(14),dp(12),dp(14),dp(12));input.setTypeface(AdventureStyle.BODY);input.setTextSize(16);
            input.setText(draft.optString(field));
            if(java.util.Arrays.asList(VehicleProfile.LIMITS).contains(field))input.setHint("Leave blank if unknown");
            if (id == 0 && field.equals("Date") && draft.optString(field).isEmpty() && !(selected==Module.ROUTES && draft.optString("Route type").startsWith("GPX"))) input.setText(LocalDate.now().toString());
            if (field.equals("Notes")) { input.setMinLines(3); input.setGravity(android.view.Gravity.TOP); }
            if(field.toLowerCase(Locale.ROOT).contains("date")){
                LinearLayout dateRow=new LinearLayout(this);dateRow.setGravity(android.view.Gravity.CENTER_VERTICAL);content.addView(dateRow);
                dateRow.addView(input,new LinearLayout.LayoutParams(0,-2,1));Button pick=makeButton("Pick date",()->pickDate(input));pick.setTextSize(13);LinearLayout.LayoutParams pickLp=new LinearLayout.LayoutParams(dp(86),-2);pickLp.setMargins(dp(8),0,0,0);dateRow.addView(pick,pickLp);
            }else content.addView(input);
            inputs.put(field,input);
            if(selected==Module.REMINDERS&&field.equals("Category"))button("Choose service category",()->new AlertDialog.Builder(this).setItems(ServiceSchedule.CATEGORIES,(d,i)->input.setText(ServiceSchedule.CATEGORIES[i])).setNegativeButton("Cancel",null).show());
            if(field.equals("Availability"))button("Choose availability status",()->new AlertDialog.Builder(this).setTitle("Recorded pitch availability").setItems(VehicleProfile.AVAILABILITY,(d,index)->input.setText(VehicleProfile.AVAILABILITY[index])).setNegativeButton("Cancel",null).show());


        }
        if(selected==Module.REMINDERS)button("Calculate next due from last service",()->{
            collect();try{for(String field:new String[]{"Last service date","Last service miles","Repeat months","Repeat miles"}){String error=Rules.validate(field,draft.optString(field));if(error!=null)throw new IllegalArgumentException(field+": "+error);}ServiceSchedule.calculate(draft,true);for(String field:new String[]{"Due date","Due miles"})inputs.get(field).setText(draft.optString(field));toast("Due values calculated where a last service reading and interval are provided.");}catch(RuntimeException e){toast(e.getMessage());}
        });
        if(java.util.Arrays.asList(selected.fields).contains("Trip ID")&&selected!=Module.TRIPS&&completingReminder==0){
            String name="Unlinked";for(LogStore.Entry trip:store.list(Module.TRIPS))if(draft.optString("Trip ID").equals(trip.data.optString("Trip ID")))name=trip.data.optString("Name");text("Trip: "+name,14);
            button("Choose trip",()->{collect();java.util.List<LogStore.Entry> trips=store.list(Module.TRIPS);String[] names=new String[trips.size()+1];names[0]="Unlinked";for(int i=0;i<trips.size();i++)names[i+1]=trips.get(i).data.optString("Name")+" · "+trips.get(i).data.optString("Date");new AlertDialog.Builder(this).setItems(names,(d,i)->{String key="";if(i>0){LogStore.Entry trip=trips.get(i-1);key=TripWorkflow.identify(trip.data,"Trip ID");store.save(Module.TRIPS,trip.id,trip.data);}put(draft,"Trip ID",key);editor(draft,editId);}).setNegativeButton("Cancel",null).show();});
        }
        if (selected == Module.PHOTOS) {
            photoLabel = text(draft.optString("uri").isEmpty() ? "Choose a photo (reattach after restoring a backup)" : "Photo attached",16);
            button("Choose photo", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),10));
        }
        LinearLayout actions=new LinearLayout(this);actions.setPadding(dp(20),dp(10),dp(20),dp(10));actions.setBackgroundColor(AdventureStyle.CARD);appRoot.addView(actions);
        Button cancel=makeButton("Cancel",this::discardEditor),save=makeButton("Save",this::save);LinearLayout.LayoutParams cancelLp=new LinearLayout.LayoutParams(0,-2,1);cancelLp.setMargins(0,0,dp(10),0);actions.addView(cancel,cancelLp);actions.addView(save,new LinearLayout.LayoutParams(0,-2,2));
        if (id != 0) button("Delete record", () -> new AlertDialog.Builder(this).setMessage("Permanently delete this record?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{ store.delete(id); ServiceNotifications.refresh(this); list(selected); }).show());
    }
    private void pickDate(EditText input){
        LocalDate date;try{date=LocalDate.parse(input.getText().toString());}catch(RuntimeException e){date=LocalDate.now();}
        new DatePickerDialog(this,(picker,y,m,d)->input.setText(LocalDate.of(y,m+1,d).toString()),date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show();
    }
    private void collect() { for (Map.Entry<String,EditText> e : inputs.entrySet()) put(draft,e.getKey(),e.getValue().getText().toString().trim()); }
    private void save() {
        String oldLat=draft.optString("Latitude"),oldLon=draft.optString("Longitude");collect();
        if(selected==Module.CAMPSITES&&(!oldLat.equals(draft.optString("Latitude"))||!oldLon.equals(draft.optString("Longitude"))))put(draft,"Location source",draft.optString("Latitude").isEmpty()?"":"Manually entered coordinates"); boolean invalid = false;
        for (Map.Entry<String,EditText> e : inputs.entrySet()) {
            String value = draft.optString(e.getKey()); String error = Rules.validate(e.getKey(),value);
            if (selected.required(e.getKey()) && value.isEmpty()) error = "Required";
            if (error != null) { e.getValue().setError(error); invalid = true; }
        }
        if (invalid) return;
        if(selected==Module.REMINDERS){try{ServiceSchedule.calculate(draft,false);for(String field:new String[]{"Due date","Due miles"})inputs.get(field).setText(draft.optString(field));}catch(RuntimeException e){toast("Check the last service date, mileage and intervals");return;}}
        Map<String,String> values = new LinkedHashMap<>();
        for (String field : selected.fields) values.put(field,draft.optString(field));
        String error = Rules.validateRecord(selected,values);
        if (error != null) { toast(error); return; }
        if (selected == Module.PHOTOS && draft.optString("uri").isEmpty()) { toast("Choose a photo first"); return; }
        try { if(completingReminder!=0){TripWorkflow.complete(store,completingReminder,draft);completingReminder=0;selected=Module.REMINDERS;}else store.save(selected,editId,draft); ServiceNotifications.refresh(this); if(serviceOrigin())finish();else list(selected); }
        catch (RuntimeException e) { toast(e instanceof IllegalArgumentException?e.getMessage():"Could not save. Your form is still open; please retry."); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if(request==46){if(currentScreen.equals("Records"))recordsHub();else home();return;}
        if(request==40&&result!=RESULT_OK&&selected==Module.CAMPSITES&&draft==null){list(Module.CAMPSITES);return;}
        if(request==45&&result==RESULT_OK&&data!=null&&selected==Module.CAMPSITES&&draft!=null){
            try{double lat=Double.parseDouble(data.getStringExtra("Latitude")),lon=Double.parseDouble(data.getStringExtra("Longitude"));if(!GeoData.valid(lat,lon))throw new IllegalArgumentException();collect();CampsiteLocations.set(draft,lat,lon,"Chosen on map — check entrance");inputs.get("Latitude").setText(draft.optString("Latitude"));inputs.get("Longitude").setText(draft.optString("Longitude"));}
            catch(Exception e){toast("Choose the campsite location again");}return;
        }
        if(request==40&&result==RESULT_OK&&data!=null){
            String action=data.getStringExtra("action");
            if("import".equals(action)){chooseGpx();return;}
            if("edit".equals(action)){
                selected=Module.valueOf(data.getStringExtra("module"));long id=data.getLongExtra("id",0);
                if(selected==Module.PLANS){openPlanner(id);return;}
                for(LogStore.Entry e:store.list(selected))if(e.id==id){editor(e.data,id);return;}
                list(selected);return;
            }
            JSONObject record=new JSONObject();
            if("pin".equals(action)){selected=Module.PLACES;put(record,"Name",data.getStringExtra("Name"));put(record,"Latitude",data.getStringExtra("Latitude"));put(record,"Longitude",data.getStringExtra("Longitude"));}
            else if("route".equals(action)){selected=Module.ROUTES;put(record,"Route type","Manual waypoints");put(record,"Route points",data.getStringExtra("Route points"));}
            else return;
            editor(record,0);return;
        }
        if(request==44&&result==RESULT_OK){if(selected==Module.CAMPSITES)list(Module.CAMPSITES);else home();return;}
        if(request==43&&result==RESULT_OK){list(Module.PLANS);return;}
        if(request==42&&result==RESULT_OK&&data!=null&&data.getData()!=null){importCampsites(data.getData());return;}
        if(request==41&&result==RESULT_OK&&data!=null&&data.getData()!=null){importGpx(data.getData());return;}
        if ((request == 20 || request == 21) && result == RESULT_OK && data != null && data.getData() != null) {
            handleBackupResult(request,data.getData()); return;
        }
        if (request == 10 && result == RESULT_OK && data != null && data.getData() != null && draft != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); put(draft,"uri",uri.toString()); photoLabel.setText(R.string.photo_attached); }
            catch (SecurityException e) { toast("Cannot retain access to this photo. Choose another provider."); }
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);state.putLong("completingReminder",completingReminder);state.putInt("campsiteFilter",campsiteFilter);state.putString("section",currentScreen);
        if (selected != null) state.putString("module",selected.name());
        if (draft != null) { collect(); state.putString("draft",draft.toString()); state.putLong("editId",editId); }
    }
    @Override public void onBackPressed() {
        if (draft != null) discardEditor();
        else if(!scopedTrip.isEmpty())finish();
        else if (selected != null || !currentScreen.equals("Overview")) home(); else super.onBackPressed();
    }
    private boolean serviceOrigin(){return getIntent().hasExtra("new_service")||getIntent().hasExtra("record_service")||getIntent().hasExtra("edit_service");}
    private void discardEditor() {
        new AlertDialog.Builder(this).setMessage("Discard unsaved changes?").setNegativeButton("Keep editing",null).setPositiveButton("Discard",(d,w)->{if(completingReminder!=0){completingReminder=0;selected=Module.REMINDERS;}if(serviceOrigin())finish();else list(selected);}).show();
    }
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density); }
    private void completeReminder(LogStore.Entry entry) {
        try{TripWorkflow.complete(store,entry.id,null);}catch(RuntimeException e){toast(e.getMessage());}
        ServiceNotifications.refresh(this); list(Module.REMINDERS);
    }
    private void recordService(LogStore.Entry entry){
        completingReminder=entry.id;selected=Module.MAINTENANCE;JSONObject data=new JSONObject();put(data,"Work",entry.data.optString("Service"));put(data,"Date",LocalDate.now().toString());put(data,"Odometer miles",String.valueOf(store.latestMileage()));put(data,"Trip ID",entry.data.optString("Trip ID"));editor(data,0);
    }

    private void notificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},30);
        } else startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if (code == 30) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) { ServiceNotifications.refresh(this); toast("Service notifications enabled"); }
            else new AlertDialog.Builder(this).setMessage("Notifications are off. You can enable them in Android settings. Due reminders remain visible in the app.")
                .setNegativeButton("Close",null).setPositiveButton("Open settings",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()))).show();
        }
    }
    private void backups() {
        selected=null; draft=null; screen("Backup & restore");
        text("Export all logbook records to a file you choose. Keep a copy outside this phone. The file is readable JSON; choose a private location.",16);
        text("Photo captions and notes are included. Original photo files are not included: save those separately and reattach them after restoring. Notification settings are managed by Android.",16);
        button("Export backup",()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Poultons-RV-"+LocalDate.now()+".json"),20));
        button("Restore backup",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),21));
        text("Restore replaces the current logbook after validation and confirmation. Export your current data first. Maximum backup size: 10 MB / 10,000 records.",14);
    }
    private void handleBackupResult(int request,Uri uri) {
        try {
            if (request == 20) {
                byte[] bytes = Backup.exportRecords(store).toString(2).getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = getContentResolver().openOutputStream(uri,"wt")) {
                    if (out == null) throw new java.io.IOException("No writable file"); out.write(bytes);
                }
                toast("Backup saved. Keep original photo files separately.");
            } else {
                JSONObject backup;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new java.io.IOException("No readable file"); backup=Backup.read(in);
                }
                int count=Backup.validate(backup);
                new AlertDialog.Builder(this).setTitle("Replace your logbook?")
                    .setMessage("Restore " + count + " records? Current records will be replaced. Photos will need reattaching. Export your current logbook before proceeding.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Replace & restore",(d,w)->{
                        try { Backup.restore(store,backup); ServiceNotifications.refresh(this); home(); toast("Logbook restored"); }
                        catch (Exception e) { toast("Restore failed. Existing records were kept."); }
                    }).show();
            }
        } catch (Exception e) { new AlertDialog.Builder(this).setTitle("Backup could not be processed").setMessage(e.getMessage()==null?"Choose another file and try again.":e.getMessage()).setPositiveButton("Close",null).show(); }
    }
    private void openPersonalisation(){startActivityForResult(new Intent(this,PersonalisationActivity.class),46);}
    private void openVehicle(){startActivityForResult(new Intent(this,VehicleProfileActivity.class),44);}
    private void openPlanner(long id){startActivityForResult(new Intent(this,RoutePlannerActivity.class).putExtra("id",id).putExtra("trip",scopedTrip),43);}
    private void campsiteImportHelp(){
        new AlertDialog.Builder(this).setTitle("Import Google Maps campsites")
            .setMessage("1. Open Google Takeout and deselect all, then select Saved. Create a one-off ZIP export.\n\n2. Download the export to your phone. Choose its ZIP here, or an extracted campsite list CSV. If the ZIP has several lists, choose one.\n\n3. Review the campsites and confirm. Names, notes and Google Maps links are kept; matching links are skipped. Other saved websites are skipped. Existing records stay unchanged.\n\nThis is a file import, not live sync or a shared-list link importer. No visit dates are created. After importing, open View campsites on map and tap Locate campsites to search missing locations. Search results need checking. Maximum 2,000 rows / 10 MB file / 20 MB expanded ZIP.")
            .setNegativeButton("Cancel",null).setNeutralButton("Google Takeout",(d,w)->openWeb("https://takeout.google.com/"))
            .setPositiveButton("Choose file",(d,w)->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),42)).show();
    }
    private void openWeb(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(RuntimeException e){toast("No app available to open this link.");}
    }
    private void importCampsites(Uri uri){
        android.content.Context context=getApplicationContext();
        AlertDialog loading=new AlertDialog.Builder(this).setTitle("Reading campsite lists…").setView(new ProgressBar(this)).setCancelable(false).show();
        imports.execute(()->{
            try(InputStream in=context.getContentResolver().openInputStream(uri)){
                if(in==null)throw new java.io.IOException("File unavailable. Choose it again.");
                String name="Saved places";
                try(android.database.Cursor c=context.getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
                    if(c!=null&&c.moveToFirst()&&!c.isNull(0))name=c.getString(0);
                }catch(RuntimeException ignored){/* Display names are optional; the stream is authoritative. */}
                java.util.List<CampsiteImport.SavedList> lists=CampsiteImport.read(in,name);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;loading.dismiss();
                    if(selected!=Module.CAMPSITES||draft!=null)return;
                    if(lists.size()==1)previewCampsiteList(lists.get(0));
                    else{
                        String[] names=new String[lists.size()];for(int i=0;i<lists.size();i++){CampsiteImport.SavedList list=lists.get(i);names[i]=list.name+" · "+list.campsites.size()+" Maps places";}
                        new AlertDialog.Builder(this).setTitle("Choose your campsite list").setItems(names,(d,index)->previewCampsiteList(lists.get(index))).setNegativeButton("Cancel",null).show();
                    }
                });
            }catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){loading.dismiss();campsiteImportError(e);}});}
        });
    }
    void previewCampsiteList(CampsiteImport.SavedList saved){
        CampsiteImport.Preview preview=CampsiteImport.preview(saved.campsites,store);
        String summary=saved.name+"\n"+preview.fresh.size()+" new · "+preview.duplicates+" duplicate links · "+saved.skipped+" unsupported or incomplete rows skipped";
        if(preview.fresh.isEmpty()){
            new AlertDialog.Builder(this).setTitle("No new campsites").setMessage(summary+"\n\nNo changes made. Choose a list containing Google Maps places, or check your existing campsites.").setPositiveButton("Close",null).show();return;
        }
        String[] names=new String[preview.fresh.size()];boolean[] checked=new boolean[names.length];java.util.Arrays.fill(checked,true);
        for(int i=0;i<names.length;i++)names[i]=preview.fresh.get(i).optString("Name");
        LinearLayout title=new LinearLayout(this);title.setOrientation(LinearLayout.VERTICAL);title.setPadding(dp(24),dp(20),dp(24),dp(10));
        TextView heading=new TextView(this);heading.setText(R.string.review_campsites);heading.setTextSize(20);heading.setTextColor(AdventureStyle.INK);heading.setTypeface(AdventureStyle.DISPLAY);title.addView(heading);
        TextView detail=new TextView(this);detail.setText(getString(R.string.campsite_review_detail,summary));detail.setTextSize(14);detail.setTextColor(AdventureStyle.MUTED);detail.setPadding(0,dp(8),0,0);title.addView(detail);
        AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(title).setMultiChoiceItems(names,checked,(d,index,value)->{
            checked[index]=value;int count=0;for(boolean item:checked)if(item)count++;
            ((AlertDialog)d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(count>0);
            ((AlertDialog)d).getButton(AlertDialog.BUTTON_POSITIVE).setText(getString(R.string.import_count,count));
        }).setNegativeButton("Cancel",null).setPositiveButton(getString(R.string.import_count,names.length),(d,w)->{
            java.util.List<JSONObject> chosen=new java.util.ArrayList<>();for(int i=0;i<checked.length;i++)if(checked[i])chosen.add(preview.fresh.get(i));
            saveCampsiteImport(chosen);
        }).create();dialog.show();
    }
    private void saveCampsiteImport(java.util.List<JSONObject> chosen){
        android.content.Context context=getApplicationContext();
        AlertDialog loading=new AlertDialog.Builder(this).setTitle("Importing campsites…").setView(new ProgressBar(this)).setCancelable(false).show();
        imports.execute(()->{
            try(LogStore target=new LogStore(context)){
                int added=CampsiteImport.save(target,chosen);
                runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){loading.dismiss();list(Module.CAMPSITES);toast(added+" campsites imported · "+(chosen.size()-added)+" duplicate links skipped");}});
            }catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){loading.dismiss();campsiteImportError(e);}});}
        });
    }
    private void campsiteImportError(Exception e){
        new AlertDialog.Builder(this).setTitle("Campsite import not completed").setMessage((e.getMessage()==null?"Choose a Google Takeout Saved CSV or ZIP and try again.":e.getMessage())+"\n\nNo campsites were added. Existing records were kept.").setPositiveButton("Close",null).show();
    }
    private void openMapFromEditor(){
        new AlertDialog.Builder(this).setMessage("Discard this draft and open the travel map?").setNegativeButton("Keep editing",null).setPositiveButton("Open map",(d,w)->{
            draft=null;list(selected);startActivityForResult(new Intent(this,TravelMapActivity.class),40);
        }).show();
    }
    private void confirmGpxReplace(){
        new AlertDialog.Builder(this).setMessage("Import a GPX file as a new route? This replaces the unsaved draft; saved routes are kept.").setNegativeButton("Keep editing",null).setPositiveButton("Import",(d,w)->chooseGpx()).show();
    }
    private void chooseGpx(){
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),41);
    }
    private void importGpx(Uri uri){
        toast("Reading GPX file…");
        android.content.ContentResolver resolver=getApplicationContext().getContentResolver();
        imports.execute(()->{
            try(InputStream in=resolver.openInputStream(uri)){
                if(in==null)throw new java.io.IOException("File unavailable");JSONObject route=GpxImport.read(in);
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;selected=Module.ROUTES;editor(route,0);toast("Path imported. Name the journey, link its trip and save.");});
            }catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())toast("Could not import GPX: "+(e.getMessage()==null?"invalid file":e.getMessage()));});}
        });
    }
    @Override protected void onDestroy() { imports.shutdownNow();store.close(); super.onDestroy(); }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    private static void put(JSONObject o, String key, Object value) { try { o.put(key,value); } catch (org.json.JSONException e) { throw new IllegalArgumentException(e); } }
}
