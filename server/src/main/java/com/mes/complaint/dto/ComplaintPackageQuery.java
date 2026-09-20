package com.mes.complaint.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** 追溯包分页：包头摘要，不含成员 */
@Data
public class ComplaintPackageQuery {

    private Long anchorLotId;
    /** READY / CONTAINING / CONTAINED / VOID */
    private String status;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime from;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime to;
    private long page = 1;
    private long size = 20;
}
