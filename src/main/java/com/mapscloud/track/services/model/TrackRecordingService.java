/*
 * Copyright 2008 Google Inc.
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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import com.amap.api.location.AMapLocation;
import com.dtt.signal.SignalManager1;
import com.mapscloud.track.R;
import com.mapscloud.track.services.content.DescriptionGeneratorImpl;
import com.mapscloud.track.services.content.Track;
import com.mapscloud.track.services.content.TripStatistics;
import com.mapscloud.track.services.content.TripStatisticsUpdater;
import com.mapscloud.track.services.model.Waypoint.WaypointType;
import com.mapscloud.track.services.provider.MyTracksProvider;
import com.mapscloud.track.services.provider.MyTracksProviderUtils;
import com.mapscloud.track.services.tracks.AnnouncementPeriodicTaskFactory;
import com.mapscloud.track.services.tracks.PeriodicTaskExecutor;
import com.mapscloud.track.services.tracks.Sensor;
import com.mapscloud.track.services.tracks.Sensor.SensorDataSet;
import com.mapscloud.track.services.tracks.SensorManager;
import com.mapscloud.track.services.tracks.SensorManagerFactory;
import com.mapscloud.track.services.tracks.SplitPeriodicTaskFactory;
import com.mapscloud.track.services.utils.Constant;
import com.mapscloud.track.services.utils.LocationUtils;
import com.mapscloud.track.services.utils.PreferencesUtils;
import com.mapscloud.track.services.utils.TrackNameUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import timber.log.Timber;


/**
 * A background service that registers a location listener and records track
 * points. Track points are saved to the {@link MyTracksProvider}.
 *
 * @author Leif Hendrik Wilden
 */
public class TrackRecordingService extends Service {

    private static final String TAG = TrackRecordingService.class.getSimpleName();
    public static final double PAUSE_LATITUDE = 100.0;
    public static final double RESUME_LATITUDE = 200.0;

    // One second in milliseconds
    private static final long ONE_SECOND = 1000;
    // One minute in milliseconds
    private static final long ONE_MINUTE = 60 * ONE_SECOND;

    static final int MAX_AUTO_RESUME_TRACK_RETRY_ATTEMPTS = 3;

    // The following variables are set in onCreate:
    private Context context;
    private MyTracksProviderUtils myTracksProviderUtils;
    private MyTracksLocationManager myTracksLocationManager;
    private PeriodicTaskExecutor voiceExecutor;
    private PeriodicTaskExecutor splitExecutor;
    private ExecutorService executorService;
    private SharedPreferences sharedPreferences;
    /**
     * 原轨迹数据库开启后只能记录的一条的id
     * 现改为ConcurrentHashMap来保存多个app同时开启轨迹的id
     * {@link #trackIds}
     */
    private long recordingTrackId;
    private boolean recordingTrackPaused;
    private LocationListenerPolicy locationListenerPolicy;
    private int minRecordingDistance;
    private int maxRecordingDistance;
    private int minRequiredAccuracy;
    private int autoResumeTrackTimeout;
    private long currentRecordingInterval;

    // The following variables are set when recording:
    private TripStatisticsUpdater trackTripStatisticsUpdater;
    private TripStatisticsUpdater markerTripStatisticsUpdater;
    private WakeLock wakeLock;
    private SensorManager sensorManager;
    private Location lastLocation;
    private boolean currentSegmentHasLocation;

    // Timer to periodically invoke checkLocationListener
    private final Timer timer = new Timer();

    // Handler for the timer to post a runnable to the main thread
    private final Handler handler = new Handler();

    /* it is a aidl for third part app use */
    private ServiceBinder binder = new ServiceBinder(this);

    // 记录开启轨迹的应用集合，键为applicationId，值为当前开启的轨迹id
    private HashSet<String> appIds = new HashSet<>();
    private HashMap<String, Long> trackIds = new HashMap<>();
    private HashMap<String, Boolean> trackPauseds = new HashMap<>();
    private String common_id;
    private String common_paused;

    /*
     * Note that sharedPreferenceChangeListener cannot be an anonymous inner
     * class. Anonymous inner class will get garbage collected.
     */
    private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences,
                                              String key) {
            // 恢复上次结束前的所有关键状态记录集合
            if (key == null) {
                Set<String> oldAppIds = PreferencesUtils.getArrayString(context, R.string.recording_track_appid_key);
                if (oldAppIds.size() > 0) {
                    appIds.clear();
                    appIds.addAll(oldAppIds);
                    for (String appId : oldAppIds) {
                        long trackId = PreferencesUtils.getLong(context, appId + common_id);
                        boolean trackPaused = PreferencesUtils.getBoolean(context,
                                appId + common_paused,
                                PreferencesUtils.RECORDING_TRACK_PAUSED_DEFAULT);
                        trackIds.put(appId, trackId);
                        trackPauseds.put(appId, trackPaused);
                        Timber.e("OnSharedPreferenceChangeListener 上次保存结果" +
                                        " = {appId : %s, trackId : %d, paused : %b }",
                                appId, trackId, trackPaused);
                    }
                }
            }

            // 客户端暂停、继续、开始时会刷新一次轨迹Id值
            if (key != null && key.contains(common_id)) {
                long trackId = PreferencesUtils.getLong(context, key);
                int index = key.indexOf(common_id);
                if (index > 0) {
                    String appId = key.substring(0, index);
                    Timber.e("OnSharedPreferenceChangeListener { appId : %s, trackId : %d }",
                            appId, trackId);
                    if (appId.contains("."))
                        trackIds.put(appId, trackId);
                }
            }

            // 客户端暂停、继续、开始时会刷新一次暂停状态值
            if (key != null && key.contains(common_paused)) {
                boolean trackPaused = PreferencesUtils.getBoolean(context, key,
                        PreferencesUtils.RECORDING_TRACK_PAUSED_DEFAULT);
                int index = key.indexOf(common_paused);
                if (index > 0) {
                    String appId = key.substring(0, index);
                    Timber.e("OnSharedPreferenceChangeListener { appId : %s, trackPaused : %b }",
                            appId, trackPaused);
                    if (appId.contains("."))
                        trackPauseds.put(appId, trackPaused);
                }
            }


            /*if (key == null
                    || key.equals(common_id)) {
                long trackId = PreferencesUtils.getLong(context, common_id);
                Timber.e("OnSharedPreferenceChangeListener trackId = %d", trackId);
                *//*
             * Only through the TrackRecordingService can one stop a
             * recording and set the recordingTrackId to -1L.
             *//*
                if (trackId != PreferencesUtils.RECORDING_TRACK_ID_DEFAULT) {
                    recordingTrackId = trackId;
                }
            }
            if (key == null
                    || key.equals(common_paused)) {
                recordingTrackPaused = PreferencesUtils.getBoolean(context,
                        R.string.recording_track_paused_key,
                        PreferencesUtils.RECORDING_TRACK_PAUSED_DEFAULT);
                Timber.e("OnSharedPreferenceChangeListener recordingTrackPaused = %b", recordingTrackPaused);
            }*/
            if (key == null
                    || key.equals(PreferencesUtils.getKey(context,
                    R.string.metric_units_key))) {
                boolean metricUnits = PreferencesUtils.getBoolean(context,
                        R.string.metric_units_key,
                        PreferencesUtils.METRIC_UNITS_DEFAULT);
                voiceExecutor.setMetricUnits(metricUnits);
                splitExecutor.setMetricUnits(metricUnits);
                Timber.e("OnSharedPreferenceChangeListener metricUnits = %b", metricUnits);
            }
            if (key == null
                    || key.equals(PreferencesUtils.getKey(context,
                    R.string.voice_frequency_key))) {
                voiceExecutor.setTaskFrequency(PreferencesUtils.getInt(context,
                        R.string.voice_frequency_key,
                        PreferencesUtils.VOICE_FREQUENCY_DEFAULT));
//                Timber.e("OnSharedPreferenceChangeListener voiceExecutor = %b", voiceExecutor);
            }
            if (key == null
                    || key.equals(PreferencesUtils.getKey(context,
                    R.string.split_frequency_key))) {
                int splitExecutorValue = PreferencesUtils.getInt(context,
                        R.string.split_frequency_key,
                        PreferencesUtils.SPLIT_FREQUENCY_DEFAULT);
                splitExecutor.setTaskFrequency(splitExecutorValue);
                Timber.e("OnSharedPreferenceChangeListener splitExecutorValue = %d", splitExecutorValue);
            }
            if (key == null
                    || key.equals(PreferencesUtils.getKey(context,
                    R.string.min_recording_interval_key))) {
                int minRecordingInterval = PreferencesUtils.getInt(context,
                        R.string.min_recording_interval_key,
                        PreferencesUtils.MIN_RECORDING_INTERVAL_DEFAULT);
                switch (minRecordingInterval) {
                    case PreferencesUtils.MIN_RECORDING_INTERVAL_ADAPT_BATTERY_LIFE:
                        // Choose battery life over moving time accuracy.
                        locationListenerPolicy = new AdaptiveLocationListenerPolicy(
                                30 * ONE_SECOND, 5 * ONE_MINUTE, 5);
                        break;
                    case PreferencesUtils.MIN_RECORDING_INTERVAL_ADAPT_ACCURACY:
                        // Get all the updates.
                        locationListenerPolicy = new AdaptiveLocationListenerPolicy(
                                ONE_SECOND, 30 * ONE_SECOND, 0);
                        break;
                    default:
                        locationListenerPolicy = new AbsoluteLocationListenerPolicy(
                                minRecordingInterval * ONE_SECOND);
                }
                Timber.e("OnSharedPreferenceChangeListener minRecordingInterval = %d", minRecordingInterval);
            }
            if (key == null
                    || key.equals(PreferencesUtils.getKey(context,
                    R.string.min_recording_distance_key))) {
                minRecordingDistance = PreferencesUtils.getInt(context,
                        R.string.min_recording_distance_key,
                        PreferencesUtils.MIN_RECORDING_DISTANCE_DEFAULT);
                Timber.e("OnSharedPreferenceChangeListener minRecordingDistance = %d", minRecordingDistance);
            }
            if (key == null
                    || key.equals(PreferencesUtils.getKey(context,
                    R.string.max_recording_distance_key))) {
                maxRecordingDistance = PreferencesUtils.getInt(context,
                        R.string.max_recording_distance_key,
                        PreferencesUtils.MAX_RECORDING_DISTANCE_DEFAULT);
                Timber.e("OnSharedPreferenceChangeListener maxRecordingDistance = %d", maxRecordingDistance);
            }
            if (key == null
                    || key.equals(PreferencesUtils.getKey(context,
                    R.string.min_required_accuracy_key))) {
                minRequiredAccuracy = PreferencesUtils.getInt(context,
                        R.string.min_required_accuracy_key,
                        PreferencesUtils.MIN_REQUIRED_ACCURACY_DEFAULT);
                Timber.e("OnSharedPreferenceChangeListener minRequiredAccuracy = %d", minRequiredAccuracy);
            }
            if (key == null
                    || key.equals(PreferencesUtils.getKey(context,
                    R.string.auto_resume_track_timeout_key))) {
                autoResumeTrackTimeout = PreferencesUtils.getInt(context,
                        R.string.auto_resume_track_timeout_key,
                        PreferencesUtils.AUTO_RESUME_TRACK_TIMEOUT_DEFAULT);
                Timber.e("OnSharedPreferenceChangeListener autoResumeTrackTimeout = %d", autoResumeTrackTimeout);
            }
        }
    };

    /**
     * SignalManager1 定位监听
     */
    private SignalManager1.LocationChangedObserver locationObeserver = new SignalManager1.LocationChangedObserver() {
        @Override
        public void onLocationChanged(Location location) {
            locationHandle(location);
        }

        @Override
        public void onError(Location prelocation) {

        }

        @Override
        public void onInvalid() {

        }
    };

    private void locationHandle(Location location) {
        Timber.e("TrackRecordingService onLocationChanged()");
        if (myTracksLocationManager == null || executorService == null
                || !myTracksLocationManager.isAllowed()
                || executorService.isShutdown()
                || executorService.isTerminated()) {
            return;
        }
        executorService.submit(new Runnable() {
            @Override
            public void run() {
//                    onLocationChangedAsync(location);
                onLocationChangedAllApp(location);
            }
        });
    }


    /**
     * LocationManager定位监听
     */
    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onProviderDisabled(String provider) {
            // Do nothing
        }

        @Override
        public void onProviderEnabled(String provider) {
            // Do nothing
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // Do nothing
        }

        @Override
        public void onLocationChanged(final Location location) {
            locationHandle(location);
        }
    };

    private TimerTask checkLocationListener = new TimerTask() {
        @Override
        public void run() {
            if (isRecording() && !isPaused()) {
                Timber.e("重新注册定位 TimerTask每分钟重新定位一次 checkLocationListener");
                registerLocationListener();
            }
        }
    };

    /*
     * Note that this service, through the AndroidManifest.xml, is configured to
     * allow both MyTracks and third party apps to invoke it. For the onCreate
     * callback, we cannot tell whether the caller is MyTracks or a third party
     * app, thus it cannot start/stop a recording or write/update MyTracks
     * database.
     */
    @Override
    public void onCreate() {
        super.onCreate();

        Timber.e("Track Recoding Service onCreate()");

        context = this;
        common_id = PreferencesUtils.getKey(context, R.string.recording_track_id_key);
        common_paused = PreferencesUtils.getKey(context, R.string.recording_track_paused_key);

        // 利用ContentProvider将经纬度写入到db文件
        myTracksProviderUtils = MyTracksProviderUtils.Factory.get(this);
        // 定位管理
        myTracksLocationManager = new MyTracksLocationManager(this);
        voiceExecutor = new PeriodicTaskExecutor(this,
                new AnnouncementPeriodicTaskFactory());
        splitExecutor = new PeriodicTaskExecutor(this,
                new SplitPeriodicTaskFactory());
        executorService = Executors.newSingleThreadExecutor();
        // 保存轨迹记录配置的SharedPreferences
        sharedPreferences = getSharedPreferences(Constant.SETTINGS_NAME, Context.MODE_PRIVATE);
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

        // onSharedPreferenceChanged might not set recordingTrackId.
        recordingTrackId = PreferencesUtils.RECORDING_TRACK_ID_DEFAULT;

        // 刚创建该服务时，将轨迹Id设为-1，因为还没有在数据库创建成功轨迹
//        trackIds.put(); // 暂时没法设，好像没把applicationId做好

        // Require announcementExecutor and splitExecutor to be created.
        // 调用这个方法刷新全局变量为保存在SharedPreferences的数据
        sharedPreferenceChangeListener.onSharedPreferenceChanged(sharedPreferences, null);

        // 定位每一分钟重新注册一次的任务，这里先不要
        timer.schedule(checkLocationListener, 0, ONE_MINUTE);

        /*
         * Try to restart the previous recording track in case the service has
         * been restarted by the system, which can sometimes happen.
         */
//        Track track = myTracksProviderUtils.getTrack(recordingTrackId);
//        if (track != null) { // 如果已经存在轨迹，继续之前的轨迹
//            restartTrack(track);
//        } else { // 如果轨迹是空的，显示轨迹服务启动通知，并且如果recordingTrackId不为-1，需要将SharedPreferences中置为-1和暂停状态
//            if (isRecording()) {
//                Timber.e("track is null, but recordingTrackId not -1L. " + recordingTrackId);
//                updateRecordingState(
//                        PreferencesUtils.RECORDING_TRACK_ID_DEFAULT, true);
//            }
//            showNotification();
//        }

        for (String appId : appIds) {
            long recordingTrackId = trackIds.get(appId);
            Track track = myTracksProviderUtils.getTrack(recordingTrackId);
            if (track != null) { // 如果已经存在轨迹，继续之前的轨迹
                restartTrackWithId(track, appId, recordingTrackId);
            } else { // 如果轨迹是空的，显示轨迹服务启动通知，并且如果recordingTrackId不为-1，需要将SharedPreferences中置为-1和暂停状态
                if (isRecording(appId)) {
                    Timber.e("track is null, but recordingTrackId not -1L. " + recordingTrackId);
                    appIds.remove(appId);
                    updateRecordingState(appId,
                            PreferencesUtils.RECORDING_TRACK_ID_DEFAULT, true);
                }
                showNotification(appId);
            }
        }

    }

    /*
     * Note that this service, through the AndroidManifest.xml, is configured to
     * allow both MyTracks and third party apps to invoke it. For the onStart
     * callback, we cannot tell whether the caller is MyTracks or a third party
     * app, thus it cannot start/stop a recording or write/update MyTracks
     * database.
     */
    @Override
    public void onStart(Intent intent, int startId) {
        handleStartCommand(intent, startId);
    }

    /*
     * Note that this service, through the AndroidManifest.xml, is configured to
     * allow both MyTracks and third party apps to invoke it. For the
     * onStartCommand callback, we cannot tell whether the caller is MyTracks or
     * a third party app, thus it cannot start/stop a recording or write/update
     * MyTracks database.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Timber.e("onStartCommand: intent extra RESUME_TRACK = %b and startId = %d",
                intent.getBooleanExtra(Constant.RESUME_TRACK_EXTRA_NAME, false),
                startId);
        handleStartCommand(intent, startId);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        // onDestroy方法调用时，一定要保证所有的轨迹已经结束，客户端来控制
        if (appIds.size() == 0) {
            showNotification();

            checkLocationListener.cancel();
            checkLocationListener = null;

            timer.cancel();
            timer.purge();

            sharedPreferences
                    .unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

            unregisterLocationListener();


            try {
                voiceExecutor.shutdown();
            } finally {
                voiceExecutor = null;
            }

            try {
                splitExecutor.shutdown();
            } finally {
                splitExecutor = null;
            }

            if (sensorManager != null) {
                SensorManagerFactory.releaseSystemSensorManager();
                sensorManager = null;
            }

            // Make sure we have no indirect references to this service.
            myTracksProviderUtils = null;
            myTracksLocationManager.close();
            myTracksLocationManager = null;
            binder.detachFromService();
            binder = null;

            // This should be the next to last operation
            releaseWakeLock();
            stopHeartbeatTask(true);

            /*
             * Shutdown the executor service last to avoid sending events to a dead
             * executor.
             */
            executorService.shutdown();
        }

        enableTrackRecordingService(getApplicationContext(), false);

        super.onDestroy();


        Timber.e("Track Recoding Service onDestroy()");
    }

    /**
     * Returns true if the service is recording.
     */
    public boolean isRecording() {
        return recordingTrackId != PreferencesUtils.RECORDING_TRACK_ID_DEFAULT;
    }

    public boolean isRecording(String appId) {
        return trackIds.get(appId) != null
                && trackIds.get(appId) != PreferencesUtils.RECORDING_TRACK_ID_DEFAULT
                && trackPauseds.get(appId) != null
                && !trackPauseds.get(appId);
    }

    public boolean isVaildTrackId(String appId) {
        return trackIds.get(appId) != null
                && trackIds.get(appId) != PreferencesUtils.RECORDING_TRACK_ID_DEFAULT;
    }

    /**
     * Returns true if the current recording is paused.
     */
    public boolean isPaused() {
        return recordingTrackPaused;
    }

    public boolean isPaused(String appId) {
        if (trackPauseds.get(appId) == null) {
            return true;
        }
        return trackPauseds.get(appId);
    }

    /**
     * Gets the trip statistics.
     */
    public TripStatistics getTripStatistics() {
        if (trackTripStatisticsUpdater == null) {
            return null;
        }
        return trackTripStatisticsUpdater.getTripStatistics();
    }

    /**
     * Inserts a waypoint.
     *
     * @param waypointCreationRequest the waypoint creation request
     * @return the waypoint id
     */
    public long insertWaypoint(WaypointCreationRequest waypointCreationRequest) {
        if (!isRecording() || isPaused()) {
            return -1L;
        }

        WaypointType waypointType = waypointCreationRequest.getType();
        boolean isStatistics = waypointType == WaypointType.STATISTICS;

        // Get name
        String name;
        if (waypointCreationRequest.getName() != null) {
            name = waypointCreationRequest.getName();
        } else {
            int nextWaypointNumber = myTracksProviderUtils
                    .getNextWaypointNumber(recordingTrackId, waypointType);
            if (nextWaypointNumber == -1) {
                nextWaypointNumber = 0;
            }
            name = getString(isStatistics ? R.string.marker_split_name_format
                    : R.string.marker_name_format, nextWaypointNumber);
        }

        // Get category
        String category = waypointCreationRequest.getCategory() != null ? waypointCreationRequest
                .getCategory() : "";

        // Get tripStatistics, description, and icon
        TripStatistics tripStatistics;
        String description;
        String icon;
        if (isStatistics) {
            long now = System.currentTimeMillis();
            markerTripStatisticsUpdater.updateTime(now);
            tripStatistics = markerTripStatisticsUpdater.getTripStatistics();
            markerTripStatisticsUpdater = new TripStatisticsUpdater(now);
            description = new DescriptionGeneratorImpl(this)
                    .generateWaypointDescription(tripStatistics);
            icon = getString(R.string.marker_statistics_icon_url);
        } else {
            tripStatistics = null;
            description = waypointCreationRequest.getDescription() != null ? waypointCreationRequest
                    .getDescription() : "";
            icon = getString(R.string.marker_waypoint_icon_url);
        }

        // Get length and duration
        double length;
        long duration;
        Location location = getLastValidTrackPointInCurrentSegment(recordingTrackId);
        if (location != null && trackTripStatisticsUpdater != null) {
            TripStatistics stats = trackTripStatisticsUpdater
                    .getTripStatistics();
            length = stats.getTotalDistance();
            duration = stats.getTotalTime();
        } else {
            if (!waypointCreationRequest.isTrackStatistics()) {
                return -1L;
            }
            // For track statistics, make it an impossible location
            location = new Location("");
            location.setLatitude(100);
            location.setLongitude(180);
            length = 0.0;
            duration = 0L;
        }

        // Insert waypoint
        Waypoint waypoint = new Waypoint(name, description, category, icon,
                recordingTrackId, waypointType, length, duration, -1L, -1L,
                location, tripStatistics);
        Uri uri = myTracksProviderUtils.insertWaypoint(waypoint);
        return Long.parseLong(uri.getLastPathSegment());
    }

    /**
     * Starts the service as a foreground service.
     *
     * @param notification the notification for the foreground service
     */

    protected void startForegroundService(Notification notification) {
        startForeground(2003, notification);
        SignalManager1.getInstance().enableBackgroundLocation(2003, notification);
    }

    /**
     * Stops the service as a foreground service.
     */

    protected void stopForegroundService() {
        stopForeground(true);
        SignalManager1.getInstance().disableBackgroundLocation(true);
    }

    /**
     * Handles start command.
     *
     * @param intent  the intent
     * @param startId the start id
     */
    private void handleStartCommand(Intent intent, int startId) {
        // Check if the service is called to resume track (from phone reboot)
        if (intent != null
                && intent.getBooleanExtra(Constant.RESUME_TRACK_EXTRA_NAME, false)) {
            if (!shouldResumeTrack()) {
                Log.i(TAG, "Stop resume track.");
                updateRecordingState(
                        PreferencesUtils.RECORDING_TRACK_ID_DEFAULT, true);
                stopSelfResult(startId);
                return;
            }
        }
    }

    /**
     * Returns true if should resume.
     */
    private boolean shouldResumeTrack() {
        Track track = myTracksProviderUtils.getTrack(recordingTrackId);

        if (track == null) {
            Log.d(TAG, "Not resuming. Track is null.");
            return false;
        }
        int retries = PreferencesUtils.getInt(this,
                R.string.auto_resume_track_current_retry_key,
                PreferencesUtils.AUTO_RESUME_TRACK_CURRENT_RETRY_DEFAULT);
        if (retries >= MAX_AUTO_RESUME_TRACK_RETRY_ATTEMPTS) {
            Log.d(TAG, "Not resuming. Exceeded maximum retry attempts.");
            return false;
        }
        PreferencesUtils.setInt(this,
                R.string.auto_resume_track_current_retry_key, retries + 1);

        if (autoResumeTrackTimeout == PreferencesUtils.AUTO_RESUME_TRACK_TIMEOUT_NEVER) {
            Log.d(TAG, "Not resuming. Auto-resume track timeout set to never.");
            return false;
        } else if (autoResumeTrackTimeout == PreferencesUtils.AUTO_RESUME_TRACK_TIMEOUT_ALWAYS) {
            Log.d(TAG, "Resuming. Auto-resume track timeout set to always.");
            return true;
        }

        if (track.getTripStatistics() == null) {
            Log.d(TAG, "Not resuming. No trip statistics.");
            return false;
        }
        long stopTime = track.getTripStatistics().getStopTime();
        return stopTime > 0
                && (System.currentTimeMillis() - stopTime) <= autoResumeTrackTimeout
                * ONE_MINUTE;
    }

    /**
     * 开始轨迹记录
     *
     * @param appId   开始记录的app包名
     * @param appName 开始记录的app名字
     * @return 轨迹记录ID
     */
    private long startNewTrackWithAppInfo(String appId, String appName) {
        if (isRecording(appId)) {
            return -1L;
        }
        long now = System.currentTimeMillis();
        trackTripStatisticsUpdater = new TripStatisticsUpdater(now);
        markerTripStatisticsUpdater = new TripStatisticsUpdater(now);

        // Insert a track
        Track track = new Track();
        Uri uri = null;
        try {
            uri = myTracksProviderUtils.insertTrack(track);
        } catch (Exception e) {
            e.printStackTrace();
            Timber.e("ContentResolver插入新轨迹错误：%s", e.toString());
        }
        long trackId = Long.parseLong(uri.getLastPathSegment());
        Timber.e("insert_track trackId = %d, url = %s", trackId, uri.toString());
        if (!TextUtils.isEmpty(appId)) {
            appIds.add(appId);
            trackIds.put(appId, trackId);
            trackPauseds.put(appId, false);  // 暂停状态更新为false
        }

        // Update shared preferences
        updateRecordingState(appId, trackId, false);
        PreferencesUtils.setInt(this,
                R.string.auto_resume_track_current_retry_key, 0);

        // Update database
        track.id = (trackId);
        track.appId = appId;
        track.appName = appName;
        track.serverDbId = (Constant.DEFAULT_SERVER_DB_ID);
        track.name = (TrackNameUtils.getTrackName(this, trackId, now, null));
        track.category = (PreferencesUtils.getString(this,
                R.string.default_activity_key,
                PreferencesUtils.DEFAULT_ACTIVITY_DEFAULT));
        track.setTripStatistics(trackTripStatisticsUpdater.getTripStatistics());
        myTracksProviderUtils.updateTrack(track);
        insertWaypoint(WaypointCreationRequest.DEFAULT_START_TRACK);

        if (!TextUtils.isEmpty(appId)) {
            startRecording(true, appId);
        } else {
            startRecording(true);
        }

        Timber.e("开始轨迹记录  startNewTrackWithAppInfo（%s， %s， %d）", appName, appId, trackId);
        return trackId;
    }

    /**
     * Starts a new track.
     *
     * @return the track id
     */
    private long startNewTrack() {
        return startNewTrackWithAppInfo("", "");
    }

    /**
     * Restart a track.
     *
     * @param track the track
     */
    private void restartTrack(Track track) {
        restartTrackWithId(track, "", recordingTrackId);
    }

    /**
     * Restart a track by params id and appid.
     *
     * @param track the track
     */
    private void restartTrackWithId(Track track, String appid, long trackId) {
        TripStatistics tripStatistics = track.getTripStatistics();
        trackTripStatisticsUpdater = new TripStatisticsUpdater(
                tripStatistics.getStartTime());

        long markerStartTime;
        Waypoint waypoint = myTracksProviderUtils.getLastWaypoint(trackId, WaypointType.STATISTICS);
        if (waypoint != null && waypoint.tripStatistics != null) {
            markerStartTime = waypoint.tripStatistics.getStopTime();
        } else {
            markerStartTime = tripStatistics.getStartTime();
        }
        markerTripStatisticsUpdater = new TripStatisticsUpdater(markerStartTime);

        Cursor cursor = null;
        try {
            // TODO: how to handle very long track.
            cursor = myTracksProviderUtils.getTrackPointCursor(
                    trackId, -1L, Constant.MAX_LOADED_TRACK_POINTS,
                    true);
            if (cursor == null) {
                Log.e(TAG, "Cursor is null.");
            } else {
                if (cursor.moveToLast()) {
                    do {
                        Location location = myTracksProviderUtils
                                .createTrackPoint(cursor);
                        trackTripStatisticsUpdater.addLocation(location,
                                minRecordingDistance);
                        if (location.getTime() > markerStartTime) {
                            markerTripStatisticsUpdater.addLocation(location,
                                    minRecordingDistance);
                        }
                    } while (cursor.moveToPrevious());
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "RuntimeException", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (TextUtils.isEmpty(appid)) {
            startRecording(true);
        } else {
            startRecording(true, appid);
        }
        Timber.e("继续轨迹成功 appId: %s, trackId: %d", appid, trackId);
    }

    /**
     * Resumes current track.
     */
    private void resumeCurrentTrack() {
        if (!isRecording() || !isPaused()) {
            Log.d(TAG,
                    "Ignore resumeCurrentTrack. Not recording or not paused.");
            return;
        }

        // Update shared preferences
        recordingTrackPaused = false;
        PreferencesUtils.setBoolean(this, R.string.recording_track_paused_key,
                false);

        // Update database
        Track track = myTracksProviderUtils.getTrack(recordingTrackId);
        if (track != null) {
            Location resume = new Location(LocationManager.GPS_PROVIDER);
            resume.setLongitude(0);
            resume.setLatitude(RESUME_LATITUDE);
            resume.setTime(System.currentTimeMillis());
            insertLocation(track, resume, null);
        }

        startRecording(false);
    }

    private void resumeCurrentTrack(String appId) {
        if (isRecording(appId)) {
            Log.d(TAG, "Ignore resumeCurrentTrack. Not recording or not paused.");
            return;
        }

        // Update database
        Track track = myTracksProviderUtils.getTrack(trackIds.get(appId));
        if (track != null) {
            Location resume = new Location(LocationManager.GPS_PROVIDER);
            resume.setLongitude(0);
            resume.setLatitude(RESUME_LATITUDE);
            resume.setTime(System.currentTimeMillis());
            insertLocation(track, resume, null);
        }

        startRecording(false, appId);

        trackPauseds.put(appId, false); // 暂停状态更新为false
        Timber.e("%s 暂停轨迹成功", appId);
    }

    /**
     * Common code for starting a new track, resuming a track, or restarting
     * after phone reboot.
     *
     * @param trackStarted true if track is started, false if track is resumed
     */
    private void startRecording(boolean trackStarted) {
        acquireWakeLock();
        startHeartbeatTask();

        // Update instance variables
        sensorManager = SensorManagerFactory.getSystemSensorManager(this);
        lastLocation = null;
        currentSegmentHasLocation = false;

        // Register notifications
        // if current is commer track, so use gps
        // point, if current is navigation track,
        // so use navigation point
        registerLocationListener();
        Timber.e("重新注册定位 startRecording 没有appId");

        // Send notifications
        showNotification();
        sendTrackBroadcast(
                trackStarted ? R.string.track_started_broadcast_action
                        : R.string.track_resumed_broadcast_action,
                recordingTrackId);

        // Restore periodic tasks
        voiceExecutor.restore();
        splitExecutor.restore();
    }

    private void startRecording(boolean trackStarted, String appId) {
        acquireWakeLock();
        startHeartbeatTask();

        // Update instance variables
        sensorManager = SensorManagerFactory.getSystemSensorManager(this);
        lastLocation = null;
        currentSegmentHasLocation = false;

        // Register notifications
        // if current is commer track, so use gps
        // point, if current is navigation track,
        // so use navigation point
        registerLocationListener();
        Timber.e("重新注册定位 startRecording");

        // Send notifications
        showNotification(appId);
        sendTrackBroadcast(
                trackStarted ? R.string.track_started_broadcast_action
                        : R.string.track_resumed_broadcast_action,
                trackIds.get(appId));

        // Restore periodic tasks
        voiceExecutor.restore();
        splitExecutor.restore();
        Timber.e("%s 开始轨迹成功, 以及开始暂停状态 = %b", appId, trackStarted);
    }

    /**
     * Ends the current track.
     */
    private void endCurrentTrack() {
        if (!isRecording()) {
            Log.d(TAG, "Ignore endCurrentTrack. Not recording.");
            return;
        }

        // Need to remember the recordingTrackId before setting it to -1L
        long trackId = recordingTrackId;
        boolean paused = recordingTrackPaused;

        // Update shared preferences
        updateRecordingState(PreferencesUtils.RECORDING_TRACK_ID_DEFAULT, true);

        // Update database
        Track track = myTracksProviderUtils.getTrack(trackId);
        if (track != null && !paused) {
            insertLocation(track, lastLocation,
                    getLastValidTrackPointInCurrentSegment(trackId));
            updateRecordingTrack(track,
                    myTracksProviderUtils.getLastTrackPointId(trackId), false);
        }

        endRecording(true, trackId, "");
        stopSelf();
    }

    /**
     * 结束轨迹记录
     *
     * @param appId 结束轨迹记录的app包名
     */
    private void endCurrentTrack(String appId) {
        if (!isVaildTrackId(appId)) {
            Timber.e("%s 结束轨迹时，轨迹ID = -1，轨迹无效", appId);
            return;
        }

        // Need to remember the recordingTrackId before setting it to -1L
        long trackId = trackIds.get(appId);
        boolean paused = trackPauseds.get(appId);

        // Update shared preferences
        appIds.remove(appId);
        updateRecordingState(appId, PreferencesUtils.RECORDING_TRACK_ID_DEFAULT, true);

        // Update database
        Track track = myTracksProviderUtils.getTrack(trackId);
        if (track != null && !paused) {
            insertLocation(track, lastLocation,
                    getLastValidTrackPointInCurrentSegment(trackId));
            updateRecordingTrack(track,
                    myTracksProviderUtils.getLastTrackPointId(trackId), false);
        }

        endRecording(true, trackId, appId);
        stopSelf();

        Timber.e("%s 结束轨迹成功", appId);
    }

    /**
     * Gets the last valid track point in the current segment. Returns null if
     * not available.
     *
     * @param trackId the track id
     */
    private Location getLastValidTrackPointInCurrentSegment(long trackId) {
        if (!currentSegmentHasLocation) {
            return null;
        }
        return myTracksProviderUtils.getLastValidTrackPoint(trackId);
    }

    /**
     * Pauses the current track.
     */
    private void pauseCurrentTrack() {
        if (!isRecording() || isPaused()) {
            Log.d(TAG, "Ignore pauseCurrentTrack. Not recording or paused.");
            return;
        }

        // Update shared preferences
        recordingTrackPaused = true;
        PreferencesUtils.setBoolean(this, R.string.recording_track_paused_key,
                true);

        // Update database
        Track track = myTracksProviderUtils.getTrack(recordingTrackId);
        if (track != null) {
            insertLocation(track, lastLocation,
                    getLastValidTrackPointInCurrentSegment(track.id));

            Location pause = new Location(LocationManager.GPS_PROVIDER);
            pause.setLongitude(0);
            pause.setLatitude(PAUSE_LATITUDE);
            pause.setTime(System.currentTimeMillis());
            insertLocation(track, pause, null);
        }

        endRecording(false, recordingTrackId, "");
    }

    private void pauseCurrentTrack(String appId) {
        if (!isRecording(appId)) {
            Log.d(TAG, "Ignore pauseCurrentTrack. Not recording or paused.");
            return;
        }

        // Update database
        Track track = myTracksProviderUtils.getTrack(trackIds.get(appId));
        if (track != null) {
            insertLocation(track, lastLocation,
                    getLastValidTrackPointInCurrentSegment(track.id));

            Location pause = new Location(LocationManager.GPS_PROVIDER);
            pause.setLongitude(0);
            pause.setLatitude(PAUSE_LATITUDE);
            pause.setTime(System.currentTimeMillis());
            insertLocation(track, pause, null);
        }

        endRecording(false, trackIds.get(appId), appId);

        trackPauseds.put(appId, true); // 暂停状态更新为true
    }

    /**
     * Common code for ending a track or pausing a track.
     *
     * @param trackStopped true if track is stopped, false if track is paused
     * @param trackId      the track id
     */
    private void endRecording(boolean trackStopped, long trackId, String appId) {

        Timber.e("endRecording() params{trackStopped = %b, trackId = %d, appId = %s}",
                trackStopped, trackId, appId);

        if (trackStopped && !TextUtils.isEmpty(appId)) { // 结束轨迹
            appIds.remove(appId);
            trackIds.remove(appId);
            trackPauseds.remove(appId);
        }

        Timber.e("endRecording() appIds.size() = %d", appIds.size());

        if (appIds.size() <= 0) {
            // Shutdown periodic tasks
            voiceExecutor.shutdown();
            splitExecutor.shutdown();

            // Update instance variables
            if (sensorManager != null) {
                SensorManagerFactory.releaseSystemSensorManager();
                sensorManager = null;
            }
            lastLocation = null;


            // Unregister notifications
            unregisterLocationListener();

            // Send notifications
            showNotification(appId);
            sendTrackBroadcast(
                    trackStopped ? R.string.track_stopped_broadcast_action
                            : R.string.track_paused_broadcast_action, trackId);

            releaseWakeLock();
            stopHeartbeatTask(true);
        }
    }

    /**
     * Updates the recording states.
     *
     * @param trackId the recording track id
     * @param paused  true if the recording is paused
     */
    private void updateRecordingState(long trackId, boolean paused) {
        recordingTrackId = trackId;
        PreferencesUtils.setLong(this, common_id, trackId);
        recordingTrackPaused = paused;
        PreferencesUtils.setBoolean(this, R.string.recording_track_paused_key,
                recordingTrackPaused);
    }

    /**
     * Updates the recording states.
     *
     * @param trackId the recording track id
     * @param paused  true if the recording is paused
     */
    private void updateRecordingState(String appId, long trackId, boolean paused) {
        PreferencesUtils.setArrayString(this, R.string.recording_track_appid_key, appIds);
        PreferencesUtils.setLong(this, appId + common_id, trackId);
        PreferencesUtils.setBoolean(this, appId + common_paused, paused);
    }

    /**
     * Called when location changed.
     *
     * @param location the location
     */
    private void onLocationChangedAsync(Location location) {
        try {
            if (!isRecording() || isPaused()) {
                Log.w(TAG,
                        "Ignore onLocationChangedAsync. Not recording or paused.");
                return;
            }

            Track track = myTracksProviderUtils.getTrack(recordingTrackId);
            if (track == null) {
                Log.w(TAG, "Ignore onLocationChangedAsync. No track.");
                return;
            }

            if (!LocationUtils.isValidLocation(location)) {
                Log.w(TAG,
                        "Ignore onLocationChangedAsync. location is invalid.");
                return;
            }

            if (location.getAccuracy() > minRequiredAccuracy) {
                Log.d(TAG, "Ignore onLocationChangedAsync. Poor accuracy.");
                return;
            }

            // Fix for phones that do not set the time field
            if (location.getTime() == 0L) {
                location.setTime(System.currentTimeMillis());
            }

            Location lastValidTrackPoint = getLastValidTrackPointInCurrentSegment(track
                    .id);
            long idleTime = 0L;
            if (lastValidTrackPoint != null
                    && location.getTime() > lastValidTrackPoint.getTime()) {
                idleTime = location.getTime() - lastValidTrackPoint.getTime();
            }
            locationListenerPolicy.updateIdleTime(idleTime);
            if (currentRecordingInterval != locationListenerPolicy
                    .getDesiredPollingInterval()) {
                registerLocationListener();
            }

            SensorDataSet sensorDataSet = getSensorDataSet();
            if (sensorDataSet != null) {
                location = new MyTracksLocation(location, sensorDataSet);
            }

            // Always insert the first segment location //第一次插入一个点时会调用。
            if (!currentSegmentHasLocation) {
                insertLocation(track, location, null);
                currentSegmentHasLocation = true;
                lastLocation = location;
                return;
            }

            if (!LocationUtils.isValidLocation(lastValidTrackPoint)) {
                /*
                 * Should not happen. The current segment should have a
                 * location. Just insert the current location.
                 */
                insertLocation(track, location, null);
                lastLocation = location;
                return;
            }

            Log.i("track_point", "lat = " + location.getLatitude() + " lon = "
                    + location.getLongitude());
            double distanceToLastTrackLocation = location
                    .distanceTo(lastValidTrackPoint);
            Log.i("track_point", "distanceToLastTrackLocation = "
                    + distanceToLastTrackLocation);
            if (distanceToLastTrackLocation < minRecordingDistance
                    && sensorDataSet == null) {
                Log.d(TAG,
                        "Not recording location due to min recording distance.");
            } else if (distanceToLastTrackLocation > maxRecordingDistance) {
                Log.i("track_point", "has go to insert = " + true);
                insertLocation(track, lastLocation, lastValidTrackPoint);
                Location pause = new Location(LocationManager.GPS_PROVIDER);
                pause.setLongitude(0);
                pause.setLatitude(PAUSE_LATITUDE);
                pause.setTime(lastLocation.getTime());
                insertLocation(track, pause, null);

                insertLocation(track, location, null);
            } else {
                /*
                 * (distanceToLastTrackLocation >= minRecordingDistance ||
                 * hasSensorData) && distanceToLastTrackLocation <=
                 * maxRecordingDistance
                 */
                insertLocation(track, lastLocation, lastValidTrackPoint);
                insertLocation(track, location, null);
            }
            lastLocation = location;
        } catch (Error e) {
            Log.e(TAG, "Error in onLocationChangedAsync", e);
            throw e;
        } catch (RuntimeException e) {
            Log.e(TAG, "RuntimeException in onLocationChangedAsync", e);
            throw e;
        }
    }

    private void onLocationChangedAllApp(Location location) {
        try {
            for (String appId : appIds) {
                // 如果处于暂停或者就没有开始记录，继续下一个
                if (!isRecording(appId)) {
                    Timber.e("%s 未开始或暂停轨迹记录，不保存定位点", appId);
                    continue;
                }

                // 如果没有轨迹对象，继续下一个
                Track track = myTracksProviderUtils.getTrack(trackIds.get(appId));
                if (track == null) {
                    Timber.e("%s 数据库未找到轨迹对象，不保存定位点", appId);
                    continue;
                }

                // 如果定位位置无效，继续下一个
                if (!LocationUtils.isValidLocation(location)) {
                    Timber.e("%s 定位点无效，不保存定位点", appId);
                    continue;
                }

                // 如果定位精度不符合条件，继续下一个
                if (location.getAccuracy() > minRequiredAccuracy) {
                    Timber.e("%s 定位精度 %f > 最小精度 %d，不保存定位点", appId,
                            location.getAccuracy(), minRequiredAccuracy);
                    return;
                }

                // Fix for phones that do not set the time field
                if (location.getTime() == 0L) {
                    location.setTime(System.currentTimeMillis());
                }

                Location lastValidTrackPoint = getLastValidTrackPointInCurrentSegment(track.id);
                long idleTime = 0L;
                if (lastValidTrackPoint != null
                        && location.getTime() > lastValidTrackPoint.getTime()) {
                    idleTime = location.getTime() - lastValidTrackPoint.getTime();
                    Timber.e("轨迹空闲间隔时间 = %d, 最后有效轨迹点(%f, %f)",
                            idleTime, lastValidTrackPoint.getLatitude(), lastValidTrackPoint.getLongitude());
                }
                locationListenerPolicy.updateIdleTime(idleTime);
                if (currentRecordingInterval != locationListenerPolicy
                        .getDesiredPollingInterval()) {
                    Timber.e("重新注册定位 onLocationChangedAllApp currentRecordingInterval不等于一个值");
                    registerLocationListener();
                }

                SensorDataSet sensorDataSet = getSensorDataSet();
                if (sensorDataSet != null) {
                    location = new MyTracksLocation(location, sensorDataSet);
                }

                // Always insert the first segment location //第一次插入一个点时会调用。
                Timber.e("%s 是否有当前片段，currentSegmentHasLocation = %b",
                        appId, currentSegmentHasLocation);
                if (!currentSegmentHasLocation) {
                    insertLocation(track, location, null);
                    currentSegmentHasLocation = true;
                    lastLocation = location;
                    return;
                }

                if (!LocationUtils.isValidLocation(lastValidTrackPoint)) {
                    /*
                    1
                     * Should not happen. The current segment should have a
                     * location. Just insert the current location.
                     */
                    insertLocation(track, location, null);
                    lastLocation = location;
                    Timber.e("lastValidTrackPoint无效，已经将当前点放入轨迹");
                    return;
                }

                double distanceToLastTrackLocation;
                if (location instanceof AMapLocation) {
                    String provider = ((AMapLocation) location).getProvider();
                    double lat = ((AMapLocation) location).getLatitude();
                    double lng = ((AMapLocation) location).getLongitude();
                    float bearing = ((AMapLocation) location).getBearing();

                    // 错误时其实无法回调到这里，这里打印其实没啥作用
                    int errorCode = ((AMapLocation) location).getErrorCode();
                    if (errorCode != 0) {
                        String error = ((AMapLocation) location).getLocationDetail();
                        Timber.e("高德定位错误: %s", error);
                    }

                    Location androidLocation = new Location(provider);
                    androidLocation.setLatitude(lat);
                    androidLocation.setLongitude(lng);
                    androidLocation.setBearing(bearing);
                    distanceToLastTrackLocation = androidLocation.distanceTo(lastValidTrackPoint);
                } else {
                    distanceToLastTrackLocation = location.distanceTo(lastValidTrackPoint);
                }
                Timber.i("%s 定位点信息，provider = %s, lat = %f， lon = %f, 距上次定位点距离 = %f",
                        appId, location.getProvider(), location.getLatitude(),
                        location.getLongitude(), distanceToLastTrackLocation);
                if (distanceToLastTrackLocation < minRecordingDistance
                        && sensorDataSet == null) {
                    Timber.e("%s 距离小于 %d，不保存定位点", appId, minRecordingDistance);
                } else if (distanceToLastTrackLocation > maxRecordingDistance) {
                    Timber.e("%s 距离大于 %d，保存定位点", appId, maxRecordingDistance);
                    insertLocation(track, lastLocation, lastValidTrackPoint);
                    Location pause = new Location(LocationManager.GPS_PROVIDER);
                    pause.setLongitude(0);
                    pause.setLatitude(PAUSE_LATITUDE);
                    pause.setTime(lastLocation.getTime());
                    insertLocation(track, pause, null);

                    insertLocation(track, location, null);
                } else {
                    /*
                     * (distanceToLastTrackLocation >= minRecordingDistance ||
                     * hasSensorData) && distanceToLastTrackLocation <=
                     * maxRecordingDistance
                     */
                    insertLocation(track, lastLocation, lastValidTrackPoint);
                    insertLocation(track, location, null);
                }
                lastLocation = location;
            }
        } catch (Error e) {
            Log.e(TAG, "Error in onLocationChangedAsync", e);
            throw e;
        } catch (RuntimeException e) {
            Log.e(TAG, "RuntimeException in onLocationChangedAsync", e);
            throw e;
        }
    }

    /**
     * Inserts a location.
     *
     * @param track               the track
     * @param location            the location
     * @param lastValidTrackPoint the last valid track point, can be null
     */
    private void insertLocation(Track track, Location location,
                                Location lastValidTrackPoint) {
        if (location == null) {
            Log.w(TAG, "Ignore insertLocation. loation is null.");
            return;
        }
        // Do not insert if inserted already
        if (lastValidTrackPoint != null
                && lastValidTrackPoint.getTime() == location.getTime()) {
            Log.w(TAG,
                    "Ignore insertLocation. location time same as last valid track point time.");
            return;
        }

        try {
            Uri uri = myTracksProviderUtils.insertTrackPoint(location,
                    track.id);
            long trackPointId = Long.parseLong(uri.getLastPathSegment());
            trackTripStatisticsUpdater.addLocation(location,
                    minRecordingDistance);
            markerTripStatisticsUpdater.addLocation(location,
                    minRecordingDistance);
            updateRecordingTrack(track, trackPointId,
                    LocationUtils.isValidLocation(location));
        } catch (SQLiteException e) {
            /*
             * Insert failed, most likely because of SqlLite error code 5
             * (SQLite_BUSY). This is expected to happen extremely rarely (if
             * our listener gets invoked twice at about the same time).
             */
            Log.w(TAG, "SQLiteException", e);
        }
        voiceExecutor.update();
        splitExecutor.update();
        sendTrackBroadcast(R.string.track_update_broadcast_action,
                track.id);
    }

    private void updateRecordingTrack(Track track, long trackPointId,
                                      boolean isTrackPointNewAndValid) {
        if (trackPointId >= 0) {
            if (track.startId < 0) {
                track.startId = (trackPointId);
            }
            track.stopId = (trackPointId);
        }
        if (isTrackPointNewAndValid) {
            track.numberOfPoints = (track.numberOfPoints + 1);
        }

        trackTripStatisticsUpdater.updateTime(System.currentTimeMillis());
        track.setTripStatistics(trackTripStatisticsUpdater.getTripStatistics());
        myTracksProviderUtils.updateTrack(track);
    }

    private SensorDataSet getSensorDataSet() {
        if (sensorManager == null || !sensorManager.isEnabled()
                || !sensorManager.isSensorDataSetValid()) {
            return null;
        }
        return sensorManager.getSensorDataSet();
    }

    /**
     * Registers the location listener.
     */
    private void registerLocationListener() {
        /*
         * Use the handler so the requestLocationUpdaets locationListener will
         * be invoked on the handler thread.
         */
        handler.post(new Runnable() {
            public void run() {
                unregisterLocationListener();

                if (myTracksLocationManager == null) {
                    Timber.e("注册定位时 myTracksLocationManager is null");
                    return;
                }
                try {
                    long interval = locationListenerPolicy
                            .getDesiredPollingInterval();
//                    myTracksLocationManager.requestLocationUpdates(
//                            LocationManager.GPS_PROVIDER, interval,
//                            locationListenerPolicy.getMinDistance(),
//                            locationListener, context.getMainLooper());

                    SignalManager1.getInstance().registerLocationChangedObserver(locationObeserver);

                    currentRecordingInterval = interval;

                    Timber.e("注册定位信息 provider: %s, interval: %d, minDistance: %d",
                            LocationManager.GPS_PROVIDER, interval,
                            locationListenerPolicy.getMinDistance());
                } catch (RuntimeException e) {
                    Timber.e("注册定位失败");
                }
            }
        });
    }

    /**
     * Unregisters the location manager.
     */
    private void unregisterLocationListener() {
        Timber.e("取消注册定位监听");
        if (myTracksLocationManager == null) {
            Timber.e("取消注册定位时 myTracksLocationManager is null");
            return;
        }
//        myTracksLocationManager.removeUpdates(locationListener);
        SignalManager1.getInstance().removeLocationChangedObserver(locationObeserver);
    }

    /**
     * Acquires the wake lock.
     */
    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) {
                Log.e(TAG, "powerManager is null.");
                return;
            }
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, TAG);
                if (wakeLock == null) {
                    Log.e(TAG, "wakeLock is null.");
                    return;
                }
            }
            if (!wakeLock.isHeld()) {
                wakeLock.acquire();
                if (!wakeLock.isHeld()) {
                    Log.e(TAG, "Unable to hold wakeLock.");
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Caught unexpected exception", e);
        }
    }

    /**
     * Releases the wake lock.
     */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    /**
     * Shows the notification.
     */
    private void showNotification() {
        try {
            Intent intent = new Intent("全球一张图入口");/*IntentUtils.newIntent(this,
					MainUpperLayerActivity.class).putExtra("EXTRA_TRACK_ID",
					recordingTrackId);*/

//	        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(this);
            taskStackBuilder.addNextIntent(intent);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    this);
            builder.setContentIntent(taskStackBuilder.getPendingIntent(0,
                    PendingIntent.FLAG_UPDATE_CURRENT));
//			builder.setContentIntent(contentIntent);
            builder.setContentText(getString(R.string.track_record_notification));
            builder.setContentTitle(getString(R.string.track_service_name));
            builder.setOngoing(true);
            builder.setSmallIcon(R.drawable.my_tracks_notification_icon);
            builder.setWhen(System.currentTimeMillis());


            // 【适配Android8.0】给NotificationManager对象设置NotificationChannel
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String channelId = "track_service_id";
                NotificationChannel channel = new NotificationChannel(channelId,
                        "track_service_name", NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
                builder.setChannelId(channelId);
            }

            if (isRecording() && !isPaused()) {
                startForegroundService(builder.build());
            } else if (isRecording() && isPaused()) {
                builder.setContentText(getString(R.string.track_record_notification_pause));
                startForegroundService(builder.build());
            } else {
                stopForegroundService();
            }
        } catch (NoSuchMethodError e) {
            e.printStackTrace();
        }

    }

    /**
     * Shows the notification with appid
     */
    private void showNotification(String appId) {
        try {
            Intent intent = new Intent("全球一张图入口");/*IntentUtils.newIntent(this,
					MainUpperLayerActivity.class).putExtra("EXTRA_TRACK_ID",
					recordingTrackId);*/

//	        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(this);
            taskStackBuilder.addNextIntent(intent);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    this);
            builder.setContentIntent(taskStackBuilder.getPendingIntent(0,
                    PendingIntent.FLAG_UPDATE_CURRENT));
//			builder.setContentIntent(contentIntent);
            builder.setContentText(getString(R.string.track_record_notification));
            builder.setContentTitle(getString(R.string.track_service_name)).setOngoing(
                    true);
            builder.setSmallIcon(R.drawable.my_tracks_notification_icon)
                    .setWhen(System.currentTimeMillis());


            // 【适配Android8.0】给NotificationManager对象设置NotificationChannel
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String channelId = "track_service_id";
                NotificationChannel channel = new NotificationChannel(channelId,
                        "track_service_name", NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
                builder.setChannelId(channelId);
            }

            if (isRecording(appId) && !isPaused(appId)) {
                startForegroundService(builder.build());
            } else if (isRecording(appId) && isPaused(appId)) {
                builder.setContentText(getString(R.string.track_record_notification_pause));
                startForegroundService(builder.build());
            } else {
                stopForegroundService();
            }
        } catch (NoSuchMethodError e) {
            e.printStackTrace();
        }

    }

    /**
     * Sends track broadcast.
     *
     * @param actionId the intent action id
     * @param trackId  the track id
     */
    private void sendTrackBroadcast(int actionId, long trackId) {
        Intent intent = new Intent().setAction(getString(actionId)).putExtra(
                getString(R.string.track_id_broadcast_extra), trackId);
        sendBroadcast(intent, getString(R.string.permission_notification_value));
        if (PreferencesUtils.getBoolean(this, R.string.allow_access_key,
                PreferencesUtils.ALLOW_ACCESS_DEFAULT)) {
            sendBroadcast(intent,
                    getString(R.string.broadcast_notifications_permission));
        }
    }

    /**
     * TODO: There is a bug in Android that leaks Binder instances. This bug is
     * especially visible if we have a non-static class, as there is no way to
     * nullify reference to the outer class (the service). A workaround is to
     * use a static class and explicitly clear service and detach it from the
     * underlying Binder. With this approach, we minimize the leak to 24 bytes
     * per each service instance. For more details, see the following bug:
     * http://code.google.com/p/android/issues/detail?id=6426.
     */
    private static class ServiceBinder extends ITrackRecordingService.Stub {
        private TrackRecordingService trackRecordingService;
        private DeathRecipient deathRecipient;

        public ServiceBinder(TrackRecordingService trackRecordingService) {
            this.trackRecordingService = trackRecordingService;
        }

        @Override
        public boolean isBinderAlive() {
            return trackRecordingService != null;
        }

        @Override
        public boolean pingBinder() {
            return isBinderAlive();
        }

        @Override
        public void linkToDeath(DeathRecipient recipient, int flags) {
            deathRecipient = recipient;
        }

        @Override
        public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
            if (!isBinderAlive()) {
                return false;
            }
            deathRecipient = null;
            return true;
        }

        @Override
        public long startNewTrackWithAppInfo(String appId, String appName) {
            if (!canAccess()) {
                return -1L;
            }
            Timber.e("桥梁方法 startNewTrackWithAppInfo (app: %s, appName: %s", appId, appName);
            return trackRecordingService.startNewTrackWithAppInfo(appId, appName);
        }

        @Override
        public long startNewTrack() {
            if (!canAccess()) {
                return -1L;
            }
            return trackRecordingService.startNewTrack();
        }

        @Override
        public void pauseCurrentTrack() {
            if (!canAccess()) {
                return;
            }
            trackRecordingService.pauseCurrentTrack();
        }

        @Override
        public void pauseCurrentTrackWithAppId(String appId) {
            if (!canAccess()) {
                return;
            }
            trackRecordingService.pauseCurrentTrack(appId);
        }

        @Override
        public void resumeCurrentTrack() {
            if (!canAccess()) {
                return;
            }
            trackRecordingService.resumeCurrentTrack();
        }

        @Override
        public void resumeCurrentTrackWithAppId(String appId) {
            if (!canAccess()) {
                return;
            }
            trackRecordingService.resumeCurrentTrack(appId);
        }

        @Override
        public void endCurrentTrack() {
            if (!canAccess()) {
                return;
            }
            trackRecordingService.endCurrentTrack();
        }

        @Override
        public void endCurrentTrackWithAppId(String appId) {
            if (!canAccess()) {
                return;
            }
            trackRecordingService.endCurrentTrack(appId);
        }

        @Override
        public boolean isRecording() {
            if (!canAccess()) {
                return false;
            }

            Log.e(TAG, "isRecording app: " + trackRecordingService.getPackageName()
                    + ", isRecording: " + trackRecordingService.isRecording()
                    + ", isRecording: " + !PreferencesUtils.getBoolean(trackRecordingService, R.string.recording_track_paused_key, false));
            return trackRecordingService.isRecording();
        }

        @Override
        public boolean isRecordingWithAppId(String appId) {
            if (!canAccess()) {
                return false;
            }

            Log.e(TAG, "isRecordingWithAppId app: " + appId
                    + ", isRecording: " + trackRecordingService.isRecording(appId));
            return trackRecordingService.isRecording(appId);
        }

        @Override
        public boolean isPaused() {
            if (!canAccess()) {
                return false;
            }

            Log.e(TAG, "isPaused app: " + trackRecordingService.getPackageName()
                    + ", isPause: " + trackRecordingService.isPaused()
                    + ", isPause: " + PreferencesUtils.getBoolean(trackRecordingService, R.string.recording_track_paused_key, false));
            return trackRecordingService.isPaused();
        }

        @Override
        public boolean isPausedWithAppId(String appId) {
            if (!canAccess()) {
                return false;
            }

            Log.e(TAG, "isPausedWithAppId app: " + appId
                    + ", isPaused: " + trackRecordingService.isPaused(appId));
            return trackRecordingService.isPaused(appId);
        }

        @Override
        public long getRecordingTrackId() {
            if (!canAccess()) {
                return -1L;
            }

            Log.e(TAG, "getRecordingTrackId app: " + trackRecordingService.getPackageName()
                    + ", recordingTrackId: " + trackRecordingService.recordingTrackId
                    + ", recordingTrackId: " + PreferencesUtils.getLong(trackRecordingService, R.string.recording_track_id_key));
            return trackRecordingService.recordingTrackId;
        }

        @Override
        public long getRecordingTrackIdWithAppId(String appId) {
            if (!canAccess()) {
                return -1L;
            }
            long trackId = -1L;
            if (trackRecordingService.trackIds.get(appId) != null) {
                trackId = trackRecordingService.trackIds.get(appId);
            }
            Timber.e("桥梁方法 getRecordingTrackIdWithAppId (app: %s, recordingTrackId: %d", appId, trackId);
            return trackId;
        }

        @Override
        public long getTotalTime() {
            if (!canAccess()) {
                return 0;
            }
            TripStatisticsUpdater updater = trackRecordingService.trackTripStatisticsUpdater;
            if (updater == null) {
                return 0;
            }
            if (!trackRecordingService.isPaused()) {
                updater.updateTime(System.currentTimeMillis());
            }
            return updater.getTripStatistics().getTotalTime();
        }

        @Override
        public void insertTrackPoint(Location location) {
            if (!canAccess()) {
                return;
            }
            trackRecordingService.locationListener.onLocationChanged(location);
        }

        @Override
        public long insertWaypoint(
                WaypointCreationRequest waypointCreationRequest) {
            if (!canAccess()) {
                return -1L;
            }
            return trackRecordingService
                    .insertWaypoint(waypointCreationRequest);
        }

        @Override
        public byte[] getSensorData() {
            if (!canAccess()) {
                return null;
            }
            if (trackRecordingService.sensorManager == null) {
                Log.d(TAG, "sensorManager is null.");
                return null;
            }
            if (trackRecordingService.sensorManager.getSensorDataSet() == null) {
                Log.d(TAG, "Sensor data set is null.");
                return null;
            }
            return trackRecordingService.sensorManager.getSensorDataSet()
                    .toByteArray();
        }

        @Override
        public int getSensorState() {
            if (!canAccess()) {
                return Sensor.SensorState.NONE.getNumber();
            }
            if (trackRecordingService.sensorManager == null) {
                Log.d(TAG, "sensorManager is null.");
                return Sensor.SensorState.NONE.getNumber();
            }
            return trackRecordingService.sensorManager.getSensorState()
                    .getNumber();
        }

        /**
         * Returns true if the RPC caller is from the same application or if the
         * "Allow access" setting indicates that another app can invoke this
         * service's RPCs.
         */
        private boolean canAccess() {
            // As a precondition for access, must check if the service is
            // available.
//			if (trackRecordingService == null) {
//				throw new IllegalStateException(
//						"The track recording service has been detached!");
//			}
//			if (Process.myPid() == Binder.getCallingPid()) {
//				return true;
//			} else {
//				return PreferencesUtils.getBoolean(trackRecordingService,
//						R.string.allow_access_key,
//						PreferencesUtils.ALLOW_ACCESS_DEFAULT);
//			}
            return true;
        }

        /**
         * Detaches from the track recording service. Clears the reference to
         * the outer class to minimize the leak.
         */
        private void detachFromService() {
            trackRecordingService = null;
            attachInterface(null, null);

            if (deathRecipient != null) {
                deathRecipient.binderDied();
            }
        }
    }


    /**
     * Toggles the TrackRecordingService component
     *
     * @param context
     * @param enable
     */
    public static void enableTrackRecordingService(Context context, boolean enable) {
        final ComponentName serviceComp = new ComponentName(context, TrackRecordingService.class);
        final int newState = enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        context.getPackageManager().setComponentEnabledSetting(serviceComp, newState, PackageManager.DONT_KILL_APP);
    }


    /*********** 心跳长链接 START ***********/
    private WebSocket webSocket;
    private static final int CONNECTION_TIMEOUT = 20 * 1000; // 20 secs in ms
    private boolean isManualCloseWebSocket = false;
    private Runnable heartbeatTask = new Runnable() {
        @Override
        public void run() {
            //新建client
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                    .build();
            //构造request对象
            Request request = new Request.Builder()
                    .url("ws://114.116.142.50:6010")
//                    .url("ws://echo.websocket.org")
                    .build();
            //建立连接
            client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    super.onOpen(webSocket, response);
                    Timber.d("WebSocket 打开成功：%s", response.toString());
                    TrackRecordingService.this.webSocket = webSocket;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!isManualCloseWebSocket) {
                                startHeartbeatTask(); // 长链接销毁30秒后重新创建
                            }
                        }
                    }, 5 * 60 * 1000);// 长链接建立5分钟后销毁
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    super.onFailure(webSocket, t, response);
                    Timber.d("WebSocket 打开失败：%s", t.getMessage());
                    if (!isManualCloseWebSocket) {
                        startHeartbeatTask(); // 如果打开WebSocket失败，30秒后重连
                    }
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    super.onMessage(webSocket, text);
                    Timber.d("WebSocket 接收消息：%s", text);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    super.onClosed(webSocket, code, reason);
                    Timber.d("WebSocket 关闭成功，code = %d，reason = %s", code, reason);
                    if (!isManualCloseWebSocket) {
                        startHeartbeatTask(); // 如果打开WebSocket失败，30秒后重连
                    }
                }
            });
        }
    };

    private void startHeartbeatTask() {
        stopHeartbeatTask(false);
        handler.postDelayed(heartbeatTask, 30 * 1000);
    }

    private void stopHeartbeatTask(boolean isManualCloseWebSocket) {
        TrackRecordingService.this.isManualCloseWebSocket = isManualCloseWebSocket;
        handler.removeCallbacks(heartbeatTask);
        if (webSocket != null) {
            webSocket.close(3954, "长链接5分钟一次的断开，30秒后再开启");
            webSocket.cancel();
        }
    }
    /*********** 心跳长链接 END ***********/

}
