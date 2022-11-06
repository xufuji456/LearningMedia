package com.media.rtc.engine.listener;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

public interface EngineCallback {

    void onJoinRoom();

    void onSendOffer(String userId, SessionDescription description);

    void onSendAnswer(String userId, SessionDescription description);

    void onExitRoom();

    void onReject(int type);

    void disconnected(String userId);

    void onSendIceCandidate(String userId, IceCandidate candidate);

    void onRemoteStream(String userId);

    void onDisconnected(String userId);

}
