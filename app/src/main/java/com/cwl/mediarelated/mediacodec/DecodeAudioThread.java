package com.cwl.mediarelated.mediacodec;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by Administrator on 2017/2/6 0006.
 */

public class DecodeAudioThread extends Thread {
    private boolean running=true;
    private MediaExtractor mediaExtractor;
    private MediaCodec mediaCodec;
    private int sampleRate;
    private int channel;
    public DecodeAudioThread(String filePath){
        mediaExtractor=new MediaExtractor();
        try {
            mediaExtractor.setDataSource(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int trackCount = mediaExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
            String mime = trackFormat.getString(MediaFormat.KEY_MIME);
            System.out.println(mime+"===");
            System.out.println(Arrays.toString(trackFormat.getByteBuffer("csd-0").array())+"===");
            if(mime.startsWith("audio/")){
                mediaExtractor.selectTrack(i);
                sampleRate=trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                channel=trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

                try {
                    mediaCodec=MediaCodec.createDecoderByType(mime);

                } catch (Exception e) {
                    e.printStackTrace();
                }
//                //使用自己构造的MediaFormat
//                trackFormat=makeAACCodecSpecificData(MediaCodecInfo.CodecProfileLevel.AACObjectLC,sampleRate,channel);
                mediaCodec.configure(trackFormat,null,null,0);
                mediaCodec.start();

                break;

            }
        }
    }

    //https://wiki.multimedia.cx/index.php/ADTS
    private MediaFormat makeAACCodecSpecificData(int audioProfile, int sampleRate, int channelConfig) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelConfig);

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


    @Override
    public void run() {
        super.run();
        //audio play
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channel, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack audioTrack=new AudioTrack(AudioManager.STREAM_MUSIC,sampleRate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT,minBufferSize,AudioTrack.MODE_STREAM);
        audioTrack.play();
        //
        MediaCodec.BufferInfo bufferInfo=new MediaCodec.BufferInfo();
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

        boolean first=true;
        long startWhen = 0;
        while (running){
            int inputIndex = mediaCodec.dequeueInputBuffer(10 * 1000);
            if(inputIndex>=0){
                ByteBuffer inputBuffer = inputBuffers[inputIndex];
                int readSampleDataSize = mediaExtractor.readSampleData(inputBuffer, 0);
                if(readSampleDataSize<0){
                    mediaCodec.queueInputBuffer(inputIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }else{
                    mediaCodec.queueInputBuffer(inputIndex,0,readSampleDataSize,mediaExtractor.getSampleTime(),0);
                    mediaExtractor.advance();
                }

                int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10 * 1000);
                switch (outputIndex){
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        outputBuffers=mediaCodec.getOutputBuffers();
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        MediaFormat outputFormat = mediaCodec.getOutputFormat();
                        //重新设置采样率
                        audioTrack.setPlaybackRate(outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        //超时
                        break;
                    default:
                        if(first){
                            first=false;
                            startWhen=System.currentTimeMillis();
                        }
                        long sleepTime=bufferInfo.presentationTimeUs/1000-(System.currentTimeMillis()-startWhen);
                        if(sleepTime>0){
                            try {
                                Thread.sleep(sleepTime);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        ByteBuffer outputBuffer = outputBuffers[outputIndex];
                        byte[] block=new byte[bufferInfo.size];
                        outputBuffer.get(block);
                        outputBuffer.clear();

                        audioTrack.write(block,bufferInfo.offset,bufferInfo.offset+bufferInfo.size);
                        mediaCodec.releaseOutputBuffer(outputIndex,false);
                }

                if((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0){
                    break;
                }
            }
        }
        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec=null;

        mediaExtractor.release();

        audioTrack.stop();
        audioTrack.release();


    }

    public void close(){
        running=false;
    }
}
