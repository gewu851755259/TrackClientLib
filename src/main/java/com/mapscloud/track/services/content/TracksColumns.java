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

package com.mapscloud.track.services.content;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Constants for the tracks table.
 *
 * @author Leif Hendrik Wilden
 */
public interface TracksColumns extends BaseColumns {

    String TABLE_NAME = "tracks";

    /**
     * Tracks provider uri.
     */
//    Uri CONTENT_URI = Uri.parse("content://com.stmap.app.mytracks/tracks");
    Uri CONTENT_URI = Uri.parse("content://com.yml.localtrack/tracks");

    /**
     * Track content type.
     */
    String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.track";

    /**
     * Track id content type.
     */
    String CONTENT_ITEMTYPE = "vnd.android.cursor.item/vnd.google.track";

    /**
     * Tracks table default sort order.
     */
    String DEFAULT_SORT_ORDER = "_id";

    // Columns
    String NAME          = "name"; // track name
    String DESCRIPTION   = "description"; // track description
    String CATEGORY      = "category"; // track activity type
    String TRACKTYPE     = "tracktype"; // track activity type
    String STARTID       = "startid"; // first track point id
    String STOPID        = "stopid"; // last track point id
    String STARTTIME     = "starttime"; // track start time
    String STOPTIME      = "stoptime"; // track stop time
    String NUMPOINTS     = "numpoints"; // number of track
    // points
    String TOTALDISTANCE = "totaldistance"; // total
    // distance
    String TOTALTIME     = "totaltime"; // total time
    String MOVINGTIME    = "movingtime"; // moving time
    String MINLAT        = "minlat"; // minimum latitude
    String MAXLAT        = "maxlat"; // maximum latitude
    String MINLON        = "minlon"; // minimum longitude
    String MAXLON        = "maxlon"; // maximum longitude
    String AVGSPEED      = "avgspeed"; // average speed

    // average moving speed
    String AVGMOVINGSPEED = "avgmovingspeed";
    String MAXSPEED       = "maxspeed"; // maximum speed
    String MINELEVATION   = "minelevation"; // minimum
    // elevation
    String MAXELEVATION   = "maxelevation"; // maximum
    // elevation
    String ELEVATIONGAIN  = "elevationgain"; // elevation
    // gain
    String MINGRADE       = "mingrade"; // minimum grade
    String MAXGRADE       = "maxgrade"; // maximum grade
    String MAPID          = "mapid"; // Google Maps id
    String TABLEID        = "tableid"; // Google Fusion Tables id
    String ICON           = "icon"; // track activity type icon
    String DRIVEID        = "driveid"; // Google Drive file id

    // Google drive file modified time
    String MODIFIEDTIME = "modifiedtime";

    // 1 if the Google Drive file is from the "Shared with me" directory
    String SHAREDWITHME = "sharedwithme";

    // The owner of the shared with me track
    String SHAREDOWNER       = "sharedOwner";
    String FAVORITE          = "favorite";
    String COLUMN_SERVERDBID = "server_db_id";

    String COLUMN_UPLOAD_STATE = "upload_state";
    String COLUMN_TOTAL_SIZE   = "total_size";
    String COLUMN_UPLOAD_SIZE  = "upload_size";
    String COLUMN_SHARE_SCOPE  = "share_scope";
    String COLUMN_SHARE_KEY    = "share_key";
    String COLUMN_APP_ID       = "app_id";
    String COLUMN_APP_NAME     = "app_name";


    String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" // table
            + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " // id
            + NAME + " STRING, " // name
            + DESCRIPTION + " STRING, " // description
            + CATEGORY + " STRING, " // category
            + TRACKTYPE + " INTEGER, " // category
            + STARTID + " INTEGER, " // start id
            + STOPID + " INTEGER, " // stop id
            + STARTTIME + " INTEGER, " // start time
            + STOPTIME + " INTEGER, " // stop time
            + NUMPOINTS + " INTEGER, " // num points
            + TOTALDISTANCE + " FLOAT, " // total distance
            + TOTALTIME + " INTEGER, " // total time
            + MOVINGTIME + " INTEGER, " // moving time
            + MINLAT + " INTEGER, " // min latitude
            + MAXLAT + " INTEGER, " // max latitude
            + MINLON + " INTEGER, " // min longitude
            + MAXLON + " INTEGER, " // max longitude
            + AVGSPEED + " FLOAT, " // average speed
            + AVGMOVINGSPEED + " FLOAT, " // average moving speed
            + MAXSPEED + " FLOAT, " // max speed
            + MINELEVATION + " FLOAT, " // min elevation
            + MAXELEVATION + " FLOAT, " // max elevation
            + ELEVATIONGAIN + " FLOAT, " // elevation gain
            + MINGRADE + " FLOAT, " // min grade
            + MAXGRADE + " FLOAT, " // max grade
            + MAPID + " STRING, " // map id
            + TABLEID + " STRING, " // table id
            + ICON + " STRING, " // icon
            + DRIVEID + " STRING, " // drive id
            + MODIFIEDTIME + " INTEGER, " // modified time
            + SHAREDWITHME + " INTEGER, " // shared with me
            + SHAREDOWNER + " STRING, "
            + FAVORITE + " INTEGER, "
            + COLUMN_SERVERDBID + " LONG,"

            + COLUMN_UPLOAD_STATE + " INTEGER,"
            + COLUMN_TOTAL_SIZE + " INTEGER,"
            + COLUMN_UPLOAD_SIZE + " INTEGER,"
            + COLUMN_SHARE_SCOPE + " INTEGER,"
            + COLUMN_SHARE_KEY + " TEXT,"
            + COLUMN_APP_ID + " TEXT,"
            + COLUMN_APP_NAME + " TEXT"
            + ");"; // shared
    // owner

    String[] COLUMNS = {_ID, // id
            NAME, // name
            DESCRIPTION, // description
            CATEGORY, // category
            STARTID, // start id
            STOPID, // stop id
            STARTTIME, // start time
            STOPTIME, // stop time
            NUMPOINTS, // num points
            TOTALDISTANCE, // total distance
            TOTALTIME, // total time
            MOVINGTIME, // moving time
            MINLAT, // min latitude
            MAXLAT, // max latitude
            MINLON, // min longitude
            MAXLON, // max longitude
            AVGSPEED, // average speed
            AVGMOVINGSPEED, // average moving speed
            MAXSPEED, // max speed
            MINELEVATION, // min elevation
            MAXELEVATION, // max elevation
            ELEVATIONGAIN, // elevation gain
            MINGRADE, // min grade
            MAXGRADE, // max grade
            MAPID, // map id
            TABLEID, // table id
            ICON, // icon
            DRIVEID, // drive id
            MODIFIEDTIME, // modified time
            SHAREDWITHME, // shared with me
            SHAREDOWNER,// shared owner
            FAVORITE,
            COLUMN_APP_ID,  // 保存轨迹的App包名
            COLUMN_APP_NAME  // 保存轨迹的App名称
    };

    byte[] COLUMN_TYPES = {ContentTypeIds.LONG_TYPE_ID, // id
            ContentTypeIds.STRING_TYPE_ID, // name
            ContentTypeIds.STRING_TYPE_ID, // description
            ContentTypeIds.STRING_TYPE_ID, // category
            ContentTypeIds.LONG_TYPE_ID, // start id
            ContentTypeIds.LONG_TYPE_ID, // stop id
            ContentTypeIds.LONG_TYPE_ID, // start time
            ContentTypeIds.LONG_TYPE_ID, // stop time
            ContentTypeIds.INT_TYPE_ID, // num points
            ContentTypeIds.FLOAT_TYPE_ID, // total distance
            ContentTypeIds.LONG_TYPE_ID, // total time
            ContentTypeIds.LONG_TYPE_ID, // moving time
            ContentTypeIds.INT_TYPE_ID, // min latitude
            ContentTypeIds.INT_TYPE_ID, // max latitude
            ContentTypeIds.INT_TYPE_ID, // min longitude
            ContentTypeIds.INT_TYPE_ID, // max longitude
            ContentTypeIds.FLOAT_TYPE_ID, // average speed
            ContentTypeIds.FLOAT_TYPE_ID, // average moving speed
            ContentTypeIds.FLOAT_TYPE_ID, // max speed
            ContentTypeIds.FLOAT_TYPE_ID, // min elevation
            ContentTypeIds.FLOAT_TYPE_ID, // max elevation
            ContentTypeIds.FLOAT_TYPE_ID, // elevation gain
            ContentTypeIds.FLOAT_TYPE_ID, // min grade
            ContentTypeIds.FLOAT_TYPE_ID, // max grade
            ContentTypeIds.STRING_TYPE_ID, // map id
            ContentTypeIds.STRING_TYPE_ID, // table id
            ContentTypeIds.STRING_TYPE_ID, // icon
            ContentTypeIds.STRING_TYPE_ID, // drive id
            ContentTypeIds.LONG_TYPE_ID, // modified time
            ContentTypeIds.BOOLEAN_TYPE_ID, // shared with me
            ContentTypeIds.STRING_TYPE_ID, ContentTypeIds.INT_TYPE_ID // shared
            // owner
    };

    /**
     * 轨迹表增加是哪个app记录轨迹的字段语句
     */
    String ADD_APP_ID_COLUMN = "ALTER TABLE " + TABLE_NAME
            + " ADD COLUMN " + COLUMN_APP_ID + " varchar(100) default '';";
    String ADD_APP_NAME_COLUMN = "ALTER TABLE " + TABLE_NAME
            + " ADD COLUMN " + COLUMN_APP_NAME + " varchar(100) default '';";

}
