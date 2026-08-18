package com.mes.route.dto;

import lombok.Data;

/** 路线分页查询 */
@Data
public class MesRouteQuery {
    /** 编码/名称/产品关键字 */
    private String keyword;
    /** 路线状态：1正常 0停用 */
    private Integer status;
    private long page = 1;
    private long size = 20;
}
