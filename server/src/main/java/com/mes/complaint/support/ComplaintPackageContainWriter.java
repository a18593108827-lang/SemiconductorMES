package com.mes.complaint.support;

import com.mes.complaint.mapper.MesComplaintPackageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * contain 状态机的唯一出口：包行上三条 CAS 全走这里（K17）。
 *
 * 与 {@link ComplaintPackageWriter} 对称——那个管 build 落库（带事务），这个管遏制状态机。
 *
 * <b>本类刻意不加 @Transactional</b>，原因是占位语义与事务冲突：
 * - 占位必须<b>立即对其他请求可见</b>（自动提交）：请求 A 占位期间，B 必须当场看见 CONTAINING 而被拒；
 *   若包在事务里，A 整个循环期间的改动对 B 不可见，防并发直接失效
 * - 再加事务等于长时间持有包行锁，把"禁外层事务罩整段循环"（K2）换个马甲违反
 *
 * 所以正确性靠 WHERE 条件（CAS）而非锁保证；三个布尔返回值即三个决策信号，
 * 由 Facade 决定继续 / 停手 / 收工。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintPackageContainWriter {

    private final MesComplaintPackageMapper packageMapper;

    /**
     * 抢占位（K4）：READY/CONTAINED → CONTAINING + 写入自己的 token；
     * 已是 CONTAINING 时仅当 update_time 早于「now - rescueSeconds」才允许抢占（僵尸救援窗口）。
     *
     * @return true = 抢到；
     *         false = 未抢到（可能别人正在干，也可能是 VOID / 行不存在——不在本类区分，
     *                 由 Facade 读行后按 K16 分支报 IN_PROGRESS / VOID / NOT_FOUND）
     */
    public boolean occupy(Long id, String token, int rescueSeconds) {
        // 时间减法放 Java 侧算，SQL 只做 update_time < #{staleBefore} 比较：
        // 不依赖 MySQL INTERVAL 语法，且负配置由 Math.max 兜住
        int n = Math.max(rescueSeconds, 0);
        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(n);
        return packageMapper.occupy(id, token, staleBefore) > 0;
    }

    /**
     * 续命（K14）：刷新 update_time，证明占位者还活着、别来抢。
     * 每处理完一个 Lot 调用一次。
     *
     * @return true = 所有权仍在；
     *         false = <b>已丢权</b>（token 对不上，位子被救援判僵尸后抢占），
     *                 调用方必须立即停止后续 create，不得再往别人的场子里插手
     */
    public boolean heartbeat(Long id, String token) {
        return packageMapper.heartbeat(id, token) > 0;
    }

    /**
     * 收尾（K5/K6）：交还所有权并收敛包状态，WHERE 认 token。
     *
     * @param markContained true = 本轮有 succeeded → 置 CONTAINED 并首写 contain_by/contain_time；
     *                      false = 全 skipped / 全 failed → 按原状态回退（判别式在 SQL 的 CASE 里：
     *                      contain_time IS NULL → READY，否则 → CONTAINED；IFNULL 保证"首次"语义不被覆盖）
     * @return true = 收敛成功；
     *         false = CAS 未命中（所有权已丢，被救援抢走）——<b>只记 WARN 不抛</b>：
     *                 锁批动作已真实发生，报错改变不了结果，状态由抢走位子的请求负责收敛
     */
    public boolean finish(Long id, String token, boolean markContained, Long uid) {
        int n = packageMapper.finish(id, token, markContained ? 1 : 0, uid);
        if (n == 0) {
            log.warn("complaint contain finish CAS missed id={}", id);
        }
        return n > 0;
    }
}
