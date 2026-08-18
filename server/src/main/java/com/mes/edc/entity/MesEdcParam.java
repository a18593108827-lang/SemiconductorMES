package com.mes.edc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 量测特性：要采什么（比如厚度、线宽），先建这个，后面 Spec / Plan 都挂它。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_edc_param")
public class MesEdcParam extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 特性编码，全库唯一，建好后不让改，避免历史采集对不上号 */
    private String paramCode;
    private String paramName;
    /** 单位，随便填，展示用 */
    private String unit;
    /** 值类型；一期只做数字 NUMBER */
    private String valueType;
    /** 1启用 0停用；停了新计划别再选，旧数据还在 */
    private Integer enabled;
    private String remark;

    /** 乐观锁：两人同时改，后提交的会被告知刷新重试 */
    @Version
    private Integer version;

    private Long createBy;
    private Long updateBy;
}
