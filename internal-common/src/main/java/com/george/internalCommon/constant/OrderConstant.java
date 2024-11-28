package com.george.internalCommon.constant;

// This class contains all the status of an order
public class OrderConstant {
    // 0：Invalid order
    public static final int ORDER_INVALID = 0;
    // 1：when the order starts
    public static final int ORDER_START = 1;
    // 2：Order start
    public static final int DRIVER_RECEIVE_ORDER = 2;
    // 3：To pick up passenger
    public static final int DRIVER_TO_PICK_UP_PASSENGER = 3;
    // 4：The driver arrives at the passenger starting point
    public static final int DRIVER_ARRIVED_DEPARTURE = 4;
    // 5: The passengers boarded and the driver began his journey
    public static final int PICK_UP_PASSENGER = 5;
    // 6：Destination arrived, trip ended, unpaid
    public static final int PASSENGER_GETOFF = 6;
    // 7: Initiate collection
    public static final int TO_START_PAY = 7;
    // 8: Payment completed
    public static final int SUCCESS_PAY = 8;
    // 9. Order canceled
    public static final int ORDER_CANCEL = 9;

    /**
     * Cancel codes:
     * 1: Early cancellation of passenger
     * 2: Driver early cancellation
     * 3: Cancellation of platform company
     * 4; Cancellation of passenger default
     * 5: Cancellation of driver default
     */


    public static final int CANCEL_PASSENGER_BEFORE = 1;


    public static final int CANCEL_DRIVER_BEFORE = 2;


    public static final int CANCEL_PLATFORM_BEFORE = 3;


    public static final int CANCEL_PASSENGER_ILLEGAL = 4;


    public static final int CANCEL_DRIVER_ILLEGAL = 5;


}
