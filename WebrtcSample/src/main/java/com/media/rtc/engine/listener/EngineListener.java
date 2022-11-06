package com.media.rtc.engine.listener;


import android.view.View;

public interface EngineListener {

    void init(EngineCallback callback);

    void joinRoom(String targetId);

    void userIn(String userId);

    void userReject(String userId,int type);

    void disconnected(String userId);

    void receiveOffer(String userId, String description);

    void receiveAnswer(String userId, String sdp);

    void receiveIceCandidate(String userId, String id, int label, String candidate);

    void leaveRoom(String userId);

    View setupLocalPreview(boolean isOverlay);

    void stopPreview();

    View setupRemoteVideo(String userId, boolean isO);

    void switchCamera();

    void toggleSpeaker(boolean enable);

    void release();

}
