package com.sy.recordpublishlib.utils;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by daven.liu on 2017/9/15 0015.
 */

public class MediaMuxerUtil {
    private static MediaMuxerUtil instance = null;
    private MediaMuxer mediaMuxer;
    private String videoPath = Environment.getExternalStorageDirectory() + "/" + System.currentTimeMillis() + ".mp4";
    private boolean isCacheSave;
    public static final int MEDIA_VIDEO = 0x11;
    public static final int MEDIA_AUDIO = 0x12;
    private boolean isAudioAdd;
    private boolean isVideoAdd;
    private boolean isStarted;

    public static MediaMuxerUtil getInstance() {
        if(instance == null) {
            instance = new MediaMuxerUtil();
        }
        return instance;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public void setCacheSave(boolean isCacheSave) {
        this.isCacheSave = isCacheSave;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
        if(!isCacheSave) return;
        if(mediaMuxer != null && isReadyStart()) {
            mediaMuxer.writeSampleData(trackIndex,byteBuf,bufferInfo);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public int addTrack(int type,MediaFormat format) {
        synchronized (this) {
            if(!isCacheSave || isReadyStart()) return -1;
            if(mediaMuxer == null) {
                try {
                    mediaMuxer = new MediaMuxer(videoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                } catch (IOException e) {
                    e.printStackTrace();
                    LogTools.e(e.toString());
                    return -1;
                }
            }

            int index = mediaMuxer.addTrack(format);
            if(type == MEDIA_VIDEO) {
                isVideoAdd = true;
            }else if(type == MEDIA_AUDIO) {
                isAudioAdd = true;
            }

            if(isReadyStart()) {
                start();
            }
            return index;
        }
    }

    private boolean isReadyStart() {
        return isVideoAdd && isAudioAdd;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void stop() {
        if(mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
            isAudioAdd = false;
            isVideoAdd = false;
            isStarted = false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void start() {
        if(!isCacheSave) return;
        if(mediaMuxer != null) {
            mediaMuxer.start();
            isStarted = true;
            LogTools.e("MediaMuxer---start");
        }
    }

    public void release() {
        instance = null;
    }

    public boolean isStarted() {
        return isStarted;
    }
}
