package com.mes.test.dto;

import lombok.Data;

/** 作废测试记录 */
@Data
public class TestRecordVoidDTO {

    /** 作废原因，必填 */
    private String reason;
}
