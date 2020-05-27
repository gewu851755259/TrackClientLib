# 轨迹记录服务

## 一、 介绍

**以进程间通信的形式将轨迹定位点记录在/mapplus/app/app.db文件内的三张SQLite表中。**

**1. tracks表：开启轨迹记录后，在该表内增加一条轨迹记录，生成对应的轨迹id。**

**2. trackpoints表：有了轨迹id后，通过定位，不断的将定位点存放在该表中，且对应正确的轨迹id。**

**3. waypoints表：轨迹上的特殊点的记录，包括起点、终点、采集点。**


## 二、 使用

### 2.1 工具类 TracksServiceUtils

唯一工具类 **TracksServiceUtils**，单例形式, 在任何地方使用其对象，都只能是下面形式：

    TracksServiceUtils.getTracksServiceUtilsInstance(context);

不可用全局变量记录该单例对象，因为根据需求一个app只可以启动一条轨迹记录，当这条记录结束时，会销毁轨迹记录服务，目前销毁的方法只能将单例对象置为**null**，所有如果记录了全局变量，将不能及时刷新该对象而导致再次使用它时使程序崩溃。

### 2.2 工具类使用

1. 开启轨迹服务

        // 实例化工具类对象后，便会绑定轨迹记录服务
		// 并检测该app之前是否有未停止的轨迹记录，如果有，可以得到这条轨迹记录的轨迹id、是否暂停状态
        TracksServiceUtils.getTracksServiceUtilsInstance(context);
 

2. 开始轨迹记录
   
        // 开始轨迹记录，如果该app之前没有正在进行的轨迹记录，会新建一条轨迹记录
		// 如果该app之前有正在进行的轨迹记录，不论该记录是暂停还是继续，都将继续记录
        TracksServiceUtils.getTracksServiceUtilsInstance(context).starNewTrack();

3. 暂停/继续轨迹记录

		// 暂停/继续轨迹记录，会根据内部记录值来确定是暂停还是继续
        TracksServiceUtils.getTracksServiceUtilsInstance(context).pauseOrResumeTracking();

4. 停止轨迹记录

		// 停止轨迹记录，停止时会判断轨迹是否为空，为空将不记录在表中，并且轨迹服务断开与该app的绑定，销毁单例对象
        TracksServiceUtils.getTracksServiceUtilsInstance(context).stopTracking();

5. 当前记录轨迹暂停状态获取

		// 返回boolean值，true是暂停，false为非暂停
        TracksServiceUtils.getTracksServiceUtilsInstance(context).isPause();

6. 当前轨迹记录的id

        // 如果该app当前没有开启的轨迹记录，返回id为-1
        TracksServiceUtils.getTracksServiceUtilsInstance(context).getmTrackId();

### 2.3 当前轨迹的变化

        // 实现接口 TrackDataListener，下面是接口内方法介绍
        void onLocationStateChanged(LocationState locationState);        // 定位状态发生改变
        
		void onLocationChanged(Location location);                       // 位置发生改变
        
        void onHeadingChanged(double heading);                           // 方向发生改变，We don't care. 不需要实现
        
		void onSelectedTrackChanged(Track track);                        // 地图显示轨迹在此方法内实现
        
        void onTrackUpdated(Track track);                                // We don't care.
        
        void clearTrackPoints();                                         // 清除地图上轨迹点
        
        void onSampledInTrackPoint(Location location);                   // Add a location.
        
        void onSampledOutTrackPoint(Location location);                  // We don't care.
        
        void onSegmentSplit(Location location);                          // Adds a segment split.
        
        void onNewTrackPointsDone();                                     // 新的轨迹点添加完成
        
        void clearWaypoints();                                           // 清除所有采集点
    
        void onNewWaypoint(Waypoint waypoint);                           // 新的采集点添加
    
        void onNewWaypointsDone();                                       // 新的采集点添加完成
    
        boolean onMetricUnitsChanged(boolean metricUnits);               // We don't care.
    
        boolean onReportSpeedChanged(boolean reportSpeed);               // We don't care.
    
        boolean onMinRecordingDistanceChanged(int minRecordingDistance); // We don't care.


        // 上面监听的注册方法
        mTrackDataHub = TrackDataHub.newInstance(getActivity());
        mTrackDataHub.start();
        mTrackDataHub.registerTrackDataListener(this, EnumSet.of(TrackDataType.SELECTED_TRACK,
                TrackDataType.WAYPOINTS_TABLE, TrackDataType.SAMPLED_IN_TRACK_POINTS_TABLE, TrackDataType.LOCATION));
        mTrackDataHub.loadTrack(mTrack.id);

 **具体使用可参考 JYDXT 项目中的 TracksMapFragment.java 文件**

## 三、 问题记录

### 3.1 轨迹表中增加了记录这条轨迹的应用名称和包名两个字段，经常导致向此表中插入数据失败

简单解决：删除掉mapplus/app/app.db，重新运行

代码解决：考虑数据库表升级

### 3.2 目标版本设为29后，在新建轨迹记录时利用ContentResolver向轨迹记录表插入数据返回Uri为null，导致空指针异常

问题原因：创建app.db文件失败，报读写权限异常

解决: application属性中增加拓展文件可读

   android:requestLegacyExternalStorage="true"