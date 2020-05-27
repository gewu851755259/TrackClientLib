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

package com.mapscloud.track.services.provider;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import com.dtt.app.logging.LogUtils;
import com.mapscloud.track.R;
import com.mapscloud.track.services.content.TrackPointsColumns;
import com.mapscloud.track.services.content.TracksColumns;
import com.mapscloud.track.services.tracks.WaypointsColumns;
import com.mapscloud.track.services.utils.Constant;
import com.mapscloud.track.services.utils.PreferencesUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * A {@link ContentProvider} that handles access to track points, tracks, and
 * waypoints tables.
 *
 * @author Leif Hendrik Wilden
 */
public class MyTracksProvider extends ContentProvider {

    private static final String TAG                   = MyTracksProvider.class.getSimpleName();
    private static final int    DATABASE_VERSION      = 0;
    public static final  String DRIVE_ID_TRACKS_QUERY = TracksColumns.DRIVEID + " IS NOT NULL AND "
            + TracksColumns.DRIVEID + "!=''";


    private        File sdcardDir;
    private static File databaseFile;

    /**
     * Types of url.
     *
     * @author Jimmy Shih
     */

    enum UrlType {
        TRACKPOINTS, TRACKPOINTS_ID, TRACKS, TRACKS_ID, WAYPOINTS, WAYPOINTS_ID
    }

//    private final UriMatcher     uriMatcher;
    private UriMatcher uriMatcher;
    private       SQLiteDatabase db;

    // yml 为了可以传网络库，抽取使用包名的变量在下面
    private String authority = "";
    public static Uri TRACKS_CONTENT_URI;
    public static Uri TRACKPOINTS_CONTENT_URI;
    public static Uri WAYPOINTS_CONTENT_URI;

    public MyTracksProvider() {
        sdcardDir = Environment.getExternalStorageDirectory();
        databaseFile = new File(sdcardDir, "mapplus/app/app.db");

        // yml 添加mapplus/app/app.db不存在时创建文件
        try {
            if (!databaseFile.exists()) {
                File parentFile = databaseFile.getParentFile();
                if (!parentFile.exists())
                    parentFile.mkdirs();
                databaseFile.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Unable to create mapplus/app/app.db while file not exists.", e);
        }
//        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
//        uriMatcher.addURI(MyTracksProviderUtils.AUTHORITY,
//                TrackPointsColumns.TABLE_NAME, UrlType.TRACKPOINTS.ordinal());
//        uriMatcher.addURI(MyTracksProviderUtils.AUTHORITY,
//                TrackPointsColumns.TABLE_NAME + "/#", UrlType.TRACKPOINTS_ID.ordinal());
//        uriMatcher.addURI(MyTracksProviderUtils.AUTHORITY,
//                TracksColumns.TABLE_NAME, UrlType.TRACKS.ordinal());
//        uriMatcher.addURI(MyTracksProviderUtils.AUTHORITY,
//                TracksColumns.TABLE_NAME + "/#", UrlType.TRACKS_ID.ordinal());
//        uriMatcher.addURI(MyTracksProviderUtils.AUTHORITY,
//                WaypointsColumns.TABLE_NAME, UrlType.WAYPOINTS.ordinal());
//        uriMatcher.addURI(MyTracksProviderUtils.AUTHORITY,
//                WaypointsColumns.TABLE_NAME + "/#", UrlType.WAYPOINTS_ID.ordinal());
//        Log.e(TAG, "Provider 构造方法 创建UriMatcher完毕");
    }

    @Override
    public boolean onCreate() {
        return onCreate(getContext());
    }

    /**
     * Helper method to make onCreate is testable.
     *
     * @param context context to creates database
     * @return true means run successfully
     */

    @SuppressLint("SQLiteString")
    boolean onCreate(Context context) {
        if (!canAccess()) {
            return false;
        }

        PackageManager pManager = context.getPackageManager();
        String pName = context.getPackageName();
        Log.e(TAG, "Provider onCreate方法 package name: " + pName);
        try {
            PackageInfo pInfo = pManager.getPackageInfo(pName, PackageManager.GET_PROVIDERS);
            ProviderInfo[] providers = pInfo.providers;
            for (ProviderInfo provider : providers) {
                Log.e(TAG, "name is " + provider.name);
                Log.e(TAG, "authority is " + provider.authority);
                if (MyTracksProvider.class.getName().equals(provider.name)) {
                    authority = provider.authority;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Provider onCreate方法 package not found");
            e.printStackTrace();
        }

        if (TextUtils.isEmpty(authority)) {
            authority = MyTracksProviderUtils.AUTHORITY;
        }
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(authority, TrackPointsColumns.TABLE_NAME, UrlType.TRACKPOINTS.ordinal());
        uriMatcher.addURI(authority, TrackPointsColumns.TABLE_NAME + "/#", UrlType.TRACKPOINTS_ID.ordinal());
        uriMatcher.addURI(authority, TracksColumns.TABLE_NAME, UrlType.TRACKS.ordinal());
        uriMatcher.addURI(authority, TracksColumns.TABLE_NAME + "/#", UrlType.TRACKS_ID.ordinal());
        uriMatcher.addURI(authority, WaypointsColumns.TABLE_NAME, UrlType.WAYPOINTS.ordinal());
        uriMatcher.addURI(authority, WaypointsColumns.TABLE_NAME + "/#", UrlType.WAYPOINTS_ID.ordinal());
        Log.e(TAG, "Provider onCreate方法 创建UriMatcher完毕");

        TRACKS_CONTENT_URI = Uri.parse("content://" + authority + "/tracks");
        TRACKPOINTS_CONTENT_URI = Uri.parse("content://" + authority + "/trackpoints");
        WAYPOINTS_CONTENT_URI = Uri.parse("content://" + authority + "/waypoints");

        Log.e(TAG, "Provider onCreate方法  db 是否为空：" + (db == null));
        try {
            // yml 这里可能因为mapplus/app/app.db文件不存在而导致db对象为空，所以在构造方法中判断文件为空情况创建文件
            db = SQLiteDatabase.openDatabase(databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            db.execSQL(TrackPointsColumns.CREATE_TABLE);
            db.execSQL(TracksColumns.CREATE_TABLE);
            db.execSQL(WaypointsColumns.CREATE_TABLE);
        } catch (SQLiteException e) {
            e.printStackTrace();
            Log.e(TAG, "Provider onCreate方法 Unable to open database for writing. SQLiteException = " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Provider onCreate方法 Unable to open database for writing.Exception = " + e.getMessage());
        }
        Log.e(TAG, "Provider onCreate方法 db 是否为空：" + (db == null));
        return db != null;
    }

    @Override
    public int delete(Uri url, String where, String[] selectionArgs) {
        if (!canAccess()) {
            return 0;
        }
        String  table;
        boolean shouldVacuum = false;
        switch (getUrlType(url)) {
            case TRACKPOINTS:
                table = TrackPointsColumns.TABLE_NAME;
                break;
            case TRACKS:
                table = TracksColumns.TABLE_NAME;
                shouldVacuum = true;
                break;
            case WAYPOINTS:
                table = WaypointsColumns.TABLE_NAME;
                break;
            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }

        boolean driveSync = false;
        String  driveIds  = "";
        if (table.equals(TracksColumns.TABLE_NAME)) {
            driveSync = PreferencesUtils.getBoolean(getContext(), R.string.drive_sync_key,
                    PreferencesUtils.DRIVE_SYNC_DEFAULT);
            if (driveSync) {
                driveIds = where != null ? getDriveIds(null, where, selectionArgs) : getDriveIds(
                        new String[]{TracksColumns.DRIVEID}, DRIVE_ID_TRACKS_QUERY, null);
            }
        }

        Log.w(MyTracksProvider.TAG, "Deleting table " + table);
        int count = -1;

        boolean dbNotNull = checkDBNotNull(); // 检测SQLiteDatabase对象db是否为空
        if (dbNotNull) {
            try {
                db.beginTransaction();
                count = db.delete(table, where, selectionArgs);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            getContext().getContentResolver().notifyChange(url, null, false);

            if (driveSync && table.equals(TracksColumns.TABLE_NAME)) {
                String driveDeletedList = PreferencesUtils.getString(getContext(), R.string.drive_deleted_list_key,
                        PreferencesUtils.DRIVE_DELETED_LIST_DEFAULT);
                if (driveDeletedList.equals(PreferencesUtils.DRIVE_DELETED_LIST_DEFAULT)) {
                    driveDeletedList = driveIds;
                } else {
                    driveDeletedList += ";" + driveIds;
                }
                PreferencesUtils.setString(getContext(), R.string.drive_deleted_list_key, driveDeletedList);
            }

            if (shouldVacuum) {
                // If a potentially large amount of data was deleted, reclaim its
                // space.
                Log.i(TAG, "Vacuuming the database.");
                db.execSQL("VACUUM");
            }

        }
        return count;
    }

    @Override
    public String getType(Uri url) {
        if (!canAccess()) {
            return null;
        }
        switch (getUrlType(url)) {
            case TRACKPOINTS:
                return TrackPointsColumns.CONTENT_TYPE;
            case TRACKPOINTS_ID:
                return TrackPointsColumns.CONTENT_ITEMTYPE;
            case TRACKS:
                return TracksColumns.CONTENT_TYPE;
            case TRACKS_ID:
                return TracksColumns.CONTENT_ITEMTYPE;
            case WAYPOINTS:
                return WaypointsColumns.CONTENT_TYPE;
            case WAYPOINTS_ID:
                return WaypointsColumns.CONTENT_ITEMTYPE;
            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    @Override
    public Uri insert(Uri url, ContentValues initialValues) {
        Log.i("insert_track", "insert_track_url_in_provider = canAccess begin");
        if (!canAccess()) {
            Log.i("insert_track", "insert_track_url_in_provider = in");
            return null;
        }
        Log.i("insert_track", "insert_track_url_in_provider = canAccess end");
        if (initialValues == null) {
            initialValues = new ContentValues();
        }
        Uri result = null;
        boolean dbNotNull = checkDBNotNull();  // 检测SQLiteDatabase对象db是否为空
        if (dbNotNull) {
            try {
                boolean dbIsOpen = dbNotNull && db.isOpen();
                Log.i("insert_track", "insert_track_url_in_provider = begin is db null = " + !dbNotNull + " is writeable = " + dbIsOpen);
                db.beginTransaction();
                result = insertContentValues(url, getUrlType(url), initialValues);
                db.setTransactionSuccessful();
                Log.i("insert_track", "insert_track_url_in_provider = end " + result.toString());
            } finally {
                db.endTransaction();
            }
            getContext().getContentResolver().notifyChange(url, null, false);
            Log.i("insert_track", "insert_track_url_in_provider = " + result.toString());
        }
        return result;
    }

    @Override
    public int bulkInsert(Uri url, ContentValues[] valuesBulk) {
        if (!canAccess()) {
            return 0;
        }
        int numInserted = 0;
        boolean dbNotNull = checkDBNotNull();  // 检测SQLiteDatabase对象db是否为空
        if (dbNotNull) {
            try {
                // Use a transaction in order to make the insertions run as a single
                // batch
                db.beginTransaction();

                UrlType urlType = getUrlType(url);
                for (numInserted = 0; numInserted < valuesBulk.length; numInserted++) {
                    ContentValues contentValues = valuesBulk[numInserted];
                    if (contentValues == null) {
                        contentValues = new ContentValues();
                    }
                    insertContentValues(url, urlType, contentValues);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            getContext().getContentResolver().notifyChange(url, null, false);
        }
        return numInserted;
    }

    @Override
    public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs, String sort) {
        if (!canAccess()) {
            return null;
        }
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        String             sortOrder    = null;
        switch (getUrlType(url)) {
            case TRACKPOINTS:
                queryBuilder.setTables(TrackPointsColumns.TABLE_NAME);
                sortOrder = sort != null ? sort : TrackPointsColumns.DEFAULT_SORT_ORDER;
                break;
            case TRACKPOINTS_ID:
                queryBuilder.setTables(TrackPointsColumns.TABLE_NAME);
                queryBuilder.appendWhere("_id=" + url.getPathSegments().get(1));
                break;
            case TRACKS:
                queryBuilder.setTables(TracksColumns.TABLE_NAME);
                sortOrder = sort != null ? sort : TracksColumns.DEFAULT_SORT_ORDER;
                break;
            case TRACKS_ID:
                queryBuilder.setTables(TracksColumns.TABLE_NAME);
                queryBuilder.appendWhere("_id=" + url.getPathSegments().get(1));
                break;
            case WAYPOINTS:
                queryBuilder.setTables(WaypointsColumns.TABLE_NAME);
                sortOrder = sort != null ? sort : WaypointsColumns.DEFAULT_SORT_ORDER;
                break;
            case WAYPOINTS_ID:
                queryBuilder.setTables(WaypointsColumns.TABLE_NAME);
                queryBuilder.appendWhere("_id=" + url.getPathSegments().get(1));
                break;
            default:
                throw new IllegalArgumentException("Unknown url " + url);
        }
        boolean dbNotNull = checkDBNotNull();  // 检测SQLiteDatabase对象db是否为空
        if (dbNotNull) {
            Cursor cursor = queryBuilder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
            cursor.setNotificationUri(getContext().getContentResolver(), url);
            return cursor;
        } else {
            return null;
        }
    }

    @Override
    public int update(Uri url, ContentValues values, String where, String[] selectionArgs) {
        if (!canAccess()) {
            return 0;
        }
        String table;
        String whereClause;
        switch (getUrlType(url)) {
            case TRACKPOINTS:
                table = TrackPointsColumns.TABLE_NAME;
                whereClause = where;
                break;
            case TRACKPOINTS_ID:
                table = TrackPointsColumns.TABLE_NAME;
                whereClause = TrackPointsColumns._ID + "=" + url.getPathSegments().get(1);
                if (!TextUtils.isEmpty(where)) {
                    whereClause += " AND (" + where + ")";
                }
                break;
            case TRACKS:
                table = TracksColumns.TABLE_NAME;
                whereClause = where;
                break;
            case TRACKS_ID:
                table = TracksColumns.TABLE_NAME;
                whereClause = TracksColumns._ID + "=" + url.getPathSegments().get(1);
                if (!TextUtils.isEmpty(where)) {
                    whereClause += " AND (" + where + ")";
                }
                break;
            case WAYPOINTS:
                table = WaypointsColumns.TABLE_NAME;
                whereClause = where;
                break;
            case WAYPOINTS_ID:
                table = WaypointsColumns.TABLE_NAME;
                whereClause = WaypointsColumns._ID + "=" + url.getPathSegments().get(1);
                if (!TextUtils.isEmpty(where)) {
                    whereClause += " AND (" + where + ")";
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown url " + url);
        }
        int count = -1;
        boolean dbNotNull = checkDBNotNull();  // 检测SQLiteDatabase对象db是否为空
        if (dbNotNull) {
            try {
                db.beginTransaction();
                count = db.update(table, values, whereClause, selectionArgs);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            getContext().getContentResolver().notifyChange(url, null, false);
        }
        return count;
    }

    /**
     * Returns true if the caller can access the content provider.
     */
    private boolean canAccess() {
//        if (Binder.getCallingPid() == Process.myPid()) {
//            return true;
//        } else {
//            return PreferencesUtils.getBoolean(getContext(), R.string.allow_access_key,
//                    PreferencesUtils.ALLOW_ACCESS_DEFAULT);
//        }
        return true;
    }

    /**
     * Gets the {@link UrlType} for a url.
     *
     * @param url the url
     */
    private UrlType getUrlType(Uri url) {
        return UrlType.values()[uriMatcher.match(url)];
    }

    /**
     * Inserts a content based on the url type.
     *
     * @param url           the content url
     * @param urlType       the url type
     * @param contentValues the content values
     */
    private Uri insertContentValues(Uri url, UrlType urlType, ContentValues contentValues) {
        switch (urlType) {
            case TRACKPOINTS:
                return insertTrackPoint(url, contentValues);
            case TRACKS:
                return insertTrack(url, contentValues);
            case WAYPOINTS:
                return insertWaypoint(url, contentValues);
            default:
                throw new IllegalArgumentException("Unknown url " + url);
        }
    }

    /**
     * Inserts a track point.
     *
     * @param url    the content url
     * @param values the content values
     */
    private Uri insertTrackPoint(Uri url, ContentValues values) {
        boolean hasLatitude  = values.containsKey(TrackPointsColumns.LATITUDE);
        boolean hasLongitude = values.containsKey(TrackPointsColumns.LONGITUDE);
        boolean hasTime      = values.containsKey(TrackPointsColumns.TIME);
        if (!hasLatitude || !hasLongitude || !hasTime) {
            throw new IllegalArgumentException("Latitude, longitude, and time values are required.");
        }
        long rowId = db.insert(TrackPointsColumns.TABLE_NAME, TrackPointsColumns._ID, values);
        if (rowId >= 0) {
            return ContentUris.appendId(MyTracksProvider.TRACKPOINTS_CONTENT_URI.buildUpon(), rowId).build();
        }
        throw new SQLiteException("Failed to insert a track point " + url);
    }

    /**
     * Inserts a track.
     *
     * @param url           the content url
     * @param contentValues the content values
     */
    private Uri insertTrack(Uri url, ContentValues contentValues) {
        boolean hasStartTime = contentValues.containsKey(TracksColumns.STARTTIME);
        boolean hasStartId   = contentValues.containsKey(TracksColumns.STARTID);
        if (!hasStartTime || !hasStartId) {
            throw new IllegalArgumentException("Both start time and start id values are required.");
        }
        try {
            long rowId = db.insertOrThrow(TracksColumns.TABLE_NAME, TracksColumns._ID, contentValues);
            if (rowId >= 0) {
                return ContentUris.appendId(MyTracksProvider.TRACKS_CONTENT_URI.buildUpon(), rowId).build();
            }
            throw new SQLException("Failed to insert a track " + url);
        } catch (SQLException e) {
            e.printStackTrace();
            Log.e(TAG, "插入轨迹表抛异常信息：" + e.getMessage());
            throw new SQLException("Failed to insert a track " + url);
        }
    }

    /**
     * Inserts a waypoint.
     *
     * @param url           the content url
     * @param contentValues the content values
     */
    private Uri insertWaypoint(Uri url, ContentValues contentValues) {
        long rowId = db.insert(WaypointsColumns.TABLE_NAME, WaypointsColumns._ID, contentValues);
        if (rowId >= 0) {
            return ContentUris.appendId(MyTracksProvider.WAYPOINTS_CONTENT_URI.buildUpon(), rowId).build();
        }
        throw new SQLException("Failed to insert a waypoint " + url);
    }

    /**
     * Gets a list of dirve ids.
     *
     * @param projection    the projection
     * @param where         where
     * @param selectionArgs selection args
     */
    private String getDriveIds(String[] projection, String where, String[] selectionArgs) {
        ArrayList<String> driveIds = new ArrayList<String>();
        Cursor            cursor   = null;
        try {
            cursor = query(MyTracksProvider.TRACKS_CONTENT_URI, projection, where, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(TracksColumns.DRIVEID);
                do {
                    String driveId = cursor.getString(index);
                    if (driveId != null && !driveId.equals("")) {
                        driveIds.add(driveId);
                    }
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return TextUtils.join(";", driveIds);
    }

    /**
     * 检测SQLiteDatabase对象db是否为空
     *
     * @return
     */
    @SuppressLint("SQLiteString")
    private boolean checkDBNotNull() {
        Log.e(TAG, "provider checkDBNotNull方法 db 是否为空：" + (db == null));
        if (db == null || !databaseFile.exists()) {
            try {

                // yml 这里可能因为mapplus/app/app.db文件不存在，先保证文件存在
                if (!databaseFile.exists()) {
                    File parentFile = databaseFile.getParentFile();
                    if (!parentFile.exists())
                        parentFile.mkdirs();
                    databaseFile.createNewFile();
                }

                // yml app.db文件存在后db对象一般不会为空
                db = SQLiteDatabase.openDatabase(databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
                db.execSQL(TrackPointsColumns.CREATE_TABLE);
                db.execSQL(TracksColumns.CREATE_TABLE);
                db.execSQL(WaypointsColumns.CREATE_TABLE);
            } catch (SQLiteException e) {
                e.printStackTrace();
                Log.e(TAG, "provider checkDBNotNull方法 Unable to open database for writing. SQLiteException = " + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "provider checkDBNotNull方法 Unable to open database for writing. Exception = " + e.getMessage());
            }
        }
        if (db != null){
            LogUtils.e(Constant.TAG, "当前数据库版本 = " + db.getVersion());
        }

        return db != null;
    }

}