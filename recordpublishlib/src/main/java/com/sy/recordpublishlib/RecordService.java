package com.sy.recordpublishlib;

import android.app.Service;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;

import com.sy.recordpublishlib.audio.AudioRecorder;
import com.sy.recordpublishlib.bean.RecorderBean;
import com.sy.recordpublishlib.rtmp.RESFlvData;
import com.sy.recordpublishlib.rtmp.RESFlvDataCollecter;
import com.sy.recordpublishlib.rtmp.RtmpStreamingSender;
import com.sy.recordpublishlib.screen.ScreenRecorder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Administrator on 2017/9/14.
 */

public class RecordService extends Service {
    private ScreenRecorder mScreenRecorder;
    private AudioRecorder mAudioRecorder;
    private ExecutorService executorService;
    private RtmpStreamingSender streamingSender;


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startRecord(RecorderBean bean, MediaProjection mj) {
        if(mScreenRecorder == null) {
            mScreenRecorder = new ScreenRecorder(bean,mj);
        }

        if(mAudioRecorder == null) {
            mAudioRecorder = new AudioRecorder();
        }
        //rtmp推送
//        executorService = Executors.newCachedThreadPool();
//        streamingSender = new RtmpStreamingSender();
//        streamingSender.sendStart(bean.getRtmpAddr());
//        RESFlvDataCollecter collecter = new RESFlvDataCollecter() {
//            @Override
//            public void collect(RESFlvData flvData, int type) {
//                if(streamingSender != null) {
//                    streamingSender.sendFood(flvData, type);
//                }
//            }
//        };
//        //为Video和Audio设置收集器
//        mScreenRecorder.setCollecter(collecter);
//        mAudioRecorder.setCollecter(collecter);

        mScreenRecorder.startRecord();
        mAudioRecorder.startRecord();
//        executorService.execute(streamingSender);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void stopRecord() {
        if(mScreenRecorder != null) {
            mScreenRecorder.stopRecord();
            mScreenRecorder = null;
        }

        if(mAudioRecorder != null) {
            mAudioRecorder.stopRecord();
            mAudioRecorder = null;
        }

        if (streamingSender != null) {
            streamingSender.sendStop();
            streamingSender.quit();
            streamingSender = null;
        }
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
    }

    private RecordServiceBinder binder = new RecordServiceBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecord();
    }

    public class RecordServiceBinder extends Binder {
        public RecordService getService() {
            return RecordService.this;
        }
    }
}
