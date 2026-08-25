package com.dynamsoft.dcesample;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class ResultActivity extends AppCompatActivity {
    WebView webView;
    TextView textView;
    Button saveButton;
    Long timeElapsed;
    private HashMap<Integer, HashMap<String, Object>> results;
    private static final int CREATE_FILE = 1;
    private boolean saved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        Intent intent = getIntent();
        results = (HashMap<Integer, HashMap<String, Object>>) intent.getSerializableExtra("results");
        timeElapsed = (Long) intent.getExtras().get(("timeElapsed"));
        textView = findViewById(R.id.textView);
        webView = findViewById(R.id.webView);
        saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createFile();
            }
        });
        //textView.setText(results.size());
        Log.d("DBR", "launched");
        Log.d("DBR", String.valueOf(results.size()));
        showResults();
    }

    @Override
    protected void onDestroy() {
        Log.d("DBR", "ResultActivity destroyed");
        super.onDestroy();
    }

    private int getTotalFrames() {
        HashMap<String, Object> first = results == null ? null : results.get(1);
        if (first == null || first.get("text") == null) {
            return 0;
        }
        String text = (String) first.get("text");
        try {
            return Integer.parseInt(text.split("\\|")[0].split("/")[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private String missingFramesInfo() {
        int totalFrames = getTotalFrames();
        if (totalFrames <= 0 || results == null) {
            return "unknown total";
        }
        List<Integer> missing = new ArrayList<>();
        for (int i = 1; i <= totalFrames; i++) {
            if (!results.containsKey(i)) {
                missing.add(i);
            }
        }
        if (missing.isEmpty()) {
            return null;
        }
        return missing.toString();
    }

    private void showResults() {
        try {
            HashMap<String, Object> data = processResults();
            if (data == null) {
                return;
            }
            String dataURL = (String) data.get("dataURL");
            String speed = (String) data.get("speed");
            String mime = (String) data.get("mime");
            String filename = (String) data.get("filename");
            byte[] bytes = (byte[]) data.get("bytes");
            int sizeKB = bytes.length / 1024;
            // Keep the status line short; putting a huge data URL into a TextView
            // blocks the main thread on layout measurement.
            textView.setText(speed + " (" + sizeKB + " KB, " + mime + ")");
            webView.getSettings().setDefaultTextEncodingName("UTF-8");
            webView.loadData(buildHTML(dataURL, mime, filename, sizeKB, speed), "text/html; charset=UTF-8", null);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to show result: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private HashMap<String, Object> processResults() {
        if (results == null || results.isEmpty()) {
            Toast.makeText(this, "No data received.", Toast.LENGTH_LONG).show();
            return null;
        }
        String missing = missingFramesInfo();
        if (missing != null) {
            Toast.makeText(this, "Incomplete transfer, missing frames: " + missing + ". Please scan again.", Toast.LENGTH_LONG).show();
            return null;
        }
        List<Byte> bytesList = new ArrayList<>();
        String mime = "";
        String filename = "";
        int totalFrames = getTotalFrames();
        for (int i = 1; i <= totalFrames; i++) {
            HashMap<String, Object> resultMap = results.get(i);
            if (resultMap == null) {
                continue;
            }
            String text = (String) resultMap.get("text");
            byte[] bytes = (byte[]) resultMap.get("bytes");
            if (text == null) {
                continue;
            }
            int firstSeparatorIndex = text.indexOf("|");
            int dataStart;
            if (i == 1) {
                //the first one contains 1/N|filename|mime|data
                int secondSeparatorIndex = text.indexOf("|", firstSeparatorIndex + 1);
                int thirdSeparatorIndex = text.indexOf("|", secondSeparatorIndex + 1);
                filename = text.substring(firstSeparatorIndex + 1, secondSeparatorIndex);
                mime = text.substring(secondSeparatorIndex + 1, thirdSeparatorIndex);
                dataStart = thirdSeparatorIndex + 1;
            } else {
                dataStart = firstSeparatorIndex + 1;
            }
            byte[] slice;
            if (bytes != null && dataStart < bytes.length) {
                slice = Arrays.copyOfRange(bytes, dataStart, bytes.length);
            } else {
                slice = new byte[0];
            }
            for (Byte b : slice) {
                bytesList.add(b);
            }
        }
        if (bytesList.isEmpty()) {
            Toast.makeText(this, "Transfer produced no data.", Toast.LENGTH_LONG).show();
            return null;
        }
        byte[] bytes = BytesListAsArray(bytesList);
        // The per-transfer flag sits right after "1/N|" in the first frame: 'Z' = gzip.
        String firstText = (String) results.get(1).get("text");
        String flag = firstText.substring(firstText.indexOf("|") + 1, firstText.indexOf("|") + 2);
        if ("Z".equals(flag)) {
            try {
                GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(bytes));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int len;
                while ((len = gz.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
                gz.close();
                bytes = out.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to decompress transfer: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return null;
            }
        }
        HashMap<String, Object> data = new HashMap<String, Object>();
        // Only build a data URL for images; large binaries would create huge strings
        // that both freeze the text view and bloat the WebView document.
        String dataURL = null;
        if (mime.startsWith("image/")) {
            String base64 = Base64.encodeToString(bytes, Base64.DEFAULT);
            dataURL = "data:" + mime + ";base64," + base64;
        }
        double speed = timeElapsed == null || timeElapsed == 0 ? 0 : 1000.0 * bytes.length / 1024 / timeElapsed;
        String formattedSpeed = String.format("%.2f", speed);
        data.put("bytes", bytes);
        data.put("dataURL", dataURL);
        data.put("mime", mime);
        data.put("speed", formattedSpeed + "KB/s");
        try {
            filename = URLDecoder.decode(filename, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        data.put("filename", filename);
        return data;
    }

    private byte[] BytesListAsArray(List<Byte> data) {
        byte[] bytes = new byte[data.size()];
        int index = 0;
        for (Byte b : data) {
            bytes[index] = b;
            index = index + 1;
        }
        return bytes;
    }

    private String buildHTML(String dataURL, String mime, String filename, int sizeKB, String speed) {
        String head = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">";
        head += "<style>body{font-family:system-ui;margin:16px;text-align:center;}";
        head += "h2{color:#16a34a;font-size:22px;margin:8px 0;}";
        head += "p{color:#555;font-size:15px;margin:4px 0;}";
        head += ".hint{margin-top:24px;font-size:17px;color:#1e3a8a;}</style>";
        head += "</head><body>";
        String body = "<h2>&#10003; Transfer Complete</h2>";
        body += "<p><b>" + escapeHtml(filename) + "</b></p>";
        body += "<p>" + sizeKB + " KB &middot; " + escapeHtml(speed) + "</p>";
        if (dataURL != null && mime.startsWith("image/")) {
            body += "<img style=\"max-width:90%;margin-top:16px;\" src=\"" + dataURL + "\" />";
        } else {
            body += "<p class=\"hint\">Tap Save to export the file.</p>";
        }
        String tail = "</body></html>";
        return head + body + tail;
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }


    public static Bitmap decodeBase64AsBitmap(String string) {
        Bitmap bitmap = null;
        try {
            byte[] bitmapArray = Base64.decode(string, Base64.DEFAULT);
            bitmap = BitmapFactory.decodeByteArray(bitmapArray, 0, bitmapArray.length);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private void createFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, getFilename());
        startActivityForResult(intent, CREATE_FILE);
    }

    private String getFilename() {
        try {
            String text = (String) results.get(1).get("text"); // 1/N|filename|mimetype
            int startIndex = text.indexOf("|") + 1;
            int endIndex = text.indexOf("|", startIndex + 1);
            String filename = null;
            filename = URLDecoder.decode(text.substring(startIndex, endIndex), StandardCharsets.UTF_8.name());
            return filename;
        } catch (Exception e) {
            e.printStackTrace();
            return "file.bin";
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == CREATE_FILE
                && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
            }
            if (uri == null) {
                Toast.makeText(this, "No destination selected.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                HashMap<String, Object> data = processResults();
                if (data == null) {
                    return;
                }
                byte[] bytes = (byte[]) data.get("bytes");
                OutputStream out = getContentResolver().openOutputStream(uri);
                if (out == null) {
                    Toast.makeText(this, "Cannot open the destination.", Toast.LENGTH_SHORT).show();
                    return;
                }
                out.write(bytes, 0, bytes.length);
                out.close();
                saved = true;
                Toast.makeText(this, "Saved " + bytes.length + " bytes.", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            if (saved) {
                finish();
            }
        }
    }
}
