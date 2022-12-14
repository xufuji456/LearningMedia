package com.media.rtc.engine;

import android.content.Context;
import android.view.View;

import com.media.rtc.Const;
import com.media.rtc.engine.listener.EngineCallback;
import com.media.rtc.engine.listener.SessionListener;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.lang.ref.WeakReference;

public class CallSession implements EngineCallback {

    public String mUserId;
    private int mRoomSize;
    public String mTargetId;
    private String mRoomId;

    private long startTime;
    private boolean mIsComing;
    private boolean mIsAudioOnly;
    private CallState mCallState = CallState.Idle;

    private AppRTCEngine rtcEngine;
    private SessionListener mSessionListener;
    private CallSessionCallback mSessionCallback;

    public enum CallState {
        Idle,
        Outgoing,
        Incoming,
        Connecting,
        Connected
    }

    public interface CallSessionCallback {
        void didCallEnd(String userId);

        void didChangeState(CallState callState);

        void didChangeMode(boolean onlyAudio);

        void didCreateLocalVideoTrack();

        void didReceiveRemoteVideoTrack(String userId);

        void didDisconnected(String userId);
    }

    public CallSession(Context context, String roomId, boolean audioOnly, SessionListener listener) {
        mRoomId = roomId;
        mIsAudioOnly = audioOnly;
        mSessionListener = listener;
        rtcEngine = new AppRTCEngine(audioOnly, context);
        rtcEngine.init(this);
    }

    public boolean isAudioOnly() {
        return mIsAudioOnly;
    }

    public void setTargetId(String targetIds) {
        this.mTargetId = targetIds;
    }

    public void setIsComing(boolean isComing) {
        this.mIsComing = isComing;
    }

    public boolean isComing() {
        return mIsComing;
    }

    public String getRoomId() {
        return mRoomId;
    }

    public CallState getState() {
        return mCallState;
    }

    public void setCallState(CallState callState) {
        this.mCallState = callState;
    }

    public void setSessionCallback(CallSessionCallback sessionCallback) {
        mSessionCallback = sessionCallback;
    }


    // ????????????
    public void createRoom(String room, int roomSize) {
        mSessionListener.createRoom(room, roomSize);
    }

    // ????????????
    public void joinRoom(String roomId) {
        setIsComing(true);
        mCallState = CallState.Connecting;
        mSessionListener.joinRoom(roomId);
    }

    // ????????????
    public void sendRefuse() {
        mSessionListener.sendRefuse(mRoomId, mTargetId, 1);
        release(mTargetId);
    }

    // ??????
    public void sendBusyRefuse(String room, String targetId) {
        mSessionListener.sendRefuse(mRoomId, targetId, 0);
        release(mTargetId);
    }

    // ????????????
    public void sendCancel() {
        mSessionListener.sendCancel(mRoomId, mTargetId);
        release(mTargetId);
    }

    // ????????????
    public void leave() {
        mSessionListener.sendLeave(mRoomId, mUserId);
        release(mTargetId);
    }

    // ???????????????
    public void switchCamera() {
        rtcEngine.switchCamera();
    }

    public void release(String targetId) {
        rtcEngine.release();
        mCallState = CallState.Idle;
        // ??????????????????
        mSessionCallback.didCallEnd(mUserId);
    }

    //************************????????????*****************************/

    // ??????????????????
    public void onJoinRoom(String userId, String targetId) {
        mUserId = userId;
        // ????????????
        if (!mIsComing) {
            mSessionListener.sendInvite(mRoomId, mTargetId, mIsAudioOnly);
        } else { // ????????????
            rtcEngine.joinRoom(targetId);
        }
        mSessionCallback.didCreateLocalVideoTrack();
    }

    // ???????????????
    public void onNewPeer(String userId) {
        rtcEngine.userIn(userId);
        mCallState = CallState.Connected;
        startTime = System.currentTimeMillis();
        mSessionCallback.didChangeState(mCallState);
    }

    // ????????????
    public void onRefuse(String userId, int type) {
        rtcEngine.userReject(userId, type);
    }

    // ??????????????????
    public void onTransAudio(String userId) {
        mIsAudioOnly = true;
        mSessionCallback.didChangeMode(true);
    }

    // ????????????
    public void onDisConnect(String userId) {
        rtcEngine.disconnected(userId);
    }

    // ????????????
    public void onCancel(String userId) {
        release(userId);
    }

    public void onReceiveOffer(String userId, String sdp) {
        rtcEngine.receiveOffer(userId, sdp);
    }

    public void onReceiveAnswer(String userId, String sdp) {
        rtcEngine.receiveAnswer(userId, sdp);
    }

    public void onRemoteIceCandidate(String userId, String id, int label, String candidate) {
        rtcEngine.receiveIceCandidate(userId, id, label, candidate);
    }

    public void onLeave(String userId) {
        rtcEngine.leaveRoom(userId);
    }

    public long getStartTime() {
        return startTime;
    }

    /**
     * ??????????????????
     * @param overlay ????????????????????????????????????
     * @return View
     */
    public View setupLocalVideo(boolean overlay) {
        return rtcEngine.setupLocalPreview(overlay);
    }

    /**
     * ??????????????????
     * @param userId userId
     * @param overlay ????????????????????????????????????
     * @return View
     */
    public View setupRemoteVideo(String userId, boolean overlay) {
        return rtcEngine.setupRemoteVideo(userId, overlay);
    }

    //**********************engine????????????*****************************/

    @Override
    public void onJoinRoom() {
        mCallState = CallState.Connected;
        startTime = System.currentTimeMillis();
        mSessionCallback.didChangeState(mCallState);
    }

    @Override
    public void onSendOffer(String userId, SessionDescription description) {
        mSessionListener.sendOffer(userId, description.description);
    }

    @Override
    public void onSendAnswer(String userId, SessionDescription description) {
        mSessionListener.sendAnswer(userId, description.description);
    }

    @Override
    public void onExitRoom() {
        release(mTargetId);
    }

    @Override
    public void onReject(int type) {
        release(mTargetId);
    }

    @Override
    public void disconnected(String userId) {
        release(userId);
    }

    @Override
    public void onSendIceCandidate(String userId, IceCandidate candidate) {
        mSessionListener.sendIceCandidate(userId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
    }

    @Override
    public void onRemoteStream(String userId) {
        mSessionCallback.didReceiveRemoteVideoTrack(userId);
    }

    @Override
    public void onDisconnected(String userId) {
        mSessionCallback.didDisconnected(userId);
    }

}
