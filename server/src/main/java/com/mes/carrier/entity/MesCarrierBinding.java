package com.mes.carrier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 载具当前绑定（一 Lot 一盒；解绑物理删除） */
@Data
@TableName("mes_carrier_binding")
public class MesCarrierBinding {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 载具 ID */
    private Long carrierId;
    /** 批次 ID */
    private Long lotId;
    /** 绑定时间 */
    private LocalDateTime bindTime;
    /** 绑定人 */
    private Long bindBy;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
