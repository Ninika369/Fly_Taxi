package com.george.internalCommon.response;

import lombok.Data;

/**
 * This class contains the info that users need when asking for a taxi
 */
@Data
public class TrsearchResponse {
    private Long driveMile;

    /**
     * Total ride duration in seconds. Legacy field name retained for compatibility.
     */
    private Long driveTime;
}
