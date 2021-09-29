package com.dynamsoft.dcesample;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.dynamsoft.dbr.TextResult;
import com.dynamsoft.dce.CameraView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
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
    private HashMap<Integer, HashMap<String,Object>> results;
    private static final int CREATE_FILE = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        Intent intent = getIntent();
        results = (HashMap<Integer, HashMap<String,Object>>) intent.getSerializableExtra("results");
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

    private void showResults(){
        HashMap<String,Object> data = processResults();
        String dataURL = (String) data.get("dataURL");
        String speed = (String) data.get("speed");
        textView.setText(speed +" "+ dataURL);
        //webView.loadData(buildHTML(dataURL,data.get("base64")), "text/html", "UTF-8");
        webView.getSettings().setDefaultTextEncodingName("UTF-8");
        webView.loadData(buildHTML(dataURL), "text/html; charset=UTF-8", null);
    }

    private HashMap<String,Object> processResults(){
        List<Byte> bytesList = new ArrayList<Byte>();
        String mime = "";
        String filename = "";
        for (int i=0;i<results.size();i++){
            int index = i+1;
            HashMap<String,Object> resultMap = results.get(index);
            String text = (String) resultMap.get("text");
            byte[] bytes = (byte[]) resultMap.get("bytes");
            String data = "";
            if (index == 1){
                //the first one contains 1/10|filename|image/jpeg|
                int firstSeparatorIndex = text.indexOf("|");
                int secondSeparatorIndex = text.indexOf("|",firstSeparatorIndex+1);
                int dataStart = text.indexOf("|",secondSeparatorIndex+1)+1;
                filename = text.substring(firstSeparatorIndex,secondSeparatorIndex);
                mime = text.substring(secondSeparatorIndex+1,dataStart-1);
                byte[] slice = Arrays.copyOfRange(bytes, dataStart, bytes.length);
                for (Byte b:slice){
                    bytesList.add(b);
                }
            }else{
                int dataStart = text.indexOf("|")+1;
                byte[] slice = Arrays.copyOfRange(bytes, dataStart, bytes.length);
                for (Byte b:slice){
                    bytesList.add(b);
                }
            }

        }
        HashMap<String,Object> data = new HashMap<String, Object>();
        byte[] bytes = BytesListAsArray(bytesList);
        String base64 = Base64.encodeToString(bytes,Base64.DEFAULT);
        String dataURL= "data:"+mime+";base64,"+base64;
        double speed = 1000.0*bytes.length/1024/timeElapsed;
        String formattedSpeed = String.format("%.2f",speed);
        data.put("bytes",bytes);
        data.put("dataURL",dataURL);
        data.put("mime",mime);
        data.put("speed",formattedSpeed+"KB/s");
        try {
            filename = URLDecoder.decode(filename, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        data.put("filename",filename);
        return data;
    }

    private byte[] BytesListAsArray(List<Byte> data){
        byte[] bytes = new byte[data.size()];
        int index=0;
        for (Byte b:data){
            bytes[index]=b;
            index=index+1;
        }
        return bytes;
    }

    private String buildHTML(String dataURL){
        String head = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"width:100%;\">";
        String body = "";
        if (dataURL.contains("image")){
            body = "<img style=\"max-width:100%;\" src=\""+dataURL+"\" >";
        }else{
            body = "Binary file.";
        }
        String tail = "</body></html>";
        String html = head+body+tail;
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

    private String getFilename(){
        String text = (String) results.get(1).get("text"); // 1/10|filename|mimetype
        int startIndex = text.indexOf("|")+1;
        int endIndex = text.indexOf("|",startIndex+1);
        String filename = null;
        try {
            filename = URLDecoder.decode(text.substring(startIndex,endIndex), StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return filename;
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
                // Perform operations on the document using its URI.
                HashMap<String, Object> data = processResults();
                byte[] bytes = (byte[]) data.get("bytes");

                try {
                    OutputStream out = getContentResolver().openOutputStream(uri);
                    out.write(bytes, 0, bytes.length);
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}