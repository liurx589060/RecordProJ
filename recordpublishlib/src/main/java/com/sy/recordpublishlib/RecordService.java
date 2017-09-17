package com.sy.recordpublishlib;

import android.app.Service;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;

import com.sy.recordpublishlib.audio.RESAudioClient;
import com.sy.recordpublishlib.audio.RESCoreParameters;
import com.sy.recordpublishlib.bean.RecorderBean;
import com.sy.recordpublishlib.screen.ScreenRecorder;

/**
 * Created by Administrator on 2017/9/14.
 */

public class RecordService extends Service {
    private ScreenRecorder mScreenRecorder;
    private RESAudioClient mAudioRecorder;
    private RESCoreParameters coreParameters;


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startRecord(RecorderBean bean, MediaProjection mj) {
        if(mScreenRecorder == null) {
            mScreenRecorder = new ScreenRecorder(bean,mj);
        }

        if(mAudioRecorder == null) {
            coreParameters = new RESCoreParameters();
            mAudioRecorder = new RESAudioClient(coreParameters);
        }
        mScreenRecorder.startRecord();
        mAudioRecorder.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startRecord(RecorderBean bean, MediaProjection mj, String videoPath) {
        if(mScreenRecorder == null) {
            mScreenRecorder = new ScreenRecorder(bean,mj,videoPath);
        }

        if(mAudioRecorder == null) {
            coreParameters = new RESCoreParameters();
            mAudioRecorder = new RESAudioClient(coreParameters);
        }
        mScreenRecorder.startRecord();
        mAudioRecorder.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startRecord(RecorderBean bean, MediaProjection mj, boolean isCacheSave) {
        if(mScreenRecorder == null) {
            mScreenRecorder = new ScreenRecorder(bean,mj,isCacheSave);
        }

        if(mAudioRecorder == null) {
            coreParameters = new RESCoreParameters();
            mAudioRecorder = new RESAudioClient(coreParameters);
        }
        mScreenRecorder.startRecord();
        mAudioRecorder.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void stopRecord() {
        if(mScreenRecorder != null) {
            mScreenRecorder.stopRecord();
            mScreenRecorder = null;
        }

        if(mAudioRecorder != null) {
            mAudioRecorder.stop();
            mAudioRecorder = null;
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
