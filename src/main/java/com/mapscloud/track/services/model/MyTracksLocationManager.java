/*
 * Copyright 2012 Google Inc.
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

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.mapscloud.track.services.utils.GoogleLocationUtils;

import timber.log.Timber;

/**
 * My Tracks Location Manager. Applies Google location settings before allowing
 * access to {@link LocationManager}.
 *
 * @author Jimmy Shih
 */
public class MyTracksLocationManager {

    public static final int PRIORITY_HIGH_ACCURACY = 0;
    public static final int PRIORITY_BALANCED_POWER_ACCURACY = 1;
    public static final int PRIORITY_LOW_POWER = 2;
    public static final int PRIORITY_NO_POWER = 3;

    String currentProvider = LocationManager.PASSIVE_PROVIDER;

    private static final String TAG = MyTracksLocationManager.class.getSimpleName();

    private static final String GOOGLE_SETTINGS_CONTENT_URI = "content://com.google.settings/partner";
    private static final String USE_LOCATION_FOR_SERVICES = "use_location_for_services";

    // User has agreed to use location for Google services.

    static final String USE_LOCATION_FOR_SERVICES_ON = "1";

    private static final String NAME = "name";
    private static final String VALUE = "value";

    private final LocationManager locationManager;
    private final ContentResolver contentResolver;
    private final GoogleSettingsObserver observer;
    private boolean isAvailable;
    private boolean isAllowed;

    public MyTracksLocationManager(Context context) {
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        contentResolver = context.getContentResolver();
        observer = new GoogleSettingsObserver();
        isAvailable = GoogleLocationUtils.isAvailable(context);
        isAllowed = isUseLocationForServicesOn();

        try {
            contentResolver.registerContentObserver(
                    Uri.parse(GOOGLE_SETTINGS_CONTENT_URI + "/" + USE_LOCATION_FOR_SERVICES), false, observer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes the {@link MyTracksLocationManager}.
     */
    public void close() {
        contentResolver.unregisterContentObserver(observer);
    }

    /**
     * Observer for Google location settings.
     *
     * @author Jimmy Shih
     */
    private class GoogleSettingsObserver extends ContentObserver {

        public GoogleSettingsObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            isAllowed = isUseLocationForServicesOn();
        }
    }

    /**
     * Returns true if allowed to access the location manager. Returns true if
     * there is no Google location settings or the Google location settings
     * allows access to location data.
     */
    public boolean isAllowed() {
        return isAllowed;
    }

    /**
     * @see LocationManager#isProviderEnabled(String)
     */
    public boolean isProviderEnabled(String provider) {
        return isAllowed ? locationManager.isProviderEnabled(provider) : false;
    }

    /**
     * @see LocationManager#getProvider(String)
     */
    public LocationProvider getProvider(String name) {
        return isAllowed ? locationManager.getProvider(name) : null;
    }

    /**
     * @see LocationManager#getLastKnownLocation(String)
     */
    @SuppressLint("MissingPermission")
    public Location getLastKnownLocation(String provider) {
        return isAllowed ? locationManager.getLastKnownLocation(provider) : null;
    }

    /**
     * @see LocationManager#requestLocationUpdates(String,
     * long, float, LocationListener)
     */
    @SuppressLint("MissingPermission")
    public void requestLocationUpdates(String provider, long minTime, float minDistance,
                                       LocationListener listener, Looper looper) {
        if (!LocationManager.PASSIVE_PROVIDER.equals(provider)) {
            currentProvider = getBestProvider(PRIORITY_HIGH_ACCURACY);
            Timber.e("currentProvider = %s", currentProvider);
        }
        locationManager.requestLocationUpdates(currentProvider, minTime, minDistance,
                listener, looper == null ? Looper.getMainLooper() : looper);
        // Start network provider along with gps
        if (shouldStartNetworkProvider(PRIORITY_HIGH_ACCURACY)) {
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        minTime, minDistance,
                        listener, looper);
            } catch (IllegalArgumentException iae) {
                iae.printStackTrace();
            }
        }
    }

    /**
     * @param listener
     * @see LocationManager#removeUpdates(LocationListener)
     */
    @SuppressLint("MissingPermission")
    public void removeUpdates(LocationListener listener) {
        locationManager.removeUpdates(listener);
    }

    /**
     * Returns true if the Google location settings for
     * {@link #USE_LOCATION_FOR_SERVICES} is on.
     */
    private boolean isUseLocationForServicesOn() {
        if (!isAvailable) {
            return true;
        }
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(Uri.parse(GOOGLE_SETTINGS_CONTENT_URI), new String[]{VALUE}, NAME + "=?",
                    new String[]{USE_LOCATION_FOR_SERVICES}, null);
            if (cursor != null && cursor.moveToNext()) {
                return USE_LOCATION_FOR_SERVICES_ON.equals(cursor.getString(0));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to read " + USE_LOCATION_FOR_SERVICES, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }


    private String getBestProvider(int priority) {
        String provider = null;
        // Pick best provider only if user has not explicitly chosen passive mode
        if (priority != PRIORITY_NO_POWER) {
            provider = locationManager.getBestProvider(getCriteria(priority), true);
        }
        return provider != null ? provider : LocationManager.PASSIVE_PROVIDER;
    }

    @VisibleForTesting
    static Criteria getCriteria(int priority) {
        Criteria criteria = new Criteria();
        criteria.setAccuracy(priorityToAccuracy(priority));
        criteria.setCostAllowed(true);
        criteria.setPowerRequirement(priorityToPowerRequirement(priority));

        Log.i(TAG, "priority = " + priority +
                ", priorityToAccuracy = " + priorityToAccuracy(priority) +
                ", priorityToPowerRequirement = " + priorityToPowerRequirement(priority));
        return criteria;
    }

    private static int priorityToAccuracy(int priority) {
        switch (priority) {
            case PRIORITY_HIGH_ACCURACY:
            case PRIORITY_BALANCED_POWER_ACCURACY:
                return Criteria.ACCURACY_FINE;
            case PRIORITY_LOW_POWER:
            case PRIORITY_NO_POWER:
            default:
                return Criteria.ACCURACY_COARSE;
        }
    }

    private static int priorityToPowerRequirement(int priority) {
        switch (priority) {
            case PRIORITY_HIGH_ACCURACY:
                return Criteria.POWER_HIGH;
            case PRIORITY_BALANCED_POWER_ACCURACY:
                return Criteria.POWER_MEDIUM;
            case PRIORITY_LOW_POWER:
            case PRIORITY_NO_POWER:
            default:
                return Criteria.POWER_LOW;
        }
    }

    private boolean shouldStartNetworkProvider(int priority) {
        return (priority == PRIORITY_HIGH_ACCURACY
                || priority == PRIORITY_BALANCED_POWER_ACCURACY)
                && currentProvider.equals(LocationManager.GPS_PROVIDER);
    }


}
