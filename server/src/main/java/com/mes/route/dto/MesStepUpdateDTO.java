package com.mes.route.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 编辑工序（编码不可改） */
@Data
public class MesStepUpdateDTO {

    @NotBlank(message = "工序名称不能为空")
    private String stepName;

    /** 类型：1加工 2量测 3其它 */
    @NotNull(message = "工序类型不能为空")
    private Integer stepType;

    /** 状态：1正常 0禁用 */
    @NotNull(message = "状态不能为空")
    private Integer status;

    private String eqpType;
    private Integer allowSkip;
    private Integer maxQueueMin;
    /** 最短加工分钟，空=不管 */
    private Integer minProcessMin;
    /** 最长加工分钟，空=不管 */
    private Integer maxProcessMin;
    private String remark;
}
