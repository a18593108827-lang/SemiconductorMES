package com.mes.test.dto;

import lombok.Data;

/** 规则分页。传了才过滤 */
@Data
public class TestAdviceRuleQuery {

    /** 产品编码，精确匹配；传空串查全局档 */
    private String productCode;

    /** Bin 号，精确匹配 */
    private String binCode;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    /** 页码，从 1 起 */
    private long page = 1;

    /** 每页条数 */
    private long size = 20;
}
