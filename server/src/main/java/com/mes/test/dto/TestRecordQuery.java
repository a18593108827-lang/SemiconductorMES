package com.mes.test.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 测试记录分页条件，字段都可空 */
@Data
public class TestRecordQuery {

    private Long lotId;

    /** 批次号，模糊 */
    private String lotNo;

    /** CP / FT / OTHER */
    private String stage;

    /** 程序名，模糊 */
    private String programName;

    /** 测试完成时间起 */
    private LocalDateTime from;

    /** 测试完成时间止 */
    private LocalDateTime to;

    private long page = 1;
    private long size = 20;
}
