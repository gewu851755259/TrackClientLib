package com.mapscloud.track.services.utils;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * 本地记录当前使用轨迹服务正在记录的工具类
 */
public class LocalPropertiesUtils {

    private static final String APPNAMEFORTRACK = "app_name_for_track";

    public static void loadTrackPropertiesFile() {
        try {
            Properties properties     = new Properties();
            File       propertiesFile = new File(Environment.getExternalStorageDirectory(), "mapplus/app/track.properties");
            if (!propertiesFile.exists()) {
                propertiesFile.createNewFile();
            }
            properties.load(new FileInputStream(propertiesFile));
            if (!properties.containsKey(APPNAMEFORTRACK)) {
                properties.put(APPNAMEFORTRACK, "");
                properties.store(new FileOutputStream(propertiesFile), "");
                Log.e(Constant.TAG, "TracksServiceUtils单例对象被创建，执行了本地track.properties文件清空");
            }
            Log.e(Constant.TAG, "TracksServiceUtils单例对象被创建，本地track.properties文件没有app_name_for_track");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getPropertiesValue() {
        try {
            Properties properties     = new Properties();
            File       propertiesFile = new File(Environment.getExternalStorageDirectory(), "mapplus/app/track.properties");
            properties.load(new FileInputStream(propertiesFile));
            if (properties.containsKey(APPNAMEFORTRACK)) {
                return properties.getProperty(APPNAMEFORTRACK);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }


    public static void setPropertiesValue(String value) {
        try {
            Properties properties     = new Properties();
            File       propertiesFile = new File(Environment.getExternalStorageDirectory(), "mapplus/app/track.properties");
            properties.load(new FileInputStream(propertiesFile));
            if (properties.containsKey(APPNAMEFORTRACK)) {
                properties.setProperty(APPNAMEFORTRACK, value);
                properties.store(new FileOutputStream(propertiesFile), "");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
