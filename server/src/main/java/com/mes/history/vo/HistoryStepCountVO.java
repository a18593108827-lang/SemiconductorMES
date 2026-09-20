package com.mes.history.vo;

import lombok.Data;

/** 履历按工序计数（报表 Move 按站） */
@Data
public class HistoryStepCountVO {

    /** 可为 null：履历未记 step_id */
    private Long stepId;
    private long count;
}
