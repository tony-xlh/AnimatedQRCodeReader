package com.dynamsoft.dcesample;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
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
import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
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

import com.dynamsoft.dbr.BarcodeReader;
import com.dynamsoft.dbr.BarcodeReaderException;
import com.dynamsoft.dbr.DBRDLSLicenseVerificationListener;
import com.dynamsoft.dbr.DCESettingParameters;
import com.dynamsoft.dbr.EnumErrorCode;
import com.dynamsoft.dbr.IntermediateResult;
import com.dynamsoft.dbr.IntermediateResultCallback;
import com.dynamsoft.dbr.PublicRuntimeSettings;
import com.dynamsoft.dbr.TextResultCallback;
import com.dynamsoft.dbr.TextResult;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraEnhancerException;
import com.dynamsoft.dce.CameraDLSLicenseVerificationListener;
import com.dynamsoft.dce.Frame;
import com.dynamsoft.dce.CameraListener;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.TorchState;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.dynamsoft.dbr.EnumLocalizationMode.LM_SCAN_DIRECTLY;

public class MainActivity extends AppCompatActivity {

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
    private BarcodeReader reader;
    CameraEnhancer mCameraEnhancer;
    private TextResultCallback mTextResultCallback;
    private IntermediateResultCallback mIntermediateResultCallback;

    RelativeLayout viewWarning;
    TextView tvWarning;
    RelativeLayout viewFetching;
    private SpannableString clickString = new SpannableString("try again");
    private com.dynamsoft.dbr.DMDLSConnectionParameters dbrParameters;
    private com.dynamsoft.dce.DMDLSConnectionParameters dceParameters;

    private final int STATE_NEED_DISCARD = 0;
    private final int STATE_NEED_DECODE = 1;
    private final int DCE_RESULTS = 0x0001;
    private Frame mLastFrame;
    private boolean detectStart = true;
    private int resultNum = 0;
    private int notFilteredNum = 0;
    private int filteredNum = 0;
    private int total = 0;
    private HashMap<Integer,String> results = new HashMap<Integer,String>();
    private int wid;
    private int hgt;
    private JSONObject mJson;
    private int lastFrameId = -1;
    private final String ExpiredError = "The license has expired";
    private boolean bShowing = false;
    private DBRDLSLicenseVerificationListener dbrDLSListener;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        askForStoragePermissions();
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
        try {
            reader = new BarcodeReader();
            dbrParameters = new com.dynamsoft.dbr.DMDLSConnectionParameters();
            // The organization id 200001 here will grant you a public trial license good for 7 days.
            // After that, please visit: https://www.dynamsoft.com/customer/license/trialLicense?product=dbr&utm_source=installer&package=android
            // to request for 30 days extension.
            dbrParameters.organizationID = "200001";
            dbrDLSListener = new DBRDLSLicenseVerificationListener() {
                @Override
                public void DLSLicenseVerificationCallback(boolean b, Exception e) {
                    if (!b) {
                        e.printStackTrace();
                        (MainActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String msg = "Unable to resolve host";
                                String msg1 = "Failed to connect to";
                                String tips = "";
                                viewFetching.setVisibility(View.GONE);
                                if (e.getMessage().contains(msg)) {
                                    tips = "Dynamsoft Barcode Reader is unable to connect to the public Internet to acquire a license. Please connect your device to the Internet or contact support@dynamsoft.com to acquire an offline license.";
                                    showLicTip();
                                } else {
                                    tips = e.getMessage();
                                }
                                if (e instanceof BarcodeReaderException) {
                                    if (((BarcodeReaderException) e).getErrorCode() == EnumErrorCode.DBR_LICENSE_EXPIRED) {
                                        tips = "Please visit: https://www.dynamsoft.com/customer/license/trialLicense?product=dbr&utm_source=installer&package=android to request for 30 days extension.";
                                    }
                                }
                                showExDialog(MainActivity.this, tips);

                            }
                        });
                    } else if (b) {
                        (MainActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                viewFetching.setVisibility(View.GONE);
                                viewWarning.setVisibility(View.GONE);
                            }
                        });
                    }
                }
            };
            reader.initLicenseFromDLS(dbrParameters, dbrDLSListener);
            PublicRuntimeSettings s = reader.getRuntimeSettings();
            s.localizationModes = new int[]{LM_SCAN_DIRECTLY, 0, 0, 0, 0, 0, 0, 0};
            s.deblurLevel = 0;
            reader.updateRuntimeSettings(s);
            reader.setModeArgument("BinarizationModes", 0, "EnableFillBinaryVacancy", "0");
            reader.setModeArgument("BinarizationModes", 0, "BlockSizeX", "71");
            reader.setModeArgument("BinarizationModes", 0, "BlockSizeY", "71");
        } catch (BarcodeReaderException e) {
            e.printStackTrace();
        }
        mTextResultCallback = new TextResultCallback() {
            @Override
            public void textResultCallback(int i, TextResult[] textResults, Object o) {
                if (textResults != null && textResults.length > 0) {
                    resultNum++;
                    if (textResults[0].exception != null && textResults[0].exception.contains(ExpiredError)) {
                        (MainActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showExDialog(MainActivity.this, "Please visit: https://www.dynamsoft.com/customer/license/trialLicense?product=dbr&utm_source=installer&package=android to request for 30 days extension.");
                            }
                        });
                    }
                    for (TextResult textResult:textResults){
                        try {
                            String text = textResult.barcodeText;
                            String meta = text.split(",")[0];
                            int totalOfThisOne = Integer.parseInt(meta.split("/")[1]);
                            if (total != totalOfThisOne && total!=0){
                                total = totalOfThisOne;
                                results.clear();
                                return;
                            }
                            total = totalOfThisOne;
                            int index = Integer.parseInt(meta.split("/")[0]);
                            results.put(index,text);
                            if (results.size()==total){
                                onReadingCompleted();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                }
            }
        };

        mIntermediateResultCallback = new IntermediateResultCallback() {
            @Override
            public void intermediateResultCallback(int i, IntermediateResult[] intermediateResults, Object o) {
            }
        };
        mCameraEnhancer = new CameraEnhancer(MainActivity.this);
        mCameraEnhancer.addCameraView(cameraView);
        dceParameters = new com.dynamsoft.dce.DMDLSConnectionParameters();
        // The organization id 200001 here will grant you a public trial license good for 7 days.
        // After that, please visit: https://www.dynamsoft.com/customer/license/trialLicense?product=dce&utm_source=installer&package=android
        // to request for 30 days extension.
        dceParameters.organizationID = "200001";
        mCameraEnhancer.initLicenseFromDLS(dceParameters, new CameraDLSLicenseVerificationListener() {
            @Override
            public void DLSLicenseVerificationCallback(boolean isSuccess, Exception e) {
                if (!isSuccess) {
                    e.printStackTrace();
                }
            }
        });
        mJson = new JSONObject();
        try {
            mJson.put("graydiffthreshold", 30);//auto zoom
            mJson.put("conversioncountthreshold", 30);//auto zoom
            mJson.put("sensorvalue", 5);//filter by sensor
            mJson.put("sharpnessthreshold", 0.2);//filter by sharpness
            mJson.put("sharpnessthresholdlarge", 0.4);//filter by sharpness
            mJson.put("abssharpnessthreshold", 200);//filter by sharpness
            mJson.put("absgraythreshold", 35);//filter by sharpness
            mJson.put("claritythreshold", 0.1);//focus by sharpness
            mJson.put("ternimatefocusbysharpness", 0.02);//focus by sharpness
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            mCameraEnhancer.updateCameraSetting(mJson);
        } catch (CameraEnhancerException e) {
            e.printStackTrace();
        }
        mJson = null;

        DCESettingParameters dceSettingParameters = new DCESettingParameters();
        dceSettingParameters.cameraInstance = mCameraEnhancer;
        dceSettingParameters.textResultCallback = mTextResultCallback;
        dceSettingParameters.intermediateResultCallback = mIntermediateResultCallback;
        reader.SetCameraEnhancerParam(dceSettingParameters);


        mCameraEnhancer.addCameraListener(new CameraListener() {
            @Override
            public void onPreviewOriginalFrame(Frame frame) {
                if (mLastFrame != null) {
                    int frameState;
                    if (frame.frameId == lastFrameId + 1) {
                        notFilteredNum++;
                        frameState = STATE_NEED_DECODE;//need to decode
                    } else {
                        filteredNum++;
                        frameState = STATE_NEED_DISCARD;//discarded
                    }
                    wid = mLastFrame.width;
                    hgt = mLastFrame.height;
                    DCE_Process(mLastFrame, frameState);
                }
                mLastFrame = frame;
            }


            @Override
            public void onPreviewFilterFrame(Frame frame) {
                lastFrameId = frame.frameId;
            }

            @Override
            public void onPreviewFastFrame(Frame frame) {
            }
        });

        cameraView.addOverlay();

        mCameraEnhancer.enableSensorControl(false);
        mCameraEnhancer.enableAutoZoom(false);
        mCameraEnhancer.enableDCEAutoFocus(true);
        mCameraEnhancer.enableRegularAutoFocus(false);
        mCameraEnhancer.enableAutoFocusOnSharpnessChange(false);
        mCameraEnhancer.enableFrameFilter(true);
        mCameraEnhancer.enableFastMode(false);

        btnFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TorchState torchState = mCameraEnhancer.getTorchCurrentState();
                if (torchState == TorchState.TORCH_STATE_ON) {
                    mCameraEnhancer.setTorchDesiredState(TorchState.TORCH_STATE_OFF);
                    btnFlash.setBackground(getResources().getDrawable(R.drawable.flash_off));
                } else if (torchState == TorchState.TORCH_STATE_OFF) {
                    mCameraEnhancer.setTorchDesiredState(TorchState.TORCH_STATE_ON);
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
                mCameraEnhancer.startScanning();
            }
        });
        btnDCEPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                detectStart = false;
                timerRunning = false;
                mCameraEnhancer.stopScanning();
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
                    mCameraEnhancer.startScanning();
                } else {
                    mCameraEnhancer.stopScanning();
                }
            }
        });
        runTime();
    }

    private void onReadingCompleted(){
        HashMap<Integer,String> clone = (HashMap<Integer, String>) results.clone();
        restart();
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("results", clone);
        startActivity(intent);
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
    public void onResume() {
        try {
            reader.StartCameraEnhancer();
//            mCameraEnhancer.startScanning();
            detectStart = true;
            timerRunning = true;
            startTime = System.currentTimeMillis();
            timerHandler.post(timerRunable);
        } catch (CameraEnhancerException cameraException) {
            cameraException.printStackTrace();
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        timerRunning = false;
        try {
            reader.StopCameraEnhancer();
//            mCameraEnhancer.stopScanning();
            timerHandler.removeCallbacks(timerRunable);
        } catch (CameraEnhancerException cameraException) {
            cameraException.printStackTrace();
        }
        super.onPause();
    }

    private void restart(){
        detectStart = false;
        timerRunning = false;
        ThumbnailDCE.removeAllViews();
        mCameraEnhancer.stopScanning();
        wasTimerRunning = false;
        unit_ms = 0;
        startTime = 0;
        resultNum = 0;
        filteredNum = 0;
        notFilteredNum = 0;
        totalTime = 0;
        meanTime = 0;
        total = 0;
        results.clear();
    }

    private void askForStoragePermissions() {
        String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, perms, 1);
        }
    }

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case DCE_RESULTS:
                    Frame frame = (Frame) msg.obj;
                    if (frame.frameId % 5 == 0) {
                        Bitmap bm = convertYUVtoRGB(frame.data, wid, hgt, MainActivity.this);
                        Matrix matrix = new Matrix();
                        float scale = (float) 200 / (float) wid;
                        matrix.setScale(scale, scale);
                        bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
                        bm = drawBorderOnBitmap(bm, msg.arg1, (float) (bm.getWidth() / 10));
                        ImageView img = new ImageView(MainActivity.this);
                        img.setRotation(90);
                        img.setLayoutParams(new LinearLayout.LayoutParams(viewThumnailDCE.getHeight(), viewThumnailDCE.getHeight()));
                        img.setImageBitmap(bm);
                        ThumbnailDCE.addView(img);
                        if (ThumbnailDCE.getChildCount() > 10) {
                            ThumbnailDCE.removeViewAt(0);
                        }
                        viewThumnailDCE.scrollTo(ThumbnailDCE.getMeasuredWidth(), 0);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private Bitmap convertYUVtoRGB(byte[] yuvData, int width, int height, Context context) {
        RenderScript rs;
        ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
        Type.Builder yuvType, rgbaType;
        Allocation in, out;
        rs = RenderScript.create(context);
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
        yuvType = new Type.Builder(rs, Element.U8(rs)).setX(yuvData.length);
        in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);

        rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height);
        out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);
        in.copyFrom(yuvData);
        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);
        Matrix matrix = new Matrix();
        matrix.setScale(0.5f, 0.5f);
        Bitmap bmpout = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        out.copyTo(bmpout);
        return bmpout;
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

    private void DCE_Process(Frame frame, int frameState) {
        String postfix = "";
        int color;
        if (frameState == STATE_NEED_DISCARD)
            color = Color.parseColor("#ffff4444");
        else
            color = Color.parseColor("#5f5DE55D");

        Message message = handler.obtainMessage();
        message.what = DCE_RESULTS;
        message.obj = frame;
        message.arg1 = color;
        handler.sendMessage(message);
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
        if (msg.contains("Please visit")) {
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
    private long totalTime = 0;
    private long meanTime;

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
                        String rates = num.format((double) resultNum / (double) (notFilteredNum + filteredNum));
                        StringBuilder sb = new StringBuilder();
                        sb.append("total frame number:");
                        sb.append(filteredNum + notFilteredNum);
                        sb.append("\ndiscarded frame number:");
                        sb.append(filteredNum);
                        sb.append( "\nsuccessful number:");
                        sb.append(resultNum);
                        sb.append("\ndecode rate:");
                        sb.append(rates);
                        sb.append("\nprogress:");
                        sb.append(results.keySet().size());
                        sb.append("/");
                        sb.append(total);
                        viewFrameMessage.setText( sb.toString());
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
        reader.initLicenseFromDLS(dbrParameters, dbrDLSListener);
        mCameraEnhancer.initLicenseFromDLS(dceParameters, new CameraDLSLicenseVerificationListener() {
            @Override
            public void DLSLicenseVerificationCallback(boolean b, Exception e) {
                if (!b)
                    e.printStackTrace();
            }
        });
    }
}
