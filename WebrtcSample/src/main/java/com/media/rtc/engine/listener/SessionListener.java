package com.media.rtc.engine.listener;

public interface SessionListener {

    void createRoom(String room, int roomSize);

    void joinRoom(String roomId);

    void sendOffer(String userId, String sdp);

    void sendAnswer(String userId, String sdp);

    void sendInvite(String roomId, String targetId, boolean audioOnly);

    void sendIceCandidate(String userId, String id, int label, String candidate);

    void sendRefuse(String roomId, String targetId, int refuseType);

    void sendCancel(String roomId, String targetId);

    void sendLeave(String roomId, String userId);

}
