package com.mes.track.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** TrackOut 完工（自动进下一站 wait 或 completed） */
@Data
public class TrackOutDTO {

    @NotNull(message = "批次ID不能为空")
    private Long lotId;

    /** 分支结果码；空则走 default/normal */
    private String resultCode;
}
