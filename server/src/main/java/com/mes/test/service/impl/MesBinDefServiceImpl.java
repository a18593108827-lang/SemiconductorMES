package com.mes.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.test.dto.TestBinQuery;
import com.mes.test.dto.TestBinSaveDTO;
import com.mes.test.entity.MesBinDef;
import com.mes.test.mapper.MesBinDefMapper;
import com.mes.test.service.MesBinDefService;
import com.mes.test.vo.TestBinDefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MesBinDefServiceImpl implements MesBinDefService {

    /** 适用范围和产品/程序/版本的空与非空对不上 */
    private static final String SCOPE_ERR = "TEST_BIN_SCOPE_INCONSISTENT: Bin适用范围与维度不一致";
    /** 乐观锁版本不一致，或缺少版本号 */
    private static final String CONFLICT = "TEST_BIN_DEF_CONFLICT: 数据已被他人修改，请刷新后重试";

    private final MesBinDefMapper mesBinDefMapper;

    /** 按传入的维度过滤，没传的维度不参与条件 */
    @Override
    public List<TestBinDefVO> list(TestBinQuery query) {
        LambdaQueryWrapper<MesBinDef> qw = new LambdaQueryWrapper<>();
        if (query.getProductCode() != null) {
            qw.eq(MesBinDef::getProductCode, query.getProductCode());
        }
        if (query.getProgramName() != null) {
            qw.eq(MesBinDef::getProgramName, query.getProgramName());
        }
        if (query.getProgramVersion() != null) {
            qw.eq(MesBinDef::getProgramVersion, query.getProgramVersion());
        }
        if (query.getBinType() != null && !query.getBinType().isBlank()) {
            qw.eq(MesBinDef::getBinType, query.getBinType().trim());
        }
        qw.orderByAsc(MesBinDef::getBinCode);
        return mesBinDefMapper.selectList(qw).stream().map(this::toVo).toList();
    }

    /** 新增一档。相同五元键已存在则拒 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestBinDefVO create(TestBinSaveDTO dto) {
        MesBinDef row = new MesBinDef();
        fill(row, dto);
        row.setVersion(0);
        try {
            mesBinDefMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("TEST_BIN_DEF_CONFLICT: 相同档位已存在");
        }
        return toVo(row);
    }

    /** 修改或停用。版本不一致则拒，不覆盖 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TestBinSaveDTO dto) {
        MesBinDef row = mesBinDefMapper.selectById(id);
        AssertUtil.notNull(row, "Bin定义不存在");
        AssertUtil.notNull(dto.getVersion(), "TEST_BIN_DEF_CONFLICT: 缺少版本号");
        fill(row, dto);
        row.setVersion(dto.getVersion());
        int n;
        try {
            n = mesBinDefMapper.updateById(row);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("TEST_BIN_DEF_CONFLICT: 相同档位已存在");
        }
        AssertUtil.isTrue(n > 0, CONFLICT);
    }

    /**
     * TestBinSaveDTO -> MesBinDef
     * 校验适用范围和取值，再写入行
     */
    private void fill(MesBinDef row, TestBinSaveDTO dto) {
        String scope = trimToEmpty(dto.getBinScope());
        String product = trimToEmpty(dto.getProductCode());
        String program = trimToEmpty(dto.getProgramName());
        String version = trimToEmpty(dto.getProgramVersion());
        String binType = trimToEmpty(dto.getBinType());
        String binCode = trimToEmpty(dto.getBinCode());
        String binName = trimToEmpty(dto.getBinName());
        AssertUtil.notBlank(scope, SCOPE_ERR);
        AssertUtil.notBlank(binType, "TEST_RECORD_FIELD_REQUIRED: binType不能为空");
        AssertUtil.notBlank(binCode, "TEST_RECORD_FIELD_REQUIRED: binCode不能为空");
        AssertUtil.notBlank(binName, "TEST_RECORD_FIELD_REQUIRED: binName不能为空");
        AssertUtil.isTrue("HARD".equals(binType) || "SOFT".equals(binType), "TEST_RECORD_FIELD_REQUIRED: binType只能是HARD或SOFT");
        assertScope(scope, product, program, version);
        int ship = dto.getIsShippable() == null ? 0 : dto.getIsShippable();
        int status = dto.getStatus() == null ? 1 : dto.getStatus();
        AssertUtil.isTrue(ship == 0 || ship == 1, "TEST_RECORD_FIELD_REQUIRED: isShippable只能为0或1");
        AssertUtil.isTrue(status == 0 || status == 1, "TEST_RECORD_FIELD_REQUIRED: status只能为0或1");
        row.setBinScope(scope);
        row.setProductCode(product);
        row.setProgramName(program);
        row.setProgramVersion(version);
        row.setBinType(binType);
        row.setBinCode(binCode);
        row.setBinName(binName);
        row.setFailureMode(emptyToNull(dto.getFailureMode()));
        row.setIsShippable(ship);
        row.setStatus(status);
        row.setRemark(emptyToNull(dto.getRemark()));
    }

    /** 适用范围必须和三个维度的空与非空一致 */
    static void assertScope(String scope, String product, String program, String version) {
        boolean ok = switch (scope) {
            case "GLOBAL" -> product.isEmpty() && program.isEmpty() && version.isEmpty();
            case "PRODUCT" -> !product.isEmpty() && program.isEmpty() && version.isEmpty();
            case "PROGRAM" -> !product.isEmpty() && !program.isEmpty() && version.isEmpty();
            case "PROGRAM_VERSION" -> !product.isEmpty() && !program.isEmpty() && !version.isEmpty();
            default -> false;
        };
        AssertUtil.isTrue(ok, SCOPE_ERR);
    }

    /** 去掉首尾空白，null 当空串 */
    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** 空白转成 null */
    private static String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** 字典行转出参 */
    private TestBinDefVO toVo(MesBinDef row) {
        TestBinDefVO vo = new TestBinDefVO();
        vo.setId(row.getId());
        vo.setBinScope(row.getBinScope());
        vo.setProductCode(row.getProductCode());
        vo.setProgramName(row.getProgramName());
        vo.setProgramVersion(row.getProgramVersion());
        vo.setBinType(row.getBinType());
        vo.setBinCode(row.getBinCode());
        vo.setBinName(row.getBinName());
        vo.setFailureMode(row.getFailureMode());
        vo.setIsShippable(row.getIsShippable());
        vo.setStatus(row.getStatus());
        vo.setVersion(row.getVersion());
        vo.setRemark(row.getRemark());
        return vo;
    }
}
