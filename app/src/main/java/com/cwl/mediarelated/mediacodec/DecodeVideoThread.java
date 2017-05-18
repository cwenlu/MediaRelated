package com.cwl.mediarelated.mediacodec;

import android.annotation.TargetApi;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Administrator on 2017/2/5 0005.
 * MediaCodec+MediaExtractor
 */

public class DecodeVideoThread extends Thread {

    //媒体信息提取器
    private MediaExtractor mediaExtractor;
    private MediaCodec mediaCodec;
    private boolean running = true;

    @RequiresApi(api = Build.VERSION_CODES.N)
    public DecodeVideoThread(Surface surface, AssetFileDescriptor assetFileDescriptor) {
        mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(assetFileDescriptor);
        } catch (IOException e) {
            e.printStackTrace();
        }
        init(surface);
    }

    public DecodeVideoThread(Surface surface, String filePath) {
        mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        init(surface);
    }

    //利用的mediaextractor
    private void init(Surface surface) {
        int trackCount = mediaExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
            String mime = trackFormat.getString(MediaFormat.KEY_MIME);

            if (mime.startsWith("video/")) {
                Log.i("tag", mime);
                Log.i("tag",Arrays.toString(trackFormat.getByteBuffer("csd-0").array()));
                Log.i("tag",Arrays.toString(trackFormat.getByteBuffer("csd-1").array()));
                mediaExtractor.selectTrack(i);
                try {
                    mediaCodec = MediaCodec.createDecoderByType(mime);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mediaCodec.configure(trackFormat, surface, null, 0);
                mediaCodec.start();
                break;
            }
        }
    }




    @Override
    public void run() {
        super.run();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

        boolean needInput = true;
        boolean first = true;
        long startWhen = 0;
        while (running) {
            if (needInput) {
                int inputIndex = mediaCodec.dequeueInputBuffer(10 * 1000);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        //新版api，测试
                        Log.i("tag", "aaaaaaaaa");
                        inputBuffer = mediaCodec.getInputBuffer(inputIndex);
                    } else {
                        inputBuffer = inputBuffers[inputIndex];
                    }
                    int sampleDataSize = mediaExtractor.readSampleData(inputBuffer, 0);
                    if (mediaExtractor.advance() && sampleDataSize > 0) {
                        mediaCodec.queueInputBuffer(inputIndex, 0, sampleDataSize, mediaExtractor.getSampleTime(), 0);
                    } else {
                        mediaCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        needInput = false;
                    }
                }
            }

            int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10 * 1000);
            switch (outputIndex) {
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.i("tag", "output format" + mediaCodec.getOutputFormat());
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.i("tag", "如果前面指定了超时值，这个则表明超时");
                    break;
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    outputBuffers = mediaCodec.getOutputBuffers();
                    Log.i("tag", "输出缓冲区更改");
                    break;
                default:
                    if (first) {
                        first = false;
                        startWhen = System.currentTimeMillis();
                    }
                    //presentationTimeUs表示的是展示的时间，单位是微妙
                    long sleepTime = bufferInfo.presentationTimeUs / 1000 - (System.currentTimeMillis() - startWhen);
                    //这样处理只能保证播放不变快，没有处理播放太慢的情况
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    mediaCodec.releaseOutputBuffer(outputIndex, true);


            }

            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                //结束循环
                break;
            }
        }
        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;
        mediaExtractor.release();
    }

    public void close() {
        running = false;
    }
}
