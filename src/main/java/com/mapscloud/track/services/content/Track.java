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

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

import com.mapscloud.track.services.basic.BasicRecordBean;

import java.util.ArrayList;

/**
 * A track.
 * 
 * @author Leif Hendrik Wilden
 * @author Rodrigo Damazio
 */
public class Track extends BasicRecordBean implements Parcelable {

    private TripStatistics tripStatistics = new TripStatistics();

    // Location points (which may not have been loaded)
    private ArrayList<Location> locations = new ArrayList<Location>();

    public Track() {
    }

    private Track(Parcel in) {

        appId = in.readString();
        appName = in.readString();

        id = in.readLong();
        name = in.readString();
        description = in.readString();
        category = in.readString();
        trackType = in.readInt();
        startId = in.readLong();
        stopId = in.readLong();
        numberOfPoints = in.readInt();
        mapId = in.readString();
        tableId = in.readString();
        icon = in.readString();
        driveId = in.readString();
        modifiedTime = in.readLong();
        sharedWithMe = in.readByte() == 1;
        sharedOwner = in.readString();
        favorite = in.readInt();
        serverDbId = in.readLong();

        ClassLoader classLoader = getClass().getClassLoader();
        tripStatistics = in.readParcelable(classLoader);

        for (int i = 0; i < numberOfPoints; ++i) {
            Location location = in.readParcelable(classLoader);
            locations.add(location);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

        dest.writeString(appId);
        dest.writeString(appName);

        dest.writeLong(id);
        dest.writeString(name);
        dest.writeString(description);
        dest.writeString(category);
        dest.writeInt(trackType);
        dest.writeLong(startId);
        dest.writeLong(stopId);
        dest.writeInt(numberOfPoints);
        dest.writeString(mapId);
        dest.writeString(tableId);
        dest.writeString(icon);
        dest.writeString(driveId);
        dest.writeLong(modifiedTime);
        dest.writeByte((byte) (sharedWithMe ? 1 : 0));
        dest.writeString(sharedOwner);
        dest.writeInt(favorite);
        dest.writeLong(serverDbId);

        dest.writeParcelable(tripStatistics, 0);
        for (int i = 0; i < numberOfPoints; ++i) {
            dest.writeParcelable(locations.get(i), 0);
        }
    }

    public static final Parcelable.Creator<Track> CREATOR = new Parcelable.Creator<Track>() {
        @Override
        public Track createFromParcel(Parcel in) {
            return new Track(in);
        }

        @Override
        public Track[] newArray(int size) {
            return new Track[size];
        }
    };

    public TripStatistics getTripStatistics() {
        return tripStatistics;
    }

    public void setTripStatistics(TripStatistics tripStatistics) {
        this.tripStatistics = tripStatistics;
    }

    public void addLocation(Location location) {
        locations.add(location);
    }

    public ArrayList<Location> getLocations() {
        return locations;
    }

    public void setLocations(ArrayList<Location> locations) {
        this.locations = locations;
    }

}
