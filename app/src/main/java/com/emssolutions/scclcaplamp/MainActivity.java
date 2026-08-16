package com.emssolutions.scclcaplamp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final int CAMERA_REQ = 1101;
    private static final long SAME_QR_ABSENT_UNLOCK_MS = 1800L;
    private static final long FAILURE_COOLDOWN_MS = 1600L;
    private final int NAVY = Color.rgb(12,38,63), BLUE = Color.rgb(17,118,190), TEAL = Color.rgb(0,142,124), RED = Color.rgb(196,43,43), SOFT = Color.rgb(243,247,250);
    private PreviewView previewView;
    private TextView status, nameV, empV, lampV, msnV, roleV, mobileV, countV, excelV, actionV;
    private LampDb db;
    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private ProcessCameraProvider cameraProvider;
    private final Object scanLock = new Object();
    private boolean processing = false;
    private String lockedRaw = "";
    private long lastBarcodeSeenMs = 0L, lastFailureMs = 0L;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        db = new LampDb(this); scanner = BarcodeScanning.getClient(); cameraExecutor = Executors.newSingleThreadExecutor();
        setContentView(buildUi()); renderTodayCount();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQ);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout page = vertical(); page.setBackgroundColor(SOFT); page.setPadding(dp(10),0,dp(10),dp(16)); scroll.addView(page);
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(14),dp(11),dp(14),dp(11)); header.setBackgroundColor(NAVY);
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.sccl_logo); header.addView(logo,new LinearLayout.LayoutParams(dp(58),dp(58)));
        LinearLayout titleBox = vertical(); titleBox.setPadding(dp(10),0,0,0); titleBox.addView(text("LRDMS",24,Color.WHITE,true)); titleBox.addView(text("EMS Solution's • Automatic • Offline",12,Color.rgb(190,210,225),false)); header.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1)); page.addView(header,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout cameraCard = card(); LinearLayout cameraTitle = new LinearLayout(this); cameraTitle.setOrientation(LinearLayout.HORIZONTAL); cameraTitle.setGravity(Gravity.CENTER_VERTICAL); cameraTitle.addView(text("Scan Employee QR",18,NAVY,true),new LinearLayout.LayoutParams(0,-2,1)); countV = text("0 today",12,TEAL,true); cameraTitle.addView(countV); cameraCard.addView(cameraTitle);
        previewView = new PreviewView(this); previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE); cameraCard.addView(previewView,new LinearLayout.LayoutParams(-1,dp(315)));
        status = text("Starting rear camera…",14,Color.DKGRAY,true); status.setPadding(0,dp(10),0,dp(3)); cameraCard.addView(status); page.addView(cameraCard,cardLp());
        LinearLayout resultCard = card(); LinearLayout resultHeader = new LinearLayout(this); resultHeader.setOrientation(LinearLayout.HORIZONTAL); resultHeader.setGravity(Gravity.CENTER_VERTICAL); resultHeader.addView(text("Last Attendance",18,NAVY,true),new LinearLayout.LayoutParams(0,-2,1)); actionV = text("READY",16,BLUE,true); resultHeader.addView(actionV); resultCard.addView(resultHeader);
        nameV = field(resultCard,"Name"); LinearLayout ids = new LinearLayout(this); ids.setOrientation(LinearLayout.HORIZONTAL); empV = miniField(ids,"Emp ID"); msnV = miniField(ids,"MSN"); lampV = miniField(ids,"Lamp No"); resultCard.addView(ids,new LinearLayout.LayoutParams(-1,-2)); roleV = field(resultCard,"Designation"); mobileV = field(resultCard,"Mobile Number"); page.addView(resultCard,cardLp());
        LinearLayout excelCard = card(); excelCard.addView(text("Daily Excel",16,NAVY,true)); excelV = text("Automatically updated after every valid scan.",12,Color.DKGRAY,false); excelV.setPadding(0,dp(5),0,0); excelCard.addView(excelV); TextView path=text("Downloads / SCCL FRS / SCCL_CapLamp_Attendance_YYYY-MM-DD.xlsx",11,Color.GRAY,false); path.setPadding(0,dp(4),0,0); excelCard.addView(path); page.addView(excelCard,cardLp());
        TextView footer=text("No login • No master import • No Save button • No action selection • No cloud",11,Color.GRAY,false); footer.setGravity(Gravity.CENTER); footer.setPadding(dp(4),dp(12),dp(4),0); page.addView(footer); return scroll;
    }

    private void startCamera() {
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) return;
        status.setText("Starting rear camera…");
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get(); cameraProvider.unbindAll(); Preview preview = new Preview.Builder().build(); preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (imageProxy.getImage()==null) { imageProxy.close(); return; }
                    InputImage image=InputImage.fromMediaImage(imageProxy.getImage(),imageProxy.getImageInfo().getRotationDegrees());
                    scanner.process(image).addOnSuccessListener(this::handleBarcodes).addOnFailureListener(e -> runOnUiThread(() -> status.setText("Scanner error — camera remains active."))).addOnCompleteListener(t -> imageProxy.close());
                });
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis); runOnUiThread(() -> status.setText("READY — show an employee SCCL QR to the camera."));
            } catch(Exception ex) { runOnUiThread(() -> status.setText("Camera error: "+safe(ex.getMessage()))); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void handleBarcodes(List<Barcode> barcodes) {
        long now=System.currentTimeMillis(); String raw="";
        if (barcodes!=null) for (Barcode b:barcodes) { String v=b.getRawValue(); if (v!=null && !v.trim().isEmpty()) { raw=v; break; } }
        synchronized (scanLock) {
            if (raw.isEmpty()) { if (!lockedRaw.isEmpty() && now-lastBarcodeSeenMs>=SAME_QR_ABSENT_UNLOCK_MS) lockedRaw=""; return; }
            lastBarcodeSeenMs=now; if (processing || raw.equals(lockedRaw)) return; processing=true;
        }
        final String acceptedRaw=raw; runOnUiThread(() -> autoBook(acceptedRaw));
    }

    private void autoBook(String raw) {
        try {
            EmployeeQr e=EmployeeQr.parse(raw); validateEmployee(e); boolean open=db.hasOpenIssue(e.empId); String action=open?"RETURN":"ISSUE";
            if (open) { String activeLamp=db.openLamp(e.empId); if (!empty(activeLamp) && !activeLamp.equalsIgnoreCase(e.lampNo.trim())) throw new Exception("RETURN blocked: QR Lamp "+e.lampNo+" does not match open Lamp "+activeLamp+"."); }
            Record r=new Record(); r.date=today(); r.time=timeNow(); r.action=action; r.msn=e.msn.trim(); r.empId=e.empId.trim(); r.lampNo=e.lampNo.trim(); r.name=e.name.trim(); r.designation=e.designation.trim(); r.mobile=e.mobile.trim(); r.qrData=e.raw; r.remarks="LRDMS Android AUTO"; db.add(r);
            renderRecord(r); autoWriteDailyExcel(); successVibrate(); status.setText(action+" BOOKED — "+r.name+" / Lamp "+r.lampNo+" / "+r.time+". Ready for next employee."); actionV.setText(action+" ✓"); actionV.setTextColor("ISSUE".equals(action)?BLUE:TEAL);
            synchronized (scanLock) { lockedRaw=raw; processing=false; lastBarcodeSeenMs=System.currentTimeMillis(); }
        } catch(Exception ex) {
            failureVibrate(); actionV.setText("NOT BOOKED"); actionV.setTextColor(RED); status.setText(safe(ex.getMessage())); long now=System.currentTimeMillis(); synchronized (scanLock) { processing=false; if (now-lastFailureMs<FAILURE_COOLDOWN_MS) lockedRaw=raw; lastFailureMs=now; }
        }
    }

    private void validateEmployee(EmployeeQr e) throws Exception {
        if (empty(e.empId)) throw new Exception("QR rejected: Emp ID is missing."); if (empty(e.msn)) throw new Exception("QR rejected: MSN is missing."); if (empty(e.lampNo) || "-".equals(e.lampNo.trim())) throw new Exception("QR rejected: allotted Lamp No is missing."); if (empty(e.name)) throw new Exception("QR rejected: employee Name is missing."); if (empty(e.designation)) throw new Exception("QR rejected: Designation is missing."); if (empty(e.mobile)) throw new Exception("QR rejected: Mobile Number is missing.");
    }

    private void autoWriteDailyExcel() {
        try { List<Record> rows=db.getForDate(today()); byte[] data=XlsxWriter.create(rows); String fileName="SCCL_CapLamp_Attendance_"+today()+".xlsx"; writeOrReplaceDownload(fileName,data); excelV.setText("Updated automatically: "+timeNow()+" • "+rows.size()+" record"+(rows.size()==1?"":"s")); renderTodayCount(); }
        catch(Exception ex) { excelV.setText("Attendance saved locally. Excel update warning: "+safe(ex.getMessage())); renderTodayCount(); }
    }

    private void writeOrReplaceDownload(String fileName,byte[] data) throws Exception {
        ContentResolver cr=getContentResolver(); String relative=Environment.DIRECTORY_DOWNLOADS+"/SCCL FRS/"; Uri collection=MediaStore.Downloads.EXTERNAL_CONTENT_URI; Uri target=null; String[] projection={MediaStore.Downloads._ID}; String selection=MediaStore.Downloads.DISPLAY_NAME+"=? AND "+MediaStore.Downloads.RELATIVE_PATH+"=?";
        try(Cursor c=cr.query(collection,projection,selection,new String[]{fileName,relative},MediaStore.Downloads.DATE_ADDED+" DESC")) { if (c!=null && c.moveToFirst()) target= ContentUris.withAppendedId(collection,c.getLong(0)); }
        if (target==null) {
            ContentValues cv=new ContentValues(); cv.put(MediaStore.Downloads.DISPLAY_NAME,fileName); cv.put(MediaStore.Downloads.MIME_TYPE,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); cv.put(MediaStore.Downloads.RELATIVE_PATH,relative); cv.put(MediaStore.Downloads.IS_PENDING,1); target=cr.insert(collection,cv); if (target==null) throw new Exception("Unable to create daily Excel in Downloads.");
            try(OutputStream os=cr.openOutputStream(target,"w")) { if (os==null) throw new Exception("Unable to write daily Excel."); os.write(data); }
            ContentValues ready=new ContentValues(); ready.put(MediaStore.Downloads.IS_PENDING,0); cr.update(target,ready,null,null);
        } else {
            try(OutputStream os=cr.openOutputStream(target,"rwt")) { if (os==null) throw new Exception("Unable to update daily Excel."); os.write(data); }
        }
    }

    private void renderRecord(Record r) { nameV.setText(value(r.name)); empV.setText(value(r.empId)); lampV.setText(value(r.lampNo)); msnV.setText(value(r.msn)); roleV.setText(value(r.designation)); mobileV.setText(value(r.mobile)); }
    private void renderTodayCount() { countV.setText(db.todayCount(today())+" today"); }
    private void successVibrate() { Vibrator v=getSystemService(Vibrator.class); if (v!=null) v.vibrate(VibrationEffect.createWaveform(new long[]{0,70,45,70},-1)); }
    private void failureVibrate() { Vibrator v=getSystemService(Vibrator.class); if (v!=null) v.vibrate(VibrationEffect.createOneShot(260,VibrationEffect.DEFAULT_AMPLITUDE)); }

    @Override public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if (requestCode==CAMERA_REQ) { if (grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) startCamera(); else new AlertDialog.Builder(this).setTitle("Camera permission required").setMessage("LRDMS needs the camera only to scan employee QR codes. Enable Camera permission to use the app.").setPositiveButton("OK",null).show(); }
    }
    @Override protected void onDestroy() { super.onDestroy(); if (cameraProvider!=null) cameraProvider.unbindAll(); scanner.close(); cameraExecutor.shutdown(); db.close(); }
    private String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date());}
    private String timeNow(){return new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date());}
    private boolean empty(String s){return s==null||s.trim().isEmpty();}
    private String value(String s){return empty(s)?"-":s;}
    private String safe(String s){return s==null||s.trim().isEmpty()?"Unknown error":s;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout l=vertical();l.setPadding(dp(14),dp(14),dp(14),dp(14));l.setBackgroundColor(Color.WHITE);return l;}
    private LinearLayout.LayoutParams cardLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);return p;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView label(String s){TextView t=text(s,11,Color.rgb(70,95,115),true);t.setPadding(0,dp(7),0,dp(2));return t;}
    private TextView field(LinearLayout parent,String name){parent.addView(label(name));TextView v=text("-",16,Color.rgb(23,48,71),true);v.setPadding(0,0,0,dp(7));parent.addView(v);return v;}
    private TextView miniField(LinearLayout parent,String name){LinearLayout box=vertical();box.setPadding(dp(5),0,dp(5),dp(6));TextView l=label(name);TextView v=text("-",15,Color.rgb(23,48,71),true);box.addView(l);box.addView(v);parent.addView(box,new LinearLayout.LayoutParams(0,-2,1));return v;}
}
