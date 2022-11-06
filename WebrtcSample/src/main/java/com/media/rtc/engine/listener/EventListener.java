package com.media.rtc.engine.listener;

public interface EventListener {

    void onLoginIn(String userId);

    void onInvite(String room, boolean audioOnly, String userId, String targetId);

    void onCancel(String inviteId);

    void onPeer(String userId, String targetId, int roomSize);

    void onNewPeer(String userId);

    void onReject(String userId, int type);

    void onOffer(String userId, String sdp);

    void onAnswer(String userId, String sdp);

    void onIceCandidate(String userId, String id, int label, String candidate);

    void onLeave(String userId);

    void logout(String userId);

    void onTransAudio(String userId);

    void onDisConnect(String userId);

    void reConnect();

}
