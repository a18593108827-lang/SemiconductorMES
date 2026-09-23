package com.mes.complaint.support;

import com.mes.alarm.facade.AlarmFacade;
import com.mes.history.facade.HistoryFacade;
import com.mes.hold.service.HoldService;
import com.mes.lot.service.MesLotService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 履历 / Hold / 告警上限的**生效值**回归（K15 / D7 / D9 / F10）。
 *
 * <p>保护点：配置 &lt; 1 时必须回落 100（封面与查询共用同一来源，不得印 0）；
 * Hold / 告警上限必须取自常量而不是散落字面量。用 mock 顶掉 4 个依赖，不触库（K2）。
 */
class ComplaintPackageAssemblerCapsTest {

    private ComplaintPackageAssembler assembler(int historyPerLot) {
        ComplaintPackageAssembler a = new ComplaintPackageAssembler(
                mock(HistoryFacade.class), mock(HoldService.class),
                mock(AlarmFacade.class), mock(MesLotService.class));
        ReflectionTestUtils.setField(a, "historyPerLot", historyPerLot);
        return a;
    }

    @Test
    void historyPerLotFallsBackTo100WhenMisconfigured() {
        assertThat(assembler(0).historyPerLot()).isEqualTo(100);
        assertThat(assembler(-5).historyPerLot()).isEqualTo(100);
    }

    @Test
    void historyPerLotKeepsConfiguredValueWhenSane() {
        assertThat(assembler(50).historyPerLot()).isEqualTo(50);
        assertThat(assembler(1).historyPerLot()).isEqualTo(1);
    }

    @Test
    void holdAndAlarmCapsComeFromConstants() {
        ComplaintPackageAssembler a = assembler(100);
        assertThat(a.holdCap()).isEqualTo(20);
        assertThat(a.alarmCap()).isEqualTo(20);
    }
}
