package com.george.internalCommon.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * This class stores information of a relationship between a driver and a car
 * </p>
 *
 * @author george
 * @since 2024-11-03
 */
@Data
public class DriverCarBindingRelationship implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long driverId;

    private Long carId;

    private Integer bindState;

    private LocalDateTime bindingTime;

    private LocalDateTime unBindingTime;

}
