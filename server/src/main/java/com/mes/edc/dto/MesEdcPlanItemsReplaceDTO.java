package com.mes.edc.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 整表替换计划项 */
@Data
public class MesEdcPlanItemsReplaceDTO {

    @NotNull(message = "计划项列表不能为空")
    @Valid
    private List<MesEdcPlanItemDTO> items = new ArrayList<>();

    @Data
    public static class MesEdcPlanItemDTO {
        @NotNull(message = "特性不能为空")
        private Long paramId;
        /** 空=跟 active Spec */
        private Long specId;
        /** 不传按列表顺序从 1 编 */
        private Integer sortNo;
        /** 1必采；默认 1 */
        private Integer mandatory;
    }
}
