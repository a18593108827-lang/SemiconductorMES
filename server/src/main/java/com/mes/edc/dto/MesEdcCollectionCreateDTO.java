package com.mes.edc.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 手录提交采集 */
@Data
public class MesEdcCollectionCreateDTO {

    @NotNull(message = "批次不能为空")
    private Long lotId;

    @NotNull(message = "本趟TrackIn不能为空")
    private Long trackInTxId;

    private Long eqpId;
    private String remark;

    @NotNull(message = "采集项不能为空")
    @Valid
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        @NotNull(message = "特性不能为空")
        private Long paramId;
        @NotNull(message = "量测值不能为空")
        private BigDecimal value;
    }
}
