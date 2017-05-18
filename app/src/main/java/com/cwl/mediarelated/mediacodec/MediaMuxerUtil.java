package com.cwl.mediarelated.mediacodec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by chenwenlu on 2017/2/14 0014.
 * 随便演示了下，有耗时，不能ui线程中运用
 */

public class MediaMuxerUtil {
    /**
     * 提取音/视频

     * @param inPath
     * @param outPath
     * @param isVideo
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static void trackExtractor(String inPath, String outPath, boolean isVideo) {
        //提取器
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(inPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        MediaMuxer mediaMuxer = null;
        //复用器
        try {
            mediaMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }

        int trackIndex = 0;
        int trackCount = mediaExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
            String mime = trackFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/") && isVideo) {
                trackIndex = mediaMuxer.addTrack(trackFormat);
                mediaExtractor.selectTrack(i);
                break;
            } else if (mime.startsWith("audio/") && !isVideo) {
                trackIndex = mediaMuxer.addTrack(trackFormat);
                mediaExtractor.selectTrack(i);
                break;
            }
        }
        mediaMuxer.start();

        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1000);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        long videoSampleTime=getSampleTime(mediaExtractor,buffer);
        while (true) {
            int readSampleDataSize = mediaExtractor.readSampleData(buffer, 0);
            if (readSampleDataSize < 0) {
                break;
            }
            bufferInfo.size = readSampleDataSize;
            bufferInfo.offset = 0;
            bufferInfo.flags = mediaExtractor.getSampleFlags();
            bufferInfo.presentationTimeUs += videoSampleTime;
            mediaMuxer.writeSampleData(trackIndex, buffer, bufferInfo);
            mediaExtractor.advance();
        }

        mediaExtractor.release();
        mediaMuxer.stop();
        //内部也会执行stop，所以可以不用执行stop
        mediaMuxer.release();


    }

    private static long getSampleTime(MediaExtractor mediaExtractor, ByteBuffer buffer) {
        long videoSampleTime;
//            mediaExtractor.readSampleData(buffer, 0);
//            //skip first I frame
//            if (mediaExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC)
//                mediaExtractor.advance();
        mediaExtractor.readSampleData(buffer, 0);
        long firstVideoPTS = mediaExtractor.getSampleTime();
        mediaExtractor.advance();
        mediaExtractor.readSampleData(buffer, 0);
        long SecondVideoPTS = mediaExtractor.getSampleTime();
        videoSampleTime = Math.abs(SecondVideoPTS - firstVideoPTS);
        Log.d("MediaMuxerUtil", "videoSampleTime is " + videoSampleTime);
        return videoSampleTime;
    }


    /**
     * 合成音视频
     *
     * @param videoPath
     * @param audioPath
     * @param outPath
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static void trackMuxer(String videoPath, String audioPath, String outPath) {
        MediaExtractor videoExtractor = new MediaExtractor();
        try {
            videoExtractor.setDataSource(videoPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat videoFormat = null;
        int videoTrackIndex = -1;
        int videoTrackCount = videoExtractor.getTrackCount();
        for (int i = 0; i < videoTrackCount; i++) {
            videoFormat = videoExtractor.getTrackFormat(i);
            String mime = videoFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                videoTrackIndex = i;
                break;
            }
        }

        MediaExtractor audioExtractor = new MediaExtractor();
        try {
            audioExtractor.setDataSource(audioPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat audioFormat = null;
        int audioTrackIndex = -1;
        int audioTrackCount = audioExtractor.getTrackCount();
        for (int i = 0; i < audioTrackCount; i++) {
            audioFormat = audioExtractor.getTrackFormat(i);
            String mime = audioFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i;
                break;
            }
        }

        videoExtractor.selectTrack(videoTrackIndex);
        audioExtractor.selectTrack(audioTrackIndex);

        MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

        MediaMuxer mediaMuxer = null;
        try {
            mediaMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int writeVideoTrackIndex = mediaMuxer.addTrack(videoFormat);
        int writeAudioTrackIndex = mediaMuxer.addTrack(audioFormat);
        mediaMuxer.start();
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1000);
        long videoSampleTime=getSampleTime(videoExtractor,byteBuffer);
        while (true) {
            int readVideoSampleSize = videoExtractor.readSampleData(byteBuffer, 0);
            if (readVideoSampleSize < 0) {
                break;
            }
            videoBufferInfo.size = readVideoSampleSize;
            videoBufferInfo.presentationTimeUs +=videoSampleTime;
            videoBufferInfo.offset = 0;
            videoBufferInfo.flags = videoExtractor.getSampleFlags();
            mediaMuxer.writeSampleData(writeVideoTrackIndex, byteBuffer, videoBufferInfo);
            videoExtractor.advance();
        }
        long audioSampleTime=getSampleTime(audioExtractor,byteBuffer);
        while (true) {
            int readAudioSampleSize = audioExtractor.readSampleData(byteBuffer, 0);
            if (readAudioSampleSize < 0) {
                break;
            }

            audioBufferInfo.size = readAudioSampleSize;
            audioBufferInfo.presentationTimeUs += audioSampleTime;
            audioBufferInfo.offset = 0;
            audioBufferInfo.flags = audioExtractor.getSampleFlags();
            mediaMuxer.writeSampleData(writeAudioTrackIndex, byteBuffer, audioBufferInfo);
            audioExtractor.advance();
        }

        mediaMuxer.stop();
        mediaMuxer.release();
        videoExtractor.release();
        audioExtractor.release();
    }

    /**
     * 拼接视频
     *
     * @param iv 输入源
     * @param ov 输出
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static void spliceVideo(String ov, String... iv) {


    }

    /**
     * 裁剪视频
     *
     * @param path
     * @param target
     * @param start  ms毫秒
     * @param end
     */
    //好像带b帧的视频处理有问题，音视频不同步
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static void clipVideo(String path, String target, long start, long end) {
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        MediaMuxer mediaMuxer = null;
        try {
            mediaMuxer = new MediaMuxer(target, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }

        int trackCount = mediaExtractor.getTrackCount();
        int[] trackIndex = new int[2];
        for (int i = 0; i < trackCount; i++) {
            MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
            String mime = trackFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                trackIndex[0]=mediaMuxer.addTrack(trackFormat);
                long duration = trackFormat.getLong(MediaFormat.KEY_DURATION);
                end = duration < end*1000 ? duration : end*1000;
            }else if(mime.startsWith("audio/")){
                trackIndex[1]=mediaMuxer.addTrack(trackFormat);

            }

        }
        mediaMuxer.start();

        ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1000);
        for (int i = 0; i < trackIndex.length; i++) {
            int track=trackIndex[i];
            mediaExtractor.selectTrack(track);
            mediaExtractor.seekTo(start*1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            long startSeek=mediaExtractor.getSampleTime();
            long sampleTime=getSampleTime(mediaExtractor,byteBuffer);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            while (true) {
                int readSampleDataSize = mediaExtractor.readSampleData(byteBuffer, 0);
                if (readSampleDataSize < 0) {
                    mediaExtractor.unselectTrack(track);
                    break;
                }
                bufferInfo.size = readSampleDataSize;
                bufferInfo.offset = 0;
                bufferInfo.flags = mediaExtractor.getSampleFlags();
                //mediaExtractor.getSampleTime()返回微妙，presentationTimeUs也是微妙
                //必须是增长顺序，不然会报如下错误
                //MPEG4Writer: timestampUs 40000 < lastTimestampUs 80000 for Video track
                //mediaExtractor 是DTS提取，所以mediaExtractor.getSampleTime()不一定是增长的(没有B帧是增长的)
                if(i==0){
                    bufferInfo.presentationTimeUs +=sampleTime;
                }else{
                    bufferInfo.presentationTimeUs = mediaExtractor.getSampleTime()-startSeek;
                }
//                bufferInfo.presentationTimeUs +=sampleTime;
                mediaMuxer.writeSampleData(track, byteBuffer, bufferInfo);
                mediaExtractor.advance();
                if(bufferInfo.presentationTimeUs>end*1000){
                    mediaExtractor.unselectTrack(track);
                    break;
                }
            }
        }

        mediaExtractor.release();
        mediaMuxer.release();

    }


}
