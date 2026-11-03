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
    private Module selected;
    private JSONObject draft;
    private long editId;
    private final Map<String, EditText> inputs = new LinkedHashMap<>();
    private TextView photoLabel;
    private final java.util.concurrent.ExecutorService imports=java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new LogStore(this);
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
        else if (getIntent().getBooleanExtra("show_reminders",false)) list(Module.REMINDERS);
        else home();
    }
    @Override protected void onResume() {
        super.onResume(); ServiceNotifications.refresh(this);
    }
    private void screen(String title) {
        boolean dashboard=title.equals("The Poultons’ RV Logbook");
        boolean form=title.startsWith("Add: ")||title.startsWith("Edit: ");
        currentScreen=dashboard?"Overview":form?(editId==0?"New ":"Edit ")+singular(selected):title;
        appRoot=new LinearLayout(this);appRoot.setOrientation(LinearLayout.VERTICAL);appRoot.setBackgroundColor(AdventureStyle.PAPER);setContentView(appRoot);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(16),dp(6),dp(16),dp(6));toolbar.setBackgroundColor(AdventureStyle.CARD);appRoot.addView(toolbar);
        if(!dashboard){Button back=new Button(this);back.setText("‹");back.setTextSize(28);back.setTextColor(AdventureStyle.INK);back.setBackgroundColor(android.graphics.Color.TRANSPARENT);back.setContentDescription("Back");toolbar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));back.setOnClickListener(v->{if(draft!=null)discardEditor();else home();});}
        ImageView crest=new ImageView(this);crest.setImageResource(R.drawable.poulton_crest);crest.setContentDescription("The Poulton’s RV");crest.setScaleType(ImageView.ScaleType.FIT_CENTER);toolbar.addView(crest,new LinearLayout.LayoutParams(dp(30),dp(42)));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);brand.setPadding(dp(10),0,0,0);toolbar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView name=new TextView(this);name.setText(R.string.brand_name);name.setTypeface(AdventureStyle.DISPLAY);name.setTextColor(AdventureStyle.INK);name.setTextSize(17);brand.addView(name);
        TextView sub=new TextView(this);sub.setText(R.string.app_subtitle);sub.setTextColor(AdventureStyle.MUTED);sub.setTextSize(12);brand.addView(sub);
        View divider=new View(this);divider.setBackgroundColor(AdventureStyle.BORDER);appRoot.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);appRoot.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(20),dp(20),dp(28));scroll.addView(content);
        TextView heading=text(currentScreen,28);heading.setTypeface(AdventureStyle.DISPLAY);heading.setTextColor(AdventureStyle.INK);heading.setPadding(0,0,0,dp(8));if(Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);
        if(!form)bottomNavigation(dashboard);
        appRoot.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
    }
    private String singular(Module module){
        switch(module){case TRIPS:return "trip";case CHECKS:case ARRIVAL:return "check";case FUEL:return "fuel entry";case EXPENSES:return "expense";case MAINTENANCE:return "maintenance record";case CAMPSITES:return "campsite";case PHOTOS:return "photo";case MILEAGE:return "mileage reading";case REMINDERS:return "service reminder";case PLACES:return "visited place";default:return "route";}
    }
    private void bottomNavigation(boolean dashboard){
        LinearLayout nav=new LinearLayout(this);nav.setBackgroundColor(AdventureStyle.CARD);nav.setPadding(dp(10),dp(8),dp(10),dp(8));appRoot.addView(nav);
        navItem(nav,"Home",Module.TRIPS,dashboard,this::home);
        navItem(nav,"Map",Module.PLACES,false,()->startActivityForResult(new Intent(this,TravelMapActivity.class),40));
        navItem(nav,"Checks",Module.CHECKS,(selected!=null&&selected.isChecklist())||currentScreen.equals("Checklists"),this::checksHub);
        navItem(nav,"Records",Module.MAINTENANCE,!dashboard&&(selected==null||!selected.isChecklist())&&!currentScreen.equals("Checklists"),this::recordsHub);
    }
    private void navItem(LinearLayout row,String title,Module icon,boolean active,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(11);b.setTypeface(AdventureStyle.DISPLAY);b.setTextColor(active?AdventureStyle.INK:AdventureStyle.MUTED);b.setSelected(active);b.setMinHeight(dp(52));b.setPadding(dp(3),dp(4),dp(3),dp(4));b.setBackground(AdventureStyle.pressable(this,active?AdventureStyle.SOFT:AdventureStyle.CARD,0));
        android.graphics.drawable.Drawable symbol=new NavigationIcon(title,active?AdventureStyle.INK:AdventureStyle.MUTED);symbol.setBounds(0,0,dp(21),dp(21));b.setCompoundDrawables(null,symbol,null,null);b.setCompoundDrawablePadding(dp(4));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);row.addView(b,lp);b.setOnClickListener(v->action.run());
    }
    private void section(String label,int color){TextView t=text(label,20);t.setTypeface(AdventureStyle.DISPLAY);t.setTextColor(AdventureStyle.INK);t.setPadding(0,dp(22),0,dp(10));if(Build.VERSION.SDK_INT>=28)t.setAccessibilityHeading(true);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTypeface(AdventureStyle.BODY);t.setTextColor(AdventureStyle.TEXT);t.setLineSpacing(dp(3),1);t.setPadding(0,dp(6),0,dp(8));content.addView(t);return t;}
    private Button makeButton(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(AdventureStyle.DISPLAY);b.setMinHeight(dp(48));b.setPadding(dp(14),dp(10),dp(14),dp(10));
        boolean secondary=label.startsWith("Edit")||label.startsWith("Choose")||label.startsWith("Pick")||label.startsWith("Cancel")||label.startsWith("Reset")||label.startsWith("Open")||label.startsWith("Notification")||label.startsWith("Import")||label.startsWith("Restore")||label.startsWith("Backup")||label.startsWith("View");
        boolean danger=label.startsWith("Delete");b.setTextColor(danger?AdventureStyle.DANGER:secondary?AdventureStyle.INK:AdventureStyle.CARD);b.setBackground(AdventureStyle.pressable(this,danger?0xfff9eeee:secondary?AdventureStyle.CARD:AdventureStyle.INK,secondary||danger?AdventureStyle.BORDER:0));b.setOnClickListener(v->action.run());return b;
    }
    private void button(String label,Runnable action){Button b=makeButton(label,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));content.addView(b,lp);}
    private void home(){
        selected=null;draft=null;screen("The Poultons’ RV Logbook");
        TextView intro=text("Journeys, checklists and vehicle care.",14);intro.setTextColor(AdventureStyle.MUTED);
        ImageView hero=new ImageView(this);hero.setImageResource(R.drawable.adventure_header);hero.setContentDescription("The Poulton’s RV Adventure Logbook");hero.setAdjustViewBounds(true);hero.setScaleType(ImageView.ScaleType.FIT_CENTER);hero.setBackground(AdventureStyle.panel(this,AdventureStyle.CARD,0));hero.setClipToOutline(true);LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,-2);heroLp.setMargins(0,dp(8),0,dp(12));content.addView(hero,heroLp);
        LinearLayout stats=new LinearLayout(this);stats.setBaselineAligned(false);content.addView(stats);stat(stats,String.format(Locale.UK,"%,.0f",store.latestMileage()),"Odometer · miles");stat(stats,String.format(Locale.UK,"£%,.2f",store.totalCost()),"Recorded costs");
        int due=store.dueCount();Button service=makeButton(due==0?"Service reminders · all up to date":due+" service reminder(s) due · review",()->list(Module.REMINDERS));service.setTextColor(AdventureStyle.INK);service.setTextSize(14);service.setBackground(AdventureStyle.pressable(this,AdventureStyle.SOFT,0));LinearLayout.LayoutParams serviceLp=new LinearLayout.LayoutParams(-1,-2);serviceLp.setMargins(0,dp(8),0,0);content.addView(service,serviceLp);
        section("Your next journey",AdventureStyle.GREEN);LinearLayout quick=new LinearLayout(this);quick.setBaselineAligned(false);content.addView(quick);tile(quick,Module.TRIPS,true);
        Button map=makeButton("Travel map\nRoutes & visited places",()->startActivityForResult(new Intent(this,TravelMapActivity.class),40));styleTile(map,Module.PLACES);LinearLayout.LayoutParams mapLp=new LinearLayout.LayoutParams(0,-2,1);mapLp.setMargins(dp(5),dp(4),0,dp(4));quick.addView(map,mapLp);
        section("Ready for the road",AdventureStyle.GREEN);checklistProgress(Module.CHECKS,"Pre-trip inspection");checklistProgress(Module.ARRIVAL,"Arrival / setup");
        section("Your logbook",AdventureStyle.GREEN);
        moduleGrid(new Module[]{Module.MAINTENANCE,Module.FUEL,Module.EXPENSES,Module.CAMPSITES,Module.PHOTOS,Module.MILEAGE,Module.PLACES,Module.ROUTES});
        section("Your data",AdventureStyle.GREEN);button("Backup & restore",this::backups);TextView footer=text("Saved on this device · Miles · Litres · GBP",12);footer.setTextColor(AdventureStyle.MUTED);footer.setGravity(android.view.Gravity.CENTER);
    }
    private void checksHub(){selected=null;draft=null;screen("Checklists");TextView note=text("One step closer to your next adventure.",14);note.setTextColor(AdventureStyle.MUTED);checklistProgress(Module.CHECKS,"Pre-trip inspection");checklistProgress(Module.ARRIVAL,"Arrival / setup");}
    private void recordsHub(){selected=null;draft=null;screen("Records");TextView note=text("Your trips, running costs and vehicle history.",14);note.setTextColor(AdventureStyle.MUTED);section("Journeys & places",AdventureStyle.GREEN);moduleGrid(new Module[]{Module.TRIPS,Module.CAMPSITES,Module.PLACES,Module.ROUTES,Module.PHOTOS});section("Vehicle care",AdventureStyle.GREEN);moduleGrid(new Module[]{Module.MAINTENANCE,Module.REMINDERS,Module.MILEAGE});section("Running costs",AdventureStyle.GREEN);moduleGrid(new Module[]{Module.FUEL,Module.EXPENSES});section("Settings & data",AdventureStyle.GREEN);button("Backup & restore",this::backups);text("Version 1.4 · The Poulton’s RV Logbook",12);}
    private void moduleGrid(Module[] areas){int columns=getResources().getConfiguration().fontScale>1.2f||getResources().getConfiguration().screenWidthDp<360?1:2;for(int i=0;i<areas.length;i+=columns){LinearLayout row=new LinearLayout(this);row.setBaselineAligned(false);content.addView(row);tile(row,areas[i],true);if(columns==2&&i+1<areas.length)tile(row,areas[i+1],false);}}
    private void checklistProgress(Module module,String label){
        int total=store.list(module).size();long done=store.list(module).stream().filter(e->e.data.optBoolean("done")).count();
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(5),dp(14),dp(14));card.setBackground(AdventureStyle.pressable(this,AdventureStyle.CARD,AdventureStyle.BORDER));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));content.addView(card,lp);
        Button b=makeButton(module.title+"\n"+done+" of "+total+" completed",()->list(module));b.setTextColor(AdventureStyle.INK);b.setTextSize(15);b.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);b.setPadding(0,dp(6),0,dp(10));b.setBackgroundColor(android.graphics.Color.TRANSPARENT);card.addView(b,new LinearLayout.LayoutParams(-1,-2));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(total,1));bar.setProgress((int)done);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(AdventureStyle.GREEN));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(AdventureStyle.SOFT));card.addView(bar,new LinearLayout.LayoutParams(-1,dp(5)));card.setOnClickListener(v->list(module));
    }
    private void renderChecklist(Module module){
        if(store.list(module).isEmpty())text("No checks yet. Add a record above.",16);
        for(LogStore.Entry entry:store.list(module)){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(9),dp(5),dp(9),dp(5));card.setBackground(AdventureStyle.panel(this,AdventureStyle.CARD,AdventureStyle.BORDER));
            LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);cardLp.setMargins(0,dp(4),0,dp(4));content.addView(card,cardLp);
            LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);card.addView(row);
            CheckBox check=new CheckBox(this);check.setText(entry.data.optString("Item"));check.setTextSize(16);check.setTextColor(AdventureStyle.TEXT);check.setButtonTintList(android.content.res.ColorStateList.valueOf(AdventureStyle.GREEN));check.setMinHeight(dp(52));check.setChecked(entry.data.optBoolean("done"));check.setTextColor(entry.data.optBoolean("done")?AdventureStyle.MUTED:AdventureStyle.TEXT);row.addView(check,new LinearLayout.LayoutParams(0,-2,1));
            check.setOnCheckedChangeListener((v,done)->{put(entry.data,"done",done);store.save(module,entry.id,entry.data);check.setTextColor(done?AdventureStyle.MUTED:AdventureStyle.TEXT);});
            Button edit=makeButton("Edit",()->editor(entry.data,entry.id));edit.setContentDescription("Edit "+entry.data.optString("Item"));row.addView(edit,new LinearLayout.LayoutParams(dp(64),-2));
            if(!entry.data.optString("Notes").isEmpty()){
                TextView notes=new TextView(this);notes.setText(entry.data.optString("Notes"));notes.setTextSize(14);notes.setTextColor(AdventureStyle.TEXT);notes.setPadding(dp(8),0,0,dp(5));card.addView(notes);
            }
        }
    }
    private void stat(LinearLayout row,String value,String label){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackground(AdventureStyle.panel(this,AdventureStyle.CARD,AdventureStyle.BORDER));
        TextView l=new TextView(this);l.setText(label);l.setTextSize(12);l.setTextColor(AdventureStyle.MUTED);card.addView(l);
        TextView n=new TextView(this);n.setText(value);n.setTextSize(24);n.setTypeface(AdventureStyle.DISPLAY);n.setTextColor(AdventureStyle.INK);n.setPadding(0,dp(5),0,0);card.addView(n);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(row.getChildCount()==0?0:dp(5),0,row.getChildCount()==0?dp(5):0,0);row.addView(card,lp);
    }
    private void styleTile(Button b,Module module){
        b.setTextColor(AdventureStyle.INK);b.setBackground(AdventureStyle.pressable(this,AdventureStyle.CARD,AdventureStyle.BORDER));b.setTextSize(14);b.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);b.setMinHeight(dp(96));b.setPadding(dp(12),dp(12),dp(10),dp(12));
        android.graphics.drawable.Drawable icon=new LogbookIcon(module);icon.setBounds(0,0,dp(23),dp(23));b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(dp(10));
        String value=b.getText().toString();int line=value.indexOf('\n');if(line>=0){android.text.SpannableString caption=new android.text.SpannableString(value);caption.setSpan(new android.text.style.RelativeSizeSpan(.85f),line+1,value.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);caption.setSpan(new android.text.style.ForegroundColorSpan(AdventureStyle.MUTED),line+1,value.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);b.setText(caption);}
    }
    private void tile(LinearLayout row,Module module,boolean left){Button b=makeButton(module.title+"\n"+store.list(module).size()+" entries",()->list(module));styleTile(b,module);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(left?0:dp(5),dp(5),left?dp(5):0,dp(5));row.addView(b,lp);}
    private void emptyState(Module module){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(android.view.Gravity.CENTER);card.setPadding(dp(24),dp(28),dp(24),dp(28));card.setBackground(AdventureStyle.panel(this,AdventureStyle.CARD,AdventureStyle.BORDER));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(16),0,0);content.addView(card,lp);
        ImageView image=new ImageView(this);image.setImageDrawable(new LogbookIcon(module));image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(image,new LinearLayout.LayoutParams(dp(40),dp(40)));
        TextView title=new TextView(this);title.setText(R.string.no_entries);title.setTextSize(19);title.setTypeface(AdventureStyle.DISPLAY);title.setTextColor(AdventureStyle.INK);title.setPadding(0,dp(14),0,dp(6));card.addView(title);
        TextView detail=new TextView(this);detail.setText(getString(R.string.empty_detail,singular(module)));detail.setTextSize(14);detail.setTextColor(AdventureStyle.MUTED);detail.setGravity(android.view.Gravity.CENTER);card.addView(detail);
    }
    private void list(Module module) {
        selected = module; draft = null; screen(module.title); TextView count=text(store.list(module).size()+" "+(module.isChecklist()?"checks":"entries"),14);count.setTextColor(AdventureStyle.MUTED);button("Add record", () -> editor(new JSONObject(),0));
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
            text("Due when either date or mileage is reached. Mileage uses your highest logged reading. Android checks approximately daily; battery settings may delay alerts.",16);
            button("Notification settings",this::notificationSettings);
        }
        if (store.list(module).isEmpty()) emptyState(module);
        for (LogStore.Entry e : store.list(module)) {
            LinearLayout parent = content;
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(12),dp(16),dp(12));
            card.setBackground(AdventureStyle.panel(this,AdventureStyle.CARD,AdventureStyle.BORDER));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1,-2); cardLp.setMargins(0,dp(12),0,dp(4)); parent.addView(card,cardLp); content=card;
            LinearLayout recordContent=content;
            TextView recordTitle=new TextView(this);recordTitle.setText(e.data.optString(module.fields[0],module.title));recordTitle.setTypeface(AdventureStyle.DISPLAY);recordTitle.setTextSize(18);recordTitle.setTextColor(AdventureStyle.INK);recordTitle.setPadding(0,dp(2),0,dp(8));recordContent.addView(recordTitle);
            StringBuilder label = new StringBuilder();
            if (module == Module.REMINDERS && !e.data.optBoolean("done") && Rules.due(e.data.optString("Due date"),e.data.optString("Due miles"),store.latestMileage(),LocalDate.now())) label.append("DUE • ");
            for (String field : module.fields) if (!field.equals(module.fields[0]) && !field.equals("Route points") && !e.data.optString(field).isEmpty()) label.append(field).append(": ").append(e.data.optString(field)).append("\n");
            if(module==Module.ROUTES)try{org.json.JSONArray segments=GeoData.parse(e.data.optString("Route points"));label.append(GeoData.pointCount(segments)).append(" points · ").append(String.format(Locale.UK,"%.1f",GeoData.miles(segments))).append(" path miles\n");}catch(Exception ignored){}
            if (module == Module.REMINDERS) {
                CheckBox check = new CheckBox(this); check.setText(R.string.service_completed); check.setTextColor(AdventureStyle.TEXT);check.setButtonTintList(android.content.res.ColorStateList.valueOf(AdventureStyle.GREEN)); check.setMinHeight(dp(48)); check.setChecked(e.data.optBoolean("done"));
                if (module == Module.REMINDERS && e.data.optBoolean("done")) check.setEnabled(false);
                content.addView(check);
                check.setOnCheckedChangeListener((v,done)->{
                    if (module == Module.REMINDERS && done) {
                        check.setOnCheckedChangeListener(null); check.setChecked(false);
                        new AlertDialog.Builder(this).setTitle("Complete service?").setMessage("Mark this reminder completed? If a repeat interval is set, a new reminder will start from today and your current logged mileage. Record the work separately under Maintenance.")
                            .setNegativeButton("Cancel",(d,w)->list(module)).setPositiveButton("Complete",(d,w)->completeReminder(e)).setOnCancelListener(d->list(module)).show();
                    } else { put(e.data,"done",done); store.save(module,e.id,e.data); ServiceNotifications.refresh(this); list(module); }
                });
            }
            if(label.length()>0){TextView detail=text(label.toString().trim(),14);detail.setTextColor(AdventureStyle.MUTED);}
            if (module == Module.PHOTOS && !e.data.optString("uri").isEmpty()) button("Open photo", () -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(e.data.optString("uri")),"image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
                catch (RuntimeException ex) { toast("Photo unavailable. Edit this record to choose it again."); }
            });
            button("Edit record", () -> editor(e.data,e.id));
            content = parent;
        }
    }
    private void editor(JSONObject data, long id) {
        try { draft = new JSONObject(data.toString()); } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
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
        for (String field : selected.fields) {
            if(field.equals("Route points")||field.equals("Route type"))continue;
            TextView fieldLabel=text(field + (selected.required(field) ? " *" : ""),13);fieldLabel.setTypeface(AdventureStyle.DISPLAY);
            EditText input = new EditText(this);input.setId(View.generateViewId());fieldLabel.setLabelFor(input.getId()); input.setSingleLine(!field.equals("Notes"));
            if (Rules.isNumber(field)) input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            if(field.equals("Latitude")||field.equals("Longitude"))input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
            if (field.toLowerCase(java.util.Locale.ROOT).contains("date")) input.setHint("YYYY-MM-DD");
            input.setTextColor(AdventureStyle.TEXT);input.setHintTextColor(0xff756b59);input.setMinHeight(dp(52));
            input.setBackground(AdventureStyle.input(this));input.setPadding(dp(14),dp(12),dp(14),dp(12));input.setTypeface(AdventureStyle.BODY);input.setTextSize(16);
            input.setText(draft.optString(field));
            if (id == 0 && field.equals("Date") && draft.optString(field).isEmpty() && !(selected==Module.ROUTES && draft.optString("Route type").startsWith("GPX"))) input.setText(LocalDate.now().toString());
            if (field.equals("Notes")) { input.setMinLines(3); input.setGravity(android.view.Gravity.TOP); }
            if(field.toLowerCase(Locale.ROOT).contains("date")){
                LinearLayout dateRow=new LinearLayout(this);dateRow.setGravity(android.view.Gravity.CENTER_VERTICAL);content.addView(dateRow);
                dateRow.addView(input,new LinearLayout.LayoutParams(0,-2,1));Button pick=makeButton("Pick date",()->pickDate(input));pick.setTextSize(13);LinearLayout.LayoutParams pickLp=new LinearLayout.LayoutParams(dp(86),-2);pickLp.setMargins(dp(8),0,0,0);dateRow.addView(pick,pickLp);
            }else content.addView(input);
            inputs.put(field,input);
            if(field.equals("Trip"))button("Choose saved trip",()->{
                java.util.LinkedHashSet<String> names=new java.util.LinkedHashSet<>();names.add("");
                for(LogStore.Entry trip:store.list(Module.TRIPS))names.add(trip.data.optString("Name"));
                String[] values=names.toArray(new String[0]),labels=values.clone();labels[0]="Unlinked";
                new AlertDialog.Builder(this).setTitle("Link to a trip").setItems(labels,(d,index)->input.setText(values[index])).setNegativeButton("Cancel",null).show();
            });

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
        collect(); boolean invalid = false;
        for (Map.Entry<String,EditText> e : inputs.entrySet()) {
            String value = draft.optString(e.getKey()); String error = Rules.validate(e.getKey(),value);
            if (selected.required(e.getKey()) && value.isEmpty()) error = "Required";
            if (error != null) { e.getValue().setError(error); invalid = true; }
        }
        if (invalid) return;
        Map<String,String> values = new LinkedHashMap<>();
        for (String field : selected.fields) values.put(field,draft.optString(field));
        String error = Rules.validateRecord(selected,values);
        if (error != null) { toast(error); return; }
        if (selected == Module.PHOTOS && draft.optString("uri").isEmpty()) { toast("Choose a photo first"); return; }
        try { store.save(selected,editId,draft); ServiceNotifications.refresh(this); list(selected); }
        catch (RuntimeException e) { toast("Could not save. Your form is still open; please retry."); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if(request==40&&result==RESULT_OK&&data!=null){
            String action=data.getStringExtra("action");
            if("import".equals(action)){chooseGpx();return;}
            if("edit".equals(action)){
                selected=Module.valueOf(data.getStringExtra("module"));long id=data.getLongExtra("id",0);
                for(LogStore.Entry e:store.list(selected))if(e.id==id){editor(e.data,id);return;}
                list(selected);return;
            }
            JSONObject record=new JSONObject();
            if("pin".equals(action)){selected=Module.PLACES;put(record,"Name",data.getStringExtra("Name"));put(record,"Latitude",data.getStringExtra("Latitude"));put(record,"Longitude",data.getStringExtra("Longitude"));}
            else if("route".equals(action)){selected=Module.ROUTES;put(record,"Route type","Manual waypoints");put(record,"Route points",data.getStringExtra("Route points"));}
            else return;
            editor(record,0);return;
        }
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
        super.onSaveInstanceState(state);state.putString("section",currentScreen);
        if (selected != null) state.putString("module",selected.name());
        if (draft != null) { collect(); state.putString("draft",draft.toString()); state.putLong("editId",editId); }
    }
    @Override public void onBackPressed() {
        if (draft != null) discardEditor();
        else if (selected != null || !currentScreen.equals("Overview")) home(); else super.onBackPressed();
    }
    private void discardEditor() {
        new AlertDialog.Builder(this).setMessage("Discard unsaved changes?").setNegativeButton("Keep editing",null).setPositiveButton("Discard",(d,w)->list(selected)).show();
    }
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density); }
    private void completeReminder(LogStore.Entry entry) {
        android.database.sqlite.SQLiteDatabase db = store.getWritableDatabase(); db.beginTransaction();
        try {
            JSONObject completed = new JSONObject(entry.data.toString()); put(completed,"done",true); store.save(Module.REMINDERS,entry.id,completed);
            int months = Integer.parseInt(entry.data.optString("Repeat months","0").isEmpty()?"0":entry.data.optString("Repeat months"));
            double miles = entry.data.optDouble("Repeat miles",0);
            if (months > 0 || miles > 0) {
                JSONObject next = new JSONObject(entry.data.toString()); put(next,"done",false);
                put(next,"Due date",months > 0 ? LocalDate.now().plusMonths(months).toString() : "");
                put(next,"Due miles",miles > 0 ? String.valueOf(store.latestMileage()+miles) : "");
                store.save(Module.REMINDERS,0,next);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) { toast("Could not complete service. Please retry."); }
        finally { db.endTransaction(); }
        ServiceNotifications.refresh(this); list(Module.REMINDERS);
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
