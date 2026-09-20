package com.mes.carrier.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Lot 侧绑载具 */
@Data
public class LotCarrierBindDTO {

    /** 载具 id 或 carrierCode */
    @NotBlank(message = "载具不能为空")
    private String carrierRef;
}
