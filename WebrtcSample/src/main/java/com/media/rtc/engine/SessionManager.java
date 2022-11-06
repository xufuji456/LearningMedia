package com.media.rtc.engine;


import android.content.Context;
import android.util.Log;

import com.media.RTCApplication;
import com.media.rtc.activity.CallActivity;
import com.media.rtc.engine.listener.EventListener;
import com.media.rtc.engine.listener.SessionListener;

import java.net.URI;
import java.net.URISyntaxException;

public class SessionManager implements EventListener, SessionListener {

    private final static String TAG = SessionManager.class.getSimpleName();

    private String mUserId;
    // 呼叫会话
    private CallSession mCallSession;
    // 信令处理
    private SignalSocketClient mSocketClient;

    private static class SessionHolder {
        private final static SessionManager mSessionManager = new SessionManager();
    }

    private SessionManager() {}

    public static SessionManager getInstance() {
        return SessionHolder.mSessionManager;
    }

    // 登录连接
    public void connect(String url, String userId, int id) throws URISyntaxException {
        if (mSocketClient != null && mSocketClient.isOpen()) {
            Log.e(TAG, "socket has opened...");
            return;
        }

        String fullUrl = url + "/" + userId + "/" + id;
        URI uri = new URI(fullUrl);
        mSocketClient = new SignalSocketClient(uri, this);
        mSocketClient.connect();
    }

    @Override
    public void onLoginIn(String userId) {
        mUserId = userId;
    }

    @Override
    public void onInvite(String room, boolean audioOnly, String userId, String targetId) {
        // 接受到邀请，打开通话界面
        CallActivity.openActivity(RTCApplication.getInstance(), userId, false, audioOnly, room);
    }

    @Override
    public void onCancel(String inviteId) {
        mCallSession.onCancel(inviteId);
    }

    @Override
    public void onPeer(String userId, String targetId, int roomSize) {
        mCallSession.onJoinRoom(userId, targetId);
    }

    @Override
    public void onNewPeer(String userId) {
        mCallSession.onNewPeer(userId);
    }

    @Override
    public void onReject(String userId, int type) {
        mCallSession.onReject(type);
    }

    @Override
    public void onOffer(String userId, String sdp) {
        mCallSession.onReceiveOffer(userId, sdp);
    }

    @Override
    public void onAnswer(String userId, String sdp) {
        mCallSession.onReceiveAnswer(userId, sdp);
    }

    @Override
    public void onIceCandidate(String userId, String id, int label, String candidate) {
        mCallSession.onRemoteIceCandidate(userId, id, label, candidate);
    }

    @Override
    public void onLeave(String userId) {
        mCallSession.onLeave(userId);
    }

    @Override
    public void logout(String userId) {

    }

    @Override
    public void onTransAudio(String userId) {
        mCallSession.onTransAudio(userId);
    }

    @Override
    public void onDisConnect(String userId) {
        mCallSession.onDisConnect(userId);
    }

    @Override
    public void reConnect() {

    }

    @Override
    public void createRoom(String room, int roomSize) {
        mSocketClient.createRoom(room, roomSize, mUserId);
    }

    @Override
    public void joinRoom(String roomId) {
        mSocketClient.sendJoin(roomId, mUserId);
    }

    @Override
    public void sendOffer(String userId, String sdp) {
        mSocketClient.sendOffer(mUserId, userId, sdp);
    }

    @Override
    public void sendAnswer(String userId, String sdp) {
        mSocketClient.sendAnswer(mUserId, userId, sdp);
    }

    @Override
    public void sendInvite(String roomId, String targetId, boolean audioOnly) {
        mSocketClient.sendInvite(roomId, mUserId, targetId, audioOnly);
    }

    @Override
    public void sendIceCandidate(String userId, String id, int label, String candidate) {
        mSocketClient.sendIceCandidate(mUserId, userId, id, label, candidate);
    }

    @Override
    public void sendRefuse(String roomId, String targetId, int refuseType) {
        mSocketClient.sendRefuse(roomId, targetId, mUserId, refuseType);
    }

    @Override
    public void sendCancel(String roomId, String targetId) {
        mSocketClient.sendCancel(roomId, mUserId, targetId);
    }

    @Override
    public void sendLeave(String roomId, String userId) {
        mSocketClient.sendLeave(mUserId, roomId, userId);
    }

    // 呼叫通话
    public boolean startOutCall(Context context, String room, String targetId, boolean audioOnly) {
        if (mCallSession != null && mCallSession.getState() != CallSession.CallState.Idle) {
            Log.e(TAG, "busy now, try again later...");
            return false;
        }
        // 初始化会话
        mCallSession = new CallSession(context, room, audioOnly, this);
        mCallSession.setTargetId(targetId);
        mCallSession.setIsComing(false);
        mCallSession.setCallState(CallSession.CallState.Outgoing);
        mCallSession.createRoom(room, 2);
        return true;
    }

    // 接收通话
    public boolean startInCall(Context context, String room, String targetId, boolean audioOnly) {
        if (mCallSession != null && mCallSession.getState() != CallSession.CallState.Idle) {
            Log.e(TAG, "busy now, try again later...");
            mCallSession.sendBusyRefuse(room, targetId);
            return false;
        }
        // 初始化会话
        mCallSession = new CallSession(context, room, audioOnly, this);
        mCallSession.setTargetId(targetId);
        mCallSession.setIsComing(true);
        mCallSession.setCallState(CallSession.CallState.Incoming);
        return true;
    }

    // 结束通话
    public void endCall() {
        if (mCallSession == null)
            return;
        if (mCallSession.isComing()) {
            if (mCallSession.getState() == CallSession.CallState.Incoming) {
                mCallSession.sendRefuse();
            } else {
                mCallSession.leave();
            }
        } else {
            if (mCallSession.getState() == CallSession.CallState.Outgoing) {
                mCallSession.sendCancel();
            } else {
                mCallSession.leave();
            }
        }
        mCallSession.setCallState(CallSession.CallState.Idle);
    }

    public CallSession getCallSession() {
        return mCallSession;
    }
}
