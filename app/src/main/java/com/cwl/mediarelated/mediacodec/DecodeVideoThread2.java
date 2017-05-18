package com.cwl.mediarelated.mediacodec;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2017/2/5 0005.
 * MediaCodec+MediaExtractor
 * 采用新api异步方式
 */

public class DecodeVideoThread2 extends Thread {

    //媒体信息提取器
    private MediaExtractor mediaExtractor;
    private MediaCodec mediaCodec;
    private boolean running=true;

    @RequiresApi(api = Build.VERSION_CODES.N)
    public DecodeVideoThread2(Surface surface, AssetFileDescriptor assetFileDescriptor){
        mediaExtractor=new MediaExtractor();
        try {
            mediaExtractor.setDataSource(assetFileDescriptor);
        } catch (IOException e) {
            e.printStackTrace();
        }
        init(surface);
    }

    public DecodeVideoThread2(Surface surface, String filePath){
        mediaExtractor=new MediaExtractor();
        try {
            mediaExtractor.setDataSource(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        init(surface);
    }

    //利用的mediaextractor
    private void init(Surface surface){
        int trackCount = mediaExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
            String mime = trackFormat.getString(MediaFormat.KEY_MIME);
            if(mime.startsWith("video/")){
                Log.i("tag",mime);
                mediaExtractor.selectTrack(i);
                try {
                    mediaCodec = MediaCodec.createDecoderByType(mime);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mediaCodec.setCallback(new MediaCodec.Callback() {
                        boolean first=true;
                        long startWhen;

                        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                        @Override
                        public void onInputBufferAvailable(MediaCodec codec, int index) {

                            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(index);
                            int sampleDataSize = mediaExtractor.readSampleData(inputBuffer, 0);
                            if(sampleDataSize>0){
                                mediaCodec.queueInputBuffer(index,0,sampleDataSize,mediaExtractor.getSampleTime(),0);
                                mediaExtractor.advance();
                            }else{
                                mediaCodec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            }


                        }

                        @Override
                        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                            if(first){
                                first=false;
                                startWhen=System.currentTimeMillis();
                            }
                            long sleepTime=info.presentationTimeUs/1000-(System.currentTimeMillis()-startWhen);
                            if(sleepTime>0){
                                try {
                                    Thread.sleep(sleepTime);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            mediaCodec.releaseOutputBuffer(index,true);

//                            //下面这样设置并没有什么卵用，姿势不对？
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                                mediaCodec.releaseOutputBuffer(index,info.presentationTimeUs*1000);
//                            }
                        }

                        @Override
                        public void onError(MediaCodec codec, MediaCodec.CodecException e) {

                        }

                        @Override
                        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {

                        }
                    });
                }
                mediaCodec.configure(trackFormat,surface,null,0);
                mediaCodec.start();
                break;
            }
        }
    }


    public void close(){
        running=false;

        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec=null;
        mediaExtractor.release();
    }
}
