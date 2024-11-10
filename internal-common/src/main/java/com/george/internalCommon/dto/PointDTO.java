package com.george.internalCommon.dto;

import lombok.Data;

/**
 * This class contains the info needed to upload in a trace
 */
@Data
public class PointDTO {

    private String location;

    private String locatetime;
}
