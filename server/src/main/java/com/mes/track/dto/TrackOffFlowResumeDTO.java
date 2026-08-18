package com.mes.track.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrackOffFlowResumeDTO {

    @NotNull(message = "批次不能为空")
    private Long lotId;

    private String remark;
}
