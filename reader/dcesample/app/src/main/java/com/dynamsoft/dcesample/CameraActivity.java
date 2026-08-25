package com.dynamsoft.dcesample;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.ImageData;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.cvr.SimplifiedCaptureVisionSettings;
import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.dynamsoft.dbr.EnumBarcodeFormat;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraEnhancerException;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.EnumEnhancerFeatures;
import com.dynamsoft.dce.VideoFrameListener;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.license.LicenseVerificationListener;

import java.text.NumberFormat;
import java.util.HashMap;

/**
 * Live camera scanning mode. The Dynamsoft Barcode Reader bundle reads QR codes
 * frame by frame through the Camera Enhancer view; each frame is parsed as
 * "index/total|data" and the original file is reassembled when all frames arrive.
 */
public class CameraActivity extends AppCompatActivity {

    RelativeLayout DCELineDone;
    Button btnDCEStart;
    Button btnDCEPause;
    Button btnDCERestart;
    Button btnFlash;
    LinearLayout ThumbnailDCE;
    HorizontalScrollView viewThumnailDCE;
    TextView viewTimer;
    TextView viewFrameMessage;
    CameraView cameraView;
    TextView cameraView2;

    // Public trial license. Get a 30-day trial license from
    // https://www.dynamsoft.com/customer/license/trialLicense?product=dbr
    private static final String LICENSE_KEY = "DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==";
    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private CaptureVisionRouter router;
    private CameraEnhancer mCameraEnhancer;

    RelativeLayout viewWarning;
    TextView tvWarning;
    RelativeLayout viewFetching;
    private SpannableString clickString = new SpannableString("try again");

    private boolean detectStart = true;
    private boolean torchOn = false;
    private int frameIndex = 0;
    private int totalFrames = 0;
    private int successFrames = 0;
    private int total = 0;
    private HashMap<Integer, HashMap<String, Object>> results = new HashMap<>();
    private boolean bShowing = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        askForCameraPermission();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        cameraView = findViewById(R.id.cameraView);
        cameraView2 = findViewById(R.id.cameraView2);
        DCELineDone = findViewById(R.id.line_dce_done);
        btnDCEStart = findViewById(R.id.btn_dce_start);
        btnDCERestart = findViewById(R.id.btn_dce_restart);
        btnDCEPause = findViewById(R.id.btn_dce_pause);
        btnFlash = findViewById(R.id.tv_flash);
        viewFrameMessage = findViewById(R.id.view_frame_message);
        viewTimer = findViewById(R.id.view_timer);
        viewThumnailDCE = findViewById(R.id.view_thumbnail_dce);
        ThumbnailDCE = findViewById(R.id.thumbnail_dce);
        viewWarning = findViewById(R.id.view_warning_tip);
        viewFetching = findViewById(R.id.view_fetching_tip);
        tvWarning = findViewById(R.id.tv_licenseWarning);
        clickString.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View view) {
                refetchLicense();
            }
        }, 0, clickString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        clickString.setSpan(new ForegroundColorSpan(Color.parseColor("#FFFE8E14")), 0, clickString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        viewFetching.setVisibility(View.VISIBLE);
        initLicense();
        initCameraAndRouter();

        btnFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (torchOn) {
                    mCameraEnhancer.turnOffTorch();
                    torchOn = false;
                    btnFlash.setBackground(getResources().getDrawable(R.drawable.flash_off));
                } else {
                    mCameraEnhancer.turnOnTorch();
                    torchOn = true;
                    btnFlash.setBackground(getResources().getDrawable(R.drawable.flash_on));
                }
            }
        });

        btnDCEStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                detectStart = true;
                timerRunning = true;
                if (!wasTimerRunning)
                    startTime = System.currentTimeMillis();
                wasTimerRunning = true;
                startCapturing();
            }
        });
        btnDCEPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                detectStart = false;
                timerRunning = false;
                stopCapturing();
            }
        });
        btnDCERestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restart();
            }
        });

        cameraView2.setOnTouchListener(new DoubleTouchListener() {
            @Override
            public void onDoubleTap(View v) {
                detectStart = !detectStart;
                timerRunning = detectStart;
                if (!wasTimerRunning)
                    startTime = System.currentTimeMillis();
                wasTimerRunning = true;
                if (detectStart) {
                    startCapturing();
                } else {
                    stopCapturing();
                }
            }
        });
        runTime();
    }

    private void initLicense() {
        LicenseManager.initLicense(LICENSE_KEY, new LicenseVerificationListener() {
            @Override
            public void onLicenseVerified(boolean isSuccessful, Exception e) {
                if (isSuccessful) {
                    (CameraActivity.this).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            viewFetching.setVisibility(View.GONE);
                            viewWarning.setVisibility(View.GONE);
                        }
                    });
                } else {
                    e.printStackTrace();
                    (CameraActivity.this).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String msg = "";
                            if (e != null && e.getMessage() != null) {
                                msg = e.getMessage();
                            }
                            viewFetching.setVisibility(View.GONE);
                            if (msg.contains("Unable to resolve host") || msg.contains("Failed to connect")) {
                                showLicTip();
                            } else {
                                showExDialog(CameraActivity.this, msg);
                            }
                        }
                    });
                }
            }
        });
    }

    private void initCameraAndRouter() {
        mCameraEnhancer = new CameraEnhancer(cameraView, this);
        try {
            mCameraEnhancer.enableEnhancedFeatures(EnumEnhancerFeatures.EF_ENHANCED_FOCUS | EnumEnhancerFeatures.EF_FRAME_FILTER);
        } catch (CameraEnhancerException e) {
            e.printStackTrace();
        }
        mCameraEnhancer.addListener(new VideoFrameListener() {
            @Override
            public void onFrameOutput(final ImageData data, long timeStamp) {
                frameIndex++;
                (CameraActivity.this).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        totalFrames = frameIndex;
                        if (frameIndex % 5 == 0) {
                            addThumbnail(data);
                        }
                    }
                });
            }
        });
        router = new CaptureVisionRouter(this);
        try {
            router.setInput(mCameraEnhancer);
            SimplifiedCaptureVisionSettings settings = router.getSimplifiedSettings(EnumPresetTemplate.PT_READ_BARCODES);
            settings.barcodeSettings.barcodeFormatIds = EnumBarcodeFormat.BF_QR_CODE;
            router.updateSettings(EnumPresetTemplate.PT_READ_BARCODES, settings);
        } catch (CaptureVisionRouterException e) {
            e.printStackTrace();
        }
        router.addResultReceiver(new CapturedResultReceiver() {
            @Override
            public void onDecodedBarcodesReceived(DecodedBarcodesResult result) {
                if (result == null || result.getItems() == null || result.getItems().length == 0) {
                    return;
                }
                (CameraActivity.this).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        successFrames++;
                        for (BarcodeResultItem item : result.getItems()) {
                            processRead(item);
                        }
                    }
                });
            }

            @Override
            public void onCapturedResultReceived(CapturedResult result) {
                if (result != null && result.getErrorCode() != 0) {
                    String msg = result.getErrorMessage();
                    Log.e("DBR", "CapturedResult error: " + msg);
                    if (msg != null && msg.toLowerCase().contains("license")) {
                        (CameraActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showExDialog(CameraActivity.this, "Please visit: https://www.dynamsoft.com/customer/license/trialLicense?product=dbr to request for 30 days extension.");
                            }
                        });
                    }
                }
            }
        });
    }

    private void startCapturing() {
        mCameraEnhancer.open();
        router.startCapturing(EnumPresetTemplate.PT_READ_BARCODES, new CompletionListener() {
            @Override
            public void onSuccess() {
                Log.d("DBR", "capturing started");
            }

            @Override
            public void onFailure(int i, String s) {
                Log.e("DBR", "capturing failed: " + s);
            }
        });
    }

    private void stopCapturing() {
        router.stopCapturing();
        mCameraEnhancer.close();
    }

    private void addThumbnail(ImageData data) {
        try {
            Bitmap bm = data.toBitmap();
            Matrix matrix = new Matrix();
            float scale = (float) 200 / (float) data.width;
            matrix.setScale(scale, scale);
            Bitmap scaled = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
            scaled = drawBorderOnBitmap(scaled, Color.parseColor("#5f5DE55D"), (float) scaled.getWidth() / 10);
            ImageView img = new ImageView(CameraActivity.this);
            img.setRotation(90);
            img.setLayoutParams(new LinearLayout.LayoutParams(viewThumnailDCE.getHeight(), viewThumnailDCE.getHeight()));
            img.setImageBitmap(scaled);
            ThumbnailDCE.addView(img);
            if (ThumbnailDCE.getChildCount() > 10) {
                ThumbnailDCE.removeViewAt(0);
            }
            viewThumnailDCE.scrollTo(ThumbnailDCE.getMeasuredWidth(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onReadingCompleted() {
        Log.d("DBR", "transfer completed");
        Toast.makeText(this, "Transfer complete", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("timeElapsed", System.currentTimeMillis() - startTime);
        HashMap<Integer, Object> clone = (HashMap<Integer, Object>) results.clone();
        restart();
        intent.putExtra("results", clone);
        startActivity(intent);
        // Do not return to the stopped camera screen; finish so the user
        // lands back on the home screen.
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_contact) {
            openUrl();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED && detectStart) {
            startCapturing();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (detectStart && hasCameraPermission()) {
            startCapturing();
        }
        timerHandler.post(timerRunable);
    }

    @Override
    public void onPause() {
        timerRunning = false;
        stopCapturing();
        timerHandler.removeCallbacks(timerRunable);
        super.onPause();
    }

    private boolean hasCameraPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void restart() {
        detectStart = false;
        timerRunning = false;
        ThumbnailDCE.removeAllViews();
        stopCapturing();
        wasTimerRunning = false;
        unit_ms = 0;
        startTime = 0;
        frameIndex = 0;
        totalFrames = 0;
        successFrames = 0;
        total = 0;
        results.clear();
    }

    private void askForCameraPermission() {
        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void processRead(BarcodeResultItem item) {
        try {
            String text = item.getText();
            String meta = text.substring(0, text.indexOf("|"));
            int totalOfThisOne = Integer.parseInt(meta.split("/")[1]);
            if (total != totalOfThisOne && total != 0) {
                total = totalOfThisOne;
                results.clear();
                return;
            }
            total = totalOfThisOne;
            int index = Integer.parseInt(meta.split("/")[0]);
            HashMap<String, Object> resultMap = new HashMap<>();
            resultMap.put("text", text);
            resultMap.put("bytes", item.getBytes());
            results.put(index, resultMap);
            if (results.size() == total) {
                onReadingCompleted();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Bitmap drawBorderOnBitmap(Bitmap bitmap, int borderColor, float StrokeWidth) {
        if (!bitmap.isMutable())
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(StrokeWidth);
        paint.setColor(borderColor);
        paint.setAntiAlias(true);
        Path path = new Path();
        path.reset();
        path.addRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), Path.Direction.CCW);
        path.close();
        canvas.drawPath(path, paint);
        return bitmap;
    }

    public abstract static class DoubleTouchListener implements View.OnTouchListener {
        private static final long DOUBLE_TIME = 500;
        private static long lastClickTime = 0;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                long currentTimeMillis = System.currentTimeMillis();
                if (currentTimeMillis - lastClickTime < DOUBLE_TIME) {
                    onDoubleTap(v);
                    lastClickTime = 0;
                } else
                    lastClickTime = currentTimeMillis;
            }
            return false;
        }

        public abstract void onDoubleTap(View v);
    }

    void showExDialog(Context context, String msg) {
        if (bShowing)
            return;
        bShowing = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        bShowing = false;
                    }
                });
        if (msg == null || msg.contains("Please visit")) {
            builder.setTitle("The license has expired.")
                    .setPositiveButton("Ok", null);
            TextView tv = new TextView(context);
            tv.setText(R.string.visit);
            tv.setLineSpacing(8f, 1f);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
            tv.setPadding(100, 50, 100, 0);
            tv.setMovementMethod(LinkMovementMethod.getInstance());
            builder.setView(tv);
            builder.show();
        } else {
            builder.setTitle("Exception")
                    .setMessage(msg)
                    .setPositiveButton("Ok", null)
                    .show();
        }
    }

    private int unit_ms = 0;
    private boolean timerRunning = true;
    private boolean wasTimerRunning = false;
    private long lastTime;
    private long startTime = 0;
    private Handler timerHandler;
    private Runnable timerRunable;

    private void runTime() {
        timerHandler = new Handler();
        timerHandler.post(timerRunable = new Runnable() {
                    @Override
                    public void run() {
                        long t = System.currentTimeMillis();
                        long duringTime = 0;
                        if (unit_ms == 0 || startTime > lastTime)
                            duringTime = t - startTime;
                        else
                            duringTime = t - lastTime;
                        if (timerRunning) {
                            unit_ms += duringTime;
                        }
                        lastTime = t;
                        final TextView textView = findViewById(R.id.view_timer);
                        int seconds = unit_ms / 1000 % 60;
                        int minute = unit_ms / 60000;
                        String time = String.format("%02d:%02d.%02d", minute, seconds, unit_ms / 10 % 100);
                        textView.setText(time);

                        NumberFormat num = NumberFormat.getPercentInstance();
                        String rates = totalFrames == 0 ? "0%" : num.format((double) successFrames / (double) totalFrames);
                        StringBuilder sb = new StringBuilder();
                        sb.append("total frame number:");
                        sb.append(totalFrames);
                        sb.append("\nsuccessful number:");
                        sb.append(successFrames);
                        sb.append("\ndecode rate:");
                        sb.append(rates);
                        sb.append("\nprogress:");
                        sb.append(results.keySet().size());
                        sb.append("/");
                        sb.append(total);
                        viewFrameMessage.setText(sb.toString());
                        t = System.currentTimeMillis() - t;
                        timerHandler.postDelayed(this, 50 - t);
                    }
                }
        );
    }

    private void openUrl() {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.VIEW");
        Uri contentUrl = Uri.parse("https://www.dynamsoft.com/company/contact/");
        intent.setData(contentUrl);
        startActivity(intent);
    }

    private void showLicTip() {
        viewFetching.setVisibility(View.GONE);
        viewWarning.setVisibility(View.VISIBLE);
        tvWarning.setMovementMethod(LinkMovementMethod.getInstance());
        tvWarning.setText("License activation time out. Please check your network and ");
        tvWarning.append(clickString);
        tvWarning.append(".");
        tvWarning.setLongClickable(false);
    }

    private void refetchLicense() {
        viewWarning.setVisibility(View.GONE);
        viewFetching.setVisibility(View.VISIBLE);
        LicenseManager.initLicense(LICENSE_KEY, new LicenseVerificationListener() {
            @Override
            public void onLicenseVerified(boolean b, Exception e) {
                if (b) {
                    (CameraActivity.this).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            viewFetching.setVisibility(View.GONE);
                            viewWarning.setVisibility(View.GONE);
                        }
                    });
                } else {
                    e.printStackTrace();
                }
            }
        });
    }
}
