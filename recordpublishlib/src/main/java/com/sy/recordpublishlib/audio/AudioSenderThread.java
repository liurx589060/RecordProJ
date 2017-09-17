package com.sy.recordpublishlib.audio;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.sy.recordpublishlib.utils.LogTools;
import com.sy.recordpublishlib.utils.MediaMuxerUtil;
import com.sy.recordpublishlib.utils.Packager;

import java.nio.ByteBuffer;

import static android.content.ContentValues.TAG;

/**
 * Created by lakeinchina on 26/05/16.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class AudioSenderThread extends Thread {
    private static final long WAIT_TIME = 5000;//1ms;
    private MediaCodec.BufferInfo eInfo;
    private long startTime = 0;
    private MediaCodec dstAudioEncoder;

    private int mAudioTrackIndex = -1;
    private MediaMuxerUtil mMuxer;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public AudioSenderThread(String name, MediaCodec encoder) {
        super(name);
        eInfo = new MediaCodec.BufferInfo();
        startTime = 0;
        dstAudioEncoder = encoder;
        mMuxer = MediaMuxerUtil.getInstance();
    }

    private boolean shouldQuit = false;

    public void quit() {
        shouldQuit = true;
        this.interrupt();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void run() {
        while (!shouldQuit) {
            int eobIndex = dstAudioEncoder.dequeueOutputBuffer(eInfo, WAIT_TIME);
            switch (eobIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "AudioSenderThread,MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
//                        LogTools.d("AudioSenderThread,MediaCodec.INFO_TRY_AGAIN_LATER");
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG, "AudioSenderThread,MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:" +
                            dstAudioEncoder.getOutputFormat().toString());
                    ByteBuffer csd0 = dstAudioEncoder.getOutputFormat().getByteBuffer("csd-0");
                    sendAudioSpecificConfig(0, csd0);
                    resetOutputFormat();
                    break;
                default:
                    Log.d(TAG, "AudioSenderThread,MediaCode,eobIndex=" + eobIndex);
                    if (startTime == 0) {
                        startTime = eInfo.presentationTimeUs / 1000;
                    }
                    /**
                     * we send audio SpecificConfig already in INFO_OUTPUT_FORMAT_CHANGED
                     * so we ignore MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                     */
                    if (eInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && eInfo.size != 0) {
                        ByteBuffer realData = dstAudioEncoder.getOutputBuffers()[eobIndex];
                        realData.position(eInfo.offset);
                        realData.limit(eInfo.offset + eInfo.size);
                        sendRealData((eInfo.presentationTimeUs / 1000) - startTime, realData);
                        encodeToAudioTrack(realData);
                    }
                    dstAudioEncoder.releaseOutputBuffer(eobIndex, false);
                    break;
            }
        }
        eInfo = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void sendAudioSpecificConfig(long tms, ByteBuffer realData) {
        int packetLen = Packager.FLVPackager.FLV_AUDIO_TAG_LENGTH +
                realData.remaining();
        byte[] finalBuff = new byte[packetLen];
        realData.get(finalBuff, Packager.FLVPackager.FLV_AUDIO_TAG_LENGTH,
                realData.remaining());
        Packager.FLVPackager.fillFlvAudioTag(finalBuff,
                0,
                true);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void resetOutputFormat() {
        //添加音轨
        MediaFormat newFormat = dstAudioEncoder.getOutputFormat();
        if(mMuxer != null) {
            mAudioTrackIndex = mMuxer.addTrack(MediaMuxerUtil.MEDIA_AUDIO,newFormat);
            Log.e("yy","audioTrack=" + mAudioTrackIndex);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void encodeToAudioTrack(ByteBuffer realData) {
        if(mMuxer != null) {
            mMuxer.writeSampleData(mAudioTrackIndex,realData,eInfo);
        }
    }

    private void sendRealData(long tms, ByteBuffer realData) {
        int packetLen = Packager.FLVPackager.FLV_AUDIO_TAG_LENGTH +
                realData.remaining();
        byte[] finalBuff = new byte[packetLen];
        realData.get(finalBuff, Packager.FLVPackager.FLV_AUDIO_TAG_LENGTH,
                realData.remaining());
        Packager.FLVPackager.fillFlvAudioTag(finalBuff,
                0,
                false);
    }
}
