package com.mes.track.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Track 事务履历（只追加）
 */
@Data
@TableName("mes_tx_log")
public class MesTxLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long lotId;
    private String lotNo;
    /** RELEASE / MOVE / TRACK_IN / TRACK_OUT / … */
    private String txType;
    private String fromStatus;
    private String toStatus;
    private Integer fromSortNo;
    private Integer toSortNo;
    private Long stepId;
    private Long eqpId;
    private Long recipeId;
    private Long recipeVersionId;
    private Long routeVersionId;
    private String remark;
    /** 事务扩展 JSON（如 Rework 的 reason/count） */
    private String extJson;
    private Long operUserId;
    private String operUserName;
    private LocalDateTime createTime;
}
