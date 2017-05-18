package com.cwl.mediarelated.mediacodec;

import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.cwl.mediarelated.R;
import com.cwl.mediarelated.util.ToastUtils;

import java.io.IOException;
import java.io.InputStream;

import butterknife.BindView;
import butterknife.ButterKnife;

public class DecodeActivity extends AppCompatActivity {

    @BindView(R.id.preview)
    SurfaceView preview;
    //低版本同步方式(含新的api方式)
    DecodeVideoThread decodeVideoThread;
    //新版本异步方式
    DecodeVideoThread2 decodeVideoThread2;
    private DecodeAudioThread decodeAudioThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_decode);
        ButterKnife.bind(this);
        decodeAudioThread = new DecodeAudioThread(Environment.getExternalStorageDirectory().getAbsolutePath()+"/input.mp4");
        decodeAudioThread.start();
        preview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                decodeVideoThread=new DecodeVideoThread(holder.getSurface(), Environment.getExternalStorageDirectory().getAbsolutePath()+"/input.mp4");
                decodeVideoThread.start();
//                decodeVideoThread2=new DecodeVideoThread2(holder.getSurface(),Environment.getExternalStorageDirectory().getAbsolutePath()+"/pptv/download/邻家花美男(第10集)[高清].mp4");
//                decodeVideoThread2.start();

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
//                decodeVideoThread.close();
                decodeVideoThread2.close();
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        decodeVideoThread.close();
        decodeAudioThread.close();
    }
}
