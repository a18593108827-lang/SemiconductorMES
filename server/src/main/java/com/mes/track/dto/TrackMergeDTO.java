package com.mes.track.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Merge 合批请求：主批保留，源批并入后终态 merged */
@Data
public class TrackMergeDTO {

    /** 保留的主 Lot（lot_no 不变） */
    @NotNull(message = "主批次不能为空")
    private Long mainLotId;

    /** 被吞并的源 Lot 列表（不可含主批） */
    @NotEmpty(message = "源批次列表不能为空")
    private List<Long> sourceLotIds;

    /** 原因码，写入 genealogy / tx_log */
    private String reasonCode;

    /** 备注，写入 tx_log.remark */
    private String remark;
}
