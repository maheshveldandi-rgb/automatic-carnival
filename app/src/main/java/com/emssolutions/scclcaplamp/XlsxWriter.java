package com.emssolutions.scclcaplamp;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class XlsxWriter {
    public static byte[] create(List<Record> rows) throws Exception {
        String[] headers = {"S.No","Date","Time","Action","MSN","Emp ID","Lamp No","Name","Designation","Mobile Number","QRData","Remarks"};
        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        appendRow(sheet, 1, headers);
        int rnum = 2;
        for (int i = 0; i < rows.size(); i++, rnum++) {
            Record r = rows.get(i);
            String[] values = {String.valueOf(i+1), r.date, r.time, r.action, r.msn, r.empId, r.lampNo, r.name, r.designation, r.mobile, r.qrData, r.remarks};
            appendRow(sheet, rnum, values);
        }
        sheet.append("</sheetData></worksheet>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Mobile Lamp Attendance\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            put(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        }
        return out.toByteArray();
    }
    private static void appendRow(StringBuilder s, int rowNum, String[] values) {
        s.append("<row r=\"").append(rowNum).append("\">");
        for (int i = 0; i < values.length; i++) {
            String ref = col(i + 1) + rowNum;
            s.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(values[i])).append("</t></is></c>");
        }
        s.append("</row>");
    }
    private static String col(int n) { StringBuilder s = new StringBuilder(); while (n > 0) { n--; s.insert(0, (char)('A' + (n % 26))); n /= 26; } return s.toString(); }
    private static String xml(String v) { if (v == null) return ""; return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private static void put(ZipOutputStream zip, String name, String text) throws Exception { zip.putNextEntry(new ZipEntry(name)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
}
