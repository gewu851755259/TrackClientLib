package com.mapscloud.track.android.interfaces;


/**
 * 轨迹服务控制台监听，返回客户端服务启动状态
 */
public interface TowerListener {

    void onTrackConnected();

    void onTrackDisconnected();

}
