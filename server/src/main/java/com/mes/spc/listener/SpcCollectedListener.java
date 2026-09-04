package com.mes.spc.listener;

import com.mes.edc.event.EdcCollectedEvent;
import com.mes.spc.facade.SpcFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 采集事务提交后再判。判坏了也不回滚采集。 */
@Component
@RequiredArgsConstructor
public class SpcCollectedListener {

    private final SpcFacade spcFacade;

    /** 库已经写下了才动手，避免读不到刚采的点。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCollected(EdcCollectedEvent event) {
        if (event == null) {
            return;
        }
        spcFacade.onCollected(event.getCollectionId());
    }
}
