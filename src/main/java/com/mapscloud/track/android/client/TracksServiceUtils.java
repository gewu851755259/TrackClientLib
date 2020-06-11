package com.mapscloud.track.android.client;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.location.Location;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.mapscloud.track.R;
import com.mapscloud.track.android.interfaces.TowerListener;
import com.mapscloud.track.services.basic.BasicRecordBean;
import com.mapscloud.track.services.content.Track;
import com.mapscloud.track.services.content.TrackPointsColumns;
import com.mapscloud.track.services.content.TripStatistics;
import com.mapscloud.track.services.model.ITrackRecordingService;
import com.mapscloud.track.services.model.TrackRecordingServiceConnectionUtils;
import com.mapscloud.track.services.model.Waypoint;
import com.mapscloud.track.services.model.WaypointCreationRequest;
import com.mapscloud.track.services.provider.MyTracksProviderUtils;
import com.mapscloud.track.services.utils.Constant;
import com.mapscloud.track.services.utils.LocalPropertiesUtils;
import com.mapscloud.track.services.utils.PreferencesUtils;

import java.util.ArrayList;
import java.util.List;

public class TracksServiceUtils {

    public static int NOTCURRENTAPPSTART = 0;  // 当前应用未记录轨迹
    public static int CURRENTAPPSTART = 1;  // 当前应用正在记录轨迹
    public static int NOAPPHASSTARTTRACK = 2;  // 没有任何应用记录轨迹

    private final Context context;

    private boolean startNewRecording = false; // 记录是否开始新轨迹的记录
    private boolean isRecording = false; // 是否正在记录
    private boolean isPause = false; // 是否暂停，使用和isRecording状态相反
    private long mTrackId = -1L;   // 当前记录轨迹的ID，因为一个应用能且仅能有一个轨迹正在记录，所以tid只为一个long值
    public static boolean mShowOverView = false; // 无用变量

    /**
     * 进程服务{@link com.mapscloud.track.services.model.TrackRecordingService}的经理
     */
    private ControlTower serviceMgr;
    private ITrackRecordingService mITrackRecordingService;
    private TowerListener towerListener = new TowerListener() {
        @Override
        public void onTrackConnected() {
            // 通过Runnable的run方法正式开始记录轨迹
            // 直接调用run方法还是同步执行，没有创建新线程，不知道实例化Runnable的具体意义在哪
            if (bindChangedCallback != null)
                bindChangedCallback.run();
            Log.e(Constant.TAG, "服务控制塔: 轨迹服务连接");
        }

        @Override
        public void onTrackDisconnected() {
            Log.e(Constant.TAG, "服务控制塔: 轨迹服务中断");

//            stopTracking();
        }
    };

    private static TracksServiceUtils mTrackServiceUtils;
    private MyTracksProviderUtils myTracksProviderUtils;
    private String appId = "";
    private String appName = "";

    private TracksServiceUtils(Context context) {
        this.context = context;

//        LocalPropertiesUtils.loadTrackPropertiesFile();

        serviceMgr = new ControlTower(context);

        myTracksProviderUtils = MyTracksProviderUtils.Factory.get(context);

        mITrackRecordingService = serviceMgr.getTrackServices(); // 首次创建该Util得到的ITrackRecordingService必然为空
        if (null != mITrackRecordingService) {
            try {
                mTrackId = mITrackRecordingService.getRecordingTrackId();
                Log.e(Constant.TAG, "TracksServiceUtils单例对象被创建，之前存在轨迹记录，TrackId = " + mTrackId);
                isPause = mITrackRecordingService.isPaused();
                isRecording = !isPause;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Log.e(Constant.TAG, "TracksServiceUtils单例对象被创建，连接并启动轨迹服务，但未必开始记录");
            serviceMgr.connect(towerListener);
        }
    }

    public static TracksServiceUtils getTracksServiceUtilsInstance(Context context) {
        if (null != mTrackServiceUtils) {
            return mTrackServiceUtils;
        } else {
            mTrackServiceUtils = new TracksServiceUtils(context);
            return mTrackServiceUtils;
        }
    }

    /**
     * 在Application中初始化
     *
     * @param context 绑定服务的进程
     */
    public static void init(Context context) {
        getTracksServiceUtilsInstance(context);
    }


    /**
     * 开始轨迹记录，这部分的处理Drone其实是又创建了一个进程间通信IDroneApi来处理，
     * 于是可以监测到当前的飞机是否连接。
     * 同理这个Util在开始轨迹记录时应该也创建一个新的aidl，利用pingBinder()可以得到
     * 轨迹是否正在记录，并且意外断开时也可以用IBinder.DeathRecipient内的方法来处理。
     * <p>
     * 原来的
     */
    public synchronized void starNewTrack() {
        LocalPropertiesUtils.setPropertiesValue(context.getPackageName());
        startNewRecording = true;
        if (serviceMgr.isTowerConnected()) {
            bindChangedCallback.run();
        } else {
            serviceMgr.connect(towerListener);
        }
    }

    /**
     * 与{@link #starNewTrack()}相对应的暂停/继续轨迹记录方法
     */
    public void pauseOrResumeTracking() {
        try {
            if (isRecording) {
//                mITrackRecordingService.pauseCurrentTrack();
                mITrackRecordingService.pauseCurrentTrackWithAppId(appId);
                updateRecordingState(mTrackId, true);
                isRecording = false;
                isPause = true;
            } else if (isPause) {
//                mITrackRecordingService.resumeCurrentTrack();
                mITrackRecordingService.resumeCurrentTrackWithAppId(appId);
                updateRecordingState(mTrackId, false);
                isRecording = true;
                isPause = false;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 与{@link #starNewTrack()}相对应的结束轨迹方法
     */
    public void stopTracking() {

        try {
            if (mITrackRecordingService != null)
                mITrackRecordingService.endCurrentTrackWithAppId(appId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        serviceMgr.disconnect();

        LocalPropertiesUtils.setPropertiesValue("");
        isRecording = false;
        isPause = false;
        mTrackId = -1L;

        mTrackServiceUtils = null;
    }

    /**
     * 插入采集点
     *
     * @param recordMark 标签、图片、音频、视频的详细信息
     * @param markType   1：Note；2：Image；3：Sound；4：Video
     * @return 返回插入结果，-1为失败
     */
    public long insertWayPoint(BasicRecordBean recordMark, int markType) {
        WaypointCreationRequest waypointCreationRequest = new WaypointCreationRequest(Waypoint.WaypointType.WAYPOINT, false,
                recordMark.title, String.valueOf(markType), recordMark.content, null);

        return TrackRecordingServiceConnectionUtils.addMarker(context, serviceMgr, waypointCreationRequest);
    }

    /**
     * 判断当前应用是否开始着轨迹记录
     * 判断方法欠妥，只能知道最后开始轨迹记录的app是哪个，可能有多个app都在记录
     *
     * @return 具体查看返回状态值的含义
     */
    public int isTrackStartInCurrentApp() {
        String appName = LocalPropertiesUtils.getPropertiesValue();
        if (TextUtils.isEmpty(appName)) {
            return NOAPPHASSTARTTRACK;
        } else if (appName.equals(context.getPackageName())) {
            return CURRENTAPPSTART;
        } else {
            return NOTCURRENTAPPSTART;
        }
    }

    // Callback when the trackRecordingServiceConnection binding changes.
    private final Runnable bindChangedCallback = new Runnable() {

        /**
         * 实际开始记录轨迹的方法
         */
        @Override
        public void run() {
            try {
                PackageManager packageManager = context.getPackageManager();
                PackageInfo packageInfo = packageManager.getPackageInfo(
                        context.getPackageName(), 0);
                appId = packageInfo.packageName;
                int labelRes = packageInfo.applicationInfo.labelRes;
                appName = context.getResources().getString(labelRes);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            } catch (Resources.NotFoundException e) {
                e.printStackTrace();
            }

            // reset the track service status, exit the app when tracking
            // the service status has lose, so should reset the status form
            // service
            try {
                mITrackRecordingService = serviceMgr.getTrackServices();
                // yml run方法在进程间通讯的服务绑定成功后调用，mITrackRecordingService不会为空
//                if (null != mITrackRecordingService
//                        && (Constant.trackid != mITrackRecordingService.getRecordingTrackId())) {
//                    mTrackId = mITrackRecordingService.getRecordingTrackId();
//                    isPause = mITrackRecordingService.isPaused();
//                    isRecording = !isPause;
//                    // update the track button status
//                    if (null != onServiceReConnected) {
//                        onServiceReConnected.onServiceRecConnected();
//                    }
//                }

                if (null != mITrackRecordingService
                        && (Constant.trackid != mITrackRecordingService.getRecordingTrackIdWithAppId(appId))) {
                    mTrackId = mITrackRecordingService.getRecordingTrackIdWithAppId(appId);
                    isPause = mITrackRecordingService.isPausedWithAppId(appId);
                    isRecording = !isPause;
                    // update the track button status
                    if (null != onServiceReConnected) {
                        onServiceReConnected.onServiceRecConnected();
                    }
                }

            } catch (RemoteException e1) {
                e1.printStackTrace();
            }

            if (!startNewRecording) {
                return;
            }

            // yml 不使用trackRecordingServiceConnection，采用新的controlTower
            try {
                if (mTrackId < 1) {
                    // yml 换新的开始轨迹记录方法，可传入appId和appName
//                    mTrackId = service.startNewTrack();
                    mTrackId = mITrackRecordingService.startNewTrackWithAppInfo(appId, appName);

                    if (mTrackId < 1) {
//                        Toast.makeText(context, R.string.track_record_false, Toast.LENGTH_SHORT).show();
                        Log.e(Constant.TAG, context.getResources().getString(R.string.track_record_false));
                        return;
                    }
//                    Toast.makeText(context, R.string.track_list_record_success, Toast.LENGTH_SHORT).show();
                    Log.e(Constant.TAG, context.getResources().getString(R.string.track_list_record_success));
                }
                updateRecordingState(mTrackId, false); // 利用SharedPreferences保存当前tid和是否暂停的状态
                Log.d(Constant.TAG, "tid = " + mITrackRecordingService.getRecordingTrackId());
                isRecording = true;
//                onTrackHasStart(); // 此方法想通知页面刷新，但实际也不需要
                startNewRecording = false;
            } catch (Exception e) {
                // record current exception log
//                Toast.makeText(context, R.string.track_list_record_error + " " + e.getMessage(), Toast.LENGTH_LONG)
//                        .show();
                Log.e(Constant.TAG, context.getResources().getString(R.string.track_list_record_error));
                Log.e(Constant.TAG, "Unable to start a new recording.", e);
            }

        }
    };

    // save current recording status, include track id and pause status.
    // the data used in TrackDataHub's isSelectedRecordRecording
    private void updateRecordingState(long trackId, boolean paused) {
        String trackIdKey = appId + context.getResources().getString(R.string.recording_track_id_key);
        String trackPausedKey = appId + context.getResources().getString(R.string.recording_track_paused_key);
        PreferencesUtils.setLong(context, trackIdKey, trackId);
        PreferencesUtils.setBoolean(context, trackPausedKey, paused);
    }

    public long getmTrackId() {
        return mTrackId;
    }

    public void setTrackId(long mTrackId) {
        this.mTrackId = mTrackId;
        PreferencesUtils
                .setLong(context, appId + R.string.recording_track_id_key, PreferencesUtils.RECORDING_TRACK_ID_DEFAULT);
    }

    public boolean isRecording() {
        return isRecording;
    }

    public boolean isPause() {
        return isPause;
    }

    // 无用方法
    public void setModelHasStart(boolean b) {
    }

    /**
     * 判断轨迹服务是否绑定着
     *
     * @return true:绑定；false：未绑定
     */
    public boolean isBindTrackService() {
        return serviceMgr.isTowerConnected();
    }

    /**
     * 退出应用，但不结束轨迹记录，当再次进入应用会回调此接口
     */
    public interface OnServiceReConnected {
        void onServiceRecConnected();
    }

    private OnServiceReConnected onServiceReConnected;

    public void setServiceReConnectedListener(OnServiceReConnected onServiceReConnected) {
        this.onServiceReConnected = onServiceReConnected;
    }


    /* ---- yml 新添加util提供方法，之前都是在UI中直接使用MyTracksProviderUtils，这里进行封装 ----*/

    /**
     * 根据轨迹id获取轨迹对象
     */
    public Track getTrackWithId(long trackId) {
        return myTracksProviderUtils.getTrack(trackId);
    }

    /**
     * 根据关键字查询轨迹结果
     */
    public void getTracksWithKey(String key, RecordQueryListener listener) {
        new RecordQueryAsyncTask(listener).execute(key);
    }

    /**
     * 获取全部轨迹列表
     *
     * @param listener 查询结果返回监听
     */
    public void getAllTracks(RecordQueryListener listener) {
        getAllTracks(listener, true);
    }

    /**
     * 获取全部轨迹列表
     *
     * @param isSelfApp 是否只查询本APP的轨迹记录
     * @param listener  查询结果返回监听
     */
    public void getAllTracks(RecordQueryListener listener, boolean isSelfApp) {
        new RecordQueryAsyncTask(listener, isSelfApp).execute();
    }

    public interface RecordQueryListener {
        void queryResult(List<BasicRecordBean> records);
    }

    class RecordQueryAsyncTask extends AsyncTask<String, Void, List<BasicRecordBean>> {

        private RecordQueryListener listener;
        private boolean isSelfApp = true;

        public RecordQueryAsyncTask(RecordQueryListener listener) {
            this.listener = listener;
        }

        public RecordQueryAsyncTask(RecordQueryListener listener, boolean isSelfApp) {
            this.listener = listener;
            this.isSelfApp = isSelfApp;
        }

        @Override
        protected List<BasicRecordBean> doInBackground(String... params) {
            try {
                List<Track> tracks;

                if (params.length > 0) {
                    tracks = myTracksProviderUtils.getAllTracksByKey(params[0]);
                } else {
                    tracks = myTracksProviderUtils.getAllTracks();
                }

                List<BasicRecordBean> basicRecordBean = new ArrayList<>();
                for (Track track : tracks) {
                    // current only get navigation track
                    // if (track.getTrackType() == 1) {
                    if (isSelfApp) {
                        if (appId.equals(track.appId)) {
                            basicRecordBean.add(track);
                        }
                    } else {
                        basicRecordBean.add(track);
                    }
                    // }
                }
                return basicRecordBean;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<BasicRecordBean> result) {
            if (listener != null) {
                listener.queryResult(result);
            }
        }
    }

    /**
     * 根据轨迹ID获取此条轨迹的边界经纬度值
     *
     * @param trackId 轨迹ID
     * @return 轨迹边界，顺序为左上右下，地理上为西北东南
     */
    public double[] getTrackEdge(long trackId) {
        double[] edge = new double[4];
        Track track = getTrackWithId(trackId);
        if (track != null){
            TripStatistics trip = track.getTripStatistics();
            edge[0] = trip.getLeftDegrees();
            edge[1] = trip.getTopDegrees();
            edge[2] = trip.getRightDegrees();
            edge[3] = trip.getBottomDegrees();
        }
        return edge;
    }

    /**
     * 根据轨迹ID获取此条轨迹的所有定位点
     *
     * @param trackId 轨迹ID
     */
    public ArrayList<Location> getTrackPoints(long trackId) {
        return getTrackPoints(trackId, -1);
    }

    /**
     * 根据轨迹ID获取此条轨迹的所有定位点
     *
     * @param trackId           轨迹ID
     * @param startTrackPointId 该轨迹起始定位点在轨迹点表中的ID，不想使用传-1
     * @return
     */
    public ArrayList<Location> getTrackPoints(long trackId, long startTrackPointId) {
        Cursor cursor = myTracksProviderUtils.getTrackPointCursor(trackId, -1, -1, false);
        ArrayList<Location> locations = new ArrayList<Location>();
        // ArrayList<Location> locations = mTrack.getLocations();
        while (cursor.moveToNext()) {
            // int trackId =
            // cursor.getInt(cursor.getColumnIndex(TrackPointsColumns.TRACKID));
            int lat = cursor.getInt(cursor.getColumnIndex(TrackPointsColumns.LATITUDE));
            int lon = cursor.getInt(cursor.getColumnIndex(TrackPointsColumns.LONGITUDE));
            int time = cursor.getInt(cursor.getColumnIndex(TrackPointsColumns.TIME));
            Location location = new Location("gps");
            location.setLatitude(lat / 1E6D);
            location.setLongitude(lon / 1E6D);
            location.setTime(time);
            locations.add(location);
        }
        return locations;
    }


    public void deleteAllTrack() {
        myTracksProviderUtils.deleteAllTracks();
    }

    public void deleteTrackWithID(long trackId) {
        myTracksProviderUtils.deleteTrack(trackId);
    }

}
