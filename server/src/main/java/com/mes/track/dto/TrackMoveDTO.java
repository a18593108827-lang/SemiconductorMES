package com.mes.track.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 独立移站请求：批还在等待，没加工，只挪到工艺下一站 */
@Data
public class TrackMoveDTO {

    @NotNull(message = "批次不能为空")
    private Long lotId;

    /** 想去哪一站；不填就默认下一站；填了也必须等于下一站（P1 不允许乱跳） */
    private Integer toSortNo;

    /** 可选备注，写进履历 */
    private String remark;
}
