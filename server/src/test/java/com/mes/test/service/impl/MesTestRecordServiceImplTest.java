package com.mes.test.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.mes.common.BusinessException;
import com.mes.lot.service.MesLotService;
import com.mes.lot.vo.MesLotVO;
import com.mes.test.dto.TestBinLineDTO;
import com.mes.test.dto.TestRecordCreateDTO;
import com.mes.test.entity.MesBinDef;
import com.mes.test.entity.MesTestBinSummary;
import com.mes.test.entity.MesTestRecord;
import com.mes.test.entity.MesTestSubmitGuard;
import com.mes.test.mapper.MesBinDefMapper;
import com.mes.test.mapper.MesTestBinSummaryMapper;
import com.mes.test.mapper.MesTestRecordMapper;
import com.mes.test.mapper.MesTestRecordNoSeqMapper;
import com.mes.test.mapper.MesTestSubmitGuardMapper;
import com.mes.test.vo.TestRecordVO;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TD-1 步骤 f：测试登记核心校验（V1 / V2 / V5 / V6 / K7）。
 *
 * <p>全部 mock，不触库。反向验证见 plan §8 R8（临时改坏对账 / 去掉守卫插入后用例必须变红）。
 */
@ExtendWith(MockitoExtension.class)
class MesTestRecordServiceImplTest {

    @Mock private MesTestRecordMapper mesTestRecordMapper;
    @Mock private MesTestBinSummaryMapper mesTestBinSummaryMapper;
    @Mock private MesTestSubmitGuardMapper mesTestSubmitGuardMapper;
    @Mock private MesTestRecordNoSeqMapper mesTestRecordNoSeqMapper;
    @Mock private MesBinDefMapper mesBinDefMapper;
    @Mock private MesLotService mesLotService;

    @InjectMocks
    private MesTestRecordServiceImpl service;

    private MockedStatic<StpUtil> stp;

    @BeforeEach
    void openStp() {
        stp = mockStatic(StpUtil.class);
        stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
    }

    @AfterEach
    void closeStp() {
        stp.close();
    }

    // ── V1 对账 ──────────────────────────────────────────────

    @Test
    void v1_hardSumEqualsTotal_passes() {
        List<TestBinLineDTO> lines = List.of(
                hard("1", 20),
                hard("2", 5),
                soft("S1", 3));
        ReflectionTestUtils.invokeMethod(service, "checkSum", lines, 25);
    }

    @Test
    void v1_hardSumOffByOne_rejects() {
        List<TestBinLineDTO> lines = List.of(hard("1", 24));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "checkSum", lines, 25))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("TEST_BIN_SUM_MISMATCH");
    }

    // ── V5 同请求档号重复 ────────────────────────────────────

    @Test
    void v5_duplicateBinCodeInRequest_rejects() {
        List<TestBinLineDTO> lines = List.of(hard("1", 10), hard("1", 15));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "assertLines", lines))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("TEST_BIN_CODE_DUPLICATED");
    }

    @Test
    void v5_sameCodeDifferentType_allowed() {
        List<TestBinLineDTO> lines = List.of(hard("1", 10), soft("1", 2));
        ReflectionTestUtils.invokeMethod(service, "assertLines", lines);
    }

    // ── V2 四级回退 + R4-F1 空 productCode ───────────────────

    @Test
    void v2_picksProgramVersionOverLowerScopes() {
        List<MesBinDef> defs = List.of(
                def("GLOBAL", "", "", "", "G", 0),
                def("PRODUCT", "P1", "", "", "P", 0),
                def("PROGRAM", "P1", "PROG", "", "PRG", 0),
                def("PROGRAM_VERSION", "P1", "PROG", "1.0", "PV", 1));
        MesBinDef picked = ReflectionTestUtils.invokeMethod(
                service, "pick", defs, "HARD", "1", "P1", "PROG", "1.0");
        assertThat(picked.getBinName()).isEqualTo("PV");
        assertThat(picked.getIsShippable()).isEqualTo(1);
    }

    @Test
    void v2_fallsBackToGlobalWhenNoMatch() {
        List<MesBinDef> defs = List.of(def("GLOBAL", "", "", "", "G", 1));
        MesBinDef picked = ReflectionTestUtils.invokeMethod(
                service, "pick", defs, "HARD", "1", "OTHER", "X", "9");
        assertThat(picked.getBinName()).isEqualTo("G");
    }

    @Test
    void v2_missingDef_rejectsNotFound() {
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                        service, "pick", List.of(), "HARD", "99", "P1", "PROG", "1.0"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("TEST_BIN_DEF_NOT_FOUND");
    }

    /**
     * R4-F1：lot.productCode 为空时，PRODUCT(pc='') 与 GLOBAL 都会命中
     * {@code product.equals(pc) && pn/pv 空 → rank 2}，二者同分；pick 取先出现的最高分。
     */
    @Test
    void r4f1_emptyProductCode_productAndGlobalBothRank2() {
        MesBinDef global = def("GLOBAL", "", "", "", "G", 0);
        MesBinDef productEmpty = def("PRODUCT", "", "", "", "P-empty", 1);
        Integer productRank = ReflectionTestUtils.invokeMethod(
                service, "rank", productEmpty, "", "PROG", "1.0");
        Integer globalRank = ReflectionTestUtils.invokeMethod(
                service, "rank", global, "", "PROG", "1.0");
        assertThat(productRank).isEqualTo(2);
        assertThat(globalRank).isEqualTo(2);

        MesBinDef picked = ReflectionTestUtils.invokeMethod(
                service, "pick", List.of(global, productEmpty), "HARD", "1", "", "PROG", "1.0");
        assertThat(picked.getBinName()).isEqualTo("G");
    }

    // ── V6 守卫 UK → 业务码；且头表零落库 ────────────────────

    @Test
    void v6_guardDuplicate_convertsToBusinessCode_andSkipsRecordInsert() {
        stubLot("wait", "P1");
        when(mesBinDefMapper.selectList(any())).thenReturn(List.of(
                def("GLOBAL", "", "", "", "Pass", 1)));
        doThrow(new DuplicateKeyException("Duplicate entry for uk_test_submit_guard"))
                .when(mesTestSubmitGuardMapper).insertGuard(any(MesTestSubmitGuard.class));

        assertThatThrownBy(() -> service.create(validDto(25)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("TEST_RECORD_DUPLICATE");

        verify(mesTestRecordMapper, never()).insert(any(MesTestRecord.class));
        verify(mesTestBinSummaryMapper, never()).insert(any(MesTestBinSummary.class));
        verify(mesTestRecordNoSeqMapper, never()).bump(any());
    }

    /** 反向保护：create 路径不得对守卫表做 SELECT 判重（只 insertGuard） */
    @Test
    void v6_createDoesNotPrecheckGuardBySelect() {
        stubHappyPathInserts();
        stubLot("wait", "P1");
        when(mesBinDefMapper.selectList(any())).thenReturn(List.of(
                def("GLOBAL", "", "", "", "Pass", 1)));

        service.create(validDto(25));

        verify(mesTestSubmitGuardMapper).insertGuard(any(MesTestSubmitGuard.class));
        // Mockito 默认只 verify 显式方法；此处确认没有其它交互（仅 insertGuard）
        org.mockito.Mockito.verifyNoMoreInteractions(mesTestSubmitGuardMapper);
    }

    // ── K7 快照：落库用登记时字典值 ──────────────────────────

    @Test
    void k7_summarySnapshotsBinNameAndShippableAtCreate() {
        stubHappyPathInserts();
        stubLot("wait", "P1");
        when(mesBinDefMapper.selectList(any())).thenReturn(List.of(
                def("GLOBAL", "", "", "", "Pass-at-create", 1)));

        TestRecordVO vo = service.create(validDto(25));

        ArgumentCaptor<MesTestBinSummary> cap = ArgumentCaptor.forClass(MesTestBinSummary.class);
        verify(mesTestBinSummaryMapper).insert(cap.capture());
        MesTestBinSummary saved = cap.getValue();
        assertThat(saved.getBinName()).isEqualTo("Pass-at-create");
        assertThat(saved.getIsShippable()).isEqualTo(1);
        assertThat(vo.getBins()).hasSize(1);
        assertThat(vo.getBins().get(0).getBinName()).isEqualTo("Pass-at-create");
    }

    // ── helpers ──────────────────────────────────────────────

    private void stubLot(String status, String productCode) {
        MesLotVO lot = new MesLotVO();
        lot.setId(10L);
        lot.setLotNo("LOT-T001");
        lot.setStatus(status);
        lot.setProductCode(productCode);
        when(mesLotService.get(10L)).thenReturn(lot);
    }

    private void stubHappyPathInserts() {
        when(mesTestSubmitGuardMapper.insertGuard(any())).thenReturn(1);
        when(mesTestRecordNoSeqMapper.bump(any())).thenReturn(1);
        when(mesTestRecordNoSeqMapper.lastInsertId()).thenReturn(7L);
        doAnswer(inv -> {
            MesTestRecord r = inv.getArgument(0);
            r.setId(100L);
            return 1;
        }).when(mesTestRecordMapper).insert(any(MesTestRecord.class));
        when(mesTestBinSummaryMapper.insert(any(MesTestBinSummary.class))).thenReturn(1);
    }

    private static TestRecordCreateDTO validDto(int totalQty) {
        TestRecordCreateDTO dto = new TestRecordCreateDTO();
        dto.setLotId(10L);
        dto.setTestStage("FT");
        dto.setProgramName("PROG");
        dto.setProgramVersion("1.0");
        dto.setTestTime(LocalDateTime.of(2026, 10, 9, 12, 0));
        dto.setTotalQty(totalQty);
        dto.setSourceType("MANUAL");
        dto.setBins(new ArrayList<>(List.of(hard("1", totalQty))));
        return dto;
    }

    private static TestBinLineDTO hard(String code, int qty) {
        TestBinLineDTO line = new TestBinLineDTO();
        line.setBinType("HARD");
        line.setBinCode(code);
        line.setBinQty(qty);
        return line;
    }

    private static TestBinLineDTO soft(String code, int qty) {
        TestBinLineDTO line = new TestBinLineDTO();
        line.setBinType("SOFT");
        line.setBinCode(code);
        line.setBinQty(qty);
        return line;
    }

    private static MesBinDef def(
            String scope, String product, String program, String version, String name, int shippable) {
        MesBinDef d = new MesBinDef();
        d.setBinScope(scope);
        d.setProductCode(product);
        d.setProgramName(program);
        d.setProgramVersion(version);
        d.setBinType("HARD");
        d.setBinCode("1");
        d.setBinName(name);
        d.setIsShippable(shippable);
        d.setStatus(1);
        return d;
    }
}
