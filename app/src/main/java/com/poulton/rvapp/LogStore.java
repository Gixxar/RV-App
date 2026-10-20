package com.poulton.rvapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Small offline repository. JSON payloads allow incremental feature-specific schema evolution. */
public final class LogStore extends SQLiteOpenHelper {
    public static final class Entry {
        public final long id;
        public final JSONObject data;
        Entry(long id, JSONObject data) { this.id = id; this.data = data; }
    }
    public LogStore(Context context) { super(context, "rv-logbook.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, module TEXT NOT NULL, data TEXT NOT NULL)");
        db.execSQL("CREATE INDEX entries_module ON entries(module)");
        for (String item : new String[]{"Check tyres and lights", "Secure cupboards and loose items", "Disconnect shore power and water", "Retract steps and awning", "Check doors and roof vents"}) {
            JSONObject data = new JSONObject();
            try { data.put("Item", item); } catch (JSONException e) { throw new IllegalStateException(e); }
            ContentValues row = new ContentValues();
            row.put("module", Module.CHECKS.name()); row.put("data", data.toString()); db.insertOrThrow("entries", null, row);
        }
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Add a non-destructive migration before increasing the database version");
    }
    public List<Entry> list(Module module) {
        List<Entry> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("entries", new String[]{"id", "data"}, "module=?", new String[]{module.name()}, null, null, "id DESC")) {
            while (c.moveToNext()) {
                try { rows.add(new Entry(c.getLong(0), new JSONObject(c.getString(1)))); }
                catch (JSONException e) { throw new IllegalStateException("Invalid stored record", e); }
            }
        }
        return rows;
    }
    public void save(Module module, long id, JSONObject data) {
        ContentValues row = new ContentValues(); row.put("module", module.name()); row.put("data", data.toString());
        if (id == 0) getWritableDatabase().insertOrThrow("entries", null, row);
        else getWritableDatabase().update("entries", row, "id=? AND module=?", new String[]{String.valueOf(id), module.name()});
    }
    public void delete(long id) { getWritableDatabase().delete("entries", "id=?", new String[]{String.valueOf(id)}); }
    public double latestMileage() {
        double miles = 0;
        for (Module m : new Module[]{Module.MILEAGE, Module.FUEL, Module.MAINTENANCE, Module.TRIPS})
            for (Entry e : list(m)) miles = Math.max(miles, e.data.optDouble(m == Module.TRIPS ? "End miles" : "Odometer miles", 0));
        return miles;
    }
}
