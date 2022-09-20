package com.frank.living;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.frank.living.camera.Camera2Helper;
import com.frank.living.handler.ConnectionReceiver;
import com.frank.living.handler.OrientationHandler;
import com.frank.living.listener.LiveStateChangeListener;
import com.frank.living.listener.OnNetworkChangeListener;
import com.frank.living.param.AudioParam;
import com.frank.living.param.VideoParam;

public class MainActivity extends AppCompatActivity implements LiveStateChangeListener,
        OnNetworkChangeListener {

    private final static String TAG = MainActivity.class.getSimpleName();
    private final static int MSG_ERROR = 12345;

    private View liveView;
    private LivePusher livePusher;
    private boolean isPushing = false;
    private ConnectionReceiver connectionReceiver;
    private OrientationHandler orientationHandler;

    private final String[] permissions = new String[] {
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    };

    private final static String LIVE_URL = "rtmp://192.168.31.129/live/stream";

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_ERROR) {
                String errMsg = (String )msg.obj;
                if (!TextUtils.isEmpty(errMsg)) {
                    Toast.makeText(MainActivity.this, errMsg, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(permissions[0]) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(permissions[1]) == PackageManager.PERMISSION_GRANTED) {
                initPusher();
            } else {
                requestPermissions(permissions, 12345);
            }
        } else {
            initPusher();
        }

        registerBroadcast(this);
        orientationHandler = new OrientationHandler(this);
        orientationHandler.enable();
        orientationHandler.setOnOrientationListener(new OrientationHandler.OnOrientationListener() {
            @Override
            public void onOrientation(int orientation) {
                int previewDegree = (orientation + 90) % 360;
                livePusher.setPreviewDegree(previewDegree);
            }
        });
    }

    private void initView() {
        liveView = findViewById(R.id.view_camera);
        ToggleButton btnLive = findViewById(R.id.btn_live);
        btnLive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                 if (isChecked) {
                    livePusher.startPush(LIVE_URL, MainActivity.this);
                    isPushing = true;
                } else {
                    livePusher.stopPush();
                    isPushing = false;
                }
            }
        });
    }

    private void initPusher() {
        int width = 640;
        int height = 480;
        int videoBitRate = 800000;
        int videoFrameRate = 10;
        VideoParam videoParam = new VideoParam(width, height,
                Integer.parseInt(Camera2Helper.CAMERA_ID_BACK), videoBitRate, videoFrameRate);
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int numChannels = 2;
        AudioParam audioParam = new AudioParam(sampleRate, channelConfig, audioFormat, numChannels);
        livePusher = new LivePusher(this, videoParam, audioParam, liveView);
    }

    private void registerBroadcast(OnNetworkChangeListener networkChangeListener) {
        connectionReceiver = new ConnectionReceiver(networkChangeListener);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionReceiver, intentFilter);
    }

    @Override
    public void onError(String msg) {
        Log.e(TAG, "errMsg=$msg");
        mHandler.obtainMessage(MSG_ERROR, msg).sendToTarget();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        orientationHandler.disable();
        if (livePusher != null) {
            if (isPushing) {
                isPushing = false;
                livePusher.stopPush();
            }
            livePusher.release();
        }
        if (connectionReceiver != null) {
            unregisterReceiver(connectionReceiver);
        }
    }

    @Override
    public void onNetworkChange() {
        Toast.makeText(this, "network is not available", Toast.LENGTH_SHORT).show();
        if (livePusher != null && isPushing) {
            livePusher.stopPush();
            isPushing = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        initPusher();
    }

}