package com.mes.track.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Split 分批 */
@Data
public class TrackSplitDTO {

    @NotNull(message = "父批次不能为空")
    private Long parentLotId;

    private String reasonCode;
    private String remark;

    @NotEmpty(message = "子批次列表不能为空")
    @Valid
    private List<TrackSplitChildDTO> children;
}
