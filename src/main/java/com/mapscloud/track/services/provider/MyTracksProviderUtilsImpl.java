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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mapscloud.track.services.content.DescriptionGenerator;
import com.mapscloud.track.services.content.Track;
import com.mapscloud.track.services.content.TrackPointsColumns;
import com.mapscloud.track.services.content.TracksColumns;
import com.mapscloud.track.services.content.TripStatistics;
import com.mapscloud.track.services.content.TripStatisticsUpdater;
import com.mapscloud.track.services.model.MyTracksLocation;
import com.mapscloud.track.services.model.Waypoint;
import com.mapscloud.track.services.model.Waypoint.WaypointType;
import com.mapscloud.track.services.tracks.Sensor;
import com.mapscloud.track.services.tracks.WaypointsColumns;
import com.mapscloud.track.services.utils.LocationUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * {@link MyTracksProviderUtils} implementation.
 *
 * @author Leif Hendrik Wilden
 */
public class MyTracksProviderUtilsImpl implements MyTracksProviderUtils {

    private static final String TAG = MyTracksProviderUtilsImpl.class.getSimpleName();

    private static final int MAX_LATITUDE = 90000000;

    private final ContentResolver contentResolver;
    private       int             defaultCursorBatchSize = 2000;

    public MyTracksProviderUtilsImpl(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    @Override
    public void clearTrack(long trackId) {
        deleteTrackPointsAndWaypoints(trackId);
        Track track = new Track();
        track.id = (trackId);
        updateTrack(track);
    }

    @Override
    public Track createTrack(Cursor cursor) {
        int    appIdIndex         = cursor.getColumnIndexOrThrow(TracksColumns.COLUMN_APP_ID);
        int    appNameIndex       = cursor.getColumnIndexOrThrow(TracksColumns.COLUMN_APP_NAME);
        int    idIndex            = cursor.getColumnIndexOrThrow(TracksColumns._ID);
        int    nameIndex          = cursor.getColumnIndexOrThrow(TracksColumns.NAME);
        int    descriptionIndex   = cursor.getColumnIndexOrThrow(TracksColumns.DESCRIPTION);
        int    categoryIndex      = cursor.getColumnIndexOrThrow(TracksColumns.CATEGORY);
        int    trackTypeIndex     = cursor.getColumnIndexOrThrow(TracksColumns.TRACKTYPE);
        int    startIdIndex       = cursor.getColumnIndexOrThrow(TracksColumns.STARTID);
        int    stopIdIndex        = cursor.getColumnIndexOrThrow(TracksColumns.STOPID);
        int    startTimeIndex     = cursor.getColumnIndexOrThrow(TracksColumns.STARTTIME);
        int    stopTimeIndex      = cursor.getColumnIndexOrThrow(TracksColumns.STOPTIME);
        int    numPointsIndex     = cursor.getColumnIndexOrThrow(TracksColumns.NUMPOINTS);
        int    totalDistanceIndex = cursor.getColumnIndexOrThrow(TracksColumns.TOTALDISTANCE);
        int    totalTimeIndex     = cursor.getColumnIndexOrThrow(TracksColumns.TOTALTIME);
        int    movingTimeIndex    = cursor.getColumnIndexOrThrow(TracksColumns.MOVINGTIME);
        int    minLatIndex        = cursor.getColumnIndexOrThrow(TracksColumns.MINLAT);
        int    maxLatIndex        = cursor.getColumnIndexOrThrow(TracksColumns.MAXLAT);
        int    minLonIndex        = cursor.getColumnIndexOrThrow(TracksColumns.MINLON);
        int    maxLonIndex        = cursor.getColumnIndexOrThrow(TracksColumns.MAXLON);
        int    maxSpeedIndex      = cursor.getColumnIndexOrThrow(TracksColumns.MAXSPEED);
        int    minElevationIndex  = cursor.getColumnIndexOrThrow(TracksColumns.MINELEVATION);
        int    maxElevationIndex  = cursor.getColumnIndexOrThrow(TracksColumns.MAXELEVATION);
        int    elevationGainIndex = cursor.getColumnIndexOrThrow(TracksColumns.ELEVATIONGAIN);
        int    minGradeIndex      = cursor.getColumnIndexOrThrow(TracksColumns.MINGRADE);
        int    maxGradeIndex      = cursor.getColumnIndexOrThrow(TracksColumns.MAXGRADE);
        int    mapIdIndex         = cursor.getColumnIndexOrThrow(TracksColumns.MAPID);
        int    tableIdIndex       = cursor.getColumnIndexOrThrow(TracksColumns.TABLEID);
        int    iconIndex          = cursor.getColumnIndexOrThrow(TracksColumns.ICON);
        int    driveIdIndex       = cursor.getColumnIndexOrThrow(TracksColumns.DRIVEID);
        int    modifiedTimeIndex  = cursor.getColumnIndexOrThrow(TracksColumns.MODIFIEDTIME);
        int    sharedWithMeIndex  = cursor.getColumnIndexOrThrow(TracksColumns.SHAREDWITHME);
        int    sharedOwnerIndex   = cursor.getColumnIndexOrThrow(TracksColumns.SHAREDOWNER);
        int    favoriteIndex      = cursor.getColumnIndexOrThrow(TracksColumns.FAVORITE);
        int    serverDbIdIndex    = cursor.getColumnIndex(TracksColumns.COLUMN_SERVERDBID);
        int    uploadState        = cursor.getInt(cursor.getColumnIndex(TracksColumns.COLUMN_UPLOAD_STATE) == -1 ? 0 : cursor.getColumnIndex(TracksColumns.COLUMN_UPLOAD_STATE));
        int    totalSize          = cursor.getInt(cursor.getColumnIndex(TracksColumns.COLUMN_TOTAL_SIZE) == -1 ? 0 : cursor.getColumnIndex(TracksColumns.COLUMN_TOTAL_SIZE));
        int    uploadSize         = cursor.getInt(cursor.getColumnIndex(TracksColumns.COLUMN_UPLOAD_SIZE) == -1 ? 0 : cursor.getColumnIndex(TracksColumns.COLUMN_UPLOAD_SIZE));
        int    shareScope         = cursor.getInt(cursor.getColumnIndex(TracksColumns.COLUMN_SHARE_SCOPE) == -1 ? 0 : cursor.getColumnIndex(TracksColumns.COLUMN_SHARE_SCOPE));
        String shareKey           = cursor.getString(cursor.getColumnIndex(TracksColumns.COLUMN_SHARE_KEY) == -1 ? 0 : cursor.getColumnIndex(TracksColumns.COLUMN_SHARE_KEY));

        Track          track          = new Track();
        TripStatistics tripStatistics = track.getTripStatistics();
        if (!cursor.isNull(appIdIndex)) {
            track.appId = (cursor.getString(appIdIndex));
        }
        if (!cursor.isNull(appNameIndex)) {
            track.appName = (cursor.getString(appNameIndex));
        }
        if (!cursor.isNull(idIndex)) {
            track.id = (cursor.getLong(idIndex));
        }
        if (!cursor.isNull(nameIndex)) {
            track.name = (cursor.getString(nameIndex));
        }
        if (!cursor.isNull(descriptionIndex)) {
            track.description = (cursor.getString(descriptionIndex));
        }
        if (!cursor.isNull(categoryIndex)) {
            track.category = (cursor.getString(categoryIndex));
        }
        if (!cursor.isNull(trackTypeIndex)) {
            track.trackType = (cursor.getInt(trackTypeIndex));
        }
        if (!cursor.isNull(favoriteIndex)) {
            track.favorite = (cursor.getInt(favoriteIndex));
        }
        if (!cursor.isNull(serverDbIdIndex)) {
            track.serverDbId = (cursor.getLong(serverDbIdIndex));
        }
        if (!cursor.isNull(startIdIndex)) {
            track.startId = (cursor.getLong(startIdIndex));
        }
        if (!cursor.isNull(stopIdIndex)) {
            track.stopId = (cursor.getLong(stopIdIndex));
        }
        if (!cursor.isNull(startTimeIndex)) {
            tripStatistics.setStartTime(cursor.getLong(startTimeIndex));
        }
        if (!cursor.isNull(stopTimeIndex)) {
            tripStatistics.setStopTime(cursor.getLong(stopTimeIndex));
        }
        if (!cursor.isNull(numPointsIndex)) {
            track.numberOfPoints = (cursor.getInt(numPointsIndex));
        }
        if (!cursor.isNull(totalDistanceIndex)) {
            tripStatistics.setTotalDistance(cursor.getFloat(totalDistanceIndex));
        }
        if (!cursor.isNull(totalTimeIndex)) {
            tripStatistics.setTotalTime(cursor.getLong(totalTimeIndex));
        }
        if (!cursor.isNull(movingTimeIndex)) {
            tripStatistics.setMovingTime(cursor.getLong(movingTimeIndex));
        }
        if (!cursor.isNull(minLatIndex) && !cursor.isNull(maxLatIndex) && !cursor.isNull(minLonIndex)
                && !cursor.isNull(maxLonIndex)) {
            int bottom = cursor.getInt(minLatIndex);
            int top    = cursor.getInt(maxLatIndex);
            int left   = cursor.getInt(minLonIndex);
            int right  = cursor.getInt(maxLonIndex);
            tripStatistics.setBounds(left, top, right, bottom);
        }
        if (!cursor.isNull(maxSpeedIndex)) {
            tripStatistics.setMaxSpeed(cursor.getFloat(maxSpeedIndex));
        }
        if (!cursor.isNull(minElevationIndex)) {
            tripStatistics.setMinElevation(cursor.getFloat(minElevationIndex));
        }
        if (!cursor.isNull(maxElevationIndex)) {
            tripStatistics.setMaxElevation(cursor.getFloat(maxElevationIndex));
        }
        if (!cursor.isNull(elevationGainIndex)) {
            tripStatistics.setTotalElevationGain(cursor.getFloat(elevationGainIndex));
        }
        if (!cursor.isNull(minGradeIndex)) {
            tripStatistics.setMinGrade(cursor.getFloat(minGradeIndex));
        }
        if (!cursor.isNull(maxGradeIndex)) {
            tripStatistics.setMaxGrade(cursor.getFloat(maxGradeIndex));
        }
        if (!cursor.isNull(mapIdIndex)) {
            track.mapId = (cursor.getString(mapIdIndex));
        }
        if (!cursor.isNull(tableIdIndex)) {
            track.tableId = (cursor.getString(tableIdIndex));
        }
        if (!cursor.isNull(iconIndex)) {
            track.icon = (cursor.getString(iconIndex));
        }
        if (!cursor.isNull(driveIdIndex)) {
            track.driveId = (cursor.getString(driveIdIndex));
        }
        if (!cursor.isNull(modifiedTimeIndex)) {
            track.modifiedTime = (cursor.getLong(modifiedTimeIndex));
        }
        if (!cursor.isNull(sharedWithMeIndex)) {
            track.sharedWithMe = (cursor.getInt(sharedWithMeIndex) == 1);
        }
        if (!cursor.isNull(sharedOwnerIndex)) {
            track.sharedOwner = (cursor.getString(sharedOwnerIndex));
        }

        track.uploadState = uploadState;
        track.totalSize = totalSize;
        track.uploadSize = uploadSize;
        track.shareScope = shareScope;
        track.shareKey = shareKey;
        return track;
    }

    @Override
    public void deleteAllTracks() {
        contentResolver.delete(MyTracksProvider.TRACKPOINTS_CONTENT_URI, null, null);
        contentResolver.delete(MyTracksProvider.WAYPOINTS_CONTENT_URI, null, null);
        // Delete tracks last since it triggers a database vaccum call
        contentResolver.delete(MyTracksProvider.TRACKS_CONTENT_URI, null, null);
    }

    @Override
    public void deleteTrack(long trackId) {
        Track track = getTrack(trackId);
        if (track != null) {
            deleteTrackPointsAndWaypoints(trackId);

            // Delete track last since it triggers a database vaccum call
            contentResolver.delete(MyTracksProvider.TRACKS_CONTENT_URI, TracksColumns._ID + "=?",
                    new String[]{Long.toString(trackId)});
        }
    }

    /**
     * Deletes track points and waypoints of a track. Assumes
     * {@link TracksColumns#STARTID}, {@link TracksColumns#STOPID}, and
     * {@link TracksColumns#NUMPOINTS} will be updated by the caller.
     *
     * @param trackId the track id
     */
    private void deleteTrackPointsAndWaypoints(long trackId) {
        Track track = getTrack(trackId);
        if (track != null) {
            String   where         = TrackPointsColumns._ID + ">=? AND " + TrackPointsColumns._ID + "<=?";
            String[] selectionArgs = new String[]{Long.toString(track.startId), Long.toString(track.stopId)};
            contentResolver.delete(MyTracksProvider.TRACKPOINTS_CONTENT_URI, where, selectionArgs);
            contentResolver.delete(MyTracksProvider.WAYPOINTS_CONTENT_URI, WaypointsColumns.TRACKID + "=?",
                    new String[]{Long.toString(trackId)});
        }
    }

    @Override
    public List<Track> getAllTracks() {
        ArrayList<Track> tracks = new ArrayList<Track>();
        Cursor           cursor = null;
        try {
            cursor = getTrackCursor(null, null, null, TracksColumns._ID + " desc");
            if (cursor != null && cursor.moveToFirst()) {
                tracks.ensureCapacity(cursor.getCount());
                do {
                    tracks.add(createTrack(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return tracks;
    }

    @Override
    public List<Track> getTracks() {
        ArrayList<Track> tracks = new ArrayList<Track>();
        Cursor           cursor = null;
        try {
            cursor = getTrackCursor(null, TracksColumns.TRACKTYPE + "=?", new String[]{Integer.toString(0)}, TracksColumns._ID + " desc");
            if (cursor != null && cursor.moveToFirst()) {
                tracks.ensureCapacity(cursor.getCount());
                do {
                    tracks.add(createTrack(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return tracks;
    }

    @Override
    public List<Track> getAllNaviTracks() {
        ArrayList<Track> tracks = new ArrayList<Track>();
        Cursor           cursor = null;
        try {
            cursor = getTrackCursor(null, TracksColumns.TRACKTYPE + "=?", new String[]{Integer.toString(1)}, TracksColumns._ID + " desc");
            if (cursor != null && cursor.moveToFirst()) {
                tracks.ensureCapacity(cursor.getCount());
                do {
                    tracks.add(createTrack(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return tracks;
    }

    @Override
    public List<Track> getAllTracksByKey(String key) {
        ArrayList<Track> tracks = new ArrayList<Track>();
        Cursor           cursor = null;
        try {
            StringBuffer selection_sb = new StringBuffer();
            selection_sb.append(TracksColumns.NAME).append(" LIKE ").append("'%").append(key).append("%'")
                    .append(" or ").append(TracksColumns.DESCRIPTION).append(" LIKE ").append("'%").append(key)
                    .append("%'").append(" or ").append(TracksColumns.CATEGORY).append(" LIKE ").append("'%")
                    .append(key).append("%'");
            String       selection        = selection_sb.toString();
            StringBuffer track_columns_id = new StringBuffer();
            track_columns_id.append(TracksColumns._ID).append(" desc");
            cursor = getTrackCursor(null, selection, null, track_columns_id.toString());
            if (cursor != null && cursor.moveToFirst()) {
                tracks.ensureCapacity(cursor.getCount());
                do {
                    tracks.add(createTrack(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return tracks;
    }

    @Override
    public List<Track> getFavoritedTracks() {
        ArrayList<Track> tracks = new ArrayList<Track>();
        Cursor           cursor = null;
        try {
            String selection = TracksColumns.FAVORITE + "=1";
            cursor = getTrackCursor(null, selection, null, TracksColumns._ID + " desc");
            if (cursor != null && cursor.moveToFirst()) {
                tracks.ensureCapacity(cursor.getCount());
                do {
                    tracks.add(createTrack(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return tracks;
    }

    @Override
    public Track getLastTrack() {
        Cursor cursor = null;
        try {
            String selection = TracksColumns._ID + "=(select max(" + TracksColumns._ID + ") from "
                    + TracksColumns.TABLE_NAME + ")";
            cursor = getTrackCursor(null, selection, null, TracksColumns._ID);
            if (cursor != null && cursor.moveToNext()) {
                return createTrack(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    @Override
    public Track getTrack(long trackId) {
        if (trackId < 0) {
            return null;
        }
        Cursor cursor = null;
        try {
            cursor = getTrackCursor(null, TracksColumns._ID + "=?", new String[]{Long.toString(trackId)},
                    TracksColumns._ID);
            if (cursor != null && cursor.moveToNext()) {
                return createTrack(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    @Override
    public Cursor getTrackCursor(String selection, String[] selectionArgs, String sortOrder) {
        return getTrackCursor(null, selection, selectionArgs, sortOrder);
    }

    @Override
    public Uri insertTrack(Track track) {
        checkDBNotNull();
        return contentResolver.insert(MyTracksProvider.TRACKS_CONTENT_URI, createContentValues(track));
    }

    @Override
    public void updateTrack(Track track) {
        contentResolver.update(MyTracksProvider.TRACKS_CONTENT_URI, createContentValues(track), TracksColumns._ID + "=?",
                new String[]{Long.toString(track.id)});
    }

    private ContentValues createContentValues(Track track) {
        ContentValues  values         = new ContentValues();
        TripStatistics tripStatistics = track.getTripStatistics();

        // Value < 0 indicates no id is available
        if (track.id >= 0) {
            values.put(TracksColumns._ID, track.id);
        }

        values.put(TracksColumns.COLUMN_APP_ID, track.appId);
        values.put(TracksColumns.COLUMN_APP_NAME, track.appName);

        values.put(TracksColumns.NAME, track.name);
        values.put(TracksColumns.DESCRIPTION, track.description);
        values.put(TracksColumns.CATEGORY, track.category);
        values.put(TracksColumns.TRACKTYPE, track.trackType);
        values.put(TracksColumns.STARTID, track.startId);
        values.put(TracksColumns.STOPID, track.stopId);
        values.put(TracksColumns.STARTTIME, tripStatistics.getStartTime());
        values.put(TracksColumns.STOPTIME, tripStatistics.getStopTime());
        values.put(TracksColumns.NUMPOINTS, track.numberOfPoints);
        values.put(TracksColumns.TOTALDISTANCE, tripStatistics.getTotalDistance());
        values.put(TracksColumns.TOTALTIME, tripStatistics.getTotalTime());
        values.put(TracksColumns.MOVINGTIME, tripStatistics.getMovingTime());
        values.put(TracksColumns.MINLAT, tripStatistics.getBottom());
        values.put(TracksColumns.MAXLAT, tripStatistics.getTop());
        values.put(TracksColumns.MINLON, tripStatistics.getLeft());
        values.put(TracksColumns.MAXLON, tripStatistics.getRight());
        values.put(TracksColumns.AVGSPEED, tripStatistics.getAverageSpeed());
        values.put(TracksColumns.AVGMOVINGSPEED, tripStatistics.getAverageMovingSpeed());
        values.put(TracksColumns.MAXSPEED, tripStatistics.getMaxSpeed());
        values.put(TracksColumns.MINELEVATION, tripStatistics.getMinElevation());
        values.put(TracksColumns.MAXELEVATION, tripStatistics.getMaxElevation());
        values.put(TracksColumns.ELEVATIONGAIN, tripStatistics.getTotalElevationGain());
        values.put(TracksColumns.MINGRADE, tripStatistics.getMinGrade());
        values.put(TracksColumns.MAXGRADE, tripStatistics.getMaxGrade());
        values.put(TracksColumns.MAPID, track.mapId);
        values.put(TracksColumns.TABLEID, track.tableId);
        values.put(TracksColumns.ICON, track.icon);
        values.put(TracksColumns.DRIVEID, track.driveId);
        values.put(TracksColumns.MODIFIEDTIME, track.modifiedTime);
        values.put(TracksColumns.SHAREDWITHME, track.sharedWithMe);
        values.put(TracksColumns.SHAREDOWNER, track.sharedOwner);
        values.put(TracksColumns.FAVORITE, track.favorite);
        values.put(TracksColumns.COLUMN_SERVERDBID, track.serverDbId);
        return values;
    }

    /**
     * Gets a track cursor.
     *
     * @param projection    the projection
     * @param selection     the selection
     * @param selectionArgs the selection arguments
     * @param sortOrder     the sort oder
     */
    private Cursor getTrackCursor(String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        Cursor c = null;
        try {
            c = contentResolver.query(MyTracksProvider.TRACKS_CONTENT_URI, projection,
                    selection, selectionArgs, sortOrder);
        } catch (SQLiteException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return c;
    }

    @Override
    public Waypoint createWaypoint(Cursor cursor) {
        int idIndex            = cursor.getColumnIndexOrThrow(WaypointsColumns._ID);
        int nameIndex          = cursor.getColumnIndexOrThrow(WaypointsColumns.NAME);
        int descriptionIndex   = cursor.getColumnIndexOrThrow(WaypointsColumns.DESCRIPTION);
        int categoryIndex      = cursor.getColumnIndexOrThrow(WaypointsColumns.CATEGORY);
        int iconIndex          = cursor.getColumnIndexOrThrow(WaypointsColumns.ICON);
        int trackIdIndex       = cursor.getColumnIndexOrThrow(WaypointsColumns.TRACKID);
        int typeIndex          = cursor.getColumnIndexOrThrow(WaypointsColumns.TYPE);
        int lengthIndex        = cursor.getColumnIndexOrThrow(WaypointsColumns.LENGTH);
        int durationIndex      = cursor.getColumnIndexOrThrow(WaypointsColumns.DURATION);
        int startTimeIndex     = cursor.getColumnIndexOrThrow(WaypointsColumns.STARTTIME);
        int startIdIndex       = cursor.getColumnIndexOrThrow(WaypointsColumns.STARTID);
        int stopIdIndex        = cursor.getColumnIndexOrThrow(WaypointsColumns.STOPID);
        int longitudeIndex     = cursor.getColumnIndexOrThrow(WaypointsColumns.LONGITUDE);
        int latitudeIndex      = cursor.getColumnIndexOrThrow(WaypointsColumns.LATITUDE);
        int timeIndex          = cursor.getColumnIndexOrThrow(WaypointsColumns.TIME);
        int altitudeIndex      = cursor.getColumnIndexOrThrow(WaypointsColumns.ALTITUDE);
        int accuracyIndex      = cursor.getColumnIndexOrThrow(WaypointsColumns.ACCURACY);
        int speedIndex         = cursor.getColumnIndexOrThrow(WaypointsColumns.SPEED);
        int bearingIndex       = cursor.getColumnIndexOrThrow(WaypointsColumns.BEARING);
        int totalDistanceIndex = cursor.getColumnIndexOrThrow(WaypointsColumns.TOTALDISTANCE);
        int totalTimeIndex     = cursor.getColumnIndexOrThrow(WaypointsColumns.TOTALTIME);
        int movingTimeIndex    = cursor.getColumnIndexOrThrow(WaypointsColumns.MOVINGTIME);
        int maxSpeedIndex      = cursor.getColumnIndexOrThrow(WaypointsColumns.MAXSPEED);
        int minElevationIndex  = cursor.getColumnIndexOrThrow(WaypointsColumns.MINELEVATION);
        int maxElevationIndex  = cursor.getColumnIndexOrThrow(WaypointsColumns.MAXELEVATION);
        int elevationGainIndex = cursor.getColumnIndexOrThrow(WaypointsColumns.ELEVATIONGAIN);
        int minGradeIndex      = cursor.getColumnIndexOrThrow(WaypointsColumns.MINGRADE);
        int maxGradeIndex      = cursor.getColumnIndexOrThrow(WaypointsColumns.MAXGRADE);
        int recordMediaId      = cursor.getColumnIndexOrThrow(WaypointsColumns.IDINRECORDTABLE);
        int recordMediaType    = cursor.getColumnIndexOrThrow(WaypointsColumns.IDINRECORDTABLE);

        Waypoint waypoint = new Waypoint();

        if (!cursor.isNull(idIndex)) {
            waypoint.id = (cursor.getLong(idIndex));
        }
        if (!cursor.isNull(nameIndex)) {
            waypoint.name = (cursor.getString(nameIndex));
        }
        if (!cursor.isNull(descriptionIndex)) {
            waypoint.description = (cursor.getString(descriptionIndex));
        }
        if (!cursor.isNull(categoryIndex)) {
            waypoint.category = (cursor.getString(categoryIndex));
        }
        if (!cursor.isNull(iconIndex)) {
            waypoint.icon = (cursor.getString(iconIndex));
        }
        if (!cursor.isNull(trackIdIndex)) {
            waypoint.trackId = (cursor.getLong(trackIdIndex));
        }
        if (!cursor.isNull(typeIndex)) {
            waypoint.type = (WaypointType.values()[cursor.getInt(typeIndex)]);
        }
        if (!cursor.isNull(lengthIndex)) {
            waypoint.length = (cursor.getFloat(lengthIndex));
        }
        if (!cursor.isNull(durationIndex)) {
            waypoint.duration = (cursor.getLong(durationIndex));
        }
        if (!cursor.isNull(startIdIndex)) {
            waypoint.startId = (cursor.getLong(startIdIndex));
        }
        if (!cursor.isNull(stopIdIndex)) {
            waypoint.stopId = (cursor.getLong(stopIdIndex));
        }

        if (!cursor.isNull(recordMediaId)) {
            waypoint.recordMediaId = (cursor.getLong(recordMediaId));
        }

        if (!cursor.isNull(recordMediaType)) {
            waypoint.recordMediaType = (cursor.getInt(recordMediaType));
        }

        Location location = new Location("");
        if (!cursor.isNull(longitudeIndex) && !cursor.isNull(latitudeIndex)) {
            location.setLongitude(((double) cursor.getInt(longitudeIndex)) / 1E6);
            location.setLatitude(((double) cursor.getInt(latitudeIndex)) / 1E6);
        }
        if (!cursor.isNull(timeIndex)) {
            location.setTime(cursor.getLong(timeIndex));
        }
        if (!cursor.isNull(altitudeIndex)) {
            location.setAltitude(cursor.getFloat(altitudeIndex));
        }
        if (!cursor.isNull(accuracyIndex)) {
            location.setAccuracy(cursor.getFloat(accuracyIndex));
        }
        if (!cursor.isNull(speedIndex)) {
            location.setSpeed(cursor.getFloat(speedIndex));
        }
        if (!cursor.isNull(bearingIndex)) {
            location.setBearing(cursor.getFloat(bearingIndex));
        }
        waypoint.location = (location);

        TripStatistics tripStatistics    = new TripStatistics();
        boolean        hasTripStatistics = false;
        if (!cursor.isNull(startTimeIndex)) {
            tripStatistics.setStartTime(cursor.getLong(startTimeIndex));
            hasTripStatistics = true;
        }
        if (!cursor.isNull(totalDistanceIndex)) {
            tripStatistics.setTotalDistance(cursor.getFloat(totalDistanceIndex));
            hasTripStatistics = true;
        }
        if (!cursor.isNull(totalTimeIndex)) {
            tripStatistics.setTotalTime(cursor.getLong(totalTimeIndex));
            hasTripStatistics = true;
        }
        if (!cursor.isNull(movingTimeIndex)) {
            tripStatistics.setMovingTime(cursor.getLong(movingTimeIndex));
            hasTripStatistics = true;
        }
        if (!cursor.isNull(maxSpeedIndex)) {
            tripStatistics.setMaxSpeed(cursor.getFloat(maxSpeedIndex));
            hasTripStatistics = true;
        }
        if (!cursor.isNull(minElevationIndex)) {
            tripStatistics.setMinElevation(cursor.getFloat(minElevationIndex));
            hasTripStatistics = true;
        }
        if (!cursor.isNull(maxElevationIndex)) {
            tripStatistics.setMaxElevation(cursor.getFloat(maxElevationIndex));
            hasTripStatistics = true;
        }
        if (!cursor.isNull(elevationGainIndex)) {
            tripStatistics.setTotalElevationGain(cursor.getFloat(elevationGainIndex));
            hasTripStatistics = true;
        }
        if (!cursor.isNull(minGradeIndex)) {
            tripStatistics.setMinGrade(cursor.getFloat(minGradeIndex));
            hasTripStatistics = true;
        }
        if (!cursor.isNull(maxGradeIndex)) {
            tripStatistics.setMaxGrade(cursor.getFloat(maxGradeIndex));
            hasTripStatistics = true;
        }

        if (hasTripStatistics) {
            waypoint.tripStatistics = (tripStatistics);
        }
        return waypoint;
    }

    @Override
    public void deleteWaypoint(long waypointId, DescriptionGenerator descriptionGenerator) {
        final Waypoint waypoint = getWaypoint(waypointId);
        if (waypoint != null && waypoint.type == WaypointType.STATISTICS && descriptionGenerator != null) {
            final Waypoint nextWaypoint = getNextStatisticsWaypointAfter(waypoint);
            if (nextWaypoint == null) {
                Log.d(TAG, "Unable to find the next statistics marker after deleting one.");
            } else {
                nextWaypoint.tripStatistics.merge(waypoint.tripStatistics);
                nextWaypoint.description = (descriptionGenerator
                        .generateWaypointDescription(nextWaypoint.tripStatistics));
                if (!updateWaypoint(nextWaypoint)) {
                    Log.e(TAG, "Unable to update the next statistics marker after deleting one.");
                }
            }
        }
        contentResolver.delete(MyTracksProvider.WAYPOINTS_CONTENT_URI, WaypointsColumns._ID + "=?",
                new String[]{Long.toString(waypointId)});
    }

    @Override
    public long getFirstWaypointId(long trackId) {
        if (trackId < 0) {
            return -1L;
        }
        Cursor cursor = null;
        try {
            cursor = getWaypointCursor(new String[]{WaypointsColumns._ID}, WaypointsColumns.TRACKID + "=?",
                    new String[]{Long.toString(trackId)}, WaypointsColumns._ID, 1);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(WaypointsColumns._ID));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1L;
    }

    @Override
    public Waypoint getLastWaypoint(long trackId, WaypointType waypointType) {
        if (trackId < 0) {
            return null;
        }
        Cursor cursor = null;
        try {
            String   selection     = WaypointsColumns.TRACKID + "=? AND " + WaypointsColumns.TYPE + "=?";
            String[] selectionArgs = new String[]{Long.toString(trackId), Integer.toString(waypointType.ordinal())};
            cursor = getWaypointCursor(null, selection, selectionArgs, WaypointsColumns._ID + " DESC", 1);
            if (cursor != null && cursor.moveToFirst()) {
                return createWaypoint(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    @Override
    public int getNextWaypointNumber(long trackId, WaypointType waypointType) {
        if (trackId < 0) {
            return -1;
        }
        Cursor cursor = null;
        try {
            String[] projection    = {WaypointsColumns._ID};
            String   selection     = WaypointsColumns.TRACKID + "=?  AND " + WaypointsColumns.TYPE + "=?";
            String[] selectionArgs = new String[]{Long.toString(trackId), Integer.toString(waypointType.ordinal())};
            cursor = getWaypointCursor(projection, selection, selectionArgs, WaypointsColumns._ID, -1);
            if (cursor != null) {
                int count = cursor.getCount();
                /*
                 * For statistics markers, the first marker is for the track
                 * statistics, so return the count as the next user visible
                 * number.
                 */
                return waypointType == WaypointType.STATISTICS ? count : count + 1;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1;
    }

    @Override
    public Waypoint getWaypoint(long waypointId) {
        if (waypointId < 0) {
            return null;
        }
        Cursor cursor = null;
        try {
            cursor = getWaypointCursor(null, WaypointsColumns._ID + "=?", new String[]{Long.toString(waypointId)},
                    WaypointsColumns._ID, 1);
            if (cursor != null && cursor.moveToFirst()) {
                return createWaypoint(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    @Override
    public Cursor getWaypointCursor(String selection, String[] selectionArgs, String sortOrder, int maxWaypoints) {
        return getWaypointCursor(null, selection, selectionArgs, sortOrder, maxWaypoints);
    }

    @Override
    public Cursor getWaypointCursor(long trackId, long minWaypointId, int maxWaypoints) {
        if (trackId < 0) {
            return null;
        }

        String   selection;
        String[] selectionArgs;
        if (minWaypointId >= 0) {
            selection = WaypointsColumns.TRACKID + "=? AND " + WaypointsColumns._ID + ">=?";
            selectionArgs = new String[]{Long.toString(trackId), Long.toString(minWaypointId)};
        } else {
            selection = WaypointsColumns.TRACKID + "=?";
            selectionArgs = new String[]{Long.toString(trackId)};
        }
        return getWaypointCursor(null, selection, selectionArgs, WaypointsColumns._ID, maxWaypoints);
    }

    @Override
    public Uri insertWaypoint(Waypoint waypoint) {
        waypoint.id = (-1L);
        return contentResolver.insert(MyTracksProvider.WAYPOINTS_CONTENT_URI, createContentValues(waypoint));
    }

    @Override
    public boolean updateWaypoint(Waypoint waypoint) {
        int rows = contentResolver.update(MyTracksProvider.WAYPOINTS_CONTENT_URI, createContentValues(waypoint),
                WaypointsColumns._ID + "=?", new String[]{Long.toString(waypoint.id)});
        return rows == 1;
    }

    ContentValues createContentValues(Waypoint waypoint) {
        ContentValues values = new ContentValues();

        // Value < 0 indicates no id is available
        if (waypoint.id >= 0) {
            values.put(WaypointsColumns._ID, waypoint.id);
        }
        values.put(WaypointsColumns.NAME, waypoint.name);
        values.put(WaypointsColumns.DESCRIPTION, waypoint.description);
        values.put(WaypointsColumns.CATEGORY, waypoint.category);
        values.put(WaypointsColumns.ICON, waypoint.icon);
        values.put(WaypointsColumns.TRACKID, waypoint.trackId);
        values.put(WaypointsColumns.TYPE, waypoint.type.ordinal());
        values.put(WaypointsColumns.LENGTH, waypoint.length);
        values.put(WaypointsColumns.DURATION, waypoint.duration);
        values.put(WaypointsColumns.STARTID, waypoint.startId);
        values.put(WaypointsColumns.STOPID, waypoint.stopId);

        values.put(WaypointsColumns.IDINRECORDTABLE, waypoint.recordMediaId);
        values.put(WaypointsColumns.RECORD_MEDIA_TYPE, waypoint.recordMediaType);

        Location location = waypoint.location;
        if (location != null) {
            values.put(WaypointsColumns.LONGITUDE, (int) (location.getLongitude() * 1E6));
            values.put(WaypointsColumns.LATITUDE, (int) (location.getLatitude() * 1E6));
            values.put(WaypointsColumns.TIME, location.getTime());
            if (location.hasAltitude()) {
                values.put(WaypointsColumns.ALTITUDE, location.getAltitude());
            }
            if (location.hasAccuracy()) {
                values.put(WaypointsColumns.ACCURACY, location.getAccuracy());
            }
            if (location.hasSpeed()) {
                values.put(WaypointsColumns.SPEED, location.getSpeed());
            }
            if (location.hasBearing()) {
                values.put(WaypointsColumns.BEARING, location.getBearing());
            }
        }

        TripStatistics tripStatistics = waypoint.tripStatistics;
        if (tripStatistics != null) {
            values.put(WaypointsColumns.STARTTIME, tripStatistics.getStartTime());
            values.put(WaypointsColumns.TOTALDISTANCE, tripStatistics.getTotalDistance());
            values.put(WaypointsColumns.TOTALTIME, tripStatistics.getTotalTime());
            values.put(WaypointsColumns.MOVINGTIME, tripStatistics.getMovingTime());
            values.put(WaypointsColumns.AVGSPEED, tripStatistics.getAverageSpeed());
            values.put(WaypointsColumns.AVGMOVINGSPEED, tripStatistics.getAverageMovingSpeed());
            values.put(WaypointsColumns.MAXSPEED, tripStatistics.getMaxSpeed());
            values.put(WaypointsColumns.MINELEVATION, tripStatistics.getMinElevation());
            values.put(WaypointsColumns.MAXELEVATION, tripStatistics.getMaxElevation());
            values.put(WaypointsColumns.ELEVATIONGAIN, tripStatistics.getTotalElevationGain());
            values.put(WaypointsColumns.MINGRADE, tripStatistics.getMinGrade());
            values.put(WaypointsColumns.MAXGRADE, tripStatistics.getMaxGrade());
        }
        return values;
    }

    private Waypoint getNextStatisticsWaypointAfter(Waypoint waypoint) {
        Cursor cursor = null;
        try {
            String selection = WaypointsColumns._ID + ">?  AND " + WaypointsColumns.TRACKID + "=? AND "
                    + WaypointsColumns.TYPE + "=" + WaypointType.STATISTICS.ordinal();
            String[] selectionArgs = new String[]{Long.toString(waypoint.id), Long.toString(waypoint.trackId)};
            cursor = getWaypointCursor(null, selection, selectionArgs, WaypointsColumns._ID, 1);
            if (cursor != null && cursor.moveToFirst()) {
                return createWaypoint(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * Gets a waypoint cursor.
     *
     * @param projection    the projection
     * @param selection     the selection
     * @param selectionArgs the selection args
     * @param sortOrder     the sort order
     * @param maxWaypoints  the maximum number of waypoints
     */
    private Cursor getWaypointCursor(String[] projection, String selection,
                                     String[] selectionArgs, String sortOrder, int maxWaypoints) {
        if (sortOrder == null) {
            sortOrder = WaypointsColumns._ID;
        }
        if (maxWaypoints >= 0) {
            sortOrder += " LIMIT " + maxWaypoints;
        }

        Cursor c = null;
        try {
            c = contentResolver.query(MyTracksProvider.WAYPOINTS_CONTENT_URI, projection,
                    selection, selectionArgs, sortOrder);
        } catch (SQLiteException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return c;
    }

    @Override
    public int bulkInsertTrackPoint(Location[] locations, int length, long trackId) {
        if (length == -1) {
            length = locations.length;
        }
        ContentValues[] values = new ContentValues[length];
        for (int i = 0; i < length; i++) {
            values[i] = createContentValues(locations[i], trackId);
        }
        return contentResolver.bulkInsert(MyTracksProvider.TRACKPOINTS_CONTENT_URI, values);
    }

    @Override
    public Location createTrackPoint(Cursor cursor) {
        Location location = new MyTracksLocation("");
        fillTrackPoint(cursor, new CachedTrackPointsIndexes(cursor), location);
        return location;
    }

    @Override
    public long getFirstTrackPointId(long trackId) {
        if (trackId < 0) {
            return -1L;
        }
        Cursor cursor = null;
        try {
            String selection = TrackPointsColumns._ID + "=(select min(" + TrackPointsColumns._ID + ") from "
                    + TrackPointsColumns.TABLE_NAME + " WHERE " + TrackPointsColumns.TRACKID + "=?)";
            String[] selectionArgs = new String[]{Long.toString(trackId)};
            cursor = getTrackPointCursor(new String[]{TrackPointsColumns._ID}, selection, selectionArgs,
                    TrackPointsColumns._ID);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(TrackPointsColumns._ID));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1L;
    }

    @Override
    public long getLastTrackPointId(long trackId) {
        if (trackId < 0) {
            return -1L;
        }
        Cursor cursor = null;
        try {
            String selection = TrackPointsColumns._ID + "=(select max(" + TrackPointsColumns._ID + ") from "
                    + TrackPointsColumns.TABLE_NAME + " WHERE " + TrackPointsColumns.TRACKID + "=?)";
            String[] selectionArgs = new String[]{Long.toString(trackId)};
            cursor = getTrackPointCursor(new String[]{TrackPointsColumns._ID}, selection, selectionArgs,
                    TrackPointsColumns._ID);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(TrackPointsColumns._ID));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1L;
    }

    @Override
    public Location getFirstValidTrackPoint(long trackId) {
        if (trackId < 0) {
            return null;
        }
        String selection = TrackPointsColumns._ID + "=(select min(" + TrackPointsColumns._ID + ") from "
                + TrackPointsColumns.TABLE_NAME + " WHERE " + TrackPointsColumns.TRACKID + "=? AND "
                + TrackPointsColumns.LATITUDE + "<=" + MAX_LATITUDE + ")";
        String[] selectionArgs = new String[]{Long.toString(trackId)};
        return findTrackPointBy(selection, selectionArgs);
    }

    @Override
    public Location getLastValidTrackPoint(long trackId) {
        if (trackId < 0) {
            return null;
        }
        String selection = TrackPointsColumns._ID + "=(select max(" + TrackPointsColumns._ID + ") from "
                + TrackPointsColumns.TABLE_NAME + " WHERE " + TrackPointsColumns.TRACKID + "=? AND "
                + TrackPointsColumns.LATITUDE + "<=" + MAX_LATITUDE + ")";
        String[] selectionArgs = new String[]{Long.toString(trackId)};
        return findTrackPointBy(selection, selectionArgs);
    }

    @Override
    public Location getLastValidTrackPoint() {
        String selection = TrackPointsColumns._ID + "=(select max(" + TrackPointsColumns._ID + ") from "
                + TrackPointsColumns.TABLE_NAME + " WHERE " + TrackPointsColumns.LATITUDE + "<=" + MAX_LATITUDE + ")";
        return findTrackPointBy(selection, null);
    }

    @Override
    public Cursor getTrackPointCursor(long trackId, long startTrackPointId, int maxLocations, boolean descending) {
        if (trackId < 0) {
            return null;
        }

        String   selection;
        String[] selectionArgs;
        if (startTrackPointId >= 0) {
            String comparison = descending ? "<=" : ">=";
            selection = TrackPointsColumns.TRACKID + "=? AND " + TrackPointsColumns._ID + comparison + "?";
            selectionArgs = new String[]{Long.toString(trackId), Long.toString(startTrackPointId)};
        } else {
            selection = TrackPointsColumns.TRACKID + "=?";
            selectionArgs = new String[]{Long.toString(trackId)};
        }

        String sortOrder = TrackPointsColumns._ID;
        if (descending) {
            sortOrder += " DESC";
        }
        if (maxLocations >= 0) {
            sortOrder += " LIMIT " + maxLocations;
        }
        return getTrackPointCursor(null, selection, selectionArgs, sortOrder);
    }

    @Override
    public LocationIterator getTrackPointLocationIterator(final long trackId, final long startTrackPointId,
                                                          final boolean descending, final LocationFactory locationFactory) {
        if (locationFactory == null) {
            throw new IllegalArgumentException("locationFactory is null");
        }
        return new LocationIterator() {
            private long lastTrackPointId = -1L;
            private Cursor cursor = getCursor(startTrackPointId);
            private final CachedTrackPointsIndexes indexes = cursor != null ? new CachedTrackPointsIndexes(cursor)
                    : null;

            /**
             * Gets the track point cursor.
             *
             * @param trackPointId
             *            the starting track point id
             */
            private Cursor getCursor(long trackPointId) {
                return getTrackPointCursor(trackId, trackPointId, defaultCursorBatchSize, descending);
            }

            /**
             * Advances the cursor to the next batch. Returns true if
             * successful.
             */
            private boolean advanceCursorToNextBatch() {
                long trackPointId = lastTrackPointId == -1L ? -1L : lastTrackPointId + (descending ? -1 : 1);
                Log.d(TAG, "Advancing track point id: " + trackPointId);
                cursor.close();
                cursor = getCursor(trackPointId);
                return cursor != null;
            }

            @Override
            public long getLocationId() {
                return lastTrackPointId;
            }

            @Override
            public boolean hasNext() {
                if (cursor == null) {
                    return false;
                }
                if (cursor.isAfterLast()) {
                    return false;
                }
                if (cursor.isLast()) {
                    if (cursor.getCount() != defaultCursorBatchSize) {
                        return false;
                    }
                    return advanceCursorToNextBatch() && !cursor.isAfterLast();
                }
                return true;
            }

            @Override
            public Location next() {
                if (cursor == null) {
                    throw new NoSuchElementException();
                }
                if (!cursor.moveToNext()) {
                    if (!advanceCursorToNextBatch() || !cursor.moveToNext()) {
                        throw new NoSuchElementException();
                    }
                }
                lastTrackPointId = cursor.getLong(indexes.idIndex);
                Location location = locationFactory.createLocation();
                fillTrackPoint(cursor, indexes, location);
                return location;
            }

            @Override
            public void close() {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public Uri insertTrackPoint(Location location, long trackId) {
        return contentResolver.insert(MyTracksProvider.TRACKPOINTS_CONTENT_URI, createContentValues(location, trackId));
    }

    /**
     * Creates the {@link ContentValues} for a {@link Location}.
     *
     * @param location the location
     * @param trackId  the track id
     */
    private ContentValues createContentValues(Location location, long trackId) {
        ContentValues values = new ContentValues();
        values.put(TrackPointsColumns.TRACKID, trackId);
        values.put(TrackPointsColumns.LONGITUDE, (int) (location.getLongitude() * 1E6));
        values.put(TrackPointsColumns.LATITUDE, (int) (location.getLatitude() * 1E6));

        // Hack for Samsung phones that don't properly populate the time field
        long time = location.getTime();
        if (time == 0) {
            time = System.currentTimeMillis();
        }
        values.put(TrackPointsColumns.TIME, time);
        if (location.hasAltitude()) {
            values.put(TrackPointsColumns.ALTITUDE, location.getAltitude());
        }
        if (location.hasAccuracy()) {
            values.put(TrackPointsColumns.ACCURACY, location.getAccuracy());
        }
        if (location.hasSpeed()) {
            values.put(TrackPointsColumns.SPEED, location.getSpeed());
        }
        if (location.hasBearing()) {
            values.put(TrackPointsColumns.BEARING, location.getBearing());
        }

        if (location instanceof MyTracksLocation) {
            MyTracksLocation myTracksLocation = (MyTracksLocation) location;
            if (myTracksLocation.getSensorDataSet() != null) {
                values.put(TrackPointsColumns.SENSOR, myTracksLocation.getSensorDataSet().toByteArray());
            }
        }
        return values;
    }

    /**
     * Fills a track point from a cursor.
     *
     * @param cursor   the cursor pointing to a location.
     * @param indexes  the cached track points indexes
     * @param location the track point
     */
    private void fillTrackPoint(Cursor cursor, CachedTrackPointsIndexes indexes, Location location) {
        location.reset();

        if (!cursor.isNull(indexes.longitudeIndex)) {
            location.setLongitude(((double) cursor.getInt(indexes.longitudeIndex)) / 1E6);
        }
        if (!cursor.isNull(indexes.latitudeIndex)) {
            location.setLatitude(((double) cursor.getInt(indexes.latitudeIndex)) / 1E6);
        }
        if (!cursor.isNull(indexes.timeIndex)) {
            location.setTime(cursor.getLong(indexes.timeIndex));
        }
        if (!cursor.isNull(indexes.altitudeIndex)) {
            location.setAltitude(cursor.getFloat(indexes.altitudeIndex));
        }
        if (!cursor.isNull(indexes.accuracyIndex)) {
            location.setAccuracy(cursor.getFloat(indexes.accuracyIndex));
        }
        if (!cursor.isNull(indexes.speedIndex)) {
            location.setSpeed(cursor.getFloat(indexes.speedIndex));
        }
        if (!cursor.isNull(indexes.bearingIndex)) {
            location.setBearing(cursor.getFloat(indexes.bearingIndex));
        }
        if (location instanceof MyTracksLocation && !cursor.isNull(indexes.sensorIndex)) {
            MyTracksLocation myTracksLocation = (MyTracksLocation) location;
            try {
                myTracksLocation.setSensorDataSet(Sensor.SensorDataSet.parseFrom(cursor.getBlob(indexes.sensorIndex)));
            } catch (InvalidProtocolBufferException e) {
                Log.w(TAG, "Failed to parse sensor data.", e);
            }
        }
    }

    private Location findTrackPointBy(String selection, String[] selectionArgs) {
        Cursor cursor = null;
        try {
            cursor = getTrackPointCursor(null, selection, selectionArgs, TrackPointsColumns._ID);
            if (cursor != null && cursor.moveToNext()) {
                return createTrackPoint(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * Gets a track point cursor.
     *
     * @param projection    the projection
     * @param selection     the selection
     * @param selectionArgs the selection arguments
     * @param sortOrder     the sort order
     */
    private Cursor getTrackPointCursor(String[] projection, String selection,
                                       String[] selectionArgs, String sortOrder) {
        Cursor c = null;
        try {
            c = contentResolver.query(MyTracksProvider.TRACKPOINTS_CONTENT_URI,
                    projection, selection, selectionArgs, sortOrder);
        } catch (SQLiteException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return c;
    }

    /**
     * A cache of track points indexes.
     */
    private static class CachedTrackPointsIndexes {
        public final int idIndex;
        public final int longitudeIndex;
        public final int latitudeIndex;
        public final int timeIndex;
        public final int altitudeIndex;
        public final int accuracyIndex;
        public final int speedIndex;
        public final int bearingIndex;
        public final int sensorIndex;

        public CachedTrackPointsIndexes(Cursor cursor) {
            idIndex = cursor.getColumnIndex(TrackPointsColumns._ID);
            longitudeIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.LONGITUDE);
            latitudeIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.LATITUDE);
            timeIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.TIME);
            altitudeIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.ALTITUDE);
            accuracyIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.ACCURACY);
            speedIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.SPEED);
            bearingIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.BEARING);
            sensorIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.SENSOR);
        }
    }

    /**
     * Sets the default cursor batch size. For testing purpose.
     *
     * @param defaultCursorBatchSize the default cursor batch size
     */
    void setDefaultCursorBatchSize(int defaultCursorBatchSize) {
        this.defaultCursorBatchSize = defaultCursorBatchSize;
    }

    @Override
    public void updateAllPointInTrack(long trackId, String name, List<Location> points) throws Exception {
        Track track = getTrack(trackId);
        if (track != null) {
            String   where         = TrackPointsColumns._ID + ">=? AND " + TrackPointsColumns._ID + "<=?";
            String[] selectionArgs = new String[]{Long.toString(track.startId), Long.toString(track.stopId)};
            contentResolver.delete(MyTracksProvider.TRACKPOINTS_CONTENT_URI, where, selectionArgs);
        }

        track.name = name;
        TripStatisticsUpdater trackTripStatisticsUpdater = new TripStatisticsUpdater(track.getTripStatistics().getStartTime());
        for (Location location : points) {
            try {
                Uri  uri          = insertTrackPoint(location, track.id);
                long trackPointId = Long.parseLong(uri.getLastPathSegment());
                trackTripStatisticsUpdater.addLocation(location, 0);
                updateRecordingTrack(track, trackPointId, LocationUtils.isValidLocation(location), trackTripStatisticsUpdater);
            } catch (SQLiteException e) {
                throw new Exception("更新数据库失败");
            }
        }

        updateTrack(track);
    }

    private void updateRecordingTrack(Track track, long trackPointId, boolean isTrackPointNewAndValid, TripStatisticsUpdater trackTripStatisticsUpdater) {
        if (trackPointId >= 0) {
            if (track.id < 0) {
                track.startId = (trackPointId);
            }
            track.stopId = (trackPointId);
        }
        if (isTrackPointNewAndValid) {
            track.numberOfPoints = (track.numberOfPoints + 1);
        }

        trackTripStatisticsUpdater.updateTime(System.currentTimeMillis());
        track.setTripStatistics(trackTripStatisticsUpdater.getTripStatistics());
        updateTrack(track);
    }

    @Override
    public String getDefaultTitle(String currentName) {
        StringBuffer sb     = new StringBuffer();
        int          counts = 0;
        sb.append(TracksColumns.NAME).append(" LIKE ").append("'%").append("轨迹").append("%'");
        Cursor cursor = getTrackCursor(sb.toString(), null, null);
        try {

            if (null != cursor) {
                counts = cursor.getCount();
                String defaultName = "轨迹" + (counts + 1);
                if (TextUtils.isEmpty(currentName)) {
                    return defaultName;
                }
                if (currentName.startsWith("轨迹")) {
                    return currentName;
                }
                return defaultName;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "轨迹" + counts;
    }

    @Override
    public List<Track> getTracks(String selection, String[] selectionArgs) {
        ArrayList<Track> tracks = new ArrayList<Track>();
        Cursor           cursor = null;
        try {
            cursor = getTrackCursor(null, selection, selectionArgs, TracksColumns._ID + " desc");
            if (cursor != null && cursor.moveToFirst()) {
                tracks.ensureCapacity(cursor.getCount());
                do {
                    tracks.add(createTrack(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return tracks;
    }

    public void updateUploadInfo(Context context, ContentValues values,
                                 String whereClause, String[] whereArgs) {
        contentResolver.update(MyTracksProvider.TRACKS_CONTENT_URI, values, whereClause, whereArgs);
    }


    /**
     * 检测mapplus/app/app.db文件是否为空，如果为空重新创建
     *
     * @return
     */
    private boolean checkDBNotNull() {
        File sdcardDir = Environment.getExternalStorageDirectory();
        File databaseFile = new File(sdcardDir, "mapplus/app/app.db");
        if (!databaseFile.exists()) {
            try {
                // yml 这里可能因为mapplus/app/app.db文件不存在，先保证文件存在
                if (!databaseFile.exists()) {
                    File parentFile = databaseFile.getParentFile();
                    if (!parentFile.exists())
                        parentFile.mkdirs();
                    databaseFile.createNewFile();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "利用ContentResolver操作数据库表检测db文件为空后重新创建错误", e);
            }
        }
        return databaseFile.exists();
    }

}