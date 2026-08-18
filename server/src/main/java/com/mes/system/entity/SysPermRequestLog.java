package com.mes.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限申请流转日志
 */
@Data
@TableName("sys_perm_request_log")
public class SysPermRequestLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long requestId;

    private String fromStatus;

    private String toStatus;

    private Long operatorId;

    private String opinion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
