package com.mes.wip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 在制投影（只读查询源） */
@Data
@TableName("mes_wip_lot")
public class MesWipLot {

    @TableId(value = "lot_id", type = IdType.INPUT)
    private Long lotId;

    private String lotNo;
    private String productCode;
    private Integer qty;
    private Integer priority;
    /** Hot Lot 0/1 */
    private Integer hotFlag;
    private String customerLot;
    /** wait / processing / held */
    private String status;
    private Integer currentSortNo;
    private Long currentStepId;
    private Long currentEqpId;
    private Long routeId;
    private Long routeVersionId;
    private LocalDateTime updateTime;
}
