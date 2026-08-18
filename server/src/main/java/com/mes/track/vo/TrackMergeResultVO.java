package com.mes.track.vo;

import lombok.Data;

import java.util.List;

/** Merge 合批事务结果 */
@Data
public class TrackMergeResultVO {

    /** 合批后的主批 */
    private LotBrief main;
    /** 已并入并终态为 merged 的源批 */
    private List<MergedBrief> merged;
    /** 本笔 mes_tx_log.id */
    private Long txId;

    /** 主批摘要 */
    @Data
    public static class LotBrief {
        private Long lotId;
        private String lotNo;
        /** 合批后数量 */
        private Integer qty;
    }

    /** 被吞源批摘要 */
    @Data
    public static class MergedBrief {
        private Long lotId;
        private String lotNo;
        /** 本次并入主批的数量（合批前源 qty） */
        private Integer qtyMerged;
    }
}
