package com.george.servicepassengeruser.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * @Author: George Sun
 * @Date: 2024-10-17-19:26
 * @Description: com.george.servicepassengeruser.dto
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



}
