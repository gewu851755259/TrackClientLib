package com.mapscloud.track.services.basic;

public abstract class BasicRecordBean {

    public static final int FAVORITE = 1;

    // app info
    public String appId;
    public String appName;

    // text begin
    public String name;
    public long id = -1L;
    public double lon;
    public double lat;
    public double altim;
    public String category;
    public String from;
    public int type;

    public String title;
    public String content;
    public String address;
    public int zoomlevel;
    public int favorite;
    public String time;

    public long trackid;
    public long waypointid;
    public long serverDbId;
    // text end

    // sound begin
    public String path; // 路径
    // sound end

    // image begin
    public double azim;
    // image end

    // graffiti begin
    public String screenPointPath;
    public String screenImagePath;
    // graffiti begin

    // track begin
    public String description = "";
    public int trackType = 0;
    public long startId = -1L;
    public long stopId = -1L;

    /*
     * The number of location points (present even if the points themselves are
     * not loaded)
     */
    public int numberOfPoints = 0;
    public String mapId = "";
    public String tableId = "";
    public String icon = "";
    public String driveId = "";
    public long modifiedTime = -1L;
    public boolean sharedWithMe = false;
    public String sharedOwner = "";
    // track end

    /*
     * for upload
     */
    public int uploadState = 0;
    public int totalSize = 0;
    public int uploadSize = 0;
    public int shareScope;
    public String shareKey;

    public static final int US_NONE = 0;
    public static final int US_PENDING = 1;
    public static final int US_UPLOADING = 2;
    public static final int US_COMPLETED = 3;

    public static final int SCOPE_PRIVATE = 1;
    public static final int SCOPE_PUBLIC = 2;
    public static final int SCOPE_SHARE = 3;

    @Override
    public String toString() {
        return "BasicRecordBean{" +
                "appId='" + appId + '\'' +
                ", appName='" + appName + '\'' +
                ", name='" + name + '\'' +
                ", id=" + id +
                ", lon=" + lon +
                ", lat=" + lat +
                ", altim=" + altim +
                ", category='" + category + '\'' +
                ", from='" + from + '\'' +
                ", type=" + type +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", address='" + address + '\'' +
                ", zoomlevel=" + zoomlevel +
                ", favorite=" + favorite +
                ", time='" + time + '\'' +
                ", trackid=" + trackid +
                ", waypointid=" + waypointid +
                ", serverDbId=" + serverDbId +
                ", path='" + path + '\'' +
                ", azim=" + azim +
                ", screenPointPath='" + screenPointPath + '\'' +
                ", screenImagePath='" + screenImagePath + '\'' +
                ", description='" + description + '\'' +
                ", trackType=" + trackType +
                ", startId=" + startId +
                ", stopId=" + stopId +
                ", numberOfPoints=" + numberOfPoints +
                ", mapId='" + mapId + '\'' +
                ", tableId='" + tableId + '\'' +
                ", icon='" + icon + '\'' +
                ", driveId='" + driveId + '\'' +
                ", modifiedTime=" + modifiedTime +
                ", sharedWithMe=" + sharedWithMe +
                ", sharedOwner='" + sharedOwner + '\'' +
                ", uploadState=" + uploadState +
                ", totalSize=" + totalSize +
                ", uploadSize=" + uploadSize +
                ", shareScope=" + shareScope +
                ", shareKey='" + shareKey + '\'' +
                '}';
    }
}
