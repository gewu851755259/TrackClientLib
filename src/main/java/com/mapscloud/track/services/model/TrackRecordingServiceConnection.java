/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.mapscloud.track.services.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.mapscloud.track.BuildConfig;
import com.mapscloud.track.services.utils.Constant;

/**
 * Wrapper for the track recording service. This handles service
 * start/bind/unbind/stop. The service must be started before it can be bound.
 * Returns the service if it is started and bound.
 *
 * @author Rodrigo Damazio
 */
public class TrackRecordingServiceConnection {

    private static final String TAG = TrackRecordingServiceConnection.class.getSimpleName();

    private final DeathRecipient deathRecipient = new DeathRecipient() {
        @Override
        public void binderDied() {
            Log.d(TAG, "Service died.");
            setTrackRecordingService(null);
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "Connected to the service.");
            try {
                service.linkToDeath(deathRecipient, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to bind a death recipient.", e);
            }
            setTrackRecordingService(ITrackRecordingService.Stub.asInterface(service));
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.i(TAG, "Disconnected from the service.");
            setTrackRecordingService(null);
        }
    };

    private final Context context;
    private final Runnable callback;
    private ITrackRecordingService trackRecordingService;

    /**
     * Constructor.
     *
     * @param context
     *            the context
     * @param callback
     *            the callback to invoke when the service binding changes
     */
    public TrackRecordingServiceConnection(Context context, Runnable callback) {
        this.context = context;
        this.callback = callback;
    }

    /**
     * Starts and binds the service.
     */
    public void startAndBind() {
        bindService(true);
    }

    /**
     * Binds the service if it is started.
     */
    public void bindIfStarted() {
        bindService(false);
    }

    /**
     * Unbinds and stops the service.
     */
    public void unbindAndStop() {
        unbind();
        context.stopService(new Intent(Constant.TRACKS_SERVICE_ACTION));
    }

    /**
     * Unbinds the service (but leave it running).
     */
    public void unbind() {
        try {
            context.unbindService(serviceConnection);
        } catch (IllegalArgumentException e) {
            // Means not bound to the service. OK to ignore.
        }
        setTrackRecordingService(null);
    }

    /**
     * Gets the track recording service if bound. Returns null otherwise
     */
    public ITrackRecordingService getServiceIfBound() {
        if (trackRecordingService != null && !trackRecordingService.asBinder().isBinderAlive()) {
            setTrackRecordingService(null);
            return null;
        }
        return trackRecordingService;
    }

    /**
     * Binds the service if it is started.
     *
     * @param startIfNeeded
     *            start the service if needed
     */
    private void bindService(boolean startIfNeeded) {
        if (trackRecordingService != null) {
            unbind();
        }

        if (!startIfNeeded && !TrackRecordingServiceConnectionUtils.isRecordingServiceRunning(context)) {
            Log.d(TAG, "Service is not started. Not binding it.");
            return;
        }

        //TODO zhh 旧的轨迹记录启动方式，zhh2018-7-31屏蔽，改用新的。
//        if (startIfNeeded) {
//            Log.i(TAG, "Starting the service.");
//            ComponentName componentName = null;
//            try{
//                componentName = context.startService(new Intent(Constant.TRACKS_SERVICE_ACTION));
//            } catch (Exception e){
//            }
//            if (componentName == null) {
//                return;
//            }
//        }
//        Log.i(TAG, "Binding the service.");
//        int flags = BuildConfig.DEBUG ? Context.BIND_DEBUG_UNBIND : 0;
//        if (!context.bindService(new Intent(Constant.TRACKS_SERVICE_ACTION), serviceConnection, flags)) {
//            return;
//        }

        long delayTime = 0;
        if (startIfNeeded) {
            Intent intent = new Intent();
            intent.setAction(Constant.TRACKS_SERVICE_ACTION);
            intent.setPackage(Constant.TRACKS_SERVICE_PACKAGE_NAME);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                ComponentName componentName = context.startService(intent);
                if (componentName == null) {
                    Intent intent1 = new Intent();
                    intent1.setComponent(new ComponentName(Constant.TRACKS_SERVICE_PACKAGE_NAME, Constant.TRACKS_SERVICE_ACTIVITY_NAME));
                    context.startActivity(intent1);
                    delayTime = 1000;
                }
            } else {
                ComponentName componentName = context.startService(intent);
                if (componentName == null) {
                    Toast.makeText(context,"请先设置轨迹服务的＂关联启动＂！\n操作步骤：＂设置＂->＂权限管理＂->＂TrackService＂->＂关联启动＂开启",Toast.LENGTH_LONG).show();
                    return;
                }
            }

        }
        handler.sendEmptyMessageDelayed(0, delayTime);

        //---------------------------   end   -------------------------- 2018-7-31

    }

    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            bindTrackService();
        }
    };

    private void bindTrackService(){
        int flags = BuildConfig.DEBUG ? Context.BIND_DEBUG_UNBIND : 0;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            Intent intentBind = new Intent();
            intentBind.setAction(Constant.TRACKS_SERVICE_ACTION);
            intentBind.setPackage(Constant.TRACKS_SERVICE_PACKAGE_NAME);
            if(!context.getApplicationContext().bindService(intentBind, serviceConnection, flags)) {
                Toast.makeText(context,"轨迹服务连接失败! 请先设置轨迹服务的＂关联启动＂！\n操作步骤：＂设置＂->＂权限管理＂->＂TrackService＂->＂关联启动＂开启",Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            if (!context.bindService(new Intent(Constant.TRACKS_SERVICE_ACTION), serviceConnection, flags)) {
                Toast.makeText(context,"轨迹服务连接失败! 请先设置轨迹服务的＂关联启动＂！\n操作步骤：＂设置＂->＂权限管理＂->＂TrackService＂->＂关联启动＂开启",Toast.LENGTH_LONG).show();
                return;
            }
        }
    }

    /**
     * Sets the trackRecordingService.
     *
     * @param value
     *            the value
     */
    private void setTrackRecordingService(ITrackRecordingService value) {
        trackRecordingService = value;
        if (callback != null) {
            callback.run();
        }
    }
}
