package com.mes.test.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.test.dto.TestBinQuery;
import com.mes.test.dto.TestBinSaveDTO;
import com.mes.test.dto.TestRecordCreateDTO;
import com.mes.test.dto.TestRecordQuery;
import com.mes.test.dto.TestRecordVoidDTO;
import com.mes.test.service.MesBinDefService;
import com.mes.test.service.MesTestRecordService;
import com.mes.test.vo.TestBinDefVO;
import com.mes.test.vo.TestRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 测试记录与 Bin 字典。只挂管理端 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final MesTestRecordService mesTestRecordService;
    private final MesBinDefService mesBinDefService;

    /** 登记一条测试记录和各档颗数 */
    @SaCheckPermission("test:create")
    @OperLog(module = "Test", action = "登记测试记录")
    @PostMapping("/records")
    public R<TestRecordVO> create(@RequestBody TestRecordCreateDTO dto) {
        return R.ok(mesTestRecordService.create(dto));
    }

    /** 分页查测试记录 */
    @SaCheckPermission("test:view")
    @GetMapping("/records")
    public R<PageResult<TestRecordVO>> page(TestRecordQuery query) {
        return R.ok(mesTestRecordService.page(query));
    }

    /** 测试记录详情，含各档占比 */
    @SaCheckPermission("test:view")
    @GetMapping("/records/{id}")
    public R<TestRecordVO> get(@PathVariable Long id) {
        return R.ok(mesTestRecordService.get(id));
    }

    /** 作废测试记录，原因必填 */
    @SaCheckPermission("test:void")
    @OperLog(module = "Test", action = "作废测试记录")
    @PutMapping("/records/{id}/void")
    public R<Void> voidRecord(@PathVariable Long id, @RequestBody TestRecordVoidDTO dto) {
        mesTestRecordService.voidRecord(id, dto == null ? null : dto.getReason());
        return R.ok();
    }

    /** 某批的全部测试结果，给批次详情用 */
    @SaCheckPermission("test:view")
    @GetMapping("/summary/by-lot/{lotId}")
    public R<List<TestRecordVO>> byLot(@PathVariable Long lotId) {
        return R.ok(mesTestRecordService.listByLot(lotId));
    }

    /** 查 Bin 字典 */
    @SaCheckPermission("test:view")
    @GetMapping("/bins")
    public R<List<TestBinDefVO>> bins(TestBinQuery query) {
        return R.ok(mesBinDefService.list(query));
    }

    /** 新增一档 Bin */
    @SaCheckPermission("test:edit-bin")
    @OperLog(module = "Test", action = "新增Bin")
    @PostMapping("/bins")
    public R<TestBinDefVO> createBin(@RequestBody TestBinSaveDTO dto) {
        return R.ok(mesBinDefService.create(dto));
    }

    /** 修改或停用一档 Bin */
    @SaCheckPermission("test:edit-bin")
    @OperLog(module = "Test", action = "修改Bin")
    @PutMapping("/bins/{id}")
    public R<Void> updateBin(@PathVariable Long id, @RequestBody TestBinSaveDTO dto) {
        mesBinDefService.update(id, dto);
        return R.ok();
    }
}
