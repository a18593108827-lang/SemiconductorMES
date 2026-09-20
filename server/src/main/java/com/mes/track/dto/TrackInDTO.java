package com.mes.track.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** TrackIn 开工 */
@Data
public class TrackInDTO {

    @NotNull(message = "批次ID不能为空")
    private Long lotId;

    /** 设备 ID（一期可选） */
    private Long eqpId;

    /** 现场扫码载具编码；扫码闸开时必填并比对 */
    private String carrierCode;
}
