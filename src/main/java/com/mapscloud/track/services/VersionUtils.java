package com.mapscloud.track.services;

import android.content.Context;

import com.mapscloud.track.R;


/**
 * Created by fhuya on 11/12/14.
 */
public class VersionUtils {

    /**
     * @param context
     * @return
     */
    public static int getCoreLibVersion(Context context){
        return context.getResources().getInteger(R.integer.track_core_version);
    }

    //Prevent instantiation.
    private VersionUtils(){}
}
