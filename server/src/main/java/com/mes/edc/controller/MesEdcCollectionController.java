package com.mes.edc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.edc.dto.MesEdcCollectionCreateDTO;
import com.mes.edc.dto.MesEdcCollectionQuery;
import com.mes.edc.service.MesEdcCollectionService;
import com.mes.edc.vo.MesEdcCollectionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 量测采集接口。
 * 提交要 collect；查询走 view。门禁钩子不在这里。
 */
@RestController
@RequestMapping("/edc/collections")
@RequiredArgsConstructor
public class MesEdcCollectionController {

    private final MesEdcCollectionService mesEdcCollectionService;

    /** 分页；可按批次 / 工序 / 本趟 visit / 结果筛 */
    @SaCheckPermission("edc:view")
    @GetMapping
    public R<PageResult<MesEdcCollectionVO>> page(MesEdcCollectionQuery query) {
        return R.ok(mesEdcCollectionService.page(query));
    }

    /** 同 visit 最新一条，现场用来看上次判定
     *  拿到当前访问 TrackOut 时要校验的数据
     */
    @SaCheckPermission("edc:view")
    @GetMapping("/latest")
    public R<MesEdcCollectionVO> latest(@RequestParam Long lotId, @RequestParam Long trackInTxId) {
        return R.ok(mesEdcCollectionService.getLatest(lotId, trackInTxId));
    }

    /** 手录提交：对照规格判 OOS，缺必采或超限则 FAIL */
    @SaCheckPermission("edc:collect")
    @OperLog(module = "EDC", action = "提交采集")
    @PostMapping
    public R<MesEdcCollectionVO> submit(@Valid @RequestBody MesEdcCollectionCreateDTO dto) {
        return R.ok(mesEdcCollectionService.submit(dto));
    }

    /** 详情含点值 */
    @SaCheckPermission("edc:view")
    @GetMapping("/{id}")
    public R<MesEdcCollectionVO> get(@PathVariable Long id) {
        return R.ok(mesEdcCollectionService.get(id));
    }
}
