package com.xunua.webrtcdemo;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.xunua.webrtcdemo.databinding.ActivityMainBinding;
import com.xunua.webrtcdemo.webRtc.WebRtcManager;

public class MainActivity extends AppCompatActivity{
    private ActivityMainBinding mBinding;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        mBinding=ActivityMainBinding.inflate(getLayoutInflater());
        super.onCreate(savedInstanceState);
        setContentView(mBinding.getRoot());
        initView();
    }

    private void initView() {
        initListener();
    }

    private void initListener() {
        mBinding.joinRoomBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String roomId=mBinding.roomIdEt.getText().toString();
                WebRtcManager.getInstance().connect(MainActivity.this,roomId,mBinding.isSpeakerCheck.isChecked());
            }
        });
    }
}