package com.mes.test.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 测试记录。列表可不带 bins */
@Data
public class TestRecordVO {

    private Long id;

    /** 业务号 */
    private String recordNo;

    private Long lotId;

    /** 批次号快照 */
    private String lotNo;

    /** CP / FT / OTHER */
    private String testStage;

    private String programName;
    private String programVersion;

    /** 测试设备，可空 */
    private Long eqpId;

    /** 设备编码快照 */
    private String eqpCode;

    /** 测试完成时间 */
    private LocalDateTime testTime;

    /** 本次测试颗数 */
    private Integer totalQty;

    /** FILE / API / MANUAL */
    private String sourceType;

    /** 文件引用或外部标识 */
    private String sourceRef;

    private String remark;
    private LocalDateTime createTime;

    /** 各档明细，列表接口为空 */
    private List<TestBinLineVO> bins;
}
