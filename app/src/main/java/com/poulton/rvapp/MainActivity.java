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
    private LinearLayout content;
    private Module selected;
    private JSONObject draft;
    private long editId;
    private final Map<String, EditText> inputs = new LinkedHashMap<>();
    private TextView photoLabel;

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
        } else if (getIntent().getBooleanExtra("show_reminders",false)) list(Module.REMINDERS);
        else home();
    }
    @Override protected void onResume() {
        super.onResume(); ServiceNotifications.refresh(this);
    }
    private void screen(String title) {
        boolean dashboard=title.equals("The Poultons’ RV Logbook");
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setBackgroundColor(AdventureStyle.PAPER);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackground(AdventureStyle.paper());
        scroll.addView(page);setContentView(scroll);
        if(dashboard) {
            ImageView hero=new ImageView(this);hero.setImageResource(R.drawable.adventure_header);
            hero.setContentDescription("The Poulton’s RV Adventure Logbook — motorhome and adventure branding");
            hero.setAdjustViewBounds(true);hero.setScaleType(ImageView.ScaleType.FIT_CENTER);
            page.addView(hero,new LinearLayout.LayoutParams(-1,-2));
        }
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(16),dp(16),dp(24));page.addView(content);
        if(!dashboard) brand();
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        section(dashboard ? "YOUR ADVENTURE LOGBOOK" : title,AdventureStyle.INK);
    }
    private void brand() {
        LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView crest=new ImageView(this);crest.setImageResource(R.drawable.poulton_crest);crest.setContentDescription("The Poulton’s RV crest");
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(crest,new LinearLayout.LayoutParams(dp(48),dp(58)));
        TextView name=new TextView(this);name.setText("THE POULTON’S\nRV ADVENTURE LOGBOOK");name.setTextColor(AdventureStyle.INK);name.setTypeface(AdventureStyle.DISPLAY);name.setTextSize(18);name.setPadding(dp(12),0,0,0);row.addView(name);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(14));content.addView(row,lp);
    }
    private void section(String label,int color) {
        TextView heading=new TextView(this);
        android.text.SpannableString caption=new android.text.SpannableString("★  " + label.toUpperCase(Locale.UK) + "  ★");
        caption.setSpan(new android.text.style.ForegroundColorSpan(AdventureStyle.GOLD),0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        caption.setSpan(new android.text.style.ForegroundColorSpan(AdventureStyle.GOLD),caption.length()-1,caption.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);heading.setText(caption);
        heading.setTypeface(AdventureStyle.DISPLAY);heading.setTextSize(18);heading.setTextColor(AdventureStyle.CARD);heading.setGravity(android.view.Gravity.CENTER);
        heading.setPadding(dp(10),dp(11),dp(10),dp(11));heading.setBackground(AdventureStyle.panel(this,color,AdventureStyle.GOLD));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(10));content.addView(heading,lp);
        if(Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);
    }
    private TextView text(String value, int size) {
        TextView t = new TextView(this);t.setText(value);t.setTextSize(size);t.setTypeface(AdventureStyle.BODY);t.setTextColor(AdventureStyle.TEXT);
        t.setLineSpacing(dp(2),1);t.setPadding(0,dp(8),0,dp(8));content.addView(t);return t;
    }
    private Button makeButton(String label, Runnable action) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(AdventureStyle.DISPLAY);
        b.setTextColor(AdventureStyle.CARD);b.setMinHeight(dp(50));
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},AdventureStyle.panel(this,AdventureStyle.GREEN,AdventureStyle.GOLD));
        states.addState(new int[]{},AdventureStyle.panel(this,AdventureStyle.INK,AdventureStyle.GOLD));
        b.setBackground(states);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setOnClickListener(v->action.run());return b;
    }
    private void button(String label, Runnable action) {
        Button b=makeButton(label,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));content.addView(b,lp);
    }
    private void home() {
        selected=null;draft=null;screen("The Poultons’ RV Logbook");
        TextView welcome=text("The road. The journey. The memories.",17);welcome.setTypeface(Typeface.create("serif",Typeface.ITALIC));welcome.setGravity(android.view.Gravity.CENTER);
        LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);content.addView(stats);
        stat(stats,String.format(Locale.UK,"%,.0f",store.latestMileage()),"ODOMETER · MILES");
        stat(stats,String.format(Locale.UK,"£%,.2f",store.totalCost()),"TOTAL RECORDED COSTS");
        int due=store.dueCount();
        button(due==0?"Service reminders · all up to date":due+" service reminder(s) due · review",()->list(Module.REMINDERS));
        int total=store.list(Module.CHECKS).size();long checked=store.list(Module.CHECKS).stream().filter(e->e.data.optBoolean("done")).count();
        TextView progress=text("DEPARTURE READY  ·  "+checked+" / "+total+" CHECKED",13);progress.setTypeface(AdventureStyle.DISPLAY);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(total,1));bar.setProgress((int)checked);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(AdventureStyle.GREEN));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffdfd3bd));content.addView(bar,new LinearLayout.LayoutParams(-1,dp(7)));
        section("EXPLORE YOUR LOGBOOK",AdventureStyle.GREEN);
        Module[] areas={Module.TRIPS,Module.CHECKS,Module.MAINTENANCE,Module.FUEL,Module.EXPENSES,Module.CAMPSITES,Module.PHOTOS,Module.MILEAGE};
        int columns=getResources().getConfiguration().fontScale>1.2f || getResources().getConfiguration().screenWidthDp<360 ? 1 : 2;
        for(int i=0;i<areas.length;i+=columns) {
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setBaselineAligned(false);content.addView(row);
            tile(row,areas[i],true);if(columns==2)tile(row,areas[i+1],false);
        }
        section("KEEP YOUR MEMORIES SAFE",0xff6b5b43);button("Backup & restore",this::backups);
        TextView footer=text("Making memories… one mile at a time.\n★  ★  ★",16);footer.setGravity(android.view.Gravity.CENTER);footer.setTypeface(Typeface.create("serif",Typeface.ITALIC));
        text("Version 1.1 · Miles · Litres · GBP\nSaved on this device. Back up your logbook before changing phones or uninstalling.",12);
        content.addView(new AdventureStyle.Stripes(this),new LinearLayout.LayoutParams(-1,dp(30)));
    }
    private void stat(LinearLayout row,String value,String label) {
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(10),dp(12),dp(10),dp(12));card.setBackground(AdventureStyle.panel(this,AdventureStyle.CARD,AdventureStyle.GOLD));
        TextView n=new TextView(this);n.setText(value);n.setTextSize(22);n.setTypeface(AdventureStyle.DISPLAY);n.setTextColor(AdventureStyle.INK);card.addView(n);
        TextView l=new TextView(this);l.setText(label);l.setTextSize(10);l.setTypeface(AdventureStyle.DISPLAY);l.setTextColor(0xff625642);l.setPadding(0,dp(4),0,0);card.addView(l);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(6),dp(3),dp(10));row.addView(card,lp);
    }
    private void tile(LinearLayout row,Module module,boolean left) {
        Button b=makeButton(module.title+"\n"+store.list(module).size()+" records",()->list(module));
        b.setTextColor(AdventureStyle.INK);b.setBackground(AdventureStyle.panel(this,AdventureStyle.CARD,AdventureStyle.GOLD));b.setTextSize(16);b.setGravity(android.view.Gravity.CENTER);b.setMinHeight(dp(128));
        android.graphics.drawable.Drawable icon=new LogbookIcon(module);icon.setBounds(0,0,dp(27),dp(27));b.setCompoundDrawables(null,icon,null,null);b.setCompoundDrawablePadding(dp(6));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(left?0:dp(5),dp(5),left?dp(5):0,dp(5));row.addView(b,lp);
    }
    private void list(Module module) {
        selected = module; draft = null; screen(module.title); button("Home", this::home); button("Add record", () -> editor(new JSONObject(),0));
        if (module == Module.CHECKS) {
            text("Reusable starting checks. Add checks specific to your RV and follow its manual.",16);
            button("Reset all checks for next departure", () -> new AlertDialog.Builder(this).setMessage("Mark every departure check as incomplete?").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{
                for (LogStore.Entry e : store.list(module)) { put(e.data,"done",false); store.save(module,e.id,e.data); } list(module);
            }).show());
        }
        if (module == Module.REMINDERS) {
            text("Due when either date or mileage is reached. Mileage uses your highest logged reading. Android checks approximately daily; battery settings may delay alerts.",16);
            button("Notification settings",this::notificationSettings);
        }
        if (store.list(module).isEmpty()) text("No records yet. Add your first one above.",18);
        for (LogStore.Entry e : store.list(module)) {
            LinearLayout parent = content;
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(12),dp(16),dp(12));
            card.setBackground(AdventureStyle.panel(this,AdventureStyle.CARD,AdventureStyle.GOLD));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1,-2); cardLp.setMargins(0,dp(12),0,dp(4)); parent.addView(card,cardLp); content=card;
            LinearLayout recordContent=content;
            TextView recordTitle=new TextView(this);recordTitle.setText(e.data.optString(module.fields[0],module.title).toUpperCase(Locale.UK));recordTitle.setTypeface(AdventureStyle.DISPLAY);recordTitle.setTextSize(18);recordTitle.setTextColor(AdventureStyle.CARD);recordTitle.setPadding(dp(10),dp(9),dp(10),dp(9));recordTitle.setBackground(AdventureStyle.panel(this,AdventureStyle.GREEN,AdventureStyle.GREEN));recordContent.addView(recordTitle);
            StringBuilder label = new StringBuilder();
            if (module == Module.REMINDERS && !e.data.optBoolean("done") && Rules.due(e.data.optString("Due date"),e.data.optString("Due miles"),store.latestMileage(),LocalDate.now())) label.append("DUE • ");
            for (String field : module.fields) if (!e.data.optString(field).isEmpty()) label.append(field).append(": ").append(e.data.optString(field)).append("\n");
            if (module == Module.CHECKS || module == Module.REMINDERS) {
                CheckBox check = new CheckBox(this); check.setText(module == Module.CHECKS ? "Checked" : "Service completed"); check.setTextColor(AdventureStyle.TEXT);check.setButtonTintList(android.content.res.ColorStateList.valueOf(AdventureStyle.GREEN)); check.setMinHeight(dp(48)); check.setChecked(e.data.optBoolean("done"));
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
            text(label.toString().trim(),17);
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
        for (String field : selected.fields) {
            TextView fieldLabel=text(field.toUpperCase(Locale.UK) + (selected.required(field) ? " *" : ""),14);fieldLabel.setTypeface(AdventureStyle.DISPLAY);
            EditText input = new EditText(this); input.setSingleLine(!field.equals("Notes"));
            if (Rules.isNumber(field)) input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            if (field.toLowerCase(java.util.Locale.ROOT).contains("date")) input.setHint("YYYY-MM-DD");
            input.setTextColor(AdventureStyle.TEXT);input.setHintTextColor(0xff756b59);input.setMinHeight(dp(52));
            input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(AdventureStyle.GOLD));input.setTypeface(AdventureStyle.BODY);input.setTextSize(17);
            input.setText(draft.optString(field));
            if (id == 0 && field.equals("Date") && draft.optString(field).isEmpty()) input.setText(LocalDate.now().toString());
            if (field.equals("Notes")) { input.setMinLines(3); input.setGravity(android.view.Gravity.TOP); }
            content.addView(input); inputs.put(field,input);
            if (field.toLowerCase(Locale.ROOT).contains("date")) button("Choose " + field.toLowerCase(Locale.ROOT), () -> {
                LocalDate date; try { date=LocalDate.parse(input.getText().toString()); } catch (RuntimeException e) { date=LocalDate.now(); }
                new DatePickerDialog(this,(picker,y,m,d)->input.setText(LocalDate.of(y,m+1,d).toString()),date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show();
            });
        }
        if (selected == Module.PHOTOS) {
            photoLabel = text(draft.optString("uri").isEmpty() ? "Choose a photo (reattach after restoring a backup)" : "Photo attached",16);
            button("Choose photo", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),10));
        }
        button("Save", this::save);
        button("Cancel", this::discardEditor);
        if (id != 0) button("Delete record", () -> new AlertDialog.Builder(this).setMessage("Permanently delete this record?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{ store.delete(id); ServiceNotifications.refresh(this); list(selected); }).show());
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
        super.onSaveInstanceState(state);
        if (selected != null) state.putString("module",selected.name());
        if (draft != null) { collect(); state.putString("draft",draft.toString()); state.putLong("editId",editId); }
    }
    @Override public void onBackPressed() {
        if (draft != null) discardEditor();
        else if (selected != null) home(); else super.onBackPressed();
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
        selected=null; draft=null; screen("Backup & restore"); button("Home",this::home);
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
    @Override protected void onDestroy() { store.close(); super.onDestroy(); }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    private static void put(JSONObject o, String key, Object value) { try { o.put(key,value); } catch (org.json.JSONException e) { throw new IllegalArgumentException(e); } }
}
