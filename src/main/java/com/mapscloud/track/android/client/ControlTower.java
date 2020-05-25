package com.mapscloud.track.android.client;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mapscloud.track.android.interfaces.TowerListener;
import com.mapscloud.track.services.VersionUtils;
import com.mapscloud.track.services.model.ITrackRecordingService;
import com.mapscloud.track.services.model.TrackRecordingService;
import com.mapscloud.track.services.utils.Constant;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 轨迹服务启动和断开的控制台
 */
public class ControlTower {

    private static final String TAG = "TrackControlTower";

    private final Context context;

    public ControlTower(Context context) {
        this.context = context;
    }

    private static final int    INVALID_LIB_VERSION = -1;
    private static final String SERVICES_CLAZZ_NAME = ITrackRecordingService.class.getName();
    private static final String METADATA_KEY        = "com.mapscloud.track.services.version";

    private ITrackRecordingService trackServices;
    private final IBinder.DeathRecipient deathRecipient = () -> {
        notifyTowerDisconnected();
        Log.e(Constant.TAG, "绑定服务异常断开");
    };

    // 记录服务是否连接着的状态值
    private final AtomicBoolean isServiceConnecting = new AtomicBoolean(false);

    private TowerListener towerListener;

    private final ServiceConnection trackConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {

            isServiceConnecting.set(false);

            // 注意: ITrackRecordingService实例化的asInterface方法，通过Binder对象构造
            trackServices = ITrackRecordingService.Stub.asInterface(service);

            Log.e(Constant.TAG, "trackConnection onServiceConnected mPackage = "
                    + className.getPackageName() + ", mClass = " + className.getClassName());

            try {
                trackServices.asBinder().linkToDeath(deathRecipient, 0);
                // 到这里进程间通信的服务就完全准备完毕，并通知客户端
                notifyTowerConnected();
            } catch (RemoteException e) {
                e.printStackTrace();
                notifyTowerDisconnected();

                Log.e(Constant.TAG, "trackConnection onServiceConnected catch = " + e.toString());
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            isServiceConnecting.set(false);
            notifyTowerDisconnected();
            Log.e(Constant.TAG, "trackConnection onServiceDisconnected mPackage = "
                    + className.getPackageName() + ", mClass = " + className.getClassName());
        }
    };

    void notifyTowerConnected() {
        if (towerListener == null)
            return;

        towerListener.onTrackConnected();
    }

    void notifyTowerDisconnected() {
        if (towerListener == null)
            return;

        towerListener.onTrackDisconnected();
    }


    public boolean isTowerConnected() {
        // 注意: ITrackRecordingService对象的asBinder().pingBinder()确定进程间通信的服务是否连接
        return trackServices != null && trackServices.asBinder().pingBinder();
    }

    /**
     * 这方法是控制已经启动的轨迹服务的节奏的, 不能调用
     */
    public void registerTrack(TracksServiceUtils track) {
        if (track == null)
            return;

        if (!isTowerConnected())
            throw new IllegalStateException("轨迹服务未启动，请调用控制塔连接方法");

//        track.init(this);
    }

    public void unregisterTrack() {

    }

    /**
     * 需要传连接成功还是失败的Listener
     */
    public void connect(TowerListener listener) {
        if (towerListener != null && (isServiceConnecting.get() || isTowerConnected()))
            return;

        if (listener == null) {
            throw new IllegalArgumentException("ServiceListener argument cannot be null.");
        }

        towerListener = listener;

        if (!isTowerConnected() && !isServiceConnecting.get()) {
            /**
             * 这里获取到的Intent一定可以绑定成功进程间通信的服务
             * 返回false的情况只有一种，就是该进程已经绑定过该服务，不需要再次绑定启动
             */
            final Intent serviceIntent = getAvailableServicesInstance(context);
            boolean isBindSuccess = context.bindService(serviceIntent, trackConnection,
                    Context.BIND_AUTO_CREATE);
            Log.e(Constant.TAG, "isBindSuccess = " + isBindSuccess);
            isServiceConnecting.set(isBindSuccess);
        }
    }

    public void disconnect() {
        if (trackServices != null) {
            trackServices.asBinder().unlinkToDeath(deathRecipient, 0);
            trackServices = null;
        }

        notifyTowerDisconnected();

        towerListener = null;

        try {
            context.unbindService(trackConnection);
        } catch (Exception e) {
            Log.e(TAG, "Error occurred while unbinding from DroneKit-Android.");
        }
    }

    public static final String x = "3";

    private Intent getAvailableServicesInstance(@NonNull Context context) {
        final String appId = context.getPackageName();
        final PackageManager pm = context.getPackageManager();

        //Check if an instance of the services library is up and running.
        final Intent            serviceIntent = new Intent(SERVICES_CLAZZ_NAME);
        final List<ResolveInfo> serviceInfos  = pm.queryIntentServices(serviceIntent, PackageManager.GET_META_DATA);
        if (serviceInfos != null && !serviceInfos.isEmpty()) {
            Log.e(Constant.TAG, "serviceInfos size = " + serviceInfos.size());
            for (ResolveInfo serviceInfo : serviceInfos) {
                final Bundle metaData = serviceInfo.serviceInfo.metaData;
                if (metaData == null)
                    continue;

                if (!serviceInfo.serviceInfo.packageName.equals(appId)){
                    continue;
                }

                final int coreLibVersion = metaData.getInt(METADATA_KEY, INVALID_LIB_VERSION);
                if (coreLibVersion != INVALID_LIB_VERSION && coreLibVersion >= VersionUtils.getCoreLibVersion(context)) {
                    serviceIntent.setClassName(serviceInfo.serviceInfo.packageName, serviceInfo.serviceInfo.name);
                    Log.e(Constant.TAG, METADATA_KEY + " = " + coreLibVersion);
                    Log.e(Constant.TAG, "serviceInfo.serviceInfo.packageName = " + serviceInfo.serviceInfo.packageName);
                    Log.e(Constant.TAG, "serviceInfo.serviceInfo.name = " + serviceInfo.serviceInfo.name);
                    return serviceIntent;
                }
            }
        }

        //Didn't find any that's up and running. Enable the local one
        TrackRecordingService.enableTrackRecordingService(context, true);
        serviceIntent.setClass(context, TrackRecordingService.class);
        Log.e(Constant.TAG, "serviceIntent action = " + serviceIntent.getAction());
        Log.e(Constant.TAG, "serviceIntent class = " + serviceIntent.getComponent().getPackageName()
                + "." + serviceIntent.getComponent().getClassName());
        return serviceIntent;
    }

    public ITrackRecordingService getTrackServices() {
        return trackServices;
    }
}
