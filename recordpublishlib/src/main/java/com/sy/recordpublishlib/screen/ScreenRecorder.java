package com.sy.recordpublishlib.screen;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;

import com.sy.recordpublishlib.CodeUtil.Packager;
import com.sy.recordpublishlib.bean.RecorderBean;
import com.sy.recordpublishlib.rtmp.RESFlvData;
import com.sy.recordpublishlib.rtmp.RESFlvDataCollecter;
import com.sy.recordpublishlib.utils.LogTools;
import com.sy.recordpublishlib.utils.MediaMuxerUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.sy.recordpublishlib.rtmp.RESFlvData.FLV_RTMP_PACKET_TYPE_VIDEO;

/**
 * Created by daven.liu on 2017/9/14 0014.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class ScreenRecorder {
    private RecorderBean mBean;
    private MediaProjection mMediaoproj;
    private VirtualDisplay mVirtualDisplay;
    private RESFlvDataCollecter collecter;

    private MediaCodec mEncoder;
    private Surface mSurface;
    private MediaMuxerUtil mMuxer;
    private AtomicBoolean mIsQuit = new AtomicBoolean(false);
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private int mVideoTrackIndex = -1;

    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private static final int IFRAME_INTERVAL = 2; // 2 seconds between I-frames
    private static final int TIMEOUT_US = 10000;
    private long startTime = 0;

    public ScreenRecorder(RecorderBean bean, MediaProjection mp) {
        this.mBean = bean;
        this.mMediaoproj = mp;
        mMuxer = MediaMuxerUtil.getInstance();
        mMuxer.setVideoPath(bean.getVideoPath());
        mMuxer.setCacheSave(bean.isSaveCache());
    }

    /**
     * 预处理
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void prepareEncoder() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mBean.getWidth(), mBean.getHeight());
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBean.getBitrate());
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mBean.getFps());
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        LogTools.d("created video format: " + format);
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mEncoder.createInputSurface();
        LogTools.d("created input surface: " + mSurface);
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
                    LogTools.e("start startRecord");
                    toStart();
                }
            }.start();
        } catch (IOException e) {
            LogTools.e(e.toString());
            return;
        }
    }

    /**
     * 停止录屏
     */
    public void stopRecord() {
        LogTools.e("stopRecord");
        mIsQuit.set(true);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void release() {
        mIsQuit.set(false);
        LogTools.e(" release");
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if ( mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void toStart() {
        try {
            recordVirtualDisplay();
        } catch (Exception e) {
            LogTools.e(e.toString());
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
            LogTools.d("dequeue output buffer index=" + index);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {//后续输出格式变化
                resetOutputFormat();
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {//请求超时
//                LogTools.e("retrieving buffers time out!");
                try {
                    // wait 10ms
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            } else if (index >= 0) {//有效输出
                encodeToVideoTrack(index);
                mEncoder.releaseOutputBuffer(index, false);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void encodeToVideoTrack(int index) {
        ByteBuffer encodedData = mEncoder.getOutputBuffer(index);

        if (startTime == 0) {
            startTime = mBufferInfo.presentationTimeUs / 1000;
        }

        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {//是编码需要的特定数据，不是媒体数据
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.
            // Ignore it.
            LogTools.d("ignoring BUFFER_FLAG_CODEC_CONFIG");
            mBufferInfo.size = 0;
        }
        if (mBufferInfo.size == 0) {
            LogTools.d("info.size == 0, drop it.");
            encodedData = null;
        } else {
            LogTools.d("got buffer, info: size=" + mBufferInfo.size
                    + ", presentationTimeUs=" + mBufferInfo.presentationTimeUs
                    + ", offset=" + mBufferInfo.offset);
        }
        if (encodedData != null) {
            encodedData.position(mBufferInfo.offset);
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
            if(mMuxer != null && mMuxer.isStarted()) {
                mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mBufferInfo);//写入
            }
            //推送数据
            sendRealData((mBufferInfo.presentationTimeUs / 1000) - startTime, encodedData);
            LogTools.d("sent " + mBufferInfo.size + " bytes to muxer...");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void resetOutputFormat() {
        // should happen before receiving buffers, and should only happen once
        MediaFormat newFormat = mEncoder.getOutputFormat();

        LogTools.d("output format changed.\n new format: " + newFormat.toString());
        if(mMuxer != null) {
            mVideoTrackIndex = mMuxer.addTrack(MediaMuxerUtil.MEDIA_VIDEO,newFormat);
            Log.e("yy","videoTrack=" + mVideoTrackIndex);
//            mMuxer.start();
        }
        LogTools.d("started media muxer, videoIndex=" + mVideoTrackIndex);
        //传送关键帧
        sendAVCDecoderConfigurationRecord(0, mEncoder.getOutputFormat());
    }

    /**
     * 传送关键帧
     * @param tms
     * @param format
     */
    private void sendAVCDecoderConfigurationRecord(long tms, MediaFormat format) {
        if(collecter == null) return;
        byte[] AVCDecoderConfigurationRecord = Packager.H264Packager.generateAVCDecoderConfigurationRecord(format);
        int packetLen = Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                AVCDecoderConfigurationRecord.length;
        byte[] finalBuff = new byte[packetLen];
        Packager.FLVPackager.fillFlvVideoTag(finalBuff,
                0,
                true,
                true,
                AVCDecoderConfigurationRecord.length);
        System.arraycopy(AVCDecoderConfigurationRecord, 0,
                finalBuff, Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH, AVCDecoderConfigurationRecord.length);
        RESFlvData resFlvData = new RESFlvData();
        resFlvData.droppable = false;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = FLV_RTMP_PACKET_TYPE_VIDEO;
        resFlvData.videoFrameType = RESFlvData.NALU_TYPE_IDR;
        collecter.collect(resFlvData, FLV_RTMP_PACKET_TYPE_VIDEO);

        Log.e("zz","传送关键帧");
    }

    /**
     * 传送真实视频
     * @param tms
     * @param realData
     */
    private void sendRealData(long tms, ByteBuffer realData) {
        if(collecter == null) return;
        int realDataLength = realData.remaining();
        int packetLen = Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                Packager.FLVPackager.NALU_HEADER_LENGTH +
                realDataLength;
        byte[] finalBuff = new byte[packetLen];
        realData.get(finalBuff, Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                        Packager.FLVPackager.NALU_HEADER_LENGTH,
                realDataLength);
        int frameType = finalBuff[Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                Packager.FLVPackager.NALU_HEADER_LENGTH] & 0x1F;
        Packager.FLVPackager.fillFlvVideoTag(finalBuff,
                0,
                false,
                frameType == 5,
                realDataLength);
        RESFlvData resFlvData = new RESFlvData();
        resFlvData.droppable = true;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = FLV_RTMP_PACKET_TYPE_VIDEO;
        resFlvData.videoFrameType = frameType;
        collecter.collect(resFlvData, FLV_RTMP_PACKET_TYPE_VIDEO);

        Log.e("zz","传送真实视频数据");
    }

    public RESFlvDataCollecter getCollecter() {
        return collecter;
    }

    public void setCollecter(RESFlvDataCollecter collecter) {
        this.collecter = collecter;
    }
}
