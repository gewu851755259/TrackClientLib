/*
 * Copyright 2009 Google Inc.
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

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

import com.mapscloud.track.services.content.TripStatistics;

/**
 * A waypoint.
 * 
 * @author Leif Hendrik Wilden
 * @author Rodrigo Damazio
 */
public final class Waypoint implements Parcelable {

    public static enum WaypointType {
        WAYPOINT, STATISTICS;
    }

    public long id = -1L;
    public String name = "";
    public String description = "";
    public String category = "";
    public String icon = "";
    public long trackId = -1L;
    public WaypointType type = WaypointType.WAYPOINT;
    public double length = 0.0;
    public long duration = 0;
    public long startId = -1L;
    public long stopId = -1L;
    public Location location = null;
    public TripStatistics tripStatistics = null;
    public long recordMediaId = -1L;
    public int recordMediaType = -1;

    public Waypoint() {
    }

    public Waypoint(String name, String description, String category, String icon, long trackId, WaypointType type,
            double length, long duration, long startId, long stopId, Location location, TripStatistics tripStatistics) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.icon = icon;
        this.trackId = trackId;
        this.type = type;
        this.length = length;
        this.duration = duration;
        this.startId = startId;
        this.stopId = stopId;
        this.location = location;
        this.tripStatistics = tripStatistics;
    }

    private Waypoint(Parcel source) {
        id = source.readLong();
        name = source.readString();
        description = source.readString();
        category = source.readString();
        icon = source.readString();
        trackId = source.readLong();
        type = WaypointType.values()[source.readInt()];
        length = source.readDouble();
        duration = source.readLong();
        startId = source.readLong();
        stopId = source.readLong();

        ClassLoader classLoader = getClass().getClassLoader();
        byte hasLocation = source.readByte();
        if (hasLocation > 0) {
            location = source.readParcelable(classLoader);
        }
        byte hasStats = source.readByte();
        if (hasStats > 0) {
            tripStatistics = source.readParcelable(classLoader);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(name);
        dest.writeString(description);
        dest.writeString(category);
        dest.writeString(icon);
        dest.writeLong(trackId);
        dest.writeInt(type.ordinal());
        dest.writeDouble(length);
        dest.writeLong(duration);
        dest.writeLong(startId);
        dest.writeLong(stopId);
        dest.writeByte(location == null ? (byte) 0 : (byte) 1);
        if (location != null) {
            dest.writeParcelable(location, 0);
        }
        dest.writeByte(tripStatistics == null ? (byte) 0 : (byte) 1);
        if (tripStatistics != null) {
            dest.writeParcelable(tripStatistics, 0);
        }
    }

    public static final Creator<Waypoint> CREATOR = new Creator<Waypoint>() {
        @Override
        public Waypoint createFromParcel(Parcel in) {
            return new Waypoint(in);
        }

        @Override
        public Waypoint[] newArray(int size) {
            return new Waypoint[size];
        }
    };
}
