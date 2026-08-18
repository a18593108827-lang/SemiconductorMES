package com.mes.hold.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预约锁批记录。
 * pending 不拦 Track；activated 后拦截由 mes_hold 负责。
 */
@Data
@TableName("mes_future_hold")
public class MesFutureHold {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long lotId;
    /** 批次号冗余 */
    private String lotNo;
    /** 放行时路线版本快照；激活须一致 */
    private Long routeVersionId;
    /** 目标站序 */
    private Integer targetSortNo;
    /** PRE=进站前 / POST=出站落到该站后 */
    private String timing;
    private Long reasonId;
    /** 原因编码冗余 */
    private String reasonCode;
    /** pending / activated / cancelled */
    private String status;
    /** 激活后指向 mes_hold.id */
    private Long holdId;
    private String remark;
    /** 主责人（通知后置） */
    private Long ownerUserId;
    private String ownerUserName;
    private Long createUserId;
    private String createUserName;
    private LocalDateTime createTime;
    private LocalDateTime activateTime;
    private Long cancelUserId;
    private String cancelUserName;
    private LocalDateTime cancelTime;
    private String cancelRemark;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
