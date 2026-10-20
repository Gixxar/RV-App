package com.poulton.rvapp;

import android.app.Activity;
import android.app.AlertDialog;
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
        if (state != null && state.containsKey("module")) {
            selected = Module.valueOf(state.getString("module"));
            if (state.containsKey("draft")) {
                try { editId = state.getLong("editId"); editor(new JSONObject(state.getString("draft")), editId); }
                catch (Exception e) { list(selected); }
            } else list(selected);
        } else home();
    }
    private void screen(String title) {
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density); content.setPadding(pad,pad,pad,pad);
        content.setBackgroundColor(0xfff4f7f5); scroll.setFillViewport(true); scroll.addView(content); setContentView(scroll);
        TextView heading = text(title, 26); heading.setTextColor(0xff176b65);
    }
    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setPadding(0,12,0,12); content.addView(t); return t;
    }
    private void button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); content.addView(b); b.setOnClickListener(v -> action.run());
    }
    private void home() {
        selected = null; draft = null; screen("RV Logbook");
        text("Your journeys, checks and running costs — saved on this device.",16);
        text("Recorded odometer: " + store.latestMileage() + " miles",18);
        long due = store.list(Module.REMINDERS).stream().filter(e -> !e.data.optBoolean("done") && Rules.due(e.data.optString("Due date"), e.data.optString("Due miles"), store.latestMileage(), LocalDate.now())).count();
        text(due + " service reminder(s) due",18);
        for (Module module : Module.values()) button(module.title + " (" + store.list(module).size() + ")", () -> list(module));
    }
    private void list(Module module) {
        selected = module; draft = null; screen(module.title); button("Home", this::home); button("Add record", () -> editor(new JSONObject(),0));
        if (module == Module.CHECKS) {
            text("Reusable starting checks. Add checks specific to your RV and follow its manual.",16);
            button("Reset all checks for next departure", () -> new AlertDialog.Builder(this).setMessage("Mark every departure check as incomplete?").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{
                for (LogStore.Entry e : store.list(module)) { put(e.data,"done",false); store.save(module,e.id,e.data); } list(module);
            }).show());
        }
        if (module == Module.REMINDERS) text("Due dates and mileage appear here and on Home. Background notifications are not included yet.",16);
        if (store.list(module).isEmpty()) text("No records yet. Add your first one above.",18);
        for (LogStore.Entry e : store.list(module)) {
            StringBuilder label = new StringBuilder();
            if (module == Module.REMINDERS && !e.data.optBoolean("done") && Rules.due(e.data.optString("Due date"),e.data.optString("Due miles"),store.latestMileage(),LocalDate.now())) label.append("DUE • ");
            for (String field : module.fields) if (!e.data.optString(field).isEmpty()) label.append(field).append(": ").append(e.data.optString(field)).append("\n");
            if (module == Module.CHECKS || module == Module.REMINDERS) {
                CheckBox check = new CheckBox(this); check.setText(module == Module.CHECKS ? "Checked" : "Service completed"); check.setChecked(e.data.optBoolean("done")); content.addView(check);
                check.setOnCheckedChangeListener((v,done)->{ put(e.data,"done",done); store.save(module,e.id,e.data); list(module); });
            }
            text(label.toString().trim(),17);
            if (module == Module.PHOTOS && !e.data.optString("uri").isEmpty()) button("Open photo", () -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(e.data.optString("uri")),"image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
                catch (RuntimeException ex) { toast("Photo unavailable. Edit this record to choose it again."); }
            });
            button("Edit record", () -> editor(e.data,e.id));
        }
    }
    private void editor(JSONObject data, long id) {
        draft = data; editId = id; inputs.clear(); screen((id == 0 ? "Add: " : "Edit: ") + selected.title);
        for (String field : selected.fields) {
            text(field + (field.equals(selected.fields[0]) ? " *" : ""),16);
            EditText input = new EditText(this); input.setSingleLine(!field.equals("Notes"));
            if (Rules.isNumber(field)) input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            if (field.toLowerCase(java.util.Locale.ROOT).contains("date")) input.setHint("YYYY-MM-DD");
            input.setText(data.optString(field)); content.addView(input); inputs.put(field,input);
        }
        if (selected == Module.PHOTOS) {
            photoLabel = text(data.optString("uri").isEmpty() ? "No photo selected" : "Photo attached",16);
            button("Choose photo", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),10));
        }
        button("Save", this::save);
        button("Cancel", () -> list(selected));
        if (id != 0) button("Delete record", () -> new AlertDialog.Builder(this).setMessage("Permanently delete this record?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{ store.delete(id); list(selected); }).show());
    }
    private void collect() { for (Map.Entry<String,EditText> e : inputs.entrySet()) put(draft,e.getKey(),e.getValue().getText().toString().trim()); }
    private void save() {
        collect(); boolean invalid = false;
        for (Map.Entry<String,EditText> e : inputs.entrySet()) {
            String value = draft.optString(e.getKey()); String error = Rules.validate(e.getKey(),value);
            if (e.getKey().equals(selected.fields[0]) && value.isEmpty()) error = "Required";
            if (error != null) { e.getValue().setError(error); invalid = true; }
        }
        if (invalid) return;
        if (selected == Module.TRIPS && !draft.optString("Start miles").isEmpty() && !draft.optString("End miles").isEmpty() && draft.optDouble("End miles") < draft.optDouble("Start miles")) { inputs.get("End miles").setError("Must be at least start miles"); return; }
        if (selected == Module.REMINDERS && draft.optString("Due date").isEmpty() && draft.optString("Due miles").isEmpty()) { toast("Enter a due date or due mileage"); return; }
        if (selected == Module.PHOTOS && draft.optString("uri").isEmpty()) { toast("Choose a photo first"); return; }
        try { store.save(selected,editId,draft); list(selected); }
        catch (RuntimeException e) { toast("Could not save. Your form is still open; please retry."); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request == 10 && result == RESULT_OK && data != null && data.getData() != null && draft != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); put(draft,"uri",uri.toString()); photoLabel.setText("Photo attached"); }
            catch (SecurityException e) { toast("Cannot retain access to this photo. Choose another provider."); }
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (selected != null) state.putString("module",selected.name());
        if (draft != null) { collect(); state.putString("draft",draft.toString()); state.putLong("editId",editId); }
    }
    @Override public void onBackPressed() {
        if (draft != null) new AlertDialog.Builder(this).setMessage("Discard unsaved changes?").setNegativeButton("Keep editing",null).setPositiveButton("Discard",(d,w)->list(selected)).show();
        else if (selected != null) home(); else super.onBackPressed();
    }
    @Override protected void onDestroy() { store.close(); super.onDestroy(); }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    private static void put(JSONObject o, String key, Object value) { try { o.put(key,value); } catch (org.json.JSONException e) { throw new IllegalArgumentException(e); } }
}
