package com.mes.hold.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mes_hold")
public class MesHold {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long lotId;
    private String lotNo;
    private Long reasonId;
    private String reasonCode;
    /** active / released */
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
