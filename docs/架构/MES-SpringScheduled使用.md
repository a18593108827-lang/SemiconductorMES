---
type: 架构
module: 
status: done
slices: []
aligns: []
updated: 2026-08-13
---

# Spring `@Scheduled` 使用说明

> 定位：本进程定时任务（起步单体）  
> 现状：**已启用**；Queue Time 到期扫批是第一个消费者  
> 不对齐：XXL-JOB（调度中心 / 分片 / 控制台）属后置  
> 更新：2026-08-13

---

## 1. 边界

```
Spring @Scheduled  = 本 JVM 内周期执行；应用活着才有任务
业务方法            = Job 只负责触发，处置逻辑放 Support / Handler
XXL-JOB             = 后置；多实例或统一运维一堆任务时再换触发器
```

**做什么**

- 周期扫描、超时检测、轻量对账（单实例可接受秒～分钟级延迟）
- 与 HTTP 事务解耦：到期处置用 `REQUIRES_NEW`，不被业务回滚带走

**不做什么**

- 不引入调度中心、执行器注册、GLUE、分片广播
- 不做多实例互斥（无 ShedLock / Redis 锁）
- 不替代 MQ；长耗时、可削峰的副作用仍走异步

**禁止**

- 在 `@Scheduled` 方法里直接写业务状态机（须调已有 Service/Support）
- 把 Hold / 清窗和 TrackIn 放进同一事务（到期 Hold 必须独立提交）
- 假登录骗过 `StpUtil`；无会话时操作人写 `SYSTEM`

---

## 2. 原理

`@EnableScheduling` 注册后处理器，扫描 `@Scheduled` 方法，交给 `ThreadPoolTaskScheduler`（底层 JDK `ScheduledThreadPoolExecutor`）。

| 模式 | 行为 | 本项目 |
|------|------|--------|
| `fixedDelay` | **上次结束**后再等 N ms | ✅ Queue Time 用这个 |
| `fixedRate` | 按固定间隔开始，不管上次有没有跑完 | 不用（避免重叠） |
| `cron` | 日历表达式 | 日结/报表后再考虑 |

默认单线程池。`fixedDelay` 不会并发跑同一个方法：这次没完，下次不会开始。

应用停即停；多开几个进程会各扫一遍。

---

## 3. 启用

启动类：

```java
@SpringBootApplication
@EnableScheduling
@MapperScan("com.mes.**.mapper")
public class MesApplication { ... }
```

配置（`application.yml`）：

```yaml
mes:
  qtime:
    enabled: true
    scan-ms: 30000   # fixedDelay，毫秒；改完重启生效
```

---

## 4. 本仓库落地（Queue Time）

| 类 | 职责 |
|----|------|
| `QueueTimeExpireJob` | `@Scheduled(fixedDelayString = "${mes.qtime.scan-ms:30000}")` 扫开窗 Lot |
| `QueueTimeExpireHandler.enforceIfExpired` | `REQUIRES_NEW`：Alarm + `HoldService.create(QTIME_EXCEED)` + 清窗 |
| `QueueTimeSupport.assertAndSettleOnTrackIn` | TrackIn 再拦一次，防止扫描空档抢开工 |
| `HoldServiceImpl` | 无登录 → `operUserName=SYSTEM`；`QTIME_EXCEED` 解锁必填备注 |

扫描条件：`qtime_*` 非空、`status ∈ wait|processing`、`elapsed > maxQueueMin`。  
策略 `ALARM` 不进扫描（避免每 30s 刷告警）；`HOLD` / `HOLD_ALARM` 到期锁批并**清窗**。解锁后可正常 TrackIn。

产品口径：锁是停和留痕；解锁填备注 = 本期 Continue（不对齐大厂 QMS/RL）。

---

## 5. 新增任务怎么写

1. 业务放 `*Support` / `*Handler`，Job 类只循环 + 调入口。
2. 写库处置用独立事务（`REQUIRES_NEW`），按 Lot 吞异常、打日志，一笔失败不影响下一笔。
3. 间隔用配置项，不要写死魔法数。
4. 无登录上下文：操作人允许空 / `SYSTEM`，不要 `StpUtil.login`。
5. 默认 `fixedDelay`。只有确认「必须整点」再用 cron。

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class XxxJob {

    @Scheduled(fixedDelayString = "${mes.xxx.scan-ms:60000}")
    public void scan() {
        if (!enabled) {
            return;
        }
        for (Long id : ids) {
            try {
                handler.enforce(id);
            } catch (Exception ex) {
                log.error("xxx 扫描失败 id={}", id, ex);
            }
        }
    }
}
```

---

## 6. 何时改 XXL-JOB

单实例 + 少数扫描：**继续 `@Scheduled`。**

出现下面再换触发器（业务 Handler 不动）：

- 多实例部署，不能重复扫
- 要控制台改 cron、看执行日志、失败重试、分片
- 定时任务数量明显变多，需要统一运维

XXL-JOB 要调度中心、一套表、执行器注册，比本进程注解重。架构总册里的 XXL-JOB 仍是目标形态，**不是当前实现**。

---

## 7. 关联

- `docs/架构/半导MES架构设计.md` §2.2  
- `docs/模块/Route（工艺路线）模块/MES-QueueTime接口设计.md`  
- 实现：`QueueTimeExpireJob` / `QueueTimeExpireHandler` / `MesApplication`  
