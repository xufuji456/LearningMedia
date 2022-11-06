package com.media.rtc;

public class Const {

    public final static String IP_HOST = "42.192.40.58";

    // IP地址
    public final static String IP_ADDRESS = IP_HOST + ":5000";

    // 信令地址
    public final static String URL_SIGNAL = "ws://" + IP_ADDRESS + "/ws";

    // 用户列表
    public final static String URL_USER_LIST = "http://" + IP_ADDRESS + "/userList";

    public final static String ICE_SERVER   = "stun:stun.l.google.com:19302";

    public final static String STUN_SERVER  = "stun:" + IP_HOST + ":3478?transport=udp";

    public final static String TURN_SERVER  = "turn:" + IP_HOST + ":3478?transport=udp";

    public final static String TURN_SERVER1 = "turn:" + IP_HOST + ":3478?transport=tcp";

    // 用户名
    public final static String USER_NAME = "ddssingsong";

    // 密码
    public final static String PASSWORD = "123456";

}
