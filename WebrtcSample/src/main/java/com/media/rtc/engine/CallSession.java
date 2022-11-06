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


    // 创建房间
    public void createRoom(String room, int roomSize) {
        mSessionListener.createRoom(room, roomSize);
    }

    // 加入房间
    public void joinRoom(String roomId) {
        setIsComing(true);
        mCallState = CallState.Connecting;
        mSessionListener.joinRoom(roomId);
    }

    // 拒绝接听
    public void sendRefuse() {
        mSessionListener.sendRefuse(mRoomId, mTargetId, 1);
        release(mTargetId);
    }

    // 忙线
    public void sendBusyRefuse(String room, String targetId) {
        mSessionListener.sendRefuse(mRoomId, targetId, 0);
        release(mTargetId);
    }

    // 取消呼叫
    public void sendCancel() {
        mSessionListener.sendCancel(mRoomId, mTargetId);
        release(mTargetId);
    }

    // 离开房间
    public void leave() {
        mSessionListener.sendLeave(mRoomId, mUserId);
        release(mTargetId);
    }

    // 切换摄像头
    public void switchCamera() {
        rtcEngine.switchCamera();
    }

    public void release(String targetId) {
        rtcEngine.release();
        mCallState = CallState.Idle;
        // 回调通话结束
        mSessionCallback.didCallEnd(mUserId);
    }

    //************************接收命令*****************************/

    // 加入房间成功
    public void onJoinRoom(String userId, String targetId) {
        mUserId = userId;
        // 发起邀请
        if (!mIsComing) {
            mSessionListener.sendInvite(mRoomId, mTargetId, mIsAudioOnly);
        } else { // 接受邀请
            rtcEngine.joinRoom(targetId);
        }
        mSessionCallback.didCreateLocalVideoTrack();
    }

    // 新成员加入
    public void onNewPeer(String userId) {
        rtcEngine.userIn(userId);
        mCallState = CallState.Connected;
        startTime = System.currentTimeMillis();
        mSessionCallback.didChangeState(mCallState);
    }

    // 拒接接听
    public void onRefuse(String userId, int type) {
        rtcEngine.userReject(userId, type);
    }

    // 切换语音通话
    public void onTransAudio(String userId) {
        mIsAudioOnly = true;
        mSessionCallback.didChangeMode(true);
    }

    // 网络断开
    public void onDisConnect(String userId) {
        rtcEngine.disconnected(userId);
    }

    // 取消呼叫
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
     * 本地视频预览
     * @param overlay 是否叠在上层，画中画显示
     * @return View
     */
    public View setupLocalVideo(boolean overlay) {
        return rtcEngine.setupLocalPreview(overlay);
    }

    /**
     * 远端视频预览
     * @param userId userId
     * @param overlay 是否叠在上层，画中画显示
     * @return View
     */
    public View setupRemoteVideo(String userId, boolean overlay) {
        return rtcEngine.setupRemoteVideo(userId, overlay);
    }

    //**********************engine回调函数*****************************/

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
