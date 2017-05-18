package com.cwl.mediarelated.camera;

import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import com.cwl.mediarelated.R;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;
import static android.os.Build.VERSION_CODES.M;

public class EncodeFromCameraActivity extends AppCompatActivity {

    @BindView(R.id.start)
    Button start;
    @BindView(R.id.end)
    Button end;
    @BindView(R.id.preview)
    SurfaceView preview;
    private Camera camera;
    private MediaCodec mediaCodec;
    private MediaMuxer mediaMuxer;
    volatile boolean running=true;
    FileOutputStream fileOutputStream = null;
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_encode_from_camera);
        ButterKnife.bind(this);
        try {
            mediaMuxer = new MediaMuxer(Environment.getExternalStorageDirectory().getAbsolutePath() + "/camera_out", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }

        preview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    initCamera();
                    initMediacodec();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                try {
                    camera.setPreviewDisplay(holder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                camera.startPreview();

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                uninitCamera();
            }
        });
    }

    private void initCamera() throws IOException {
        camera = Camera.open(/*Camera.CameraInfo.CAMERA_FACING_FRONT*/);
        camera.setPreviewDisplay(preview.getHolder());
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            //调整预览方向
            camera.setDisplayOrientation(90);
        }
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewSize(640, 480);
        parameters.setPictureSize(640, 480);
        //使用nv21录出来的视频会出现些微色块
        parameters.setPreviewFormat(/*ImageFormat.NV21*/ImageFormat.YV12);
        camera.setParameters(parameters);

    }

    private void uninitCamera() {
        camera.stopPreview();
        camera.release();
        camera = null;
    }

    @TargetApi(M)
    private void initMediacodec() throws IOException {
        mediaCodec = MediaCodec.createEncoderByType("video/avc");
        MediaFormat videoFormat = MediaFormat.createVideoFormat("video/avc", 640, 480);
        //使用颜色空间最好遍历支持性然后设置
        int[] colorFormats = mediaCodec.getCodecInfo().getCapabilitiesForType("video/avc").colorFormats;
        for (int colorFormat : colorFormats) {
            Log.i("tag", Integer.toHexString(colorFormat));
        }
        //注意一些key是必须设置的，参考MediaFormat文档
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1300 * 1000);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        mediaCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private void unitMediacodec() {
        if (mediaCodec != null) {
            mediaCodec.release();
            mediaCodec = null;
        }

    }

    long recordTime;
    private void encodeVideo() {
        camera.addCallbackBuffer(new byte[640 * 480 * 3 / 2]);
        mediaCodec.start();
        recordTime = System.nanoTime();
        new Thread(){
            @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
            @Override
            public void run() {
                super.run();
                try {
                    fileOutputStream=new FileOutputStream(Environment.getExternalStorageDirectory().getAbsolutePath()+"/out_camera.h264");
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                while (running){

                   camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                       @Override
                       public void onPreviewFrame(byte[] data, Camera camera) {
                           addVideoTrack(data);
                           camera.addCallbackBuffer(data);
                       }
                   });
                }

                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mediaMuxer.release();
                unitMediacodec();

            }
        }.start();


    }

    MediaCodec.BufferInfo bufferInfo=new MediaCodec.BufferInfo();
    int videoTrack;
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void addVideoTrack(byte[] data){

        int inputIndex = mediaCodec.dequeueInputBuffer(1000);
        if(inputIndex>=0){

            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputIndex);
            inputBuffer.put(data);
            mediaCodec.queueInputBuffer(inputIndex,0,data.length,Math.abs(System.nanoTime()-recordTime)/1000,0);


        }

        int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 1000);
        switch (outputIndex){
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                videoTrack=mediaMuxer.addTrack(mediaCodec.getOutputFormat());
                mediaMuxer.start();
                break;
            case MediaCodec.INFO_TRY_AGAIN_LATER:
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                break;
            default:
                if(outputIndex>=0){

                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputIndex);
                    //file write
                    try {
                        byte[] bytes=new byte[outputBuffer.limit()];
                        outputBuffer.get(bytes);
                        outputBuffer.flip();
                        fileOutputStream.write(bytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }//
                    mediaMuxer.writeSampleData(videoTrack,outputBuffer,bufferInfo);
                    mediaCodec.releaseOutputBuffer(outputIndex,false);
                }
        }

    }


    @OnClick({R.id.start, R.id.end})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.start:
                encodeVideo();
                break;
            case R.id.end:
                running=false;
                //这里需要注意清空回调，不然结束的最后一次添加的回调引发下一次的处理，然而那个时候已经执行了unitMediacodec，引发空指针
                camera.setPreviewCallbackWithBuffer(null);
                break;
        }
    }
}
