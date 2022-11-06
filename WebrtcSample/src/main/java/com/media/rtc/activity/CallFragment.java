package com.media.rtc.activity;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.media.rtc.engine.CallSession;
import com.media.rtc.R;

import org.webrtc.SurfaceViewRenderer;

public class CallFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = CallFragment.class.getSimpleName();

    private TextView txtTip;
    private TextView txtStatus;

    private View layoutOutgoing;
    private View layoutIncoming;
    private View layoutConnected;

    private FrameLayout pipRenderer;
    private FrameLayout fullscreenRenderer;
    private LinearLayout layoutInvite;

    private Chronometer txtDuration;
    private SurfaceViewRenderer localSurfaceView;
    private SurfaceViewRenderer remoteSurfaceView;

    private boolean isOutgoing = false;

    public static final long OUTGOING_WAITING_TIME = 30 * 1000;

    private CallActivity callActivity;
    protected CallSession.CallState currentState;

    private final Handler mHandler = new Handler();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_video, container, false);
        initView(view);
        init();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (txtDuration != null)
            txtDuration.stop();
        fullscreenRenderer.removeAllViews();
        pipRenderer.removeAllViews();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(waitingRunnable);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        callActivity = (CallActivity) getActivity();
        if (callActivity != null) {
            isOutgoing = callActivity.isOutgoing();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callActivity = null;
    }

    private void initView(View view) {
        txtTip = view.findViewById(R.id.txt_tip);
        txtDuration = view.findViewById(R.id.durationTextView);
        ImageView imgOutgoingHangup = view.findViewById(R.id.outgoingHangupImageView);
        ImageView imgIncomingHangup = view.findViewById(R.id.img_hangup_income);
        ImageView imgAccept = view.findViewById(R.id.img_accept_income);
        txtStatus = view.findViewById(R.id.txt_status);
        layoutOutgoing = view.findViewById(R.id.layout_outgoing);
        layoutIncoming = view.findViewById(R.id.layout_incoming);
        layoutConnected = view.findViewById(R.id.layout_connected);

        txtDuration.setVisibility(View.GONE);

        if (isOutgoing) {
            mHandler.postDelayed(waitingRunnable,OUTGOING_WAITING_TIME);
        }

        fullscreenRenderer = view.findViewById(R.id.layout_fullscreen);
        pipRenderer = view.findViewById(R.id.layout_pip);
        layoutInvite = view.findViewById(R.id.layout_invite);
        ImageView connectedAudioOnlyImageView = view.findViewById(R.id.connectedAudioOnlyImageView);
        ImageView connectedHangupImageView = view.findViewById(R.id.connectedHangupImageView);
        ImageView switchCameraImageView = view.findViewById(R.id.switchCameraImageView);
        imgOutgoingHangup.setOnClickListener(this);
        imgIncomingHangup.setOnClickListener(this);
        connectedHangupImageView.setOnClickListener(this);
        imgAccept.setOnClickListener(this);
        switchCameraImageView.setOnClickListener(this);
        pipRenderer.setOnClickListener(this);
        connectedAudioOnlyImageView.setOnClickListener(this);
    }

    private void init() {
        CallSession session = callActivity.getCurrentSession();
        if (session != null) {
            currentState = session.getState();
        }
        if (session == null || CallSession.CallState.Idle == session.getState()) {
            if (callActivity != null) {
                callActivity.finish();
            }
        } else if (CallSession.CallState.Connected == session.getState()) {
            layoutIncoming.setVisibility(View.GONE);
            layoutOutgoing.setVisibility(View.GONE);
            layoutConnected.setVisibility(View.VISIBLE);
            layoutInvite.setVisibility(View.GONE);
            startRefreshTime();
        } else {
            if (isOutgoing) {
                layoutIncoming.setVisibility(View.GONE);
                layoutOutgoing.setVisibility(View.VISIBLE);
                layoutConnected.setVisibility(View.GONE);
                txtTip.setText(R.string.str_waiting);
            } else {
                layoutIncoming.setVisibility(View.VISIBLE);
                layoutOutgoing.setVisibility(View.GONE);
                layoutConnected.setVisibility(View.GONE);
                txtTip.setText(R.string.str_video_invite);
                if (currentState == CallSession.CallState.Incoming) {
                    // 接收方，设置本地预览
                    View surfaceView = callActivity.getCurrentSession().setupLocalVideo(false);
                    if (surfaceView != null) {
                        localSurfaceView = (SurfaceViewRenderer) surfaceView;
                        localSurfaceView.setZOrderMediaOverlay(false);
                        fullscreenRenderer.addView(localSurfaceView);
                    }
                }
            }
        }
    }

    public void didChangeState(CallSession.CallState state) {
        currentState = state;
        runOnUiThread(() -> {
            if (state == CallSession.CallState.Connected) {

                layoutIncoming.setVisibility(View.GONE);
                layoutOutgoing.setVisibility(View.GONE);
                layoutConnected.setVisibility(View.VISIBLE);
                layoutInvite.setVisibility(View.GONE);
                txtTip.setVisibility(View.GONE);
                // 开启计时器
                startRefreshTime();
            }
        });
    }

    public void didChangeMode(Boolean isAudio) {
        runOnUiThread(() -> callActivity.switchAudio());
    }

    // 发起方设置本地预览
    public void didCreateLocalVideoTrack() {
        if (localSurfaceView == null) {
            View surfaceView = callActivity.getCurrentSession().setupLocalVideo(true);
            if (surfaceView != null) {
                localSurfaceView = (SurfaceViewRenderer) surfaceView;
            } else {
                if (callActivity != null) callActivity.finish();
                return;
            }
        } else {
            localSurfaceView.setZOrderMediaOverlay(true);
        }

        if (localSurfaceView.getParent() != null) {
            ((ViewGroup) localSurfaceView.getParent()).removeView(localSurfaceView);
        }
        if (isOutgoing && remoteSurfaceView == null) {
            if (fullscreenRenderer != null && fullscreenRenderer.getChildCount() != 0)
                fullscreenRenderer.removeAllViews();
            fullscreenRenderer.addView(localSurfaceView);
        } else {
            if (pipRenderer.getChildCount() != 0) pipRenderer.removeAllViews();
            pipRenderer.addView(localSurfaceView);
        }
    }

    // 接通电话，设置远端预览
    public void didReceiveRemoteVideoTrack(String userId) {
        pipRenderer.setVisibility(View.VISIBLE);
        if (localSurfaceView != null) {
            localSurfaceView.setZOrderMediaOverlay(true);
            if (isOutgoing) {
                if (localSurfaceView.getParent() != null) {
                    ((ViewGroup) localSurfaceView.getParent()).removeView(localSurfaceView);
                }
                pipRenderer.addView(localSurfaceView);
            }
        }

        View surfaceView = callActivity.getCurrentSession().setupRemoteVideo(userId, false);
        if (surfaceView != null) {
            fullscreenRenderer.setVisibility(View.VISIBLE);
            remoteSurfaceView = (SurfaceViewRenderer) surfaceView;
            fullscreenRenderer.removeAllViews();
            if (remoteSurfaceView.getParent() != null) {
                ((ViewGroup) remoteSurfaceView.getParent()).removeView(remoteSurfaceView);
            }
            fullscreenRenderer.addView(remoteSurfaceView);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        // 接听
        CallSession session = callActivity.getCurrentSession();
        if (id == R.id.img_accept_income) {
            if (session != null && session.getState() == CallSession.CallState.Incoming) {
                session.joinRoom(session.getRoomId());
            } else if (session != null) {
                if (callActivity != null) {
                    session.sendRefuse();
                    callActivity.finish();
                }
            }
        }
        // 挂断电话
        if (id == R.id.img_hangup_income || id == R.id.outgoingHangupImageView || id == R.id.connectedHangupImageView) {
            if (session != null) {
                callActivity.endCall();
            }
            if (callActivity != null) callActivity.finish();
        }

        // 切换摄像头
        if (id == R.id.switchCameraImageView) {
            session.switchCamera();
        }
        if (id == R.id.layout_pip) {
            boolean isFullScreenRemote = fullscreenRenderer.getChildAt(0) == remoteSurfaceView;
            fullscreenRenderer.removeAllViews();
            pipRenderer.removeAllViews();
            if (isFullScreenRemote) {
                remoteSurfaceView.setZOrderMediaOverlay(true);
                pipRenderer.addView(remoteSurfaceView);
                localSurfaceView.setZOrderMediaOverlay(false);
                fullscreenRenderer.addView(localSurfaceView);
            } else {
                localSurfaceView.setZOrderMediaOverlay(true);
                pipRenderer.addView(localSurfaceView);
                remoteSurfaceView.setZOrderMediaOverlay(false);
                fullscreenRenderer.addView(remoteSurfaceView);
            }
        }
    }

    public void didCallEnd() {
        txtStatus.setText("连接挂断");
        layoutIncoming.setVisibility(View.GONE);
        layoutOutgoing.setVisibility(View.GONE);
        if (layoutConnected != null)
            layoutConnected.setVisibility(View.GONE);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (callActivity != null) {
                callActivity.finish();
            }

        }, 1000);
    }

    public void didDisconnected(String useId) {
        if (callActivity != null) {
            callActivity.endCall();
        }
    }

    public void startRefreshTime() {
        if (callActivity.getCurrentSession() == null) return;
        if (txtDuration != null) {
            txtDuration.setVisibility(View.VISIBLE);
            long diff = System.currentTimeMillis() - callActivity.getCurrentSession().getStartTime();
            txtDuration.setBase(SystemClock.elapsedRealtime() - diff);
            txtDuration.start();
        }
    }

    void runOnUiThread(Runnable runnable) {
        if (callActivity != null) {
            callActivity.runOnUiThread(runnable);
        }
    }

    private final Runnable waitingRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentState != CallSession.CallState.Connected) {
                if (callActivity != null) {
                    callActivity.endCall();
                }
            }
        }
    };

}