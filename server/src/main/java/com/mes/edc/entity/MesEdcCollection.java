package com.mes.edc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 一次采集头：绑本趟 TrackIn，总结果 PASS/FAIL。
 * 同 visit 可重采，门禁认最新一条。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_edc_collection")
public class MesEdcCollection extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long lotId;
    private String lotNo;
    private Long routeVersionId;
    private Integer sortNo;
    private Long stepId;
    /** 本趟访问，对应 mes_tx_log.id */
    private Long trackInTxId;
    private Long planId;
    /** PASS / FAIL */
    private String result;
    /** MANUAL / AUTO；一期只写 MANUAL */
    private String source;
    private Long eqpId;
    private String remark;
    private Long collectedBy;
    private LocalDateTime collectedAt;
}
