package com.mes.lot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客户 Lot 映射。真相在本表，不写 mes_lot.customer_lot */
@Data
@TableName("mes_lot_customer_map")
public class MesLotCustomerMap {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 内部批 */
    private Long lotId;

    /** 批次号快照 */
    private String lotNo;

    /** INBOUND / OUTBOUND */
    private String mapType;

    /** 外部批号 */
    private String externalLotNo;

    /** 供应商或客户编码 */
    private String externalSource;

    private String customerCode;

    private Integer qty;

    private String remark;

    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
