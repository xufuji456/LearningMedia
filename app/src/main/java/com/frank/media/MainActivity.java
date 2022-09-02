package com.frank.media;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        TextView tv = findViewById(R.id.sample_text);
        MediaJniHelper jniHelper = new MediaJniHelper();
        String strFromJni = jniHelper.stringFromJNI();
        tv.setText(strFromJni);
        Log.i("MainActivity", strFromJni);

        int[] data = {1, 2, 3, 4, 5};
        jniHelper.setIntData(data);

        Button btnReflect = findViewById(R.id.btn_reflect);
        btnReflect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                jniHelper.testReflect();
            }
        });
    }

}