package com.media.rtc.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.media.rtc.engine.SessionManager;
import com.media.rtc.engine.CallSession;
import com.media.rtc.R;

import java.util.UUID;

public class CallActivity extends AppCompatActivity implements CallSession.CallSessionCallback {

    public static final String EXTRA_ROOM = "room";
    public static final String EXTRA_TARGET = "targetId";
    public static final String EXTRA_OUT_GOING = "isOutGoing";
    public static final String EXTRA_AUDIO_ONLY = "audioOnly";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isOutgoing;
    private String targetId;
    private String mRoom;
    public boolean isAudioOnly;

    private CallFragment currentFragment;


    public static void openActivity(Context context, String targetId, boolean isOutgoing, boolean isAudioOnly, String room) {
        Intent intent = new Intent(context, CallActivity.class);
        intent.putExtra(CallActivity.EXTRA_ROOM, room);
        intent.putExtra(CallActivity.EXTRA_TARGET, targetId);
        intent.putExtra(CallActivity.EXTRA_OUT_GOING, isOutgoing);
        intent.putExtra(CallActivity.EXTRA_AUDIO_ONLY, isAudioOnly);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStatusBarOrScreenStatus(this);
        setContentView(R.layout.activity_call);

        final Intent intent = getIntent();
        mRoom = intent.getStringExtra(EXTRA_ROOM);
        targetId = intent.getStringExtra(EXTRA_TARGET);
        isOutgoing = intent.getBooleanExtra(EXTRA_OUT_GOING, false);
        isAudioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false);

        init(targetId, isOutgoing, isAudioOnly, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private void init(String targetId, boolean outgoing, boolean audioOnly, boolean isReplace) {
        CallFragment fragment = new CallFragment();
        FragmentManager fragmentManager = getSupportFragmentManager();
        currentFragment = fragment;
        if (isReplace) {
            fragmentManager.beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commit();
        } else {
            fragmentManager.beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commit();
        }
        if (outgoing && !isReplace) {
            String room = UUID.randomUUID().toString() + System.currentTimeMillis();
            boolean result = SessionManager.getInstance().startOutCall(getApplicationContext(), room, targetId, audioOnly);
            if (!result) {
                finish();
            }
        } else {
            boolean result = SessionManager.getInstance().startInCall(this, mRoom, targetId, isAudioOnly);
            if (!result) {
                finish();
                return;
            }
            if (getCurrentSession().isAudioOnly() && !audioOnly) {
                isAudioOnly = getCurrentSession().isAudioOnly();
                fragment.didChangeMode(true);
            }
        }
        if (getCurrentSession() != null) {
            getCurrentSession().setSessionCallback(this);
        }
    }

    public void endCall() {
        SessionManager.getInstance().endCall();
    }

    public CallSession getCurrentSession() {
        return SessionManager.getInstance().getCallSession();
    }

    public boolean isOutgoing() {
        return isOutgoing;
    }

    public void switchAudio() {
        init(targetId, isOutgoing, true, true);
    }

    @Override
    public void didCallEnd(String userId) {
        handler.post(() -> currentFragment.didCallEnd());
    }

    @Override
    public void didChangeState(CallSession.CallState callState) {
        if (callState == CallSession.CallState.Connected) {
            isOutgoing = false;
        }
        handler.post(() -> currentFragment.didChangeState(callState));
    }

    @Override
    public void didChangeMode(boolean var1) {
        handler.post(() -> currentFragment.didChangeMode(var1));
    }

    @Override
    public void didCreateLocalVideoTrack() {
        handler.post(() -> currentFragment.didCreateLocalVideoTrack());
    }

    @Override
    public void didReceiveRemoteVideoTrack(String userId) {
        handler.post(() -> currentFragment.didReceiveRemoteVideoTrack(userId));
    }

    @Override
    public void didUserLeave(String userId) {
        handler.post(() -> currentFragment.didUserLeave(userId));
    }

    @Override
    public void didDisconnected(String userId) {
        handler.post(() -> currentFragment.didDisconnected(userId));
    }

    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        return flags;
    }

    private void setStatusBarOrScreenStatus(Activity activity) {
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        window.getDecorView().setSystemUiVisibility(getSystemUiVisibility());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(layoutParams);
        }

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
    }

}
