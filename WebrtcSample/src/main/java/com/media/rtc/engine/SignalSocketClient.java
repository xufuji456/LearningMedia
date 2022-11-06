package com.media.rtc.engine;

import android.util.Log;

import com.media.rtc.engine.listener.EventListener;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * 发送和接收信令
 */
public class SignalSocketClient extends WebSocketClient {

    private final static String TAG = SignalSocketClient.class.getSimpleName();

    private boolean mConnectFlag;
    private final EventListener mEventListener;
    private final ExecutorService mThreadExecutor;

    private final static String EVENT_LOGIN_SUCCESS = "__login_success";
    private final static String EVENT_OFFER         = "__offer";
    private final static String EVENT_ANSWER        = "__answer";
    private final static String EVENT_ICE_CANDIDATE = "__ice_candidate";
    private final static String EVENT_JOIN          = "__join";
    private final static String EVENT_CREATE        = "__create";
    private final static String EVENT_INVITE        = "__invite";
    private final static String EVENT_CANCEL        = "__cancel";
    private final static String EVENT_PEER          = "__peers";
    private final static String EVENT_NEW_PEER      = "__new_peer";
    private final static String EVENT_REJECT        = "__reject";
    private final static String EVENT_AUDIO         = "__audio";
    private final static String EVENT_LEAVE         = "__leave";
    private final static String EVENT_DISCONNECT    = "__disconnect";

    private final static String PARAM_EVENT       = "eventName";
    private final static String PARAM_ID          = "id";
    private final static String PARAM_SDP         = "sdp";
    private final static String PARAM_YOU         = "you";
    private final static String PARAM_DATA        = "data";
    private final static String PARAM_ROOM        = "room";
    private final static String PARAM_LABEL       = "label";
    private final static String PARAM_TO_ID       = "toID";
    private final static String PARAM_FROM_ID     = "fromID";
    private final static String PARAM_USER_ID     = "userID";
    private final static String PARAM_INVITE_ID   = "inviteID";
    private final static String PARAM_CANDIDATE   = "candidate";
    private final static String PARAM_ROOM_SIZE   = "roomSize";
    private final static String PARAM_USER_LIST   = "userList";
    private final static String PARAM_AUDIO_ONLY  = "audioOnly";
    private final static String PARAM_CONNECTIONS = "connections";
    private final static String PARAM_REFUSE_TYPE = "refuseType";


    public SignalSocketClient(URI serverUri, EventListener event) {
        super(serverUri);
        this.mEventListener = event;
        this.mThreadExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onOpen(ServerHandshake data) {
        mConnectFlag = true;
    }

    // 接收到信令
    @Override
    public void onMessage(String message) {
        try {
            handleMessage(message);
        } catch (JSONException e) {
            Log.e(TAG, "parse json error:" + e);
        }
    }

    @Override
    public void onError(Exception ex) {
        this.mEventListener.logout("onError");
        mConnectFlag = false;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (mConnectFlag) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.mEventListener.reConnect();
        } else {
            this.mEventListener.logout("onClose");
        }
    }

    // ---------------------------------------处理接收消息-------------------------------------

    private void handleMessage(String message) throws JSONException {
        JSONObject object = new JSONObject(message);
        String eventName  = object.getString("eventName");
        JSONObject  data  = object.getJSONObject("data");

        switch (eventName) {
            case EVENT_LOGIN_SUCCESS: //登录成功
                handleLogin(data);
                break;
            case EVENT_OFFER: // 收到offer
                handleOffer(data);
                break;
            case EVENT_ANSWER: // 收到answer
                handleAnswer(data);
                break;
            case EVENT_ICE_CANDIDATE: // 收到ICE候选地址
                handleIceCandidate(data);
                break;
            case EVENT_INVITE: // 邀请通话
                handleInvite(data);
                break;
            case EVENT_CANCEL: // 取消通话
                handleCancel(data);
                break;
            case EVENT_PEER: // 成员加入
                handlePeer(data);
                break;
            case EVENT_NEW_PEER: // 新成员加入
                handleNewPeer(data);
                break;
            case EVENT_REJECT: // 拒绝接听
                handleReject(data);
                break;
            case EVENT_AUDIO: // 语音通话
                handleTransAudio(data);
                break;
            case EVENT_LEAVE: // 离开房间
                handleLeave(data);
                break;
            case EVENT_DISCONNECT: // 断开连接
                handleDisConnect(data);
                break;
            default:
                break;
        }
    }

    /**************************接收信令***********************************/

    private void handleDisConnect(JSONObject data) throws JSONException {
        String fromId = data.getString(PARAM_FROM_ID);
        mEventListener.onDisConnect(fromId);
    }

    private void handleTransAudio(JSONObject data) throws JSONException {
        String fromId = data.getString(PARAM_FROM_ID);
        mEventListener.onTransAudio(fromId);
    }

    private void handleLogin(JSONObject data) throws JSONException {
        String userID = data.getString(PARAM_USER_ID);
        mEventListener.onLoginIn(userID);
    }

    private void handleIceCandidate(JSONObject data) throws JSONException {
        String userID    = data.getString(PARAM_FROM_ID);
        String     id    = data.getString(PARAM_ID);
        int     label    = data.getInt(PARAM_LABEL);
        String candidate = data.getString(PARAM_CANDIDATE);
        mEventListener.onIceCandidate(userID, id, label, candidate);
    }

    private void handleAnswer(JSONObject data) throws JSONException {
        String sdp = data.getString(PARAM_SDP);
        String userID = data.getString(PARAM_FROM_ID);
        mEventListener.onAnswer(userID, sdp);
    }

    private void handleOffer(JSONObject data) throws JSONException {
        String sdp = data.getString(PARAM_SDP);
        String userID = data.getString(PARAM_FROM_ID);
        mEventListener.onOffer(userID, sdp);
    }

    private void handleReject(JSONObject data) throws JSONException {
        String fromID = data.getString(PARAM_FROM_ID);
        int rejectType = Integer.parseInt(data.getString(PARAM_REFUSE_TYPE));
        mEventListener.onReject(fromID, rejectType);
    }

    private void handlePeer(JSONObject data) throws JSONException {
        String other = data.getString(PARAM_YOU);
        String connections = data.getString(PARAM_CONNECTIONS);
        int roomSize = data.getInt(PARAM_ROOM_SIZE);
        mEventListener.onPeer(other, connections, roomSize);
    }

    private void handleNewPeer(JSONObject data) throws JSONException {
        String userID = data.getString(PARAM_USER_ID);
       mEventListener.onNewPeer(userID);
    }

    private void handleCancel(JSONObject data) throws JSONException {
        String inviteID = data.getString(PARAM_INVITE_ID);
        mEventListener.onCancel(inviteID);
    }

    private void handleInvite(JSONObject data) throws JSONException {
        String room = data.getString(PARAM_ROOM);
        boolean audioOnly = data.getBoolean(PARAM_AUDIO_ONLY);
        String inviteID = data.getString(PARAM_INVITE_ID);
        String targetId = data.getString(PARAM_USER_LIST);
        mEventListener.onInvite(room, audioOnly, inviteID, targetId);
    }

    private void handleLeave(JSONObject data) throws JSONException {
        String fromID = data.getString(PARAM_FROM_ID);
        mEventListener.onLeave(fromID);
    }

    /********************************发送信令************************************/

    private void sendTask(JSONObject json, JSONObject data) throws JSONException {
        String msg = jsonToString(json, data);
        sendTask(msg, 0);
    }

    private void sendTask(String msg, long delayMs) {
        mThreadExecutor.execute(() -> {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // 调用send()发送信令
            try {
                send(msg);
            } catch (Exception e) {
                Log.e(TAG, "send signal error:" + e);
            }
        });
    }

    private JSONObject createJson(String eventName) throws JSONException {
        JSONObject json = new JSONObject();
        json.put(PARAM_EVENT, eventName);
        return json;
    }

    private String jsonToString(JSONObject json, JSONObject data) throws JSONException {
        json.put(PARAM_DATA, data);
        return json.toString();
    }

    // 创建房间
    public void createRoom(String room, int roomSize, String userId) {
        try {
            JSONObject json = createJson(EVENT_CREATE);
            JSONObject data = new JSONObject();
            data.put(PARAM_ROOM, room);
            data.put(PARAM_ROOM_SIZE, roomSize);
            data.put(PARAM_USER_ID, userId);
            sendTask(json, data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 发送邀请
    public void sendInvite(String room, String userId, String targetId, boolean audioOnly) {
        try {
            JSONObject json = createJson(EVENT_INVITE);
            JSONObject data = new JSONObject();
            data.put(PARAM_ROOM, room);
            data.put(PARAM_AUDIO_ONLY, audioOnly);
            data.put(PARAM_INVITE_ID, userId);
            data.put(PARAM_USER_LIST, targetId);
            sendTask(json, data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 取消邀请
    public void sendCancel(String mRoomId, String useId, String targetId) {
        try {
            JSONObject json = createJson(EVENT_CANCEL);
            JSONObject data = new JSONObject();
            data.put(PARAM_INVITE_ID, useId);
            data.put(PARAM_ROOM, mRoomId);
            data.put(PARAM_USER_LIST, targetId);
            sendTask(json, data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //加入房间
    public void sendJoin(String room, String userId) {
        try {
            JSONObject json = createJson(EVENT_JOIN);
            JSONObject data = new JSONObject();
            data.put(PARAM_ROOM, room);
            data.put(PARAM_USER_ID, userId);
            sendTask(json, data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 拒接接听
    public void sendRefuse(String room, String inviteID, String myId, int refuseType) {
        try {
            JSONObject json = createJson(EVENT_REJECT);
            JSONObject data = new JSONObject();
            data.put(PARAM_ROOM, room);
            data.put(PARAM_TO_ID, inviteID);
            data.put(PARAM_FROM_ID, myId);
            data.put(PARAM_REFUSE_TYPE, String.valueOf(refuseType));
            sendTask(json, data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 发送offer
    public void sendOffer(String myId, String userId, String sdp) {
        try {
            JSONObject json = createJson(EVENT_OFFER);
            JSONObject data = new JSONObject();
            data.put(PARAM_SDP, sdp);
            data.put(PARAM_USER_ID, userId);
            data.put(PARAM_FROM_ID, myId);
            sendTask(json, data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 发送answer
    public void sendAnswer(String myId, String userId, String sdp) {
        try {
            JSONObject json = createJson(EVENT_ANSWER);
            JSONObject data = new JSONObject();
            data.put(PARAM_SDP, sdp);
            data.put(PARAM_FROM_ID, myId);
            data.put(PARAM_USER_ID, userId);
            sendTask(json, data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 发送ice-candidate
    public void sendIceCandidate(String myId, String userId, String id, int label, String candidate) {
        try {
            JSONObject json = createJson(EVENT_ICE_CANDIDATE);
            JSONObject data = new JSONObject();
            data.put(PARAM_USER_ID, userId);
            data.put(PARAM_FROM_ID, myId);
            data.put(PARAM_ID, id);
            data.put(PARAM_LABEL, label);
            data.put(PARAM_CANDIDATE, candidate);
            String msg = jsonToString(json, data);
            sendTask(msg, 100);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 离开房间
    public void sendLeave(String myId, String room, String userId) {
        try {
            JSONObject json = createJson(EVENT_LEAVE);
            JSONObject data = new JSONObject();
            data.put(PARAM_ROOM, room);
            data.put(PARAM_FROM_ID, myId);
            data.put(PARAM_USER_ID, userId);
            sendTask(json, data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

}
