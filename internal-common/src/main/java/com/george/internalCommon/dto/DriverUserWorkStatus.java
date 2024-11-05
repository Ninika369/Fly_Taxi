package com.george.internalCommon.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * This class stores the working status of a driver
 * </p>
 *
 * @author george
 * @since 2024-11-04
 */
@Data
public class DriverUserWorkStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long driverId;

    /**
     * STOP：0；START：1; SUSPEND：2
     */
    private Integer workStatus;

    /**
     * Created timestamp
     */
    private LocalDateTime gmtCreate;

    /**
     * modified timestamp
     */
    private LocalDateTime gmtModified;

}
