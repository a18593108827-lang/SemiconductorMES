package com.mes.edc.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采集点：一个特性一个值，对照规格快照判 PASS/OOS。
 * 表没有 update_time，不走 BaseEntity。
 */
@Data
@TableName("mes_edc_collection_item")
public class MesEdcCollectionItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long collectionId;
    private Long paramId;
    private Long specId;
    private BigDecimal uslSnap;
    private BigDecimal lslSnap;
    private BigDecimal valueNum;
    /** PASS / OOS */
    private String itemResult;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
