package com.mes.hold.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MesHoldVO {
    private Long id;
    private Long lotId;
    private String lotNo;
    private Long reasonId;
    private String reasonCode;
    private String reasonName;
    private String status;
    private String prevStatus;
    private String remark;
    private String releaseRemark;
    private Long holdUserId;
    private String holdUserName;
    private LocalDateTime holdTime;
    private Long releaseUserId;
    private String releaseUserName;
    private LocalDateTime releaseTime;
    private LocalDateTime createTime;
}
