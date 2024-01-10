package com.xunua.webrtcdemo;

public class Config {
    public static final String ServerIP="8.134.123.132";

    public static final class EventName{
        public static final String EVENT_NAME_JOIN="__join";//加入房间服务器
        public static final String EVENT_NAME_PEERS="_peers";//房间服务器加入成功
        public static final String EVENT_NAME_NEW_PEER="_new_peer";//有新的人员加入
        public static final String EVENT_NAME_SEND_OFFER ="__offer";//主动发起请求，请求视频通话
        public static final String EVENT_NAME_RECEIVE_OFFER="_offer";//主动发起请求，请求视频通话
        public static final String EVENT_NAME_RECEIVE_ICE_CANDIDATE ="_ice_candidate";//sdp
        public static final String EVENT_NAME_SEND_ICE_CANDIDATE="__ice_candidate";//sdp
        public static final String EVENT_NAME_ANSWER="_answer";//被动方:同意或拒绝请求
    }


    //    googEchoCancellation   回音消除
    public static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    //
    //    googNoiseSuppression   噪声抑制
    public static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";

    //    googAutoGainControl    自动增益控制
    public static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    //    googHighpassFilter     高通滤波器
    public static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
}
