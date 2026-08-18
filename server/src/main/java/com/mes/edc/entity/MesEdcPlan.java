package com.mes.edc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站采集计划：这个工序要不要卡量测、采哪些特性。
 * 一 step 一份；没配计划 = TrackOut 不拦。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_edc_plan")
public class MesEdcPlan extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工序；逻辑引用 mes_step，不建硬外键 */
    private Long stepId;
    /** 1=TrackOut 必须过 EDC 门禁 */
    private Integer required;
    /** 1启用 0停用；停了等同没配 */
    private Integer enabled;
    private String remark;

    /** 乐观锁 */
    @Version
    private Integer version;

    private Long createBy;
    private Long updateBy;
}
