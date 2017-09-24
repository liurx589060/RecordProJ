package com.sy.recordpublishlib.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.sy.recordpublishlib.utils.LogTools;
import com.sy.recordpublishlib.utils.MediaMuxerUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Administrator on 2017/9/23.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class AudioRecorder {
    private static final String MIME_TYPE = "audio/mp4a-latm";
    public static final int SAMPLES_PER_FRAME = 1024;    // AAC, frameBytes/frame/channel
    public static final int FRAMES_PER_BUFFER = 25;    // AAC, frame/buffer/sec
    protected static final int TIMEOUT_USEC = 10000;    // 10[msec]
    private static final int SAMPLE_RATE = 16000;    // 44.1[KHz] is only setting guaranteed to be available on all devices.
    private static final int BIT_RATE = 64000;

    private MediaCodec mMediaCodec;
    private AudioRecord audioRecord;
    private AtomicBoolean mIsQuit = new AtomicBoolean(false);
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private MediaMuxerUtil mMuxer;//混合器
    private int mAudioTrackIndex = -1;
    private long prevOutputPTSUs = 0;

    public AudioRecorder() {
        mMuxer = MediaMuxerUtil.getInstance();
    }

    /**
     * 开始录制
     */
    public void startRecord() {
        try {
            prepareAll();
            //开线程进行数据解析
            new Thread(){
                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void run() {
                    super.run();
                    LogTools.e("start startRecord");
                    toStart();
                }
            }.start();
        } catch (IOException e) {
            LogTools.e("录制音频失败---" + e.toString());
            return;
        }
    }

    /**
     * 停止录制
     */
    public void stopRecord() {
        recordStop();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void toStart() {
        try {
            encoding();
        } catch (Exception e) {
            LogTools.e(e.toString());
            e.printStackTrace();
        } finally {
            release();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void prepareEncoder() throws IOException {
        MediaFormat audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);//CHANNEL_IN_STEREO 立体声
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
//      audioFormat.setLong(MediaFormat.KEY_MAX_INPUT_SIZE, inputFile.length());
//      audioFormat.setLong(MediaFormat.KEY_DURATION, (long)durationInMs );
        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mMediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private void prepareAudioRecorder() {
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        try {
            final int min_buffer_size = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
            if (buffer_size < min_buffer_size)
                buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;

            audioRecord = null;
            try {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer_size);
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED)
                    audioRecord = null;
            } catch (Exception e) {
                audioRecord = null;
                LogTools.e(e.toString());
            }
        } catch (Exception e) {
            LogTools.e(e.toString());
        }
    }

    /**
     * 编码中
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void encoding() {
//        while (!mIsQuit.get()) {
//            int index = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 10000);
//            LogTools.d("dequeue output buffer index=" + index);
//            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {//后续输出格式变化
//                resetOutputFormat();
//            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {//请求超时
////                LogTools.e("retrieving buffers time out!");
//                try {
//                    // wait 10ms
//                    Thread.sleep(10);
//                } catch (InterruptedException e) {
//                }
//            } else if (index >= 0) {//有效输出
//                encodeToAudioTrack(index);
//                mMediaCodec.releaseOutputBuffer(index, false);
//            }
//        }

        final ByteBuffer buf = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME);
        int readBytes;
        while (!mIsQuit.get()) {
             if (audioRecord != null) {
                buf.clear();
                readBytes = audioRecord.read(buf, SAMPLES_PER_FRAME);
                if (readBytes > 0) {
                    // set audio data to encoder
                    buf.position(readBytes);
                    buf.flip();
                    try {
                        encode(buf, readBytes, getPTSUs());
                    } catch (Exception e) {
                        Log.e("zz","解码音频(Audio)数据失败=" + e.toString());
                        e.printStackTrace();
                    }
                }
            }
            /**/
        }
        Log.e("angcyo-->", "Audio 录制线程 退出...");
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void encode(final ByteBuffer buffer, final int length, final long presentationTimeUs) {
        if (mIsQuit.get()) return;
        final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        final int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
            /*向编码器输入数据*/
        if (inputBufferIndex >= 0) {
            final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            if (buffer != null) {
                inputBuffer.put(buffer);
            }
//	            if (DEBUG) if(DEBUG) Log.v(TAG, "encode:queueInputBuffer");
            if (length <= 0) {
                // send EOS
//                    mIsEOS = true;
                Log.i("zz", "send BUFFER_FLAG_END_OF_STREAM");
                mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0,
                        presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                mMediaCodec.queueInputBuffer(inputBufferIndex, 0, length,
                        presentationTimeUs, 0);
            }
        } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            // wait for MediaCodec encoder is ready to encode
            // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
            // will wait for maximum TIMEOUT_USEC(10msec) on each call
        }

        /*获取解码后的数据*/
        ByteBuffer[] encoderOutputBuffers = mMediaCodec.getOutputBuffers();
        int encoderStatus;

        do {
            encoderStatus = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                encoderOutputBuffers = mMediaCodec.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                final MediaFormat format = mMediaCodec.getOutputFormat(); // API >= 16
                if(mMuxer != null)
                if (mMuxer != null) {
                    mAudioTrackIndex = mMuxer.addTrack(MediaMuxerUtil.MEDIA_AUDIO,format);
                }

            } else if (encoderStatus < 0) {
            } else {
                final ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // You shoud set output format to muxer here when you target Android4.3 or less
                    // but MediaCodec#getOutputFormat can not call here(because INFO_OUTPUT_FORMAT_CHANGED don't come yet)
                    // therefor we should expand and prepare output format from buffer data.
                    // This sample is for API>=18(>=Android 4.3), just ignore this flag here
                    Log.d("zz", "drain:BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    mBufferInfo.presentationTimeUs = getPTSUs();
//                    Log.e("angcyo-->", "添加音频数据 " + mBufferInfo.size);
                    if(mMuxer != null && mMuxer.isStarted()) {
                        mMuxer.writeSampleData(mAudioTrackIndex,encodedData,mBufferInfo);
                    }
                    prevOutputPTSUs = mBufferInfo.presentationTimeUs;
                }
                // return buffer to encoder
                mMediaCodec.releaseOutputBuffer(encoderStatus, false);
            }
        } while (encoderStatus >= 0);
    }

    private long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void resetOutputFormat() {
        // should happen before receiving buffers, and should only happen once
        MediaFormat newFormat = mMediaCodec.getOutputFormat();

        LogTools.d("output format changed.\n new format: " + newFormat.toString());
        if(mMuxer != null) {
            mAudioTrackIndex = mMuxer.addTrack(MediaMuxerUtil.MEDIA_AUDIO,newFormat);
            Log.e("yy","videoTrack=" + mAudioTrackIndex);
        }
        LogTools.d("started media muxer, audioTrackIndex=" + mAudioTrackIndex);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void encodeToAudioTrack(int index) {
        ByteBuffer encodedData = mMediaCodec.getOutputBuffer(index);

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
            if(mMuxer != null) {
                mMuxer.writeSampleData(mAudioTrackIndex, encodedData, mBufferInfo);//写入
            }
            LogTools.d("sent " + mBufferInfo.size + " bytes to muxer...");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void prepareAll() throws IOException {
        prepareEncoder();
        prepareAudioRecorder();

        if(mMediaCodec == null || audioRecord == null) {
            LogTools.e("mMediaCodec==null || audioRecord==null");
            return;
        }

        if(mMediaCodec != null) {
            mMediaCodec.start();
        }

        if(audioRecord != null) {
            audioRecord.startRecording();
        }
        LogTools.e("AudioRecorder startRecord");
    }

    private void recordStop() {
        LogTools.e("AudioRecorder stopRecord");
        mIsQuit.set(true);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void release() {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }
}
