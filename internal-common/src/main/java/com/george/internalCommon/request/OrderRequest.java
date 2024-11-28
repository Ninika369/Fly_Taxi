package com.george.internalCommon.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * This class contains information of order that needed to be packed in a request
 */
@Data
public class OrderRequest {
    /**
     * order ID
     */
    private Long orderId;

    // Passenger ID
    private Long passengerId;

    // Passenger Phone
    private String passengerPhone;

    // Order administrative area
    private String address;

    // departure time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime departTime;

    // order time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime orderTime;

    // departure address
    private String departure;

    // departure longitude
    private String depLongitude;

    // departure latitude
    private String depLatitude;

    // destination address
    private String destination;

    // destination longitude
    private String destLongitude;

    // destination latitude
    private String destLatitude;

    // encrypting code
    private Integer encrypt;

    // fare type code
    private String fareType;

    // fare version code
    private Integer fareVersion;

    // Request device unique code
    private String deviceCode;

    /**
     * Driver to pick up passengers departure time
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime toPickUpPassengerTime;

    /**
     * Driver's to-pick-up passengers departure time
     */
    private String toPickUpPassengerLongitude;

    /**
     * When to pick up passengers, the driver's latitude
     */
    private String toPickUpPassengerLatitude;

    /**
     * When to pick up passengers, the driver's address
     */
    private String toPickUpPassengerAddress;

    /**
     * When to pick up passengers, the driver's longitude
     */
    private String pickUpPassengerLongitude;

    /**
     * When receiving passengers, passengers board latitude
     */
    private String pickUpPassengerLatitude;

    /**
     * Passenger alighting longitude
     */
    private String passengerGetoffLongitude;

    /**
     * Passenger alighting latitude
     */
    private String passengerGetoffLatitude;

    /**
     * the type of vehicle
     */
    private String vehicleType;


}