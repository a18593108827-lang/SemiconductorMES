package com.mes.route.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 工序列表/详情 */
@Data
public class MesStepVO {
    private Long id;
    private String stepCode;
    private String stepName;
    /** 类型：1加工 2量测 3其它 */
    private Integer stepType;
    private String eqpType;
    private Integer allowSkip;
    private Integer maxQueueMin;
    /** 最短加工分钟 */
    private Integer minProcessMin;
    /** 最长加工分钟 */
    private Integer maxProcessMin;
    /** 状态：1正常 0禁用 */
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
