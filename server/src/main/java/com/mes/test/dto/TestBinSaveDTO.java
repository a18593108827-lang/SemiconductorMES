package com.mes.test.dto;

import lombok.Data;

/** 新增或修改 Bin 字典 */
@Data
public class TestBinSaveDTO {

    /** GLOBAL / PRODUCT / PROGRAM / PROGRAM_VERSION */
    private String binScope;

    /** 不适用填空串 */
    private String productCode;

    /** 不适用填空串 */
    private String programName;

    /** 不适用填空串 */
    private String programVersion;

    /** HARD / SOFT */
    private String binType;

    /** Bin 号 */
    private String binCode;

    /** 名称 */
    private String binName;

    /** 失效模式，可空 */
    private String failureMode;

    /** 0 不可出货 / 1 可出货，空则 0 */
    private Integer isShippable;

    /** 1 启用 / 0 停用，空则 1 */
    private Integer status;

    /** 修改时必填，新增忽略 */
    private Integer version;

    private String remark;
}
