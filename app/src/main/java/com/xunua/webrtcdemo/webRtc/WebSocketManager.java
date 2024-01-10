package com.xunua.webrtcdemo.webRtc;


import android.app.Activity;
import android.util.Log;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.xunua.webrtcdemo.Config;
import com.xunua.webrtcdemo.MainActivity;
import com.xunua.webrtcdemo.VideoChatActivity;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.webrtc.IceCandidate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 链接房间服务器
 */
public class WebSocketManager {
    private static final String TAG = "xunyou";
    private PeerConnectionManager mPeerConnectionManager;
    private WebSocketClient mWebSocketClient;
    private Activity mActivity;
    private boolean mIsSpeaker;

    public WebSocketManager(MainActivity mainActivity, PeerConnectionManager mPeerConnectionManager, boolean isSpeaker) {
        this.mIsSpeaker=isSpeaker;
        mActivity = mainActivity;
        this.mPeerConnectionManager=mPeerConnectionManager;
    }

    //链接socket
    public void connect(String wss,String roomId) {
        URI uri = null;
        try {
            uri = new URI(wss);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                Log.e(TAG, "onOpen: ");
                //房间服务器链接成功，跳转到聊天界面
                VideoChatActivity.startActivity(mActivity,roomId);
            }

            @Override
            public void onMessage(String message) {
                //收到信息
                Log.e(TAG, "收到响应信息");
                Map map = com.alibaba.fastjson.JSONObject.parseObject(message, Map.class);
                String eventName = (String) map.get("eventName");
                Log.e(TAG, "收到响应信息  onMessage: " + message+"           eventName:"+eventName);
                switch (eventName){
                    case Config.EventName.EVENT_NAME_PEERS:
                        /**
                         * {"eventName":"_peers","data":{"connections":[],"you":"29b04afa-50ae-4c3e-bd34-89cffed0f55f"}}
                         */
                        //加入房间服务器成功，返回房间里人数
                        handlerJoinRoom(map);
                        break;
                    case Config.EventName.EVENT_NAME_ANSWER:
                        //当前主叫，收到被叫用户回复的sdp
                        handleAnswer(map);
                        break;
                    case Config.EventName.EVENT_NAME_RECEIVE_ICE_CANDIDATE:
                        //收到对方的ICE
                        handleRemoteCandidate(map);
                        break;
                    case Config.EventName.EVENT_NAME_NEW_PEER:
                        //有新成员加入
                        if (mIsSpeaker) {
                            handleRemoteInRoom(map);
                        }
                        break;
                    case Config.EventName.EVENT_NAME_RECEIVE_OFFER:
                        //接受别人的sdp邀请
                        if (mIsSpeaker) {
                            handleOffer(map);
                        }
                        break;
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                //关闭
                Log.e(TAG, "onClose:   code:" + code + "      reason:" + reason + "       remote:" + remote);
            }

            @Override
            public void onError(Exception ex) {
                Log.e(TAG, "onError:     ex" + ex);
            }
        };

        /**
         * 设置证书
         */
        if (wss.startsWith("wss")) {
            try {
                SSLContext tls = SSLContext.getInstance("TLS");
                tls.init(null, new TrustManager[]{new TrustManagerTest()}, new SecureRandom());
                SSLSocketFactory factory = null;
                if (tls != null) {
                    factory = tls.getSocketFactory();
                }
                if (factory != null) {
                    mWebSocketClient.setSocket(factory.createSocket());
                }
            } catch (NoSuchAlgorithmException | KeyManagementException | IOException e) {
                e.printStackTrace();
            }
        }

        mWebSocketClient.connect();
    }

    // 处理Offer   后面进来的人  把他的sdp发送给到当前客户端   这时才真正开始做链接
    private void handleOffer(Map map) {
        Log.e(TAG, "handleOffer: ");
        Map data = (Map) map.get("data");
        Map sdpDic;
        if (data != null) {
            sdpDic = (Map) data.get("sdp");
            String socketId = (String) data.get("socketId");
            String sdp = (String) sdpDic.get("sdp");
            mPeerConnectionManager.onReceiveOffer(socketId, sdp);
        }
    }

    private void handleRemoteInRoom(Map map) {
        Log.e(TAG, "handleRemoteInRoom: ");
        Map data = (Map) map.get("data");
        if (data != null) {
            String socketId = (String) data.get("socketId");
            mPeerConnectionManager.onRemoteJoinToRoom(socketId);//添加远端的流到我们本地客户端
        }
    }

    /**
     * 收到远端客户端的请求地址
     * 处理交换信息
     * @param map
     */
    private void handleRemoteCandidate(Map map) {
        Log.e(TAG, "handleRemoteCandidate: ");
        Map data = (Map) map.get("data");
        Log.e(TAG, "handleRemoteCandidate:    data:"+data);
        if (data != null) {
            String socketId = (String) data.get("socketId");
            String sdpMid = (String) data.get("id");
            sdpMid = (null == sdpMid) ? "video" : sdpMid;
            int sdpMLineIndex = (int) Double.parseDouble(String.valueOf(data.get("label")));
            String candidate = (String) data.get("candidate");
            IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
            mPeerConnectionManager.onRemoteIceCandidate(socketId,iceCandidate);
        }
    }

    /**
     * 收到目标用户返回的sdp
     * @param map
     */
    private void handleAnswer(Map map) {
        Log.e(TAG, "handleAnswer: "+ com.alibaba.fastjson.JSONObject.toJSONString(map));
        Map data = (Map) map.get("data");
        Map sdpDic;
        if (data != null) {
            sdpDic= (Map) data.get("sdp");
            String socketId = (String) data.get("socketId");//对方的socketId
            String sdp= (String) sdpDic.get("sdp");//对方响应的SDP
            mPeerConnectionManager.onReceiverAnswer(socketId,sdp);
        }
    }

    /**
     * 响应加入房间的回复
     * @param map
     */
    private void handlerJoinRoom(Map map) {
        Map data = (Map) map.get("data");
        JSONArray arr;
        if (data!=null){
            arr = (JSONArray) data.get("connections");
            String js = com.alibaba.fastjson.JSONObject.toJSONString(arr, SerializerFeature.WriteClassName);
            ArrayList<String> connections = (ArrayList<String>)com.alibaba.fastjson.JSONObject.parseArray(js,String.class);
            String myId = (String) data.get("you");
            mPeerConnectionManager.join2Room(this,connections,true,myId);
        }
    }

    /**
     * 请求http  socket   加入房间
     *
     * @param roomId
     */
    public void joinRoom(String roomId) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("eventName", Config.EventName.EVENT_NAME_JOIN);//事件名称
        HashMap<String, Object> childMap = new HashMap<>();
        childMap.put("room", roomId);
        map.put("data", childMap);//事件名称
        JSONObject jsonObject = new JSONObject(map);
        String jsonStr = jsonObject.toString();
        Log.e(TAG, "joinRoom: 加入了房间：" + roomId + "     发送给服务器的指令信息为：" + jsonStr);
        mWebSocketClient.send(jsonStr);
    }

    /**
     * 发送打招呼请求
     * @param userId 接收信息的目标客户端id
     * @param sdp   sdp信息
     */
    public void sendOffer(String userId, String sdp) {
        HashMap<String, Object> childMap1 = new HashMap();
        childMap1.put("type", "offer");
        childMap1.put("sdp", sdp);

        HashMap<String, Object> childMap2 = new HashMap();
        childMap2.put("socketId", userId);
        childMap2.put("sdp", childMap1);

        HashMap<String, Object> map = new HashMap();
        map.put("eventName", Config.EventName.EVENT_NAME_SEND_OFFER);
        map.put("data", childMap2);
        com.alibaba.fastjson.JSONObject object = new com.alibaba.fastjson.JSONObject(map);
        String jsonString = object.toString();
        Log.e(TAG, "打招呼，当前客户端进入了房间   sendOffer-->" + jsonString);
        mWebSocketClient.send(jsonString);
    }

    public  void sendIceCandidate(String socketId, IceCandidate iceCandidate){
        HashMap<String, Object> childMap = new HashMap();
        childMap.put("id", iceCandidate.sdpMid);
        childMap.put("label", iceCandidate.sdpMLineIndex);
        childMap.put("candidate", iceCandidate.sdp);
        childMap.put("socketId", socketId);
        HashMap<String, Object> map = new HashMap();
        map.put("eventName", Config.EventName.EVENT_NAME_SEND_ICE_CANDIDATE);
        map.put("data", childMap);
        com.alibaba.fastjson.JSONObject object = new com.alibaba.fastjson.JSONObject(map);
        String jsonString = object.toString();
        Log.e(TAG, "sendIceCandidate-->" + jsonString);
        mWebSocketClient.send(jsonString);
    }

    public void sendAnswer(String socketId, String sdp) {
        Map<String, Object> childMap1 = new HashMap();
        childMap1.put("type", "answer");
        childMap1.put("sdp", sdp);
        HashMap<String, Object> childMap2 = new HashMap();
        childMap2.put("socketId", socketId);
        childMap2.put("sdp", childMap1);
        HashMap<String, Object> map = new HashMap();
        map.put("eventName", "__answer");
        map.put("data", childMap2);
        com.alibaba.fastjson.JSONObject object = new com.alibaba.fastjson.JSONObject(map);
        String jsonString = object.toString();
        Log.d(TAG, "send-->" + jsonString);
        mWebSocketClient.send(jsonString);
    }

    // 忽略证书
    public static class TrustManagerTest implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
