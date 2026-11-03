package com.poulton.rvapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.Manifest;
import android.os.Build;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density); content.setPadding(pad,pad,pad,pad);
        content.setBackgroundColor(0xfff4f7f5); scroll.setFillViewport(true); scroll.addView(content); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView heading = text(title, 28); heading.setTextColor(0xff124d49); heading.setTypeface(null,Typeface.BOLD);
    }
    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(0xff243c3a); t.setPadding(0,12,0,12); content.addView(t); return t;
    }
    private void button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(16);
        b.setTextColor(0xffffffff); b.setMinHeight(dp(52));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xff176b65); bg.setCornerRadius(dp(12));
        b.setBackground(bg); b.setPadding(dp(14),dp(10),dp(14),dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(5),0,dp(5)); content.addView(b,lp);
        b.setOnClickListener(v -> action.run());
    }
    private void home() {
        selected = null; draft = null; screen("The Poultons’ RV Logbook");
        text("READY FOR THE NEXT JOURNEY",12);
        text("Trips, travel checks and vehicle care, even when you’re offline.",16);
        text(String.format(Locale.UK,"%,.0f miles  •  £%,.2f total recorded costs",store.latestMileage(),store.totalCost()),18);
        int due = store.dueCount();
        button(due == 0 ? "Service reminders · all up to date" : due + " service reminder(s) due · review",() -> list(Module.REMINDERS));
        int total=store.list(Module.CHECKS).size();
        long checked=store.list(Module.CHECKS).stream().filter(e->e.data.optBoolean("done")).count();
        text("Departure checks: " + checked + " of " + total + " checked",17);
        for (Module module : Module.values()) if (module != Module.REMINDERS) button(module.title + "  ·  " + store.list(module).size(), () -> list(module));
        text("YOUR DATA",13);
        button("Backup & restore",this::backups);
        text("Version 1.0 · Miles · Litres · GBP\nSaved privately on this device. Export a backup before changing phones or uninstalling.",14);
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
            GradientDrawable bg = new GradientDrawable(); bg.setColor(0xffffffff); bg.setCornerRadius(dp(16)); card.setBackground(bg);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1,-2); cardLp.setMargins(0,dp(12),0,dp(4)); parent.addView(card,cardLp); content=card;
            StringBuilder label = new StringBuilder();
            if (module == Module.REMINDERS && !e.data.optBoolean("done") && Rules.due(e.data.optString("Due date"),e.data.optString("Due miles"),store.latestMileage(),LocalDate.now())) label.append("DUE • ");
            for (String field : module.fields) if (!e.data.optString(field).isEmpty()) label.append(field).append(": ").append(e.data.optString(field)).append("\n");
            if (module == Module.CHECKS || module == Module.REMINDERS) {
                CheckBox check = new CheckBox(this); check.setText(module == Module.CHECKS ? "Checked" : "Service completed"); check.setTextColor(0xff243c3a); check.setMinHeight(dp(48)); check.setChecked(e.data.optBoolean("done"));
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
            text(field + (selected.required(field) ? " *" : ""),16);
            EditText input = new EditText(this); input.setSingleLine(!field.equals("Notes"));
            if (Rules.isNumber(field)) input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            if (field.toLowerCase(java.util.Locale.ROOT).contains("date")) input.setHint("YYYY-MM-DD");
            input.setTextColor(0xff172f2d); input.setHintTextColor(0xff536e6b); input.setMinHeight(dp(52));
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
