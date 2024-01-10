package com.xunua.webrtcdemo.webRtc;

import static org.webrtc.RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE;

import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSON;
import com.xunua.webrtcdemo.Config;
import com.xunua.webrtcdemo.VideoChatActivity;
import com.xunua.webrtcdemo.utils.ScreenUtil;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 打洞服务器连接相关
 */
public class PeerConnectionManager implements CameraVideoCapturer.CameraEventsHandler{
    private static final String VIDEO_CODEC_VP8 = "VP8";
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String VIDEO_CODEC_H264 = "H264";
    private static final String VIDEO_CODEC_H264_BASELINE = "H264 Baseline";
    private static final String VIDEO_CODEC_H264_HIGH = "H264 High";
    private static final int BPS_IN_KBPS = 1000;

    private static final String TAG = "xunyou";
    private WebSocketManager mWebSocketManager;
    private EglBase mRootEglBase;
    //视频源
    private String myId;
    //视频 true， false音频
    private boolean videoEnable;
    private ExecutorService mExecutor;
    private PeerConnectionFactory mPeerConnectionFactory;
    private VideoChatActivity mActivity;
    //记录本地的流的MediaStream
    private MediaStream mMediaStream;
    private ArrayList<String> mConnectionIdArray;
    private ArrayList<PeerConnection.IceServer> mICEServers;
    private Map<String,Peer> mConnectionPeerDic;
    private boolean mIsSpeaker;

    /**
     * Caller: 主动向其他客户端申请交换sdp
     * Receiver：后进来的用户给当前客户端发送交换sdp的申请
     */
    enum Role{
        Caller,Receiver
    }
    Role role;


    public PeerConnectionManager(boolean isSpeaker) {
        this.mIsSpeaker=isSpeaker;
        mConnectionIdArray = new ArrayList<>();
        mExecutor = Executors.newSingleThreadExecutor();
        mICEServers=new ArrayList<>();
        mConnectionPeerDic=new HashMap<>();
        String turnServerIp="turn:"+Config.ServerIP+"?transport=udp";
        PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder(turnServerIp).setUsername("admin").setPassword("123456").createIceServer();
        mICEServers.add(iceServer);
    }

    /**
     * @param javaWebSocket
     * @param connections
     * @param isVideoEnable 是否视频通话
     * @param myId
     */
    public void join2Room(WebSocketManager javaWebSocket, ArrayList<String> connections, boolean isVideoEnable, String myId) {
        //去重，去除自己
        ArrayList<String> strings = new ArrayList<>();
        for (int i = 0; i < connections.size(); i++) {
            String socketId = connections.get(i);
            if (!strings.contains(socketId)  && !socketId.equals(myId)){
                strings.add(socketId);
            }
        }

        this.mConnectionIdArray.addAll(strings);
        this.mWebSocketManager = javaWebSocket;
        this.myId = myId;
        this.videoEnable = isVideoEnable;
        //建立本地预览
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mPeerConnectionFactory == null) {
                    mPeerConnectionFactory = createConnectionFactory();
                }
                if (mMediaStream == null && mIsSpeaker) {
                    createLoaclStream();
                }
                //建立链接，当前建立的只是空链接，还没有初始化，还没有进行sdp的发送交换
                createPeerConnections();
                if (mIsSpeaker) {
                    //把我们当前的视频流，集合到mediaStream中推送给其他设备   推流当前设备
                    addLocalStreamsPushTwoOtherDevice();
                }
                //给房间服务器的其他人发送一个offer  打招呼请求
                createOffers();
            }
        });
    }

    private void addLocalStreamsPushTwoOtherDevice() {
        for (Map.Entry<String, Peer> entry : mConnectionPeerDic.entrySet()) {
            if (mMediaStream == null && mIsSpeaker) {
                createLoaclStream();
            }
            entry.getValue().pc.addStream(mMediaStream);
        }
    }

    /**
     * 发送hello
     */
    private void createOffers() {
        for (Map.Entry<String, Peer> entry : mConnectionPeerDic.entrySet()) {
            //此时是主叫
            role=Role.Caller;
            Peer peer = entry.getValue();
            //发消息给其他客户端，让当前路由存储对方设备的地址到路由表中
            peer.pc.createOffer(peer,offerOrAnswerConstraint());//获取本地到其他客户端路由的sdp  获取成功以后，会走 onCreateSuccess
        }
    }

    private MediaConstraints offerOrAnswerConstraint() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        ArrayList<MediaConstraints.KeyValuePair> keyValuePairs = new ArrayList<>();
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(videoEnable)));
//        keyValuePairs.add(new MediaConstraints.KeyValuePair("maxHeight", "1280"));
//        keyValuePairs.add(new MediaConstraints.KeyValuePair("maxWidth", "720"));
//        keyValuePairs.add(new MediaConstraints.KeyValuePair("maxFrameRate", "30"));
//        keyValuePairs.add(new MediaConstraints.KeyValuePair("minFrameRate", "15"));
        mediaConstraints.mandatory.addAll(keyValuePairs);
        return mediaConstraints;
    }

    /**
     * 通过穿透，创建链接
     */
    private void createPeerConnections() {
        for (String str : mConnectionIdArray) {
            Peer peer = new Peer((str));
            mConnectionPeerDic.put(str,peer);
        }
    }

    private void createLoaclStream() {
        //添加一个总流
        mMediaStream=mPeerConnectionFactory.createLocalMediaStream("ARDAMS");//需要与后面的ARDAMSa0前缀一致
//        MediaConstraints audioConstraints = createAudioConstraints();
//        AudioSource audioSource = mPeerConnectionFactory.createAudioSource(audioConstraints);
//        //音频是数据源.创建一个音频轨道
//        /**
//         * id规则   ARDAMS + a/v + 轨道号                a：audio音频   v：video视频
//         */
//        AudioTrack audioTrack = mPeerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource);
//        mMediaStream.addTrack(audioTrack);
//        //音频轨道创建成功 添加音频轨道的数据源

        if (videoEnable){
            //录屏的
            //摄像头分前置/后置;camera1/camera2
            //videoCapturer   数据源
            createVideoCapturer("screencast");
        }
    }

    private VideoCapturer createVideoCapturer(String type) {
        VideoCapturer videoCapturer = null;
        switch (type){
            case "camera":
                if (Camera2Enumerator.isSupported(mActivity)){
                    Camera2Enumerator camera2Enumerator = new Camera2Enumerator(mActivity);
                    videoCapturer=createCameraCapture(camera2Enumerator);
                }else{
                    Camera1Enumerator camera1Enumerator = new Camera1Enumerator(true);
                    videoCapturer = createCameraCapture(camera1Enumerator);
                }
                break;
            case "screencast":
                mActivity.startScreenRecord(new ScreenBroadcastListener() {
                    @Override
                    public void getScreenIntent(Intent intent) {
                        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
                            ScreenCapturerAndroid videoCapturer = new ScreenCapturerAndroid(intent, new MediaProjection.Callback() {
                                @Override
                                public void onStop() {
                                    super.onStop();
                                }
                            });
//                            videoCapturer.getMediaProjection()
                            //videoSource 实例化
                            VideoSource videoSource = mPeerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
                            //将videoCapturer绑定到videoSource中
                            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().getName(), mRootEglBase.getEglBaseContext());
                            videoCapturer.initialize(surfaceTextureHelper,mActivity,videoSource.getCapturerObserver());
                            //设置预览
                            videoCapturer.startCapture(1280, 720,0);//参数按顺序： 宽度、高度、关键帧间隔(多久一个I帧)
                            //视频轨道关联
                            VideoTrack videoTrack = mPeerConnectionFactory.createVideoTrack("ARDAMSv0",videoSource);
                            mMediaStream.addTrack(videoTrack);
//                            if (mActivity != null) {
//                                //播放自己的流
//                                mActivity.onSetLocalStream(mMediaStream,myId);
//                            }
                        }
                    }
                });
//                ScreenCapturerAndroid screenCapturerAndroid = new ScreenCapturerAndroid();
//                videoCapturer=createScreenCapture(screenCapturerAndroid);
                break;
            default:
//                ScreenCapturerAndroid screenCapturerAndroid = new ScreenCapturerAndroid();
                break;
        }
        return videoCapturer;
    }

    /**
     * 获取前置摄像头     如果没有则使用后置摄像头
     * @param cameraEnumerator
     * @return   null:没有找到摄像头
     */
    private VideoCapturer createCameraCapture(CameraEnumerator cameraEnumerator) {
        String[] deviceNames = cameraEnumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (cameraEnumerator.isFrontFacing(deviceName)){
                //前置摄像头
                VideoCapturer videoCapturer=cameraEnumerator.createCapturer(deviceName, this);
                if (videoCapturer!=null){
                    return videoCapturer;
                }
            }
        }
        for (String deviceName : deviceNames) {
            if (!cameraEnumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer=cameraEnumerator.createCapturer(deviceName,this);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    private PeerConnectionFactory createConnectionFactory() {
        /**
         * 对PeerConnectionFactory 进行全局初始化
         */
        PeerConnectionFactory.initialize(PeerConnectionFactory
                .InitializationOptions
                .builder(mActivity.getApplicationContext())
                .setFieldTrials("WebRTC-H264Simulcast")
                .createInitializationOptions());
        //编码 分为  音频编码 和 视频编码
        //解码 分为  音频解码 和 视频解码
        VideoEncoderFactory videoEncoderFactory = new DefaultVideoEncoderFactory(mRootEglBase.getEglBaseContext(), true, true);
        VideoDecoderFactory videoDecoderFactory = new DefaultVideoDecoderFactory(mRootEglBase.getEglBaseContext());
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();

        PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
//                .setAudioDeviceModule(JavaAudioDeviceModule.builder(mActivity).createAudioDeviceModule())
                .setVideoDecoderFactory(videoDecoderFactory)
                .setVideoEncoderFactory(videoEncoderFactory)
                .createPeerConnectionFactory();

        return peerConnectionFactory;
    }



    public MediaConstraints createAudioConstraints(){
        //类似于一个hashmap
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(Config.AUDIO_ECHO_CANCELLATION_CONSTRAINT,"true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(Config.AUDIO_NOISE_SUPPRESSION_CONSTRAINT,"true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(Config.AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT,"false"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(Config.AUDIO_HIGH_PASS_FILTER_CONSTRAINT,"true"));
        return audioConstraints;
    }

    /**
     * 配置webRtc的全局上下文
     *
     * @param activity
     * @param eglBase
     */
    public void initContext(VideoChatActivity activity, EglBase eglBase) {
        mActivity=activity;
        mRootEglBase = eglBase;
    }

    @Override
    public void onCameraError(String s) {

    }

    @Override
    public void onCameraDisconnected() {

    }

    @Override
    public void onCameraFreezed(String s) {

    }

    @Override
    public void onCameraOpening(String s) {

    }

    @Override
    public void onFirstFrameAvailable() {

    }

    @Override
    public void onCameraClosed() {

    }

    public void onReceiverAnswer(String socketId, String sdp) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                //找到要被链接的那个对象
                Peer peer = mConnectionPeerDic.get(socketId);
                if (peer!=null){
                    String newSdp = preferCodec(sdp,VIDEO_CODEC_H264, false);
                    SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, newSdp);
                    peer.pc.setRemoteDescription(peer,sessionDescription);//设置远端对象的sdp  设置成功以后会回调 addStream（）
                }
            }
        });
    }

    /**
     * 尝试在路由中添加这个记录
     * @param socketId
     * @param iceCandidate
     */
    public void onRemoteIceCandidate(String socketId, IceCandidate iceCandidate) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Peer peer = mConnectionPeerDic.get(socketId);
                if (peer != null) {
//                    调用addIceCandidate 当前客户端会在内部请求路由器，使路由器的路由表中存储对方客户端的路由信息
                    peer.pc.addIceCandidate(iceCandidate);
                }
            }
        });
    }

    /**
     * 添加远端新加入房间的流到我们本地客户端
     * @param socketId
     */
    public void onRemoteJoinToRoom(String socketId) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mMediaStream == null && mIsSpeaker) {
                    createLoaclStream();
                }
                //PeerConnection链接
                Peer mPeer = new Peer(socketId);
                mPeer.pc.addStream(mMediaStream);
                mConnectionIdArray.add(socketId);
                mConnectionPeerDic.put(socketId, mPeer);
            }
        });
    }


    /**
     * 新建链接
     * @param socketId
     * @param sdpStr
     */
    public void onReceiveOffer(String socketId, String sdpStr) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                //                 角色  1    主叫  被叫  2
                role = Role.Receiver;
                Peer mPeer = mConnectionPeerDic.get(socketId);
                SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER, sdpStr);
                if (mPeer != null) {
                    mPeer.pc.setRemoteDescription(mPeer, sdp);//此时会走当前客户端的setSuccess，同样主叫端的setSuccess也会被触发。
                }
            }
        });
    }


    /**
     * 存储的是每一个远端的链接
     */
    private class Peer implements SdpObserver, PeerConnection.Observer{
        private PeerConnection pc;
        private String targetSocketId;

        public Peer(String targetSocketId) {
            this.targetSocketId = targetSocketId;
            pc = createPeerConnection();
        }

        public PeerConnection getPc() {
            return pc;
        }

        public void setPc(PeerConnection pc) {
            this.pc = pc;
        }

        public String getTargetSocketId() {
            return targetSocketId;
        }

        public void setTargetSocketId(String targetSocketId) {
            this.targetSocketId = targetSocketId;
        }

        private PeerConnection createPeerConnection(){
            if (mPeerConnectionFactory == null) {
                mPeerConnectionFactory=createConnectionFactory();
            }
            //创建PeerConnection
            PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(mICEServers);//mICEServers 服务器可以有多个，如果第一个失败则会找下一个
            return mPeerConnectionFactory.createPeerConnection(rtcConfiguration,this);
        }

        /**
         *
         * @param origSdp  包含路由的地址，音频视频所走的端口
         */
        @Override
        public void onCreateSuccess(SessionDescription origSdp) {
//            Log.e(TAG, "onCreateSuccess:    SDP回写成功：\n"+origSdp.description);
            Log.e(TAG, "onCreateSuccess:    SDP回写成功");
            //设置好双方的sdp，即可建立联系
            String newSdp = preferCodec(origSdp.description,VIDEO_CODEC_H264, false);
            //设置本地的sdp
            pc.setLocalDescription(Peer.this,new SessionDescription(origSdp.type,newSdp)); //设置成功则走 onSetSuccess    设置失败则走  onSetFailure
            //设置远端的sdp   客户端B的SDP
//            pc.setRemoteDescription(Peer.this,origSdp);
        }

        @Override
        public void onSetSuccess() {
            //链接管道
            /**
             * 1:只是设置了本地的sdp（setLocalDescription），没有设置远端   通过当前状态判断当前客户端属于主叫还是被叫   主叫：发送call   被叫：发送anser
             * 2：设置了远端的sdp（setRemoteDescription）， 此时不能通信
             * 3:ice交换      此时sdp都交换完了
             */
            Log.e(TAG, "onSetSuccess: ");
            PeerConnection.SignalingState signalingState = pc.signalingState();
            if (signalingState==PeerConnection.SignalingState.HAVE_LOCAL_OFFER){
                //只是设置了本地的sdp
                if (role==Role.Caller){
                    //当前是主叫
                    //通过socket 将当前客户端的sdp发送给其他客户端
                    mWebSocketManager.sendOffer(targetSocketId,pc.getLocalDescription().description);//发送SDP到被叫SocketId客户端
                }else if (role==Role.Caller){
                    //当前是被叫 来了新的客户端，把自己的sdp给到别人
                    mWebSocketManager.sendAnswer(targetSocketId,pc.getLocalDescription().description);//发送SDP到主叫SocketId客户端
                }
            }else if (signalingState==PeerConnection.SignalingState.HAVE_REMOTE_OFFER){
                //因为初始化时就设置本地的sdp，所以走到这个判断代码块时，本地的sdp肯定设置好了。
                //设置了远端的sdp
                //通知webRtc请求对方的路由器：设置ICE
                pc.createAnswer(Peer.this, offerOrAnswerConstraint());
            }else if (signalingState==PeerConnection.SignalingState.STABLE){
                //ice打洞完成，交换完了
                //开始链接
                //设置发送的配置信息
                setSenderSetting();
                if (role == Role.Receiver) {
                    Log.e(TAG, "onSetSuccess: 打洞完成，把自己的sdp发送给主叫");
                    mWebSocketManager.sendAnswer(targetSocketId, pc.getLocalDescription().description);
                }
            }
        }

        /**
         * 设置发送的配置信息
         */
        private void setSenderSetting() {
            Log.e(TAG, "Peer:  setSenderSetting    getSenders:"+ JSON.toJSON(pc.getSenders()));
            for (RtpSender sender : pc.getSenders()) {
                if (sender.track().kind().equals("video")) {
                    RtpParameters parameters = sender.getParameters();
                    parameters.degradationPreference=MAINTAIN_FRAMERATE;//保证帧率平稳优先
                    if (parameters.encodings.size()>0) {
                        RtpParameters.Encoding encoding = parameters.encodings.get(0);
                        encoding.maxFramerate = 30;//最高帧数
                        encoding.maxBitrateBps = 3000 * BPS_IN_KBPS;//最大码率  720p建议2000-3000kbs之间
                        encoding.minBitrateBps = 2000 * BPS_IN_KBPS;//最低码率
                        sender.setParameters(parameters);
                    }
                }
            }
        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }

/*------------------------------------------------------------------------------------------------------------------------------------------------------------------*/
        /**   IceObserver   SDP交换完了之后才需要用到 PeerConnection.Observer       听从打洞服务器的指挥  */

        /**
         * 对方同意或拒绝
         * @param signalingState
         */
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        /**
         * wifi 切换到手机网络时，会话协议会进行改变,链接状态change。
         * @param iceConnectionState
         */
        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        /**
         * 指挥客户端A  进行请求对方的服务器
         * @param iceCandidate  本机----我到我的路由器所要经过的地址
         */
        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Log.e(TAG, "onIceCandidate: "+iceCandidate);
            mWebSocketManager.sendIceCandidate(targetSocketId,iceCandidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        /**
         * ice交换完成后会回调这个函数
         * @param mediaStream 远端的流
         */
        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.e(TAG, "onAddStream: ");
            mActivity.onAddRoomStream(mediaStream,this.targetSocketId);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }
/*------------------------------------------------------------------------------------------------------------------------------------------------------------------*/
    }

    public interface ScreenBroadcastListener{
        /**
         * 获取屏幕录制权限返回的intent
         */
        void getScreenIntent(Intent intent);
    }


    private static String preferCodec(String sdp, String codec, boolean isAudio) {
        final String[] lines = sdp.split("\r\n");
        final int mLineIndex = findMediaDescriptionLine(isAudio, lines);
        if (mLineIndex == -1) {
            Log.w(TAG, "No mediaDescription line, so can't prefer " + codec);
            return sdp;
        }
        // A list with all the payload types with name |codec|. The payload types are integers in the
        // range 96-127, but they are stored as strings here.
        final List<String> codecPayloadTypes = new ArrayList<>();
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        final Pattern codecPattern = Pattern.compile("^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$");
        for (String line : lines) {
            Matcher codecMatcher = codecPattern.matcher(line);
            if (codecMatcher.matches()) {
                codecPayloadTypes.add(codecMatcher.group(1));
            }
        }
        if (codecPayloadTypes.isEmpty()) {
            Log.w(TAG, "No payload types with name " + codec);
            return sdp;
        }

        final String newMLine = movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex]);
        if (newMLine == null) {
            return sdp;
        }
        Log.d(TAG, "Change media description from: " + lines[mLineIndex] + " to " + newMLine);
        lines[mLineIndex] = newMLine;
        return joinString(Arrays.asList(lines), "\r\n", true /* delimiterAtEnd */);
    }

    /** Returns the line number containing "m=audio|video", or -1 if no such line exists. */
    private static int findMediaDescriptionLine(boolean isAudio, String[] sdpLines) {
        final String mediaDescription = isAudio ? "m=audio " : "m=video ";
        for (int i = 0; i < sdpLines.length; ++i) {
            if (sdpLines[i].startsWith(mediaDescription)) {
                return i;
            }
        }
        return -1;
    }

    private static @Nullable String movePayloadTypesToFront(
            List<String> preferredPayloadTypes, String mLine) {
        // The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
        final List<String> origLineParts = Arrays.asList(mLine.split(" "));
        if (origLineParts.size() <= 3) {
            Log.e(TAG, "Wrong SDP media description format: " + mLine);
            return null;
        }
        final List<String> header = origLineParts.subList(0, 3);
        final List<String> unpreferredPayloadTypes =
                new ArrayList<>(origLineParts.subList(3, origLineParts.size()));
        unpreferredPayloadTypes.removeAll(preferredPayloadTypes);
        // Reconstruct the line with |preferredPayloadTypes| moved to the beginning of the payload
        // types.
        final List<String> newLineParts = new ArrayList<>();
        newLineParts.addAll(header);
        newLineParts.addAll(preferredPayloadTypes);
        newLineParts.addAll(unpreferredPayloadTypes);
        return joinString(newLineParts, " ", false /* delimiterAtEnd */);
    }

    private static String joinString(
            Iterable<? extends CharSequence> s, String delimiter, boolean delimiterAtEnd) {
        Iterator<? extends CharSequence> iter = s.iterator();
        if (!iter.hasNext()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder(iter.next());
        while (iter.hasNext()) {
            buffer.append(delimiter).append(iter.next());
        }
        if (delimiterAtEnd) {
            buffer.append(delimiter);
        }
        return buffer.toString();
    }
}
