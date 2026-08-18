package com.mes.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 派工设备预约 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_dispatch_reserve")
public class MesDispatchReserve extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long lotId;
    /** active 时=lotId，否则 null；UNIQUE 防同批双约 */
    private Long lotSlot;
    private Long eqpId;
    /** active 时=eqpId，否则 null；UNIQUE 防同机双约 */
    private Long eqpSlot;

    /** active / released / expired / consumed */
    private String status;

    private LocalDateTime expireTime;
    private Long reserveUserId;
    private Long consumeTxId;
    private String remark;

    @Version
    private Integer version;
}
