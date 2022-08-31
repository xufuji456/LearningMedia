package com.example.ndklearning;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.ndklearning.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Example of a call to a native method
        TextView tv = binding.sampleText;
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