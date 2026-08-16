package com.emssolutions.scclcaplamp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class LampDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "SCCL_CapLamp_Attendance.db";
    private static final int DB_VERSION = 3;
    public LampDb(Context context) { super(context, DB_NAME, null, DB_VERSION); }
    @Override public void onCreate(SQLiteDatabase db) { createRecords(db); }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { if (oldVersion < 3) createRecords(db); }
    private void createRecords(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS records (id INTEGER PRIMARY KEY AUTOINCREMENT,scan_date TEXT NOT NULL, scan_time TEXT NOT NULL, action TEXT NOT NULL,msn TEXT, emp_id TEXT, lamp_no TEXT, name TEXT, designation TEXT, mobile TEXT,shift TEXT, qr_data TEXT, operator TEXT, remarks TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_emp_id ON records(emp_id, id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_date ON records(scan_date, id)");
    }
    public long add(Record r) {
        ContentValues v = new ContentValues();
        v.put("scan_date", r.date); v.put("scan_time", r.time); v.put("action", r.action);
        v.put("msn", r.msn); v.put("emp_id", r.empId); v.put("lamp_no", r.lampNo);
        v.put("name", r.name); v.put("designation", r.designation); v.put("mobile", r.mobile);
        v.put("qr_data", r.qrData); v.put("remarks", r.remarks); v.put("shift", ""); v.put("operator", "AUTO");
        return getWritableDatabase().insertOrThrow("records", null, v);
    }
    public boolean hasOpenIssue(String empId) {
        if (empId == null || empId.trim().isEmpty()) return false;
        try (Cursor c = getReadableDatabase().query("records", new String[]{"action"}, "emp_id=?", new String[]{empId.trim()}, null, null, "id DESC", "1")) {
            return c.moveToFirst() && "ISSUE".equalsIgnoreCase(c.getString(0));
        }
    }
    public String openLamp(String empId) {
        if (!hasOpenIssue(empId)) return "";
        try (Cursor c = getReadableDatabase().query("records", new String[]{"lamp_no"}, "emp_id=?", new String[]{empId.trim()}, null, null, "id DESC", "1")) {
            return c.moveToFirst() && !c.isNull(0) ? c.getString(0) : "";
        }
    }
    public List<Record> getForDate(String date) {
        List<Record> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("records", null, "scan_date=?", new String[]{date}, null, null, "id ASC")) {
            while (c.moveToNext()) {
                Record r = new Record(); r.id = c.getLong(c.getColumnIndexOrThrow("id"));
                r.date = get(c,"scan_date"); r.time = get(c,"scan_time"); r.action = get(c,"action");
                r.msn = get(c,"msn"); r.empId = get(c,"emp_id"); r.lampNo = get(c,"lamp_no");
                r.name = get(c,"name"); r.designation = get(c,"designation"); r.mobile = get(c,"mobile");
                r.qrData = get(c,"qr_data"); r.remarks = get(c,"remarks"); rows.add(r);
            }
        }
        return rows;
    }
    public int todayCount(String date) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records WHERE scan_date=?", new String[]{date})) { return c.moveToFirst() ? c.getInt(0) : 0; }
    }
    private String get(Cursor c,String name) { int i=c.getColumnIndex(name); return i<0 || c.isNull(i)?"":c.getString(i); }
}
