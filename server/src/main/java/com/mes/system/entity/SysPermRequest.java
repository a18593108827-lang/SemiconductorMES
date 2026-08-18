package com.mes.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 权限申请单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_perm_request")
public class SysPermRequest extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String requestNo;

    private Long applicantId;

    private Long roleId;

    private String reason;

    /** pending / approved / rejected / cancelled */
    private String status;

    private Long approverId;

    private String approveOpinion;

    private LocalDateTime approveTime;

    private String oaInstanceId;
}
