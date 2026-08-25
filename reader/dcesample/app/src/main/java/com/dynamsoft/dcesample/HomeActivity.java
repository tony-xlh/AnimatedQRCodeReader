package com.dynamsoft.dcesample;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.VideoView;

import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.license.LicenseVerificationListener;
import com.dynamsoft.cvr.CapturedResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;

/**
 * Home screen of the demo. It plays the embedded animated QR code video (sender side)
 * and offers two actions:
 *  - Simulate Receive: decode the video frames locally with the Dynamsoft Barcode
 *    Reader bundle and reassemble the original file (no camera involved).
 *  - Open Camera: live scanning mode.
 */
public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "DBR";
    // Public trial license. Get a 30-day trial license from
    // https://www.dynamsoft.com/customer/license/trialLicense?product=dbr
    private static final String LICENSE_KEY = "DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==";
    private static final String VIDEO_ASSET = "animated_payload.mp4";
    private static final int FRAME_INTERVAL_MS = 200; // matches the generator interval
    private static final int FPS = 1000 / FRAME_INTERVAL_MS;

    private VideoView animVideo;
    private TextView animStatusView;
    private TextView simStatsView;
    private Button btnSimulate;
    private Button btnCamera;

    private CaptureVisionRouter router;
    private File videoFile;

    private boolean simulating = false;
    private int simSuccessFrames = 0;
    private long simStartTime = 0;
    private int total = 0;
    private HashMap<Integer, HashMap<String, Object>> results = new HashMap<>();
    private volatile int videoFrameCount = -1;

    private MediaMetadataRetriever openVideoRetriever() {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(videoFile.getAbsolutePath());
        return mmr;
    }

    private int getVideoFrameCount() {
        if (videoFrameCount > 0) {
            return videoFrameCount;
        }
        try {
            MediaMetadataRetriever mmr = openVideoRetriever();
            String s = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
            mmr.release();
            videoFrameCount = s == null ? 0 : Integer.parseInt(s);
        } catch (Exception e) {
            videoFrameCount = 0;
        }
        return videoFrameCount;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        animVideo = findViewById(R.id.anim_video);
        animStatusView = findViewById(R.id.anim_status);
        simStatsView = findViewById(R.id.sim_stats);
        btnSimulate = findViewById(R.id.btn_simulate);
        btnCamera = findViewById(R.id.btn_camera);

        animVideo.setKeepScreenOn(true);
        copyVideoToFilesDir(new Callback<File>() {
            @Override
            public void onResult(File file) {
                videoFile = file;
                animVideo.setVideoPath(file.getAbsolutePath());
                animVideo.setOnPreparedListener(mp -> {
                    mp.setLooping(true);
                    animVideo.start();
                });
            }
        });
        animVideo.setOnInfoListener((mp, what, extra) -> {
            // refresh frame progress periodically for the status line
            refreshAnimStatus();
            return false;
        });

        initLicense();
        router = new CaptureVisionRouter(this);

        btnSimulate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!simulating) {
                    startSimulation();
                }
            }
        });
        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, CameraActivity.class));
            }
        });

        if (getIntent() != null && getIntent().getBooleanExtra("simulate", false)) {
            animVideo.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!simulating) {
                        startSimulation();
                    }
                }
            }, 3000);
        }
    }

    private void initLicense() {
        LicenseManager.initLicense(LICENSE_KEY, new LicenseVerificationListener() {
            @Override
            public void onLicenseVerified(boolean isSuccessful, Exception e) {
                if (isSuccessful) {
                    Log.d(TAG, "License verified");
                } else {
                    Log.e(TAG, "License failed: " + (e == null ? "" : e.getMessage()));
                }
            }
        });
    }

    /**
     * Copies the bundled video to the app files directory where both the VideoView
     * and MediaMetadataRetriever can open it with a plain file path.
     */
    private void copyVideoToFilesDir(final Callback<File> cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File dst = new File(getFilesDir(), VIDEO_ASSET);
                    InputStream in = getAssets().open(VIDEO_ASSET);
                    int assetLength = in.available();
                    in.close();
                    if (!dst.exists() || dst.length() != assetLength) {
                        in = getAssets().open(VIDEO_ASSET);
                        FileOutputStream out = new FileOutputStream(dst);
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) != -1) {
                            out.write(buf, 0, len);
                        }
                        out.close();
                        in.close();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            cb.onResult(dst);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private interface Callback<T> {
        void onResult(T t);
    }

    private void refreshAnimStatus() {
        int totalFrames = getVideoFrameCount();
        if (totalFrames <= 0) {
            return;
        }
        int currentFrame = Math.min(totalFrames, (int) ((animVideo.getCurrentPosition() / FRAME_INTERVAL_MS)) + 1);
        animStatusView.setText(currentFrame + "/" + totalFrames);
    }

    private void startSimulation() {
        simulating = true;
        btnSimulate.setEnabled(false);
        btnSimulate.setText("Simulating...");
        results.clear();
        total = 0;
        simSuccessFrames = 0;
        simStartTime = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MediaMetadataRetriever mmr = openVideoRetriever();
                    String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    String frameCountStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
                    long durationMs = durationStr == null ? 0 : Long.parseLong(durationStr);
                    int frameCount = frameCountStr == null ? (int) (durationMs / FRAME_INTERVAL_MS) : Integer.parseInt(frameCountStr);
                    Log.d(TAG, "video duration=" + durationMs + "ms frameCount=" + frameCount);
                    for (int i = 0; i < frameCount; i++) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }
                        final int frameIndex = i;
                        Bitmap bmp = getFrameBitmap(mmr, i, durationMs);
                        if (bmp == null) {
                            Log.w(TAG, "frame " + i + " extract failed");
                            continue;
                        }
                        CapturedResult result = router.capture(bmp, EnumPresetTemplate.PT_READ_BARCODES);
                        DecodedBarcodesResult barcodes = result.getDecodedBarcodesResult();
                        if (barcodes != null && barcodes.getItems() != null) {
                            for (final BarcodeResultItem item : barcodes.getItems()) {
                                Log.d(TAG, "sim frame " + (frameIndex + 1) + " -> " + item.getText().substring(0, Math.min(40, item.getText().length())));
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        simSuccessFrames++;
                                        processRead(item);
                                        updateSimStats();
                                    }
                                });
                            }
                        }
                    }
                    mmr.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        simulating = false;
                        btnSimulate.setEnabled(true);
                        btnSimulate.setText("Simulate Receive");
                    }
                });
            }
        }).start();
    }

    private Bitmap getFrameBitmap(MediaMetadataRetriever mmr, int index, long durationMs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // exact frame-by-frame extraction (the video is encoded with a GOP of 1,
            // but getFrameAtIndex works regardless of keyframe layout)
            Bitmap bmp = mmr.getFrameAtIndex(index);
            if (bmp != null) {
                return bmp;
            }
        }
        long timeUs = (long) (index * FRAME_INTERVAL_MS) * 1000L;
        Bitmap bmp = mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        if (bmp == null) {
            bmp = mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
        }
        return bmp;
    }

    private void updateSimStats() {
        long elapsed = System.currentTimeMillis() - simStartTime;
        simStatsView.setText("elapsed: " + elapsed + "ms\n"
                + "successful frames: " + simSuccessFrames + "\n"
                + "progress: " + results.size() + "/" + total);
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

    private void onReadingCompleted() {
        Log.d("DBR", "HomeActivity onReadingCompleted total=" + total + " results=" + results.size());
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("timeElapsed", System.currentTimeMillis() - simStartTime);
        HashMap<Integer, Object> clone = (HashMap<Integer, Object>) results.clone();
        results.clear();
        total = 0;
        intent.putExtra("results", clone);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (animVideo != null && videoFile != null && !animVideo.isPlaying()) {
            animVideo.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (animVideo != null && animVideo.isPlaying()) {
            animVideo.pause();
        }
    }
}
