package com.mes.test.vo;

import lombok.Data;

/** Bin 字典 */
@Data
public class TestBinDefVO {

    private Long id;

    /** GLOBAL / PRODUCT / PROGRAM / PROGRAM_VERSION */
    private String binScope;

    private String productCode;
    private String programName;
    private String programVersion;

    /** HARD / SOFT */
    private String binType;

    /** Bin 号 */
    private String binCode;

    /** 名称 */
    private String binName;

    /** 失效模式 */
    private String failureMode;

    /** 0 不可出货 / 1 可出货 */
    private Integer isShippable;

    /** 1 启用 / 0 停用 */
    private Integer status;

    /** 乐观锁版本 */
    private Integer version;

    private String remark;
}
