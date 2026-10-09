package com.mes.test.dto;

import lombok.Data;

/** Bin 字典查询。传了才过滤，空串表示查该维度为空的档 */
@Data
public class TestBinQuery {

    private String productCode;
    private String programName;
    private String programVersion;

    /** HARD / SOFT */
    private String binType;
}
