package com.george.internalCommon.constant;

/**
 * @Author: George Sun
 * @Date: 2024-11-03-13:06
 * @Description: com.george.internalCommon.constant
 */
public class DriverCarConstant {

    // the relationships between the driver and the car
    public static int DRIVER_CAR_BIND = 1;

    public static int DRIVER_CAR_UNBIND = 2;

    // the status of driver(whether he is still a driver): 1 valid; 0 invalid
    public static int DRIVER_STATE_VALID = 1;

    public static int DRIVER_STATE_INVALID = 0;

    // The existence of driver
    public static int DRIVER_EXISTS = 1;

    public static int DRIVER_NOT_EXISTS = 0;

    // the working status of driver
    public static int DRIVER_WORK_STATUS_START = 1;

    public static int DRIVER_WORK_STATUS_STOP = 0;

    public static int DRIVER_WORK_STATUS_SUSPEND = 2;

}
