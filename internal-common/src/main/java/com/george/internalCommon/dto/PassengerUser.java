package com.george.internalCommon.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: George Sun
 * @Date: 2024-10-17-19:26
 * @Description: This class is used to represent the info stored in MySQL that can represent passengers
 */

@Data
public class PassengerUser {

    private Long id;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;

    private String passengerPhone;

    private String passengerName;

    private byte passengerGender;

    private byte state;

    private String profilePhoto;



}
