package com.mes.route.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 覆盖保存草稿版本步骤（及可选边） */
@Data
public class MesRouteStepsSaveDTO {

    /** 步骤列表（可空数组，表示清空草稿步骤） */
    @NotNull(message = "步骤列表不能为空")
    @Valid
    private List<Item> steps;

    /**
     * 边列表；null=不改边；空数组=清空边。
     * edgeType=rework 时需 maxReworkCount。
     */
    @Valid
    private List<EdgeItem> edges;

    @Data
    public static class Item {
        @NotNull(message = "工序不能为空")
        private Long stepId;

        @NotNull(message = "顺序号不能为空")
        private Integer sortNo;

        /** 下一站顺序号；null 表示结束 */
        private Integer nextSortNo;
    }

    @Data
    public static class EdgeItem {
        @NotNull(message = "边起点不能为空")
        private Integer fromSortNo;

        @NotNull(message = "边终点不能为空")
        private Integer toSortNo;

        @NotNull(message = "边类型不能为空")
        private String edgeType;

        private Integer maxReworkCount;
        private String reasonCodes;
        /** branch 条件码 */
        private String conditionCode;
        /** Queue Time 上限分钟 */
        private Integer maxQueueMin;
        /** Queue Time 下限预留 */
        private Integer minQueueMin;
        /** HOLD / ALARM / HOLD_ALARM */
        private String onViolate;
        private Integer sortNo;
    }
}
