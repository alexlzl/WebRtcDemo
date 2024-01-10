package com.xunua.webrtcdemo;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.xunua.webrtcdemo.databinding.ActivityVideoChatBinding;
import com.xunua.webrtcdemo.utils.PermissionUtil;
import com.xunua.webrtcdemo.webRtc.PeerConnectionManager;
import com.xunua.webrtcdemo.webRtc.WebRtcManager;

import org.webrtc.EglBase;
import org.webrtc.MediaStream;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoChatActivity extends AppCompatActivity {
    private static final String TAG = "xunyou";
    private ActivityVideoChatBinding mBinding;
    private WebRtcManager mWebRtcManager;
    private EglBase mRootEglBase;
    private String mRoomId;
    private VideoTrack mLocalVideoTrack;//本地的视频轨道
    private Map<String,SurfaceViewRenderer> mVideoViews =new HashMap<>();//房间里所有ID列表对应的view
    private static final int SCREEN_CAPTURE_REQUEST_CODE=10011;
    private PeerConnectionManager.ScreenBroadcastListener mScreenBroadcastListener;

    public static void startActivity(Activity activity,String roomId){
        Intent intent = new Intent(activity,VideoChatActivity.class);
        intent.putExtra("roomId",roomId);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mBinding=ActivityVideoChatBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        mRoomId=getIntent().getStringExtra("roomId");
        mWebRtcManager=WebRtcManager.getInstance();
        initView();
    }

    /**
     * 开启屏幕广播
     */
    public void startScreenRecord(PeerConnectionManager.ScreenBroadcastListener mScreenBroadcastListener) {
        this.mScreenBroadcastListener=mScreenBroadcastListener;
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(screenCaptureIntent,SCREEN_CAPTURE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode==SCREEN_CAPTURE_REQUEST_CODE){
            this.mScreenBroadcastListener.getScreenIntent(intent);
        }
    }


    private void initView() {
        mRootEglBase=EglBase.create();
        if (!PermissionUtil.isNeedRequestPermission(this)) {
            mWebRtcManager.joinRoom(this,mRoomId,mRootEglBase);
        }
    }

    /**
     * 本地的视频流
     * @param stream
     * @param userId
     */
    public void onSetLocalStream(MediaStream stream,String userId){
        //总流
        if (stream.videoTracks.size()>0){
            mLocalVideoTrack = stream.videoTracks.get(0);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                addView(userId,stream);
            }
        });
    }

    /**
     * 远端的视频流
     * @param mediaStream
     * @param userId
     */
    public void onAddRoomStream(MediaStream mediaStream, String userId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "onAddRoomStream  run:   userId:"+userId);
                addView(userId,mediaStream);
            }
        });
    }

    private void addView(String userId, MediaStream stream) {
//        //使用SurfaceViewRenderer创建SurfaceView
//        SurfaceViewRenderer surfaceViewRenderer = new SurfaceViewRenderer(this);

        //初始化SurfaceViewRenderer
        mBinding.surfaceViewRenderer.init(mRootEglBase.getEglBaseContext(),null);
        //设置缩放模式
        mBinding.surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        //设置视频流是否应该水平镜像
        mBinding.surfaceViewRenderer.setMirror(false);
        //是否打开硬件进行拉伸
//        mBinding.surfaceViewRenderer.setEnableHardwareScaler(false);
//        mBinding.surfaceViewRenderer.setFpsReduction(30);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mBinding.surfaceViewRenderer.setLayoutParams(layoutParams);


        //关联
        if (stream.videoTracks.size() > 0) {
            stream.videoTracks.get(0).addSink(mBinding.surfaceViewRenderer);
        }
//        mVideoViews.put(userId, mBinding.surfaceViewRenderer);
//        int size = mVideoViews.size();
//        for (int i = 0; i < size; i++) {
////            mBinding.surfaceViewRenderer  setLayoutParams
//            String peerId = mPersons.get(i);
//            SurfaceViewRenderer renderer1 = mVideoViews.get(peerId);
//            if (renderer1 != null) {
//                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
//                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
//                layoutParams.width = 2560;
//                layoutParams.height = 1600;
//                renderer1.setLayoutParams(layoutParams);
//            }
//        }
    }
}