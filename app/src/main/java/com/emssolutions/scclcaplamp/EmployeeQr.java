package com.emssolutions.scclcaplamp;

import java.util.Locale;

public class EmployeeQr {
    public String name = "";
    public String designation = "";
    public String mobile = "";
    public String empId = "";
    public String msn = "";
    public String lampNo = "";
    public String raw = "";

    public static EmployeeQr parse(String input) throws Exception {
        if (input == null) throw new Exception("QR is empty.");
        String raw = input.replace("\r", "");
        if (!raw.toUpperCase(Locale.ROOT).contains("BEGIN:VCARD")) {
            throw new Exception("Not an SCCL employee contact QR. Refresh the employee QR in FRS V39+.");
        }
        EmployeeQr e = new EmployeeQr();
        e.raw = input;
        StringBuilder note = new StringBuilder();
        for (String sourceLine : raw.split("\n")) {
            String line = sourceLine.trim();
            int idx = line.indexOf(':');
            if (idx < 0) continue;
            String key = line.substring(0, idx).toUpperCase(Locale.ROOT);
            String value = unescape(line.substring(idx + 1).trim());
            if (key.equals("FN")) e.name = value;
            else if (key.equals("TITLE")) e.designation = value;
            else if (key.startsWith("TEL")) e.mobile = value;
            else if (key.equals("NOTE")) {
                if (note.length() > 0) note.append('\n');
                note.append(value);
            }
        }
        for (String n : note.toString().split("\n")) {
            int idx = n.indexOf(':');
            if (idx < 0) continue;
            String k = n.substring(0, idx).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            String v = n.substring(idx + 1).trim();
            if (k.equals("empid") || k.equals("employeeid") || k.equals("perno")) e.empId = v;
            else if (k.equals("msn") || k.equals("msnid") || k.equals("msno")) e.msn = v;
            else if (k.equals("lampno") || k.equals("lampnumber") || k.equals("allottedlamp")) e.lampNo = v;
        }
        if (e.empId.isEmpty() && e.msn.isEmpty()) throw new Exception("Employee ID/MSN not found in this QR.");
        return e;
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n")
                .replace("\\,", ",")
                .replace("\\;", ";")
                .replace("\\\\", "\\");
    }
}
