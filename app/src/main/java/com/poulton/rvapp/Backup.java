package com.poulton.rvapp;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Portable record backup. Original photo files are deliberately not embedded. */
public final class Backup {
    private Backup() {}
    public static JSONObject exportRecords(LogStore store) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", "poulton-rv-logbook"); root.put("version",1);
        root.put("checklistTemplatesVersion",ChecklistTemplates.VERSION);
        root.put("created",java.time.Instant.now().toString());
        JSONArray rows = new JSONArray();
        for (Module module : Module.values()) for (LogStore.Entry e : store.list(module)) {
            JSONObject data = new JSONObject(e.data.toString());
            data.remove("uri"); // Document permissions do not transfer to other installations.
            rows.put(new JSONObject().put("module",module.name()).put("data",data));
        }
        root.put("records",rows); return root;
    }
    public static JSONObject read(InputStream in) throws IOException, JSONException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) {
            if (bytes.size()+n > 10*1024*1024) throw new IOException("Backup exceeds 10 MB");
            bytes.write(buffer,0,n);
        }
        JSONObject root = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        validate(root); return root;
    }
    public static int validate(JSONObject root) throws JSONException {
        if (!"poulton-rv-logbook".equals(root.optString("format")) || root.getInt("version") != 1) throw new JSONException("Unsupported backup format");
        if(root.has("checklistTemplatesVersion") && (!(root.get("checklistTemplatesVersion") instanceof Integer) || root.getInt("checklistTemplatesVersion")<0 || root.getInt("checklistTemplatesVersion")>ChecklistTemplates.VERSION))throw new JSONException("Unsupported checklist template version");
        JSONArray records = root.getJSONArray("records");
        if (records.length() > 10000) throw new JSONException("Too many records (maximum 10,000)");
        int vehicles=0;
        for (int i=0;i<records.length();i++) {
            JSONObject record = records.getJSONObject(i);
            Module module;
            try { module = Module.valueOf(record.getString("module")); }
            catch (IllegalArgumentException e) { throw new JSONException("Unknown logbook area"); }
            if(module==Module.VEHICLE && ++vehicles>1)throw new JSONException("Only one RV profile is supported");
            JSONObject data = record.getJSONObject("data");
            Map<String,String> values = new LinkedHashMap<>();
            for (String field : module.fields) {
                if (data.has(field) && !(data.get(field) instanceof String)) throw new JSONException("Invalid field type: " + field);
                values.put(field,data.optString(field));
            }
            if (data.has("done") && !(data.get("done") instanceof Boolean)) throw new JSONException("Invalid completion flag");
            String error = Rules.validateRecord(module,values);
            if (error != null) throw new JSONException("Record " + (i+1) + ": " + error);
        }
        return records.length();
    }
    public static void restore(LogStore store, JSONObject root) throws JSONException {
        validate(root); // Validate everything before touching existing records.
        SQLiteDatabase db = store.getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("entries",null,null);
            JSONArray rows = root.getJSONArray("records");
            // Reverse export's newest-first order so lists remain stable after restore.
            for (int i=rows.length()-1;i>=0;i--) {
                JSONObject row=rows.getJSONObject(i), data=row.getJSONObject("data");
                JSONObject clean = new JSONObject(); Module module=Module.valueOf(row.getString("module"));
                for (String field : module.fields) if (data.has(field)) clean.put(field,data.getString(field));
                if (data.has("done")) clean.put("done",data.getBoolean("done"));
                ContentValues v = new ContentValues(); v.put("module",module.name()); v.put("data",clean.toString()); db.insertOrThrow("entries",null,v);
            }
            if(root.optInt("checklistTemplatesVersion",0)<ChecklistTemplates.VERSION)ChecklistTemplates.apply(db,true);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
