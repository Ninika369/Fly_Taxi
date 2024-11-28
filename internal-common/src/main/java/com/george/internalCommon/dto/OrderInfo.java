package com.george.internalCommon.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * This class contains all the needed information of an order
 * @author george
 * @since 2024-11-11
 */
@Data
public class OrderInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Order ID
     */
    private Long id;

    /**
     * Passenger ID
     */
    private Long passengerId;

    /**
     * Passenger phone number
     */
    private String passengerPhone;

    /**
     * Driver ID
     */
    private Long driverId;

    /**
     * Driver phone number
     */
    private String driverPhone;

    /**
     * Vehicle ID
     */
    private Long carId;

    /**
     * The type of vehicle
     */
    private String vehicleType;

    /**
     * Administrative district code of the starting point
     */
    private String address;

    /**
     * Order initiation time
     */
    private LocalDateTime orderTime;

    /**
     * Expected time of use
     */
    private LocalDateTime departTime;

    /**
     * Detailed address of the expected departure location
     */
    private String departure;

    /**
     * Longitude of the expected departure location
     */
    private String depLongitude;

    /**
     * Latitude of the expected departure location
     */
    private String depLatitude;

    /**
     * Expected destination
     */
    private String destination;

    /**
     * Longitude of the expected destination
     */
    private String destLongitude;

    /**
     * Latitude of the expected destination
     */
    private String destLatitude;

    /**
     * Coordinate encryption identifier: 1: GCJ-02 (Chinese survey standard), 2: WGS84 (GPS standard), 3: BD-09 (Baidu standard), 4: CGCS2000 (Beidou standard), 0: Others
     */
    private Integer encrypt;

    /**
     * Fare type code
     */
    private String fareType;

    private Integer fareVersion;

    /**
     * Vehicle longitude at the time of receiving the order
     */
    private String receiveOrderCarLongitude;

    /**
     * Vehicle latitude at the time of receiving the order
     */
    private String receiveOrderCarLatitude;

    /**
     * Time of receiving the order, i.e., the time the dispatch was successful
     */
    private LocalDateTime receiveOrderTime;

    /**
     * Driver's license number
     */
    private String licenseId;

    /**
     * Vehicle license plate number
     */
    private String vehicleNo;

    /**
     * Time when the driver departs to pick up the passenger
     */
    private LocalDateTime toPickUpPassengerTime;

    /**
     * Driver's longitude when going to pick up the passenger
     */
    private String toPickUpPassengerLongitude;

    /**
     * Driver's latitude when going to pick up the passenger
     */
    private String toPickUpPassengerLatitude;

    /**
     * Driver's location when going to pick up the passenger
     */
    private String toPickUpPassengerAddress;

    /**
     * Time when the driver arrives at the pick-up point
     */
    private LocalDateTime driverArrivedDepartureTime;

    /**
     * Time when the passenger gets in the car
     */
    private LocalDateTime pickUpPassengerTime;

    /**
     * Longitude when the passenger gets in the car
     */
    private String pickUpPassengerLongitude;

    /**
     * Latitude when the passenger gets in the car
     */
    private String pickUpPassengerLatitude;

    /**
     * Time when the passenger gets off the car
     */
    private LocalDateTime passengerGetoffTime;

    /**
     * Longitude when the passenger gets off the car
     */
    private String passengerGetoffLongitude;

    /**
     * Latitude when the passenger gets off the car
     */
    private String passengerGetoffLatitude;

    /**
     * Order cancellation time
     */
    private LocalDateTime cancelTime;

    /**
     * Cancellation initiator: 1: Passenger, 2: Driver, 3: Platform company
     */
    private Integer cancelOperator;

    /**
     * Cancellation type code: 1: Passenger cancels in advance, 2: Driver cancels in advance, 3: Platform company cancels, 4: Passenger breaches contract and cancels, 5: Driver breaches contract and cancels
     */
    private Integer cancelTypeCode;

    /**
     * Distance traveled with passengers (in meters)
     */
    private Long driveMile;

    /**
     * Time spent with passengers (in minutes)
     */
    private Long driveTime;

    /**
     * Order status: 1: Order started, 2: Driver accepted the order, 3: On the way to pick up passenger, 4: Driver arrived at the passenger's starting point, 5: Passenger got in, driver started the journey, 6: Arrived at destination, journey ended, not paid, 7: Payment initiated, 8: Payment completed, 9: Order cancelled
     */
    private Integer orderStatus;

    private Double price;

    /**
     * Creation time
     */
    private LocalDateTime gmtCreate;

    /**
     * Modification time
     */
    private LocalDateTime gmtModified;

}
