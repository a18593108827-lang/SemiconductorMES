package com.mes.route.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 路线列表/详情 */
@Data
public class MesRouteVO {
    private Long id;
    private String routeCode;
    private String routeName;
    private String productCode;
    /** 路线状态：1正常 0停用 */
    private Integer status;
    private String remark;
    /** 当前生效版本 ID（无则 null） */
    private Long activeVersionId;
    /** 当前生效版本号 */
    private Integer activeVersionNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
