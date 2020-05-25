package com.mapscloud.track.services.utils;

import java.io.File;
import java.math.BigDecimal;

public class Constant {

    public static final String MODULE_TAG = "track_service";

    public static final String FIRST_TAG                         = "first";
    public static final String ACTION_INFORECORDER               = "android.intent.action.inforecorder";

    /******************************* 记录 *******************************/
    // 0未收藏，1已收藏
    public static final int RECORD_NOT_FAVORITE = 0;
    public static final int RECORD_FAVORITE = 1;
    public static final long trackid = -1;
    public static final long DEFAULT_SERVER_DB_ID = -1;
    public static final long waypointid = -1;
    public static final int DEFAULT_TEACK_POINT_STRAT_STOP_ID = -1;
    // 经纬度保留小数位数
    public static final int POINT_SCALE = 6;
    // 精度保留方式
    public static final int POINT_ROUNDINGMODE = BigDecimal.ROUND_HALF_UP;
    // 记录列表标题显示字数
    public static final int RECORD_LISTVIEW_TITLE_SIZE = 20;

    /******************************* 记录 *******************************/

    private String MAP_DIR = "/mnt/extSdCard";
    private String ROOTPATH = "/mnt/extSdCard/";
    private String WORLDMAP_PATH = getROOTPATH() + "mapdatabase/db/worldmap.db";
    private String MULTILEVELGRID_PATH = getROOTPATH() + "mapdatabase/db/myMultiLevelGrid.db";

    public Constant() {
        try {
            File beidou = new File("/sdcard/external_sdcard/");
            File huawei = new File("/mnt/sdcard2/");
            File lenovo = new File("/mnt/sdcard/");
            File sam = new File("/mnt/extSdCard/");
            File asus = new File("/Removable/MicroSD/");
            if (sam.exists()) {
                setMAP_DIR("/mnt/extSdCard");
                setROOTPATH("/mnt/extSdCard/");
                setWORLDMAP_PATH("/mnt/extSdCard/mapdatabase/db/worldmap.db");
                setMULTILEVELGRID_PATH("/mnt/extSdCard/mapdatabase/db/myMultiLevelGrid.db");
            } else if (huawei.exists()) {
                setMAP_DIR("/mnt/sdcard2");
                setROOTPATH("/mnt/sdcard2/");
                setWORLDMAP_PATH("/mnt/sdcard2/mapdatabase/db/worldmap.db");
                setMULTILEVELGRID_PATH("/mnt/sdcard2/mapdatabase/db/myMultiLevelGrid.db");
            } else if (beidou.exists()) {
                setMAP_DIR("/sdcard/external_sdcard");
                setROOTPATH("/sdcard/external_sdcard/");
                setWORLDMAP_PATH("/sdcard/external_sdcard/mapdatabase/db/worldmap.db");
                setMULTILEVELGRID_PATH("/sdcard/external_sdcard/mapdatabase/db/myMultiLevelGrid.db");
            } else if (asus.exists()) {
                setMAP_DIR("/Removable/MicroSD");
                setROOTPATH("/Removable/MicroSD/");
                setWORLDMAP_PATH("/Removable/MicroSD/mapdatabase/db/worldmap.db");
                setMULTILEVELGRID_PATH("/Removable/MicroSD/mapdatabase/db/myMultiLevelGrid.db");
            } else if (lenovo.exists()) {
                setMAP_DIR("/mnt/sdcard");
                setROOTPATH("/mnt/sdcard/");
                setWORLDMAP_PATH("/mnt/sdcard/mapdatabase/db/worldmap.db");
                setMULTILEVELGRID_PATH("/mnt/sdcard/mapdatabase/db/myMultiLevelGrid.db");
            }
        } catch (Exception e) {
            setMAP_DIR("/mnt/extSdCard");
            setROOTPATH("/mnt/extSdCard/");
            setWORLDMAP_PATH("/mnt/extSdCard/mapdatabase/db/worldmap.db");
            setMULTILEVELGRID_PATH("/mnt/extSdCard/mapdatabase/db/myMultiLevelGrid.db");

            e.printStackTrace();
        }
    }

    public String getMAP_DIR() {
        return MAP_DIR;
    }

    public void setMAP_DIR(String mAP_DIR) {
        MAP_DIR = mAP_DIR;
    }

    public String getROOTPATH() {
        return ROOTPATH;
    }

    public void setROOTPATH(String rOOTPATH) {
        ROOTPATH = rOOTPATH;
    }

    public String getWORLDMAP_PATH() {
        return WORLDMAP_PATH;
    }

    public void setWORLDMAP_PATH(String wORLDMAP_PATH) {
        WORLDMAP_PATH = wORLDMAP_PATH;
    }

    public String getMULTILEVELGRID_PATH() {
        return MULTILEVELGRID_PATH;
    }

    public void setMULTILEVELGRID_PATH(String mULTILEVELGRID_PATH) {
        MULTILEVELGRID_PATH = mULTILEVELGRID_PATH;
    }

    // track constant begin
    public static final double PAUSE_LATITUDE = 100.0;
    public static final double RESUME_LATITUDE = 200.0;
    public static final String TRACKS_SERVICE_ACTION = "com.startmap.app.model.track.service";
    public static final String TRACKS_SERVICE_PACKAGE_NAME = "com.starmap.app.tracksservice";
    public static final String TRACKS_SERVICE_CALSS_NAME = "com.starmap.app.model.tracks.service.TrackRecordingService";
    public static final String TRACKS_SERVICE_ACTIVITY_NAME = "com.starmap.app.model.tracks.MainActivity";
    public static final String TRACKPROVIDERNAME = "com.mapscloud.track.services.provider.MyTracksProvider";
    public static final String TRACKAUTHORITIES = "com.stmap.app.mytracks";
    public static final String STARMAPSERVICEPACKAGENAME = "com.starmap.app.remoteservice";

    /**
     * Should be used by all log statements
     */
    public static final String TAG = "TrackService";

    /**
     * Name of the top-level directory inside the SD card where our files will
     * be read from/written to.
     */
    public static final String SDCARD_TOP_DIR = "MyTracks";

    /**
     * The number of distance readings to smooth to get a stable signal.
     */
    public static final int DISTANCE_SMOOTHING_FACTOR = 25;

    /**
     * The number of elevation readings to smooth to get a somewhat accurate
     * signal.
     */
    public static final int ELEVATION_SMOOTHING_FACTOR = 25;

    /**
     * The number of grade readings to smooth to get a somewhat accurate signal.
     */
    public static final int GRADE_SMOOTHING_FACTOR = 5;

    /**
     * The number of speed reading to smooth to get a somewhat accurate signal.
     */
    public static final int SPEED_SMOOTHING_FACTOR = 25;

    /**
     * Maximum number of track points displayed by the map overlay.
     */
    public static final int MAX_DISPLAYED_TRACK_POINTS = 10000;

    /**
     * Target number of track points displayed by the map overlay. We may
     * display more than this number of points.
     */
    public static final int TARGET_DISPLAYED_TRACK_POINTS = 5000;

    /**
     * Maximum number of track points ever loaded at once from the provider into
     * memory. With a recording frequency of 2 seconds, 15000 corresponds to 8.3
     * hours.
     */
    public static final int MAX_LOADED_TRACK_POINTS = 20000;

    /**
     * Maximum number of track points ever loaded at once from the provider into
     * memory in a single call to read points.
     */
    public static final int MAX_LOADED_TRACK_POINTS_PER_BATCH = 1000;

    /**
     * Maximum number of way points displayed by the map overlay.
     */
    public static final int MAX_DISPLAYED_WAYPOINTS_POINTS = 128;

    /**
     * Maximum number of way points that will be loaded at one time.
     */
    public static final int MAX_LOADED_WAYPOINTS_POINTS = 10000;

    /**
     * Any time segment where the distance traveled is less than this value will
     * not be considered moving.
     */
    public static final double MAX_NO_MOVEMENT_DISTANCE = 2;

    /**
     * Anything faster than that (in meters per second) will be considered
     * moving.
     */
    public static final double MAX_NO_MOVEMENT_SPEED = 0.224;

    /**
     * Ignore any acceleration faster than this. Will ignore any speeds that
     * imply accelaration greater than 2g's 2g = 19.6 m/s^2 = 0.0002 m/ms^2 =
     * 0.02 m/(m*ms)
     */
    public static final double MAX_ACCELERATION = 0.02;

    /** Maximum age of a GPS location to be considered current. */
    public static final long MAX_LOCATION_AGE_MS = 60 * 1000; // 1 minute

    /** Maximum age of a network location to be considered current. */
    public static final long MAX_NETWORK_AGE_MS = 1000 * 60 * 10; // 10 minutes

    /**
     * The type of account that we can use for gdata uploads.
     */
    public static final String ACCOUNT_TYPE = "com.google";

    /**
     * The name of extra intent property to indicate whether we want to resume a
     * previously recorded track.
     */
    public static final String RESUME_TRACK_EXTRA_NAME = "com.google.android.apps.mytracks.RESUME_TRACK";

    public static final String SETTINGS_NAME = "SettingsActivity";

    public static final int MD5_CHECK_bUFF = 4096;
    // track constant end

}