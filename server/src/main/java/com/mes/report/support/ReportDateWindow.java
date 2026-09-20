package com.mes.report.support;

import com.mes.common.BusinessException;
import com.mes.common.ResultCode;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 报表时间窗解析：无共享可变状态，请求内纯函数。
 * 默认近 7 日（含今天）；跨度超过 {@link #MAX_SPAN_DAYS} → 400（一期性能护栏）。
 */
public final class ReportDateWindow {

    public static final int DEFAULT_SPAN_DAYS = 7;
    public static final int MAX_SPAN_DAYS = 31;

    private final LocalDate from;
    private final LocalDate to;

    private ReportDateWindow(LocalDate from, LocalDate to) {
        this.from = from;
        this.to = to;
    }

    public LocalDate from() {
        return from;
    }

    public LocalDate to() {
        return to;
    }

    /** 含两端的日历天数 */
    public int dayCount() {
        return (int) ChronoUnit.DAYS.between(from, to) + 1;
    }

    /**
     * 补全默认时间窗并校验合法性。
     * from、to 可空：to 默认今天，from 默认 to 往前共 7 天；
     * to 早于 from，或跨度超过 31 天，抛 400。
     */
    public static ReportDateWindow resolve(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_SPAN_DAYS - 1L);
        if (end.isBefore(start)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        long span = ChronoUnit.DAYS.between(start, end) + 1;
        if (span > MAX_SPAN_DAYS) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "当前最多查询 " + MAX_SPAN_DAYS + " 天");
        }
        return new ReportDateWindow(start, end);
    }
}
