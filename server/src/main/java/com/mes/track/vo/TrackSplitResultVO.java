package com.mes.track.vo;

import lombok.Data;

import java.util.List;

/** Split 事务结果 */
@Data
public class TrackSplitResultVO {

    /** 分批后的父批 */
    private LotBrief parent;
    /** 新建子批列表 */
    private List<LotBrief> children;
    /** 本笔 tx_log.id */
    private Long txId;

    /** 批次摘要 */
    @Data
    public static class LotBrief {
        private Long lotId;
        private String lotNo;
        private Integer qty;
    }
}
