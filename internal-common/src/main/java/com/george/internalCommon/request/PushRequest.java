package com.george.internalCommon.request;

import lombok.Data;

/**
 * This class is used to push the user info to the front client
 */
@Data
public class PushRequest {

    private Long userId;
    private String identity;
    private String content;

}
