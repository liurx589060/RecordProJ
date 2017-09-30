package com.sy.record;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.sy.recordpublishlib.RecordService;
import com.sy.recordpublishlib.bean.RecorderBean;
import com.sy.recordpublishlib.screen.ScreenRecorder;

/**
 * Created by daven.liu on 2017/9/14 0014.
 */

public class ScreenRecordActivity extends AppCompatActivity {
    private Button mBtnRecord;

    private RecordService mRecordService;

    private MediaProjectionManager mMediaProjectionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen);

        tobindService();

        mBtnRecord = (Button) findViewById(R.id.btn_record);
        mBtnRecord.setTag(false);

        mBtnRecord.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View v) {
                if((boolean)mBtnRecord.getTag()) {
                    //stop
                    mBtnRecord.setText("start");
                    mBtnRecord.setTag(false);

                    toStopRecord();
                    Toast.makeText(getApplicationContext(),"停止录屏",Toast.LENGTH_SHORT).show();
                }else {
                    //start
                    mBtnRecord.setText("stop");
                    mBtnRecord.setTag(true);

                    toStartRecord();
                    Toast.makeText(getApplicationContext(),"开始录屏",Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        MediaProjection mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        RecorderBean bean = new RecorderBean(this);
        bean.setWidth(1280);
        bean.setHeight(720);
        mRecordService.startRecord(bean,mMediaProjection);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void toStartRecord() {
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, 1000);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void toStopRecord() {
        mRecordService.stopRecord();
    }

    private void tobindService() {
        Intent intent = new Intent(this,RecordService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
    }

    private void toUnbindService() {
        unbindService(conn);
    }

    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RecordService.RecordServiceBinder binder = (RecordService.RecordServiceBinder) service;
            mRecordService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mRecordService = null;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        toUnbindService();
    }
}
