package com.mes.complaint.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * contain 三段结果（部分成功语义）。
 * status/containBy/containTime 为结束 CAS 后**重读包头**的值，非本地推算（K10）。
 */
@Data
public class ComplaintContainResultVO {

    /** 本轮成功锁批（新产生 active Hold） */
    private List<ComplaintContainLotVO> succeeded = new ArrayList<>();
    /** 本轮跳过：已存在生效中的锁批——不是错误，属幂等正常路径 */
    private List<ComplaintContainLotVO> skipped = new ArrayList<>();
    /** 本轮失败（不可锁批次 / 乐观锁冲突 / 系统错），明细含 code + message */
    private List<ComplaintContainLotVO> failed = new ArrayList<>();

    /** 三段计数：与各列表 size 一致，供前端直接展示 */
    private int succeededCount;
    private int skippedCount;
    private int failedCount;

    /** 包终态 READY / CONTAINING / CONTAINED / VOID（重读所得） */
    private String status;
    /** 首次遏制人（IFNULL 首写：重复 contain 不覆盖首次值） */
    private Long containBy;
    /** 首次遏制时间（同上；亦是"原状态"判别依据——非空说明遏制过） */
    private LocalDateTime containTime;
}
