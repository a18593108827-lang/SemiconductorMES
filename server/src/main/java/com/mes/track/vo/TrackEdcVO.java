package com.mes.track.vo;

import lombok.Data;

/** 现场台量测门禁（只有 processing 才有） */
@Data
public class TrackEdcVO {
    /** 这站要不要采 */
    private Boolean required;
    /** false=不能完工 */
    private Boolean clear;
    /** NONE / NO_DATA / OOS / GATE_DISABLED */
    private String reasonCode;
    /** 给人看的一句 */
    private String message;
}
