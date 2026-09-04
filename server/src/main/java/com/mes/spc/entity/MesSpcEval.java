package com.mes.spc.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一次判异结果。记下当时用的哪版限、哪条规则。
 * 不存点值；同图同点只判一次。
 */
@Data
@TableName("mes_spc_eval")
public class MesSpcEval {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long chartId;
    /** EDC 采集项 id，逻辑引用，不建硬外键 */
    private Long collectionItemId;
    /** 0正常 1失控 */
    private Integer ooc;
    /** WE1 出界 / RUN 连跑同侧 */
    private String ruleCode;
    private BigDecimal uclSnap;
    private BigDecimal clSnap;
    private BigDecimal lclSnap;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
