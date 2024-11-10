package com.george.internalCommon.constant;

/**
 * @Author: George Sun
 * @Date: 2024-10-24-20:09
 * @Description: com.george.internalCommon.constant
 */
public class AmapConfigConstant {

    // route planning address
    public static final String DIRECTION_URL = "https://restapi.amap.com/v3/direction/driving?";

    // the url to find the codes of districts
    public static final String DISTRICT_URL = "https://restapi.amap.com/v3/config/district?";

    // the url to add new service for amap
    public static final String SERVICE_ADD_URL = "https://tsapi.amap.com/v1/track/service/add";

    // add a terminal in Amap Server
    public static final String TERMINAL_ADD = "https://tsapi.amap.com/v1/track/terminal/add";

    // the url to conduct around search in Amap Server
    public static final String TERMINAL_AROUNDSEARCH = "https://tsapi.amap.com/v1/track/terminal/aroundsearch";

    // the url to search the trace in Amap Server
    public static final String TERMINAL_TRSEARCH = "https://tsapi.amap.com/v1/track/terminal/trsearch";

    // the url that adds a trace in a terminal
    public static final String TRACK_ADD = "https://tsapi.amap.com/v1/track/trace/add";

    // the url that adds points to a trace in a terminal
    public static final String POINT_UPLOAD = "https://tsapi.amap.com/v1/track/point/upload";

    // route planning keys
    public static final String STATUS = "status";
    public static final String ROUTE = "route";
    public static final String PATHS = "paths";
    public static final String DISTANCE = "distance";
    public static final String DURATION = "duration";

    public static final String DISTRICTS = "districts";

    public static final String ADCODE = "adcode";

    public static final String NAME = "name";

    public static final String LEVEL = "level";

    public static final String STREET = "street";

}
