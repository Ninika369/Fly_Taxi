package com.george.internalCommon.request;

import com.george.internalCommon.dto.PointDTO;
import lombok.Data;

/**
 * This class contains the basic info from a driver that constitutes traces
 */
@Data
public class ApiDriverPointRequest {

    public Long carId;

    private PointDTO[] points;
}
