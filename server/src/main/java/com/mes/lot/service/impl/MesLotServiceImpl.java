package com.mes.lot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.lot.dto.MesLotCreateDTO;
import com.mes.lot.dto.MesLotQuery;
import com.mes.lot.dto.MesLotUpdateDTO;
import com.mes.lot.entity.MesLot;
import com.mes.lot.entity.MesLotGenealogy;
import com.mes.lot.mapper.MesLotGenealogyMapper;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.lot.mapper.MesLotNoSeqMapper;
import com.mes.lot.service.MesLotService;
import com.mes.lot.vo.MesLotCreateResultVO;
import com.mes.lot.vo.MesLotGenealogyNodeVO;
import com.mes.lot.vo.MesLotImpactFlatVO;
import com.mes.lot.vo.MesLotImpactMemberVO;
import com.mes.lot.vo.MesLotStepVO;
import com.mes.lot.vo.MesLotVO;
import com.mes.route.entity.MesRoute;
import com.mes.route.entity.MesRouteStep;
import com.mes.route.entity.MesRouteVersion;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesRouteMapper;
import com.mes.route.mapper.MesRouteStepMapper;
import com.mes.route.mapper.MesRouteVersionMapper;
import com.mes.route.mapper.MesStepMapper;
import com.mes.track.service.TrackService;
import com.mes.wip.service.WipProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 批次服务：创建 / 改属性 / Release 绑 active 版本快照
 */
@Service
@RequiredArgsConstructor
public class MesLotServiceImpl implements MesLotService {

    /** 已创建（未放行） */
    public static final String STATUS_CREATED = "created";
    /** 已放行（过渡态，Track 放行后为 wait） */
    public static final String STATUS_RELEASED = "released";
    public static final String STATUS_WAIT = "wait";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_HELD = "held";
    /** 打 Hot 时 priority 下限 */
    public static final int HOT_PRIORITY_FLOOR = 80;
    /** Route 生效版本状态 */
    public static final String ROUTE_ACTIVE = "active";
    /** 厂内批次号前缀 */
    public static final String LOT_NO_PREFIX = "LOT";
    private static final DateTimeFormatter LOT_DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final MesLotMapper mesLotMapper;
    private final MesLotGenealogyMapper mesLotGenealogyMapper;
    private final MesLotNoSeqMapper mesLotNoSeqMapper;
    private final MesRouteMapper mesRouteMapper;
    private final MesRouteVersionMapper mesRouteVersionMapper;
    private final MesRouteStepMapper mesRouteStepMapper;
    private final MesStepMapper mesStepMapper;
    private final TrackService trackService;
    private final WipProjectionService wipProjectionService;

    @Override
    public PageResult<MesLotVO> page(MesLotQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        Page<MesLot> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<MesLot> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            qw.and(w -> w.like(MesLot::getLotNo, keyword)
                    .or().like(MesLot::getProductCode, keyword)
                    .or().like(MesLot::getCustomerLot, keyword));
        }
        if (StringUtils.hasText(query.getStatus())) {
            qw.eq(MesLot::getStatus, query.getStatus().trim());
        }
        if (query.getHotFlag() != null) {
            AssertUtil.isTrue(query.getHotFlag() == 0 || query.getHotFlag() == 1, "hotFlag 只能为 0 或 1");
            qw.eq(MesLot::getHotFlag, query.getHotFlag());
        }
        qw.orderByDesc(MesLot::getCreateTime);

        Page<MesLot> result = mesLotMapper.selectPage(page, qw);
        List<MesLot> lots = result.getRecords();
        if (lots.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), pageNo, pageSize);
        }

        // 批量补路线 / 版本摘要，避免 N+1
        Map<Long, MesRoute> routeMap = loadRoutes(lots.stream().map(MesLot::getRouteId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, MesRouteVersion> versionMap = loadVersions(lots.stream().map(MesLot::getRouteVersionId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<MesLotVO> records = new ArrayList<>(lots.size());
        for (MesLot lot : lots) {
            records.add(toVo(lot, routeMap.get(lot.getRouteId()), versionMap.get(lot.getRouteVersionId()), null));
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesLotCreateResultVO create(MesLotCreateDTO dto) {
        String lotNo = blankToNull(dto.getLotNo());
        if (lotNo == null) {
            lotNo = nextLotNo();
        } else {
            Long count = mesLotMapper.selectCount(new LambdaQueryWrapper<MesLot>().eq(MesLot::getLotNo, lotNo));
            AssertUtil.isTrue(count == 0, "批次号已存在");
        }

        if (dto.getRouteId() != null) {
            assertRouteUsable(dto.getRouteId());
        }

        int priority = dto.getPriority() == null ? 50 : dto.getPriority();
        AssertUtil.isTrue(priority >= 1 && priority <= 100, "优先级范围为1-100");
        int hotFlag = normalizeHotFlag(dto.getHotFlag(), 0);
        priority = applyHotPriorityFloor(hotFlag, priority);

        long userId = StpUtil.getLoginIdAsLong();
        MesLot lot = new MesLot();
        lot.setLotNo(lotNo);
        lot.setProductCode(blankToNull(dto.getProductCode()));
        lot.setQty(dto.getQty());
        lot.setScrapQty(0);
        lot.setPriority(priority);
        lot.setHotFlag(hotFlag);
        lot.setCustomerLot(blankToNull(dto.getCustomerLot()));
        lot.setRouteId(dto.getRouteId());
        lot.setRouteVersionId(null);
        lot.setStatus(STATUS_CREATED);
        lot.setRemark(blankToNull(dto.getRemark()));
        lot.setVersion(0);
        lot.setCreateBy(userId);
        lot.setUpdateBy(userId);
        mesLotMapper.insert(lot);
        return new MesLotCreateResultVO(lot.getId(), lotNo);
    }

    /** 生成 LOT-yyyyMMdd-流水（按日原子取号） */
    private String nextLotNo() {
        String day = LocalDate.now().format(LOT_DAY);
        mesLotNoSeqMapper.bump(day);
        long seq = mesLotNoSeqMapper.lastInsertId();
        AssertUtil.isTrue(seq > 0, "批次号生成失败");
        return LOT_NO_PREFIX + "-" + day + "-" + String.format("%03d", seq);
    }

    @Override
    public MesLotVO get(Long id) {
        MesLot lot = mesLotMapper.selectById(id);
        AssertUtil.notNull(lot, "批次不存在");

        MesRoute route = lot.getRouteId() == null ? null : mesRouteMapper.selectById(lot.getRouteId());
        MesRouteVersion version = lot.getRouteVersionId() == null ? null : mesRouteVersionMapper.selectById(lot.getRouteVersionId());
        // 已放行：带出快照步骤供 UI / 后续 Track 参考
        List<MesLotStepVO> steps = null;
        if (lot.getRouteVersionId() != null) {
            steps = loadSteps(lot.getRouteVersionId());
        }
        return toVo(lot, route, version, steps);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MesLotUpdateDTO dto) {
        MesLot lot = mesLotMapper.selectById(id);
        AssertUtil.notNull(lot, "批次不存在");
        AssertUtil.isTrue(isEditableStatus(lot.getStatus()), "当前状态不可编辑");

        if (STATUS_CREATED.equals(lot.getStatus())) {
            if (dto.getRouteId() != null) {
                assertRouteUsable(dto.getRouteId());
            }
            lot.setRouteId(dto.getRouteId());
            lot.setProductCode(blankToNull(dto.getProductCode()));
            lot.setQty(dto.getQty());
        } else {
            AssertUtil.isFalse(dto.getRouteId() != null && !Objects.equals(dto.getRouteId(), lot.getRouteId()),
                    "已放行不可修改路线");
            AssertUtil.isFalse(dto.getQty() != null && !Objects.equals(dto.getQty(), lot.getQty()),
                    "数量变更请走 Split/Merge/Scrap/Bonus 事务");
            AssertUtil.isFalse(!Objects.equals(blankToNull(dto.getProductCode()), lot.getProductCode()),
                    "已放行不可修改产品编码");
        }

        int hotFlag = normalizeHotFlag(dto.getHotFlag(), lot.getHotFlag() == null ? 0 : lot.getHotFlag());
        int priority = applyHotPriorityFloor(hotFlag, dto.getPriority());
        lot.setPriority(priority);
        lot.setHotFlag(hotFlag);
        lot.setCustomerLot(blankToNull(dto.getCustomerLot()));
        lot.setRemark(blankToNull(dto.getRemark()));
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long id) {
        // 兼容入口：逻辑收敛到 Track
        trackService.release(id);
    }

    /**
     * 查谱系树（树形结构）。
     * 先向下铺子孙，再向上包祖先；both 时根往往是最顶祖先，查询批在中间。
     *
     * @param direction up=只向上 / down=只向下 / both=两边（默认）
     * @param depth     最多爬几层，默认 5，最大 20
     */
    @Override
    public MesLotGenealogyNodeVO genealogy(Long lotId, String direction, Integer depth) {
        return buildGenealogyTree(lotId, direction, depth, new AtomicBoolean(false));
    }

    /**
     * 把谱系树展平成名单（客诉圈影响面用）。
     * 建树规则同 genealogy；每条批打上锚点/祖先/子孙；深度砍断了标 truncated。
     *
     * @param lotId     你点的那批（锚点）
     * @param direction 同 genealogy
     * @param depth     同 genealogy
     */
    @Override
    public MesLotImpactFlatVO flattenImpact(Long lotId, String direction, Integer depth) {
        // 盒子：有没有被深度截断；递归里改，外面再读（不是多线程）
        AtomicBoolean truncated = new AtomicBoolean(false);
        MesLotGenealogyNodeVO tree = buildGenealogyTree(lotId, direction, depth, truncated);
        MesLotImpactFlatVO flat = new MesLotImpactFlatVO();
        flat.setAnchorLotId(lotId);
        flat.setAnchorLotNo(tree == null ? null : findLotNo(tree, lotId));
        flat.setTruncated(truncated.get());

        LinkedHashMap<Long, MesLotImpactMemberVO> byId = new LinkedHashMap<>();
        MesLotGenealogyNodeVO anchorNode = findNode(tree, lotId);
        // 把锚点那一批写进展平名单
        if (anchorNode != null) {
            putMember(byId, anchorNode, "ANCHOR", 0);
        }
        // 把锚点往上走的祖先写进展平名单
        List<MesLotGenealogyNodeVO> path = findPath(tree, lotId);
        if (path != null && path.size() > 1) {
            for (int i = 0; i < path.size() - 1; i++) {
                int depthFromAnchor = path.size() - 1 - i;
                putMember(byId, path.get(i), "ANCESTOR", depthFromAnchor);
            }
        }
        // 把锚点往下走的子孙写进展平名单
        if (anchorNode != null) {
            collectDescendants(anchorNode, 0, byId);
        }
        flat.setMembers(new ArrayList<>(byId.values()));
        flat.setTree(tree);
        // 补锚点批号（可能根没有批号）
        if (flat.getAnchorLotNo() == null && !byId.isEmpty()) {
            MesLotImpactMemberVO a = byId.get(lotId);
            if (a != null) {
                flat.setAnchorLotNo(a.getLotNo());
            }
        }
        return flat;
    }

    /**
     * 真正建树：genealogy / flattenImpact 共用。
     * 顺序：先向下 fillDown，再向上 buildUp。
     */
    private MesLotGenealogyNodeVO buildGenealogyTree(Long lotId, String direction, Integer depth,
                                                     AtomicBoolean truncated) {
        MesLot root = mesLotMapper.selectById(lotId);
        AssertUtil.notNull(root, "批次不存在");
        int maxDepth = depth == null || depth <= 0 ? 5 : Math.min(depth, 20);
        String dir = direction == null ? "both" : direction.trim().toLowerCase(Locale.ROOT);
        AssertUtil.isTrue("up".equals(dir) || "down".equals(dir) || "both".equals(dir),
                "direction 须为 up / down / both");

        MesLotGenealogyNodeVO node = toGeneNode(root, null);
        if ("down".equals(dir) || "both".equals(dir)) {
            fillDown(node, maxDepth, 0, truncated);
        }
        if ("up".equals(dir) || "both".equals(dir)) {
            node = buildUp(node, maxDepth, truncated);
        }
        return node;
    }

    /**
     * 向上爬：一层层把父批包在外面。
     * 每次只跟「当前批作为 child 的最新一条边」；防环；爬满层上面还有父 → 记截断。
     */
    private MesLotGenealogyNodeVO buildUp(MesLotGenealogyNodeVO current, int maxDepth,
                                          AtomicBoolean truncated) {
        MesLotGenealogyNodeVO cursor = current;
        Set<Long> visited = new HashSet<>();
        visited.add(current.getLotId());
        int i = 0;
        for (; i < maxDepth; i++) {
            MesLotGenealogy edge = mesLotGenealogyMapper.selectOne(new LambdaQueryWrapper<MesLotGenealogy>()
                    .eq(MesLotGenealogy::getChildLotId, cursor.getLotId())
                    .in(MesLotGenealogy::getTxnType, "split", "merge")
                    .orderByDesc(MesLotGenealogy::getCreateTime)
                    .last("LIMIT 1"));
            if (edge == null) {
                break;
            }
            if (!visited.add(edge.getParentLotId())) {
                break;
            }
            MesLot parent = mesLotMapper.selectById(edge.getParentLotId());
            if (parent == null) {
                break;
            }
            MesLotGenealogyNodeVO parentNode = toGeneNode(parent, null);
            applyEdge(cursor, edge);
            parentNode.setChildren(List.of(cursor));
            cursor = parentNode;
        }
        if (i >= maxDepth) {
            Long more = mesLotGenealogyMapper.selectCount(new LambdaQueryWrapper<MesLotGenealogy>()
                    .eq(MesLotGenealogy::getChildLotId, cursor.getLotId())
                    .in(MesLotGenealogy::getTxnType, "split", "merge"));
            if (more != null && more > 0) {
                truncated.set(true);
            }
        }
        return cursor;
    }

    /**
     * 向下铺：把拆合出来的子批挂到 children，再递归。
     * 到最大层数下面还有边 → 记截断，不再往下挂。
     */
    private void fillDown(MesLotGenealogyNodeVO node, int maxDepth, int level, AtomicBoolean truncated) {
        if (level >= maxDepth) {
            Long more = mesLotGenealogyMapper.selectCount(new LambdaQueryWrapper<MesLotGenealogy>()
                    .eq(MesLotGenealogy::getParentLotId, node.getLotId())
                    .in(MesLotGenealogy::getTxnType, "split", "merge"));
            if (more != null && more > 0) {
                truncated.set(true);
            }
            node.setChildren(Collections.emptyList());
            return;
        }
        List<MesLotGenealogy> edges = mesLotGenealogyMapper.selectList(new LambdaQueryWrapper<MesLotGenealogy>()
                .eq(MesLotGenealogy::getParentLotId, node.getLotId())
                .in(MesLotGenealogy::getTxnType, "split", "merge")
                .orderByAsc(MesLotGenealogy::getCreateTime));
        if (edges.isEmpty()) {
            node.setChildren(Collections.emptyList());
            return;
        }
        List<MesLotGenealogyNodeVO> kids = new ArrayList<>(edges.size());
        for (MesLotGenealogy edge : edges) {
            MesLot child = mesLotMapper.selectById(edge.getChildLotId());
            if (child == null) {
                continue;
            }
            MesLotGenealogyNodeVO childNode = toGeneNode(child, edge);
            fillDown(childNode, maxDepth, level + 1, truncated);
            kids.add(childNode);
        }
        node.setChildren(kids);
    }

    /** 往名单里加一条批；同批已加过就跳过 */
    private static void putMember(LinkedHashMap<Long, MesLotImpactMemberVO> byId,
                                  MesLotGenealogyNodeVO node, String relation, int depthFromAnchor) {
        if (node == null || node.getLotId() == null || byId.containsKey(node.getLotId())) {
            return;
        }
        MesLotImpactMemberVO m = new MesLotImpactMemberVO();
        m.setLotId(node.getLotId());
        m.setLotNo(node.getLotNo());
        m.setRelation(relation);
        m.setDepthFromAnchor(depthFromAnchor);
        m.setQty(node.getQty());
        m.setStatus(node.getStatus());
        byId.put(node.getLotId(), m);
    }

    /** 从锚点往下，把子孙都加进名单，标成 DESCENDANT */
    private static void collectDescendants(MesLotGenealogyNodeVO node, int depthFromAnchor,
                                           LinkedHashMap<Long, MesLotImpactMemberVO> byId) {
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return;
        }
        for (MesLotGenealogyNodeVO child : node.getChildren()) {
            putMember(byId, child, "DESCENDANT", depthFromAnchor + 1);
            collectDescendants(child, depthFromAnchor + 1, byId);
        }
    }

    /** 在树里找某个批次节点，没有就 null */
    private static MesLotGenealogyNodeVO findNode(MesLotGenealogyNodeVO root, Long lotId) {
        if (root == null || lotId == null) {
            return null;
        }
        if (lotId.equals(root.getLotId())) {
            return root;
        }
        if (root.getChildren() == null) {
            return null;
        }
        for (MesLotGenealogyNodeVO c : root.getChildren()) {
            MesLotGenealogyNodeVO hit = findNode(c, lotId);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** 从树根走到目标批的整条路径；找不到返回 null */
    private static List<MesLotGenealogyNodeVO> findPath(MesLotGenealogyNodeVO root, Long lotId) {
        List<MesLotGenealogyNodeVO> path = new ArrayList<>();
        if (dfsPath(root, lotId, path)) {
            return path;
        }
        return null;
    }

    /**
     * 在树里找有没有这个批次。
     * 找到：path 留下「根 → … → 该批」；找不到：回溯清掉岔路，返回 false。
     */
    private static boolean dfsPath(MesLotGenealogyNodeVO node, Long lotId, List<MesLotGenealogyNodeVO> path) {
        if (node == null) {
            return false;
        }
        path.add(node);
        if (lotId.equals(node.getLotId())) {
            return true;
        }
        if (node.getChildren() != null) {
            for (MesLotGenealogyNodeVO c : node.getChildren()) {
                if (dfsPath(c, lotId, path)) {
                    return true;
                }
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    /** 从树里取出某批的批号 */
    private static String findLotNo(MesLotGenealogyNodeVO root, Long lotId) {
        MesLotGenealogyNodeVO n = findNode(root, lotId);
        return n == null ? null : n.getLotNo();
    }

    /** Lot 转成树上一个节点；有边就把边信息挂在这个（child）节点上 */
    private static MesLotGenealogyNodeVO toGeneNode(MesLot lot, MesLotGenealogy edge) {
        MesLotGenealogyNodeVO node = new MesLotGenealogyNodeVO();
        node.setLotId(lot.getId());
        node.setLotNo(lot.getLotNo());
        node.setQty(lot.getQty());
        node.setStatus(lot.getStatus());
        if (edge != null) {
            applyEdge(node, edge);
        }
        return node;
    }

    /** 把谱系边写到节点上（表示「我怎么从上级来的」） */
    private static void applyEdge(MesLotGenealogyNodeVO node, MesLotGenealogy edge) {
        node.setTxnType(edge.getTxnType());
        node.setTxnTime(edge.getCreateTime());
        node.setQtyTransferred(edge.getQty());
        node.setTxId(edge.getTxId());
        node.setReasonCode(edge.getReasonCode());
    }

    private static boolean isEditableStatus(String status) {
        return STATUS_CREATED.equals(status)
                || STATUS_RELEASED.equals(status)
                || STATUS_WAIT.equals(status)
                || STATUS_PROCESSING.equals(status)
                || STATUS_HELD.equals(status);
    }

    private static int normalizeHotFlag(Integer hotFlag, int defaultValue) {
        if (hotFlag == null) {
            return defaultValue;
        }
        AssertUtil.isTrue(hotFlag == 0 || hotFlag == 1, "hotFlag 只能为 0 或 1");
        return hotFlag;
    }

    private static int applyHotPriorityFloor(int hotFlag, int priority) {
        AssertUtil.isTrue(priority >= 1 && priority <= 100, "优先级范围为1-100");
        if (hotFlag == 1 && priority < HOT_PRIORITY_FLOOR) {
            return HOT_PRIORITY_FLOOR;
        }
        return priority;
    }

    /** 路线存在且未停用 */
    private MesRoute assertRouteUsable(Long routeId) {
        MesRoute route = mesRouteMapper.selectById(routeId);
        AssertUtil.notNull(route, "路线不存在");
        AssertUtil.isTrue(route.getStatus() != null && route.getStatus() == 1, "路线已停用");
        return route;
    }

    private Map<Long, MesRoute> loadRoutes(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return mesRouteMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MesRoute::getId, r -> r, (a, b) -> a));
    }

    private Map<Long, MesRouteVersion> loadVersions(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return mesRouteVersionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MesRouteVersion::getId, v -> v, (a, b) -> a));
    }

    /** 按版本加载有序步骤 + 工序编码名称 */
    private List<MesLotStepVO> loadSteps(Long versionId) {
        List<MesRouteStep> rows = mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, versionId)
                .orderByAsc(MesRouteStep::getSortNo));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> stepIds = rows.stream().map(MesRouteStep::getStepId).collect(Collectors.toSet());
        Map<Long, MesStep> stepMap = mesStepMapper.selectBatchIds(stepIds).stream()
                .collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a));
        List<MesLotStepVO> list = new ArrayList<>(rows.size());
        for (MesRouteStep row : rows) {
            MesLotStepVO vo = new MesLotStepVO();
            vo.setStepId(row.getStepId());
            vo.setSortNo(row.getSortNo());
            vo.setNextSortNo(row.getNextSortNo());
            MesStep step = stepMap.get(row.getStepId());
            if (step != null) {
                vo.setStepCode(step.getStepCode());
                vo.setStepName(step.getStepName());
            }
            list.add(vo);
        }
        return list;
    }

    private MesLotVO toVo(MesLot lot, MesRoute route, MesRouteVersion version, List<MesLotStepVO> steps) {
        MesLotVO vo = new MesLotVO();
        vo.setId(lot.getId());
        vo.setLotNo(lot.getLotNo());
        vo.setProductCode(lot.getProductCode());
        vo.setQty(lot.getQty());
        vo.setScrapQty(lot.getScrapQty());
        vo.setPriority(lot.getPriority());
        vo.setHotFlag(lot.getHotFlag());
        vo.setCustomerLot(lot.getCustomerLot());
        vo.setParentLotId(lot.getParentLotId());
        vo.setMergedToLotId(lot.getMergedToLotId());
        vo.setRouteId(lot.getRouteId());
        vo.setRouteVersionId(lot.getRouteVersionId());
        vo.setCurrentSortNo(lot.getCurrentSortNo());
        vo.setCurrentStepId(lot.getCurrentStepId());
        vo.setCurrentEqpId(lot.getCurrentEqpId());
        vo.setStatus(lot.getStatus());
        vo.setRemark(lot.getRemark());
        vo.setVersion(lot.getVersion());
        vo.setCreateTime(lot.getCreateTime());
        vo.setUpdateTime(lot.getUpdateTime());
        if (route != null) {
            vo.setRouteCode(route.getRouteCode());
            vo.setRouteName(route.getRouteName());
        }
        if (version != null) {
            vo.setRouteVersionNo(version.getVersionNo());
        }
        vo.setSteps(steps);
        return vo;
    }

    private static String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
