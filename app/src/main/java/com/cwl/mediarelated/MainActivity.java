package com.cwl.mediarelated;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.cwl.mediarelated.camera.CameraActivity;
import com.cwl.mediarelated.camera.EncodeFromCameraActivity;
import com.cwl.mediarelated.camera.EncodeFromSurfaceActivity;
import com.cwl.mediarelated.mediacodec.DecodeActivity;
import com.cwl.mediarelated.mediacodec.EncodeAudioThread;
import com.cwl.mediarelated.util.CodecUtil;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.list)
    ListView list;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        String[] strings={"camera","从camera获取视频编码为mp4保存","解码视频","音视频提取与合成","音频录制与编码","从surface编码视频"};
        list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,strings));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position){
                    case 0:
                        showActivity(CameraActivity.class);
                        break;
                    case 1:
                        showActivity(EncodeFromCameraActivity.class);
                        break;
                    case 2:
                        showActivity(DecodeActivity.class);
                        break;
                    case 3:
//                        MediaMuxerUtil.trackExtractor(Environment.getExternalStorageDirectory().getPath()+"/kkk.mp4",Environment.getExternalStorageDirectory().getPath()+"/out_video",true);
//                        MediaMuxerUtil.trackExtractor(Environment.getExternalStorageDirectory().getPath()+"/kkk.mp4",Environment.getExternalStorageDirectory().getPath()+"/out_audio",false);
//                        MediaMuxerUtil.trackMuxer(Environment.getExternalStorageDirectory().getPath()+"/out_video",Environment.getExternalStorageDirectory().getPath()+"/out_audio",Environment.getExternalStorageDirectory().getPath()+"/out_out");
//                       MediaMuxerUtil.clipVideo(Environment.getExternalStorageDirectory().getPath()+"/input.mp4",Environment.getExternalStorageDirectory().getPath()+"/clip.mp4",10000,25000);
                        break;
                    case 4:
                        final EncodeAudioThread encodeAudioThread = new EncodeAudioThread();
                        encodeAudioThread.start();
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                encodeAudioThread.stopThread();
                            }
                        },10*1000);
                        break;
                    case 5:
                        showActivity(EncodeFromSurfaceActivity.class);
                        break;
                }
            }
        });

//        CodecUtil.supptedCodec("audio/mpeg");
    }

    private void showActivity(Class<? extends AppCompatActivity> compatActivityClass){
        startActivity(new Intent(this,compatActivityClass));

    }





}

