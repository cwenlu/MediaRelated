package com.cwl.mediarelated.camera;

import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;

import com.cwl.mediarelated.R;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class EncodeFromSurfaceActivity extends AppCompatActivity {

    @BindView(R.id.start)
    Button start;
    @BindView(R.id.preview)
    SurfaceView preview;
    Camera camera;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_encode_from_surface);
        ButterKnife.bind(this);

        preview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    initCamera();
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

    @OnClick(R.id.start)
    public void onClick() {
        try {
            flow();
//            flow2();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void flow() throws IOException {

        final MediaMuxer mediaMuxer = new MediaMuxer(Environment.getExternalStorageDirectory().getAbsolutePath() + "/surface_out", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", 640, 480);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1300 * 1000);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);

        final MediaCodec mediaCodec = MediaCodec.createEncoderByType("video/avc");
        mediaCodec.setCallback(new MediaCodec.Callback() {
            int videoTrack;

            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {

            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                System.out.println(outputBuffer.limit() + "===");
                mediaMuxer.writeSampleData(videoTrack, outputBuffer, info);
                codec.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    mediaCodec.release();
                    mediaMuxer.release();
                    //为了方便看出流程没有写全局变量，这里应该释放
//                    inputSurface.release();
                }

            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {

            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                videoTrack = mediaMuxer.addTrack(format);
                mediaMuxer.start();
            }
        });
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//        methoh 1
        //必须在configure之后，start之前
        final Surface inputSurface = mediaCodec.createInputSurface();

//        method 2
//        final Surface inputSurface = MediaCodec.createPersistentInputSurface();
//        //必须在configure之后，start之前
//        mediaCodec.setInputSurface(inputSurface);
        mediaCodec.start();


        new Thread() {
            @Override
            public void run() {
                super.run();
                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setTextSize(60);
                paint.setColor(Color.RED);
                paint.setTextAlign(Paint.Align.CENTER);

                long startTime=System.currentTimeMillis();
                while(System.currentTimeMillis()-startTime<10*1000){
                    Canvas canvas = inputSurface.lockCanvas(null);
                    canvas.drawColor(Color.WHITE);
                    canvas.drawText(String.valueOf(new Random().nextInt(100)),canvas.getWidth()/2,canvas.getHeight()/2,paint);
                    inputSurface.unlockCanvasAndPost(canvas);
                    try {
                        Thread.sleep(60);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                //因为surface作为输入时InputBuffer是不可用的，所以这个方法发送EOS信号
                mediaCodec.signalEndOfInputStream();
                Log.i("tag", "finish");

            }
        }.start();

    }

    @TargetApi(Build.VERSION_CODES.M)
    private void flow2() throws IOException {

        final MediaMuxer mediaMuxer = new MediaMuxer(Environment.getExternalStorageDirectory().getAbsolutePath() + "/surface_out", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", 640, 480);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1300 * 1000);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);

        final MediaCodec mediaCodec = MediaCodec.createEncoderByType("video/avc");
        mediaCodec.setCallback(new MediaCodec.Callback() {
            int videoTrack;

            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {

            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                System.out.println(outputBuffer.limit() + "===");
                mediaMuxer.writeSampleData(videoTrack, outputBuffer, info);
                codec.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    mediaCodec.release();
                    mediaMuxer.release();
                    //为了方便看出流程没有写全局变量，这里应该释放
//                    inputSurface.release();
                }

            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {

            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                videoTrack = mediaMuxer.addTrack(format);
                mediaMuxer.start();
            }
        });
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        final Surface persistentInputSurface = MediaCodec.createPersistentInputSurface();
        //必须在configure之后，start之前
        mediaCodec.setInputSurface(persistentInputSurface);
        mediaCodec.start();


        new Thread() {
            @Override
            public void run() {
                super.run();
                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setTextSize(60);
                paint.setColor(Color.RED);
                paint.setTextAlign(Paint.Align.CENTER);

                long startTime=System.currentTimeMillis();
                while(System.currentTimeMillis()-startTime<10*1000){

                    Canvas canvas = persistentInputSurface.lockCanvas(null);

                    persistentInputSurface.unlockCanvasAndPost(canvas);
                    try {
                        Thread.sleep(60);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }


                //因为surface作为输入时InputBuffer是不可用的，所以这个方法发送EOS信号
                mediaCodec.signalEndOfInputStream();
                Log.i("tag", "finish");

            }
        }.start();


    }
}
