package com.mes.complaint.support;

import com.mes.complaint.mapper.MesComplaintPackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 包号生成器：专职生成唯一业务号 CP-yyyyMMdd-序号（如 CP-20260920-3），别的不做。
 *
 * 起号规则 = 当日数字后缀 max(seq)+1，两条禁令：
 * - 禁 COUNT(*)+1：历史插入回滚/删行后行数 ≠ 序号，必然算出撞号
 * - 禁 ORDER BY package_no DESC 取最大：字符串排序下 "-9" > "-10"，取到的不是真最大
 *
 * 并发唯一性靠两层兜底：
 * - 数据库 uk_complaint_package_no 唯一键拦住重复号落库
 * - 撞号后调 {@link #nextAfterConflict()}：重读 max 再叠加 0~2 随机抖动，
 *   防多个并发请求拿同一候选"齐步走"连环互撞（herd）
 */
@Component
@RequiredArgsConstructor
public class ComplaintPackageNoAllocator {

    /** 包号日期段格式 yyyyMMdd（如 20260920） */
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    private final MesComplaintPackageMapper packageMapper;

    /** 首次起号：当日 max(seq)+1；当日还没有包则从 1 开始 */
    public String nextCandidate() {
        String ymd = LocalDate.now().format(YMD);
        return format(ymd, maxSeq(ymd) + 1);
    }

    /**
     * 撞号重起：重读当日 max(seq)+1，再叠加 0~2 随机抖动错开并发。
     * 由 Facade 在写库事务外调用后重新落库，重试上限 3 次。
     */
    public String nextAfterConflict() {
        String ymd = LocalDate.now().format(YMD);
        long seq = maxSeq(ymd) + 1 + ThreadLocalRandom.current().nextInt(3);
        return format(ymd, seq);
    }

    /** 查当日已有包号的数字后缀最大值（数字比较，非字符串排序）；无行返回 0 */
    private long maxSeq(String ymd) {
        Long max = packageMapper.selectMaxSeqOfDay(ymd);
        return max == null ? 0L : max;
    }

    /** 拼完整包号 CP-yyyyMMdd-seq */
    private static String format(String ymd, long seq) {
        return "CP-" + ymd + "-" + seq;
    }
}
