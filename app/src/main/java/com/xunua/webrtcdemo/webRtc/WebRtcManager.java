package com.xunua.webrtcdemo.webRtc;


import android.content.Intent;

import com.xunua.webrtcdemo.Config;
import com.xunua.webrtcdemo.MainActivity;
import com.xunua.webrtcdemo.VideoChatActivity;

import org.webrtc.EglBase;

public class WebRtcManager {
    private WebSocketManager mWebSocketManager;
    private PeerConnectionManager mPeerConnectionManager;
    private String mRoomId="";
    private static final WebRtcManager INSTANCE=new WebRtcManager();
    public static WebRtcManager getInstance() {
        return INSTANCE;
    }

    public WebRtcManager() {

    }

    public void connect(MainActivity mainActivity, String roomId,boolean isSpeaker){
        mRoomId=roomId;
        mPeerConnectionManager=new PeerConnectionManager(isSpeaker);
        mWebSocketManager=new WebSocketManager(mainActivity,mPeerConnectionManager,isSpeaker);
        mWebSocketManager.connect("wss://"+ Config.ServerIP+"/wss",roomId);
    }

    /**
     * 加入房间，给房间服务器发送消息
     * @param roomId
     */
    public void joinRoom(VideoChatActivity activity,String roomId, EglBase eglBaseContext){
        mPeerConnectionManager.initContext(activity,eglBaseContext);
        mWebSocketManager.joinRoom(roomId);
    }
}
