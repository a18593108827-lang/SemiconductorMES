package com.mes.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 一次测试的 Bin 汇总。无软删，随头表查询过滤 */
@Data
@TableName("mes_test_bin_summary")
public class MesTestBinSummary {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属测试记录 */
    private Long recordId;

    /** HARD / SOFT */
    private String binType;

    /** Bin 号 */
    private String binCode;

    /** 登记时的名称快照 */
    private String binName;

    /** 颗数 */
    private Integer binQty;

    /** 登记时的可出货快照：0 / 1 */
    private Integer isShippable;
}
