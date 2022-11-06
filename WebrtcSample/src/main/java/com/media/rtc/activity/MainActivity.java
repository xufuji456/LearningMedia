package com.media.rtc.activity;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.media.rtc.Const;
import com.media.rtc.engine.SessionManager;
import com.media.rtc.R;

import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    private final String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA};

    private EditText editUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, 12345);
        }

        Button btnLogin = findViewById(R.id.btn_login);
        btnLogin.setOnClickListener(this);
        Button btnCall = findViewById(R.id.btn_call);
        btnCall.setOnClickListener(this);
        editUser = findViewById(R.id.edit_user);
    }

    private void onLoginIn() {
        String username = "helloRTC";//editUser.getText().toString().trim();
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, "please input your name", Toast.LENGTH_LONG).show();
            return;
        }

        // 登录信令服务器
        try {
            SessionManager.getInstance().connect(Const.URL_SIGNAL, username, 0);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void onCall() {
        // 获取已登录的用户列表
//        AsyncHttpURLConnection httpRequest = new AsyncHttpURLConnection(Const.URL_USER_LIST,
//                new AsyncHttpURLConnection.AsyncHttpEvents() {
//            @Override
//            public void onHttpError(String errorMessage) {
//                Log.e(TAG, "onHttpError=" + errorMessage);
//            }
//
//            @Override
//            public void onHttpComplete(String response) {
//                Log.i(TAG, "onHttpComplete=" + response);
                // 解析用户列表
//            }
//        });
//        httpRequest.send();

        String target = "helloworld";
        Log.e(TAG, "call target=" + target);
        CallActivity.openActivity(MainActivity.this, target, true,  false, "");
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_login) {
            onLoginIn();
        } else if (view.getId() == R.id.btn_call) {
            onCall();
        }
    }

}
