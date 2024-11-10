package com.george.internalCommon.response;

import lombok.Data;

/**
 * This class contains the basic info of a terminal in a service
  */
@Data
public class TerminalResponse {

    private String tid;

    private Long carId;

    private String longitude ;
    private String latitude ;
}
