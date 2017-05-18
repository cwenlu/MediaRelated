package com.cwl.mediarelated.util;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by Administrator on 2015/12/23.
 */
public class ToastUtils {

    public static void showShort(Context context, CharSequence msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    public static void showLong(Context context, CharSequence msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    /**
     *
     * @param context
     * @param msg
     * @param duration 显示时间
     */
    public static void show(Context context, CharSequence msg, int duration) {
        Toast.makeText(context, msg, duration).show();
    }
}
