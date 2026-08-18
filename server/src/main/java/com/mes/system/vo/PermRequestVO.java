package com.mes.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PermRequestVO {

    private Long id;
    private String requestNo;
    private Long applicantId;
    private String applicantCode;
    private String applicantName;
    private Long roleId;
    private String roleCode;
    private String roleName;
    private String reason;
    private String status;
    private Long approverId;
    private String approverCode;
    private String approveOpinion;
    private LocalDateTime approveTime;
    private LocalDateTime createTime;
}
