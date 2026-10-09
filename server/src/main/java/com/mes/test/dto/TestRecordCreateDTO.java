package com.mes.test.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 登记一条测试记录及其 Bin 汇总 */
@Data
public class TestRecordCreateDTO {

    /** 所属批 */
    private Long lotId;

    /** CP / FT / OTHER */
    private String testStage;

    /** 测试程序名 */
    private String programName;

    /** 程序版本 */
    private String programVersion;

    /** 测试设备，可空 */
    private Long eqpId;

    /** 设备编码，可空 */
    private String eqpCode;

    /** 测试完成时间 */
    private LocalDateTime testTime;

    /** 本次测试颗数 */
    private Integer totalQty;

    /** FILE / API / MANUAL */
    private String sourceType;

    /** 文件引用或外部标识，可空 */
    private String sourceRef;

    private String remark;

    /** 各档颗数 */
    private List<TestBinLineDTO> bins;
}
