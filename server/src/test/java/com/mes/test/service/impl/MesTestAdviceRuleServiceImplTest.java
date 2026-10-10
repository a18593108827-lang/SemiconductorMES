package com.mes.test.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.mes.common.BusinessException;
import com.mes.test.dto.TestAdviceRuleSaveDTO;
import com.mes.test.entity.MesTestAdviceRule;
import com.mes.test.entity.MesTestRuleLog;
import com.mes.test.mapper.MesTestAdviceRuleMapper;
import com.mes.test.mapper.MesTestRuleLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MesTestAdviceRuleServiceImplTest {

    @Mock private MesTestAdviceRuleMapper mesTestAdviceRuleMapper;
    @Mock private MesTestRuleLogMapper mesTestRuleLogMapper;

    @InjectMocks
    private MesTestAdviceRuleServiceImpl service;

    private MockedStatic<StpUtil> stp;

    @BeforeEach
    void openStp() {
        stp = mockStatic(StpUtil.class);
        stp.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
    }

    @AfterEach
    void closeStp() {
        stp.close();
    }

    @Test
    void fallbackKeys_fullThenDedupWhenBlank() {
        List<String[]> full = MesTestAdviceRuleServiceImpl.fallbackKeys("P", "PN", "PV");
        assertThat(full).hasSize(4);
        assertThat(full.get(0)).containsExactly("P", "PN", "PV");
        assertThat(full.get(1)).containsExactly("P", "PN", "");
        assertThat(full.get(2)).containsExactly("P", "", "");
        assertThat(full.get(3)).containsExactly("", "", "");

        List<String[]> global = MesTestAdviceRuleServiceImpl.fallbackKeys("", "", "");
        assertThat(global).hasSize(1);
        assertThat(global.get(0)).containsExactly("", "", "");
    }

    @Test
    void findRule_usesMostSpecificThenFallsBack() {
        MesTestAdviceRule product = new MesTestAdviceRule();
        product.setId(2L);
        when(mesTestAdviceRuleMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(null)
                .thenReturn(product);

        MesTestAdviceRule hit = service.findRule("P", "PN", "PV", "3");
        assertThat(hit.getId()).isEqualTo(2L);
        verify(mesTestAdviceRuleMapper, times(3)).selectOne(any());
    }

    @Test
    void findRule_emptyBinReturnsNull() {
        assertThat(service.findRule("P", "PN", "PV", "  ")).isNull();
    }

    @Test
    void create_writesRuleLog() {
        when(mesTestAdviceRuleMapper.insert(any(MesTestAdviceRule.class))).thenAnswer(inv -> {
            MesTestAdviceRule row = inv.getArgument(0);
            row.setId(7301L);
            return 1;
        });
        TestAdviceRuleSaveDTO dto = saveDto();
        service.create(dto);
        ArgumentCaptor<MesTestRuleLog> cap = ArgumentCaptor.forClass(MesTestRuleLog.class);
        verify(mesTestRuleLogMapper).insert(cap.capture());
        assertThat(cap.getValue().getAction()).isEqualTo("CREATE");
        assertThat(cap.getValue().getRuleId()).isEqualTo(7301L);
        assertThat(cap.getValue().getBeforeJson()).isNull();
        assertThat(cap.getValue().getAfterJson()).contains("HARD");
        assertThat(cap.getValue().getOpBy()).isEqualTo(9L);
    }

    @Test
    void create_ukConflict() {
        doThrow(new DuplicateKeyException("uk")).when(mesTestAdviceRuleMapper).insert(any(MesTestAdviceRule.class));
        assertThatThrownBy(() -> service.create(saveDto()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("TEST_RULE_UK_CONFLICT");
    }

    @Test
    void update_staleVersion() {
        MesTestAdviceRule row = new MesTestAdviceRule();
        row.setId(1L);
        when(mesTestAdviceRuleMapper.selectById(1L)).thenReturn(row);
        when(mesTestAdviceRuleMapper.updateById(any(MesTestAdviceRule.class))).thenReturn(0);
        TestAdviceRuleSaveDTO dto = saveDto();
        dto.setVersion(3);
        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("TEST_RULE_NOT_FOUND");
    }

    private static TestAdviceRuleSaveDTO saveDto() {
        TestAdviceRuleSaveDTO dto = new TestAdviceRuleSaveDTO();
        dto.setProductCode("");
        dto.setProgramName("");
        dto.setProgramVersion("");
        dto.setBinType("HARD");
        dto.setBinCode("3");
        dto.setMaxRatio(new BigDecimal("0.020000"));
        dto.setSuggestedAction("SUGGEST_HOLD");
        dto.setDefaultReasonCode("TEST_BIN_EXCEED");
        dto.setEnabled(1);
        return dto;
    }
}
