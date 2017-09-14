package com.sy.recordpublishlib.screen;

import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;

import com.sy.recordpublishlib.bean.RecorderBean;
import com.sy.recordpublishlib.utils.LogUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.sy.recordpublishlib.utils.LogUtil.TAG;

/**
 * Created by daven.liu on 2017/9/14 0014.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class ScreenRecorder {
    private RecorderBean mBean;
    private MediaProjection mMediaoproj;
    private VirtualDisplay mVirtualDisplay;

    private MediaCodec mEncoder;
    private Surface mSurface;
    private MediaMuxer mMuxer;
    private AtomicBoolean mIsQuit = new AtomicBoolean(false);
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private boolean mMuxerStarted = false;
    private int mVideoTrackIndex = -1;
    private String mVideoPath;
    private boolean isCacheSave;

    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 20; // 30 fps
    private static final int IFRAME_INTERVAL = 2; // 2 seconds between I-frames
    private static final int TIMEOUT_US = 10000;

    public ScreenRecorder(RecorderBean bean, MediaProjection mp) {
        this.mBean = bean;
        this.mMediaoproj = mp;
    }

    public ScreenRecorder(RecorderBean bean,MediaProjection mp,String videoPath) {
        this(bean,mp);
        this.mVideoPath = videoPath;
        this.isCacheSave = true;
    }

    public ScreenRecorder(RecorderBean bean,MediaProjection mp,boolean isCaccheSave) {
        this(bean,mp);
        this.isCacheSave = isCaccheSave;
    }

    /**
     * 预处理
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void prepareEncoder() throws IOException {
//        mVideoPath = Environment.getExternalStorageDirectory() + "/" + TAG + "/" + System.currentTimeMillis() + ".mp4";
        mVideoPath = Environment.getExternalStorageDirectory() + "/" + System.currentTimeMillis() + ".mp4";

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mBean.getWidth(), mBean.getHeight());
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBean.getBitrate());
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        LogUtil.d("created video format: " + format);
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mEncoder.createInputSurface();
        LogUtil.d("created input surface: " + mSurface);
        mEncoder.start();
    }

    /**
     * 开始录制
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startRecord() {
        try {
            prepareEncoder();
            startVirtual();
            //开线程进行数据解析
            new Thread(){
                @Override
                public void run() {
                    super.run();
                    LogUtil.e("start startRecord");
                    toStart();
                }
            }.start();
        } catch (IOException e) {
            LogUtil.e(e.toString());
            return;
        }
    }

    /**
     * 停止录屏
     */
    public void stopRecord() {
        LogUtil.e("stopRecord");
        mIsQuit.set(true);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void release() {
        mIsQuit.set(false);
        mMuxerStarted = false;
        LogUtil.e(" release");
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void toStart() {
        try {
            //保存mp4
            mMuxer = new MediaMuxer(mVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            recordVirtualDisplay();
        } catch (IOException e) {
            LogUtil.e(e.toString());
            e.printStackTrace();
        } finally {
            release();
        }
    }



    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startVirtual() {
        if (mMediaoproj != null) {
            virtualDisplay();
        } else {
            throw new NullPointerException("MediaProjection = null");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void virtualDisplay() {
        mVirtualDisplay = mMediaoproj.createVirtualDisplay("record_screen", mBean.getWidth(), mBean.getHeight(), mBean.getDpi(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mSurface, null, null);
    }

    /**
     * 读取数据
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void recordVirtualDisplay() {
        while (!mIsQuit.get()) {
            int index = mEncoder.dequeueOutputBuffer(mBufferInfo, 10000);
            LogUtil.d("dequeue output buffer index=" + index);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {//后续输出格式变化
                resetOutputFormat();
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {//请求超时
                LogUtil.e("retrieving buffers time out!");
                try {
                    // wait 10ms
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            } else if (index >= 0) {//有效输出
                if (!mMuxerStarted) {
                    throw new IllegalStateException("MediaMuxer dose not call addTrack(format) ");
                }
                encodeToVideoTrack(index);
                mEncoder.releaseOutputBuffer(index, false);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void encodeToVideoTrack(int index) {
        ByteBuffer encodedData = mEncoder.getOutputBuffer(index);

        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {//是编码需要的特定数据，不是媒体数据
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.
            // Ignore it.
            LogUtil.d("ignoring BUFFER_FLAG_CODEC_CONFIG");
            mBufferInfo.size = 0;
        }
        if (mBufferInfo.size == 0) {
            LogUtil.d("info.size == 0, drop it.");
            encodedData = null;
        } else {
            LogUtil.d("got buffer, info: size=" + mBufferInfo.size
                    + ", presentationTimeUs=" + mBufferInfo.presentationTimeUs
                    + ", offset=" + mBufferInfo.offset);
        }
        if (encodedData != null) {
            encodedData.position(mBufferInfo.offset);
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
            mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mBufferInfo);//写入
            LogUtil.d("sent " + mBufferInfo.size + " bytes to muxer...");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void resetOutputFormat() {
        // should happen before receiving buffers, and should only happen once
        if (mMuxerStarted) {
            throw new IllegalStateException("output format already changed!");
        }
        MediaFormat newFormat = mEncoder.getOutputFormat();

        LogUtil.d("output format changed.\n new format: " + newFormat.toString());
        mVideoTrackIndex = mMuxer.addTrack(newFormat);
        mMuxer.start();
        mMuxerStarted = true;
        LogUtil.d("started media muxer, videoIndex=" + mVideoTrackIndex);
    }
}
