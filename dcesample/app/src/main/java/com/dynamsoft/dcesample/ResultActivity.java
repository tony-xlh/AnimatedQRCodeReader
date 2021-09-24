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

import com.dynamsoft.dce.CameraView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

public class ResultActivity extends AppCompatActivity {
    WebView webView;
    TextView textView;
    Button saveButton;
    private HashMap<Integer,String> results;
    private static final int CREATE_FILE = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        Intent intent = getIntent();
        results = (HashMap<Integer, String>) intent.getSerializableExtra("results");
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
        HashMap<String,String> data = processResults();
        String dataURL = data.get("meta")+","+data.get("base64");
        textView.setText(dataURL);
        webView.loadData(buildHTML(dataURL,data.get("base64")), "text/html", "UTF-8");
    }

    private HashMap<String,String> processResults(){
        StringBuilder sb = new StringBuilder();
        String meta = "";
        for (int i=0;i<results.size();i++){
            int index = i+1;
            String result = results.get(index);
            String data = "";
            if (index == 1){
                meta = result.split(",")[1]; //the first one contains data:image/jpeg;base64,
                data = result.split(",")[2];
            }else{
                data = result.split(",")[1];
            }
            sb.append(data);
        }
        String base64 = sb.toString();
        String mime = meta.substring(meta.indexOf(":")+1,meta.indexOf(";"));
        HashMap<String,String> data = new HashMap<String,String>();
        data.put("base64",base64);
        data.put("meta",meta);
        data.put("mime",mime);
        Log.d("DBR", mime);
        Log.d("DBR", base64);
        return data;
    }

    private String buildHTML(String dataURL,String base64){
        String head = "<!DOCTYPE html><html><body style=\"width:100%;\">";
        String body = "";
        if (base64.contains("image")){
            body = "<img style=\"max-width:100%;\" src=\""+dataURL+"\" >";
        } else if (base64.contains("text")){
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            String s = new String(bytes);
            body = "<pre>"+s+"</pre>";
        }else{
            body = "Binary file.";
        }
        String tail = "</body></html>";
        String html = head+body+tail;
        Log.d("DBR",html);
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
        startActivityForResult(intent, CREATE_FILE);
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
                HashMap<String, String> data = processResults();
                byte[] bytes = Base64.decode(data.get("base64"), Base64.DEFAULT);

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