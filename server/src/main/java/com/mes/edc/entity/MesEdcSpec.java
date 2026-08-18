package com.mes.edc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 量测规格：某特性该卡多大范围（USL/LSL）。
 * 版本化：draft 可改 → 发布成 active → 升版后旧的变 obsolete。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_edc_spec")
public class MesEdcSpec extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 挂哪个特性 */
    private Long paramId;
    /** 产品维；空串=全产品默认，跟库表唯一键对齐 */
    private String productCode;
    /** 同 param+product 内从 1 往上加（业务版本号，别跟乐观锁搞混） */
    private Integer versionNo;
    /** draft / active / obsolete */
    private String status;
    /** 上限；可只配一侧 */
    private BigDecimal usl;
    /** 下限 */
    private BigDecimal lsl;
    /** 目标值；门禁不用，留给 SPC */
    private BigDecimal target;
    private String remark;
    private LocalDateTime publishedAt;

    /** 乐观锁：两人同时改草稿，后提交的会被拦住 */
    @Version
    private Integer version;

    private Long createBy;
    private Long updateBy;
}
