package com.cwl.mediarelated.mediacodec;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static android.R.attr.format;
import static android.media.MediaCodec.CONFIGURE_FLAG_ENCODE;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC;
import static android.media.MediaFormat.createAudioFormat;
import static android.os.Build.VERSION_CODES.M;

/**
 * Created by Administrator on 2017/2/17 0017.
 * 编码出的音频播放速度很快，暂时还不知道为啥
 */

public class EncodeAudioThread extends Thread {
    boolean running=true;
    MediaCodec mediaCodec;
    MediaMuxer mediaMuxer;
    int trackIndex;
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public EncodeAudioThread(){
        try {
            mediaCodec=MediaCodec.createEncoderByType("audio/mp4a-latm");
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat mediaFormat=makeAACCodecSpecificData(AACObjectLC,44100,2);
        mediaCodec.configure(mediaFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
        try {
            mediaMuxer=new MediaMuxer(Environment.getExternalStorageDirectory().getAbsolutePath()+"/record", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        trackIndex=mediaMuxer.addTrack(mediaFormat);
        mediaMuxer.start();
    }

    private MediaFormat makeAACCodecSpecificData(int audioProfile, int sampleRate, int channelConfig) {
//        MediaFormat format = new MediaFormat();
//        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
//        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
//        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelConfig);
        MediaFormat format=MediaFormat.createAudioFormat("audio/mp4a-latm",sampleRate,channelConfig);
        //编码不设置比特率会报错 android.media.MediaCodec$CodecException: Error 0x80001001
        format.setInteger(MediaFormat.KEY_BIT_RATE,56*1000);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        int samplingFreq[] = {
                96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
                16000, 12000, 11025, 8000
        };

        // Search the Sampling Frequencies9
        int sampleIndex = -1;
        for (int i = 0; i < samplingFreq.length; ++i) {
            if (samplingFreq[i] == sampleRate) {
                Log.d("TAG", "kSamplingFreq " + samplingFreq[i] + " i : " + i);
                sampleIndex = i;
            }
        }

        if (sampleIndex == -1) {
            return null;
        }

        //没搞懂这段的计算原理
        ByteBuffer csd = ByteBuffer.allocate(2);
        csd.put((byte) ((audioProfile << 3) | (sampleIndex >> 1)));
        csd.position(1);
        csd.put((byte) ((byte) ((sampleIndex << 7) & 0x80) | (channelConfig << 3)));
        csd.flip();
        format.setByteBuffer("csd-0", csd); // add csd-0
        System.out.println(Arrays.toString(csd.array())+"===++");

        return format;
    }

    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        //39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 2;  //CPE

        // fill in ADTS data
        packet[0] = (byte)0xFF;
        packet[1] = (byte)0xF9;
        packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
        packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
        packet[4] = (byte)((packetLen&0x7FF) >> 3);
        packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
        packet[6] = (byte)0xFC;
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void run() {
        super.run();
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
        MediaCodec.BufferInfo bufferInfo=new MediaCodec.BufferInfo();

        //================

        FileOutputStream fileOutputStream = null;
        FileOutputStream fileOutputStream2 = null;
        try {
             fileOutputStream=new FileOutputStream(Environment.getExternalStorageDirectory().getAbsolutePath()+"/record.pcm");
             fileOutputStream2=new FileOutputStream(Environment.getExternalStorageDirectory().getAbsolutePath()+"/record2");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        int minBufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord audioRecord=new AudioRecord(MediaRecorder.AudioSource.MIC,44100, AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,minBufferSize);
        audioRecord.startRecording();
        byte[] buffer=new byte[minBufferSize];
        while(running){
            int readSize = audioRecord.read(buffer, 0, buffer.length);
            try {
                fileOutputStream.write(buffer,0,readSize);
            } catch (IOException e) {
                e.printStackTrace();
            }

            //===================
            int inputIndex = mediaCodec.dequeueInputBuffer(1000);
            ByteBuffer inputBuffer = inputBuffers[inputIndex];
            inputBuffer.put(buffer,0,readSize);
            mediaCodec.queueInputBuffer(inputIndex,0,readSize,0,0);
            int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 1000);
            switch (outputIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    outputBuffers = mediaCodec.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    MediaFormat outputFormat = mediaCodec.getOutputFormat();

                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    //超时
                    break;
                default:
                    mediaMuxer.writeSampleData(trackIndex,outputBuffers[outputIndex],bufferInfo);
                    //通过文件写
//                    byte[] array = outputBuffers[outputIndex].array();//会报错 ReadOnlyBufferException
                    ByteBuffer outputBuffer = outputBuffers[outputIndex];
                    byte[] newArray=new byte[outputBuffer.limit()+7];
                    addADTStoPacket(newArray,newArray.length);
                    outputBuffer.get(newArray,7,outputBuffer.limit());
                    try {
                        fileOutputStream2.write(newArray);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }//

                    mediaCodec.releaseOutputBuffer(outputIndex,false);
                    break;
            }
            //================


        }
        try {
            fileOutputStream.close();
            fileOutputStream2.close();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            audioRecord.release();
            audioRecord=null;

            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec=null;

            mediaMuxer.release();
        }


        System.out.println("finish");
        //读取播放
        FileInputStream fileInputStream = null;
        try {
             fileInputStream=new FileInputStream(Environment.getExternalStorageDirectory().getAbsolutePath()+"/record.pcm");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        AudioTrack audioTrack=new AudioTrack(AudioManager.STREAM_MUSIC,44100,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,minBufferSize,AudioTrack.MODE_STREAM);
        audioTrack.play();
        int rs = 0;
        try {
            while((rs=fileInputStream.read(buffer))!=-1){
                audioTrack.write(buffer,0,rs);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        //finally{}
        try {
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            audioTrack.release();
        }


    }

    public void stopThread(){
        running=false;
    }
}
