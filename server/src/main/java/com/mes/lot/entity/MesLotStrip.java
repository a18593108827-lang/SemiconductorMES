package com.mes.lot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Strip 条级。不建 Lot、不写 tx_log */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_lot_strip")
public class MesLotStrip extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属批 */
    private Long lotId;

    /** 条号 */
    private String stripNo;

    /** 批内序号 */
    private Integer seqNo;

    /** 本条颗数 */
    private Integer dieQty;

    /** 该条最终判定档，TD-1 可空 */
    private String binCode;

    /** 条级状态，TD-1 仅登记 */
    private String status;

    private String remark;

    private Long createBy;
}
