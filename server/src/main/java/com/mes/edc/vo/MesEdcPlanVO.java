package com.mes.edc.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 站计划对外展示 */
@Data
public class MesEdcPlanVO {
    private Long id;
    private Long stepId;
    private String stepCode;
    private String stepName;
    private Integer required;
    private Integer enabled;
    private String remark;
    private Integer version;
    private Integer itemCount;
    private List<MesEdcPlanItemVO> items = new ArrayList<>();
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
