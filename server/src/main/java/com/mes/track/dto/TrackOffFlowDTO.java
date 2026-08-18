package com.mes.track.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrackOffFlowDTO {

    @NotNull(message = "批次不能为空")
    private Long lotId;

    @NotNull(message = "Off-Flow 目标站不能为空")
    private Integer toSortNo;

    private String reasonCode;
    private String remark;
}
