package com.george.internalCommon.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author george
 * @since 2024-11-01
 */
@Data
public class Car implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * City where the vehicle is located
     */
    private String address;

    /**
     * Vehicle license plate number
     */
    private String vehicleNo;

    /**
     * Plate color (1: Blue, 2: Yellow, 3: Black, 4: White, 5: Green, 9: Other)
     */
    private String plateColor;

    /**
     * Approved passenger capacity
     */
    private Integer seats;

    /**
     * Vehicle brand
     */
    private String brand;

    /**
     * Vehicle model
     */
    private String model;

    /**
     * Vehicle type
     */
    private String vehicleType;

    /**
     * Owner of the vehicle
     */
    private String ownerName;

    /**
     * Vehicle color (1: White, 2: Black)
     */
    private String vehicleColor;

    /**
     * Engine number
     */
    private String engineId;

    /**
     * Vehicle Identification Number (VIN)
     */
    private String vin;

    /**
     * Vehicle registration date
     */
    private LocalDate certifyDateA;

    /**
     * Fuel type (1: Gasoline, 2: Diesel, 3: Natural Gas, 4: Liquefied Gas, 5: Electric, 9: Other)
     */
    private String fuelType;

    /**
     * Engine displacement (in milliliters)
     */
    private String engineDisplace;

    /**
     * Issuing agency of the vehicle transport certificate
     */
    private String transAgency;

    /**
     * Operating area of the vehicle
     */
    private String transArea;

    /**
     * Start date of the vehicle transport certificate validity
     */
    private LocalDate transDateStart;

    /**
     * End date of the vehicle transport certificate validity
     */
    private LocalDate transDateEnd;

    /**
     * Initial registration date of the vehicle
     */
    private LocalDate certifyDateB;

    /**
     * Vehicle maintenance status (0: Not maintained, 1: Maintained, 2: Unknown)
     */
    private String fixState;

    /**
     * Next annual inspection date
     */
    private LocalDate nextFixDate;

    /**
     * Annual inspection status (0: Not inspected, 1: Passed inspection, 2: Failed inspection)
     */
    private String checkState;

    /**
     * Invoice printing device serial number
     */
    private String feePrintId;

    /**
     * GPS device brand
     */
    private String gpsBrand;

    /**
     * GPS device model
     */
    private String gpsModel;

    /**
     * GPS device installation date
     */
    private LocalDate gpsInstallDate;

    /**
     * Registration date
     */
    private LocalDate registerDate;

    /**
     * Service type (1: Online booking taxi, 2: Street taxi, 3: Private carpool)
     */
    private Integer commercialType;

    /**
     * Fare code linked to the pricing rules
     */
    private String fareType;

    /**
     * Status (0: Active, 1: Inactive)
     */
    private Integer state;

    /**
     * Terminal ID
     */
    private String tid;

    /**
     * Track ID
     */
    private String trid;

    /**
     * Track name
     */
    private String trname;

    /**
     * Creation timestamp
     */
    private LocalDateTime gmtCreate;

    /**
     * Modification timestamp
     */
    private LocalDateTime gmtModified;


}
