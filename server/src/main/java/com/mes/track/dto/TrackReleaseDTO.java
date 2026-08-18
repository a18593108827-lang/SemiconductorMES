package com.mes.track.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Track 放行入参 */
@Data
public class TrackReleaseDTO {

    @NotNull(message = "批次ID不能为空")
    private Long lotId;
}
