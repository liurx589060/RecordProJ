package com.sy.recordpublishlib.bean;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;

import com.sy.recordpublishlib.RecordService;

/**
 * Created by daven.liu on 2017/9/13 0013.
 */

public class RecorderBean {
    private int width = 1280;
    private int height = 720;
    private int bitrate = 500000;
    private int fps = 30;
    private int dpi = 1;
    private String rtmpAddr;

    public RecorderBean(Activity context) {
        DisplayMetrics metric = new DisplayMetrics();
        context.getWindowManager().getDefaultDisplay().getMetrics(metric);
        this.dpi = metric.densityDpi;
    }

    public RecorderBean() {}

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getBitrate() {
        return bitrate;
    }

    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }

    public int getDpi() {
        return dpi;
    }

    public void setDpi(int dpi) {
        this.dpi = dpi;
    }

    public String getRtmpAddr() {
        return rtmpAddr;
    }

    public void setRtmpAddr(String rtmpAddr) {
        this.rtmpAddr = rtmpAddr;
    }

    public int getFps() {
        return fps;
    }

    public void setFps(int fps) {
        this.fps = fps;
    }
}
