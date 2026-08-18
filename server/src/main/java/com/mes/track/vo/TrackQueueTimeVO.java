package com.mes.track.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** Queue Time 开窗状态（context） */
@Data
public class TrackQueueTimeVO {
    private Integer fromSortNo;
    private Integer toSortNo;
    private LocalDateTime startedAt;
    private Integer maxQueueMin;
    private Long elapsedMin;
    private Long remainMin;
    private String onViolate;
    private Boolean violated;
}
