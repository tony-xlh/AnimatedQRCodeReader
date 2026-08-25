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
            textView.setText(speed + " " + dataURL);
            webView.getSettings().setDefaultTextEncodingName("UTF-8");
            webView.loadData(buildHTML(dataURL), "text/html; charset=UTF-8", null);
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
        HashMap<String, Object> data = new HashMap<String, Object>();
        byte[] bytes = BytesListAsArray(bytesList);
        String base64 = Base64.encodeToString(bytes, Base64.DEFAULT);
        String dataURL = "data:" + mime + ";base64," + base64;
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

    private String buildHTML(String dataURL) {
        String head = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"width:100%;\">";
        String body = "";
        if (dataURL.contains("image")) {
            body = "<img style=\"max-width:100%;\" src=\"" + dataURL + "\" >";
        } else if (dataURL.startsWith("data:application/pdf")) {
            body = "<embed style=\"width:100%;height:100%\" type=\"application/pdf\" src=\"" + dataURL + "\" >";
        } else {
            body = "Binary file.";
        }
        String tail = "</body></html>";
        String html = head + body + tail;
        return html;
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
