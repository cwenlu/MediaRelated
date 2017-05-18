package com.cwl.mediarelated.util;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import com.orhanobut.logger.Logger;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;

import static android.media.MediaFormat.MIMETYPE_AUDIO_AAC;
import static com.orhanobut.logger.Logger.d;

/**
 * Created by Administrator on 2016/12/17 0017.
 */

public class CodecUtil {
    //eg: name,type [OMX.MTK.AUDIO.DECODER.MP3 audio/mpeg]
    public static boolean supptedCodec(String mime){
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            //是否是编码
            codecInfo.isEncoder();

            Log.i("tag","编解码器名字："+codecInfo.getName());
            //支持的编解码器类型
            String[] types = codecInfo.getSupportedTypes();
            Log.i("tag",Arrays.toString(types));

            MediaCodecInfo.CodecCapabilities capabilitiesForType = codecInfo.getCapabilitiesForType(types[0]);
            //得到支持的颜色格式(对于视频而言，音频没有)
            Log.i("tag",Arrays.toString(capabilitiesForType.colorFormats));

            for (String type : types) {
                if(type.equals(mime)){
                    return true;
                }
            }

        }

        return false;
    }


    //读取文件自己分析
    public static void  supptedCodec2() throws IOException {
        //读取系统配置文件/system/etc/media_codecc.xml
        File file = new File("/system/etc/media_codecs.xml");
        InputStream in = null;
        try {
            in = new FileInputStream(file);
        } catch (Exception e) {
            // TODO: handle exception
        }
        if(in == null)
        {
            android.util.Log.i("xp", "in == null");
        }else{
            android.util.Log.i("xp", "in != null");
        }

        BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(in));
        String s;
        while ((s = bufferedReader.readLine())!=null){
            Logger.i(s);
        }


        boolean isHardcode = false;
        XmlPullParserFactory pullFactory;
        try {
            pullFactory = XmlPullParserFactory.newInstance();
            XmlPullParser xmlPullParser = pullFactory.newPullParser();
            xmlPullParser.setInput(in, "UTF-8");
            int eventType = xmlPullParser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = xmlPullParser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("MediaCodec".equals(tagName)) {
                            String componentName = xmlPullParser.getAttributeValue(0);
                            android.util.Log.i("xp", componentName);
                            if(componentName.startsWith("OMX."))
                            {
                                if(!componentName.startsWith("OMX.google."))
                                {
                                    isHardcode = true;
                                }
                            }
                        }
                }
                eventType = xmlPullParser.next();
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
}
