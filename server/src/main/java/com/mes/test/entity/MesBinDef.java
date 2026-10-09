package com.mes.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Bin 字典。不适用的维度填空串，不填 NULL */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_bin_def")
public class MesBinDef extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** GLOBAL / PRODUCT / PROGRAM / PROGRAM_VERSION */
    private String binScope;

    /** 适用产品，全局档为空串 */
    private String productCode;

    /** 适用测试程序，不适用为空串 */
    private String programName;

    /** 适用程序版本，不适用为空串 */
    private String programVersion;

    /** HARD / SOFT */
    private String binType;

    /** Bin 号 */
    private String binCode;

    /** 名称 */
    private String binName;

    /** 失效模式，可空 */
    private String failureMode;

    /** 按规格可出货：0 否 / 1 是 */
    private Integer isShippable;

    /** 1 启用 / 0 停用 */
    private Integer status;

    /** 乐观锁 */
    @Version
    private Integer version;

    private String remark;
}
