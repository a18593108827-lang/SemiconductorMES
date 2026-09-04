package com.mes.alarm.ws;

import com.mes.alarm.entity.MesAlarm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 告警变更推 WebSocket。
 * <p>
 * 业务里改完库后调 {@link #publishAfterCommit}；真正往网上发在 {@link #send}。
 * 必须等事务提交成功再推，否则前端收到了，库却回滚了，对不上。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmWsPublisher {

    /**
     * 前端用 STOMP 订阅的地址。
     * 对应 WebSocketConfig 里 enableSimpleBroker("/topic")，
     * 订阅路径就是 /topic/alarm.active。
     */
    public static final String TOPIC_ALARM_ACTIVE = "/topic/alarm.active";

    /** 新建一条 OPEN */
    public static final String ACTION_OPEN = "OPEN";
    /** 同键未关闭告警又响了，只加次数 */
    public static final String ACTION_BUMP = "BUMP";
    /** 有人点了确认 */
    public static final String ACTION_ACK = "ACK";
    /** 已关闭 */
    public static final String ACTION_CLEARED = "CLEARED";

    /**
     * Spring 提供的 STOMP 发送工具。
     * 调它的 convertAndSend(目的地, 对象) 就会把对象序列化成 JSON，
     * 推给所有订阅了该目的地的已连接客户端。
     */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 业务入口：先登记「事务成功后再推」，没有事务就立刻推。
     * <p>
     * TransactionSynchronizationManager = Spring 事务同步器：<br>
     * - isSynchronizationActive()：当前线程是不是在某个 @Transactional 里<br>
     * - registerSynchronization(...)：往当前事务挂回调<br>
     * - afterCommit()：只有提交成功才调用；回滚了不会推，避免假消息
     */
    public void publishAfterCommit(String action, MesAlarm row) {
        if (row == null || row.getId() == null) {
            return;
        }
        AlarmActiveMessage msg = toMessage(action, row);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 还在事务里：挂上 afterCommit，等 commit 成功再 send
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 事务提交成功后的回调：这时库已落稳，再推 WS */
                @Override
                public void afterCommit() {
                    send(msg);
                }
            });
        } else {
            // 没事务（少见）：直接推
            send(msg);
        }
    }

    /**
     * 真正发到网上。
     * convertAndSend(topic, msg)：把 msg 转到 topic，订阅了的前端都会收到一份。
     * 推送失败只打日志，不抛回业务（告警已落库，不能因为 WS 挂了把 ack/raise 弄失败）。
     */
    private void send(AlarmActiveMessage msg) {
        try {
            messagingTemplate.convertAndSend(TOPIC_ALARM_ACTIVE, msg);
        } catch (Exception e) {
            log.warn("[ALARM] WS 推送失败 action={} id={}", msg.getAction(), msg.getId(), e);
        }
    }

    /** 库里的 MesAlarm 收成前端要的摘要字段 */
    private static AlarmActiveMessage toMessage(String action, MesAlarm row) {
        AlarmActiveMessage msg = new AlarmActiveMessage();
        msg.setAction(action);
        msg.setId(row.getId());
        msg.setCode(row.getCode());
        msg.setLevel(row.getLevel());
        msg.setStatus(row.getStatus());
        msg.setMessage(row.getMessage());
        msg.setEntityType(row.getEntityType());
        msg.setEntityId(row.getEntityId());
        msg.setRaiseCount(row.getRaiseCount());
        return msg;
    }
}
