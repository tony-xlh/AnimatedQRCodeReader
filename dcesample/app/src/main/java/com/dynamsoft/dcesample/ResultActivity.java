package com.dynamsoft.dcesample;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.dynamsoft.dce.CameraView;

import java.util.HashMap;

public class ResultActivity extends AppCompatActivity {
    ImageView imageView;
    TextView textView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        Intent intent = getIntent();
        HashMap<Integer,String> results = (HashMap<Integer, String>) intent.getSerializableExtra("results");
        textView = findViewById(R.id.textView);
        imageView = findViewById(R.id.imageView);
        //textView.setText(results.size());
        Log.d("DBR", "launched");
        Log.d("DBR", String.valueOf(results.size()));
        showResults(results);
    }

    private void showResults(HashMap<Integer,String> results){
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
        Log.d("DBR", base64);
        textView.setText(meta+","+base64);
        Bitmap bitmap = decodeBase64AsBitmap(base64);
        if (bitmap!=null){
            imageView.setImageBitmap(bitmap);
        }
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
}