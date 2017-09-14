package com.sy.recordpublishlib.utils;

import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.UnknownHostException;

/**
 * Created by daven.liu on 2017/9/14 0014.
 */

public class LogUtil {
    public static final String TAG = "recordPublishLib";
    private static boolean enableLog = true;

    public static boolean isEnableLog() {
        return enableLog;
    }

    public static void setEnableLog(boolean enableLog) {
        LogUtil.enableLog = enableLog;
    }

    public static void e(String content) {
        if (!enableLog) {
            return;
        }
        Log.e(TAG, content);
    }

    public static void d(String content) {
        if (!enableLog) {
            return;
        }
        Log.d(TAG, content);
    }

    public static void trace(String msg) {
        if (!enableLog) {
            return;
        }
        trace(msg, new Throwable());
    }

    public static void trace(Throwable e) {
        if (!enableLog) {
            return;
        }
        trace(null, e);
    }

    public static void trace(String msg, Throwable e) {
        if (!enableLog) {
            return;
        }
        if (null == e || e instanceof UnknownHostException) {
            return;
        }

        final Writer writer = new StringWriter();
        final PrintWriter pWriter = new PrintWriter(writer);
        e.printStackTrace(pWriter);
        String stackTrace = writer.toString();
        if (null == msg || msg.equals("")) {
            msg = "================error!==================";
        }
        Log.e(TAG, "==================================");
        Log.e(TAG, msg);
        Log.e(TAG, stackTrace);
        Log.e(TAG, "-----------------------------------");
    }
}
