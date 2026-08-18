package com.mes.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.system.dto.PermRequestCreateDTO;
import com.mes.system.dto.PermRequestDecideDTO;
import com.mes.system.dto.PermRequestQuery;
import com.mes.system.entity.SysPermRequest;
import com.mes.system.entity.SysPermRequestLog;
import com.mes.system.entity.SysRole;
import com.mes.system.entity.SysUser;
import com.mes.system.entity.SysUserRole;
import com.mes.system.mapper.SysPermRequestLogMapper;
import com.mes.system.mapper.SysPermRequestMapper;
import com.mes.system.mapper.SysRoleMapper;
import com.mes.system.mapper.SysUserMapper;
import com.mes.system.mapper.SysUserRoleMapper;
import com.mes.system.service.SysPermRequestService;
import com.mes.system.vo.ApplyableRoleVO;
import com.mes.system.vo.PermRequestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysPermRequestServiceImpl implements SysPermRequestService {

    private static final String PENDING = "pending";
    private static final String APPROVED = "approved";
    private static final String REJECTED = "rejected";
    private static final String CANCELLED = "cancelled";

    private final SysPermRequestMapper requestMapper;
    private final SysPermRequestLogMapper logMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public List<ApplyableRoleVO> listApplyableRoles() {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getStatus, 1)
                .ne(SysRole::getRoleCode, "admin")
                .orderByAsc(SysRole::getId));
        List<ApplyableRoleVO> list = new ArrayList<>();
        for (SysRole r : roles) {
            ApplyableRoleVO vo = new ApplyableRoleVO();
            vo.setId(r.getId());
            vo.setRoleCode(r.getRoleCode());
            vo.setRoleName(r.getRoleName());
            list.add(vo);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(PermRequestCreateDTO dto) {
        long userId = StpUtil.getLoginIdAsLong();
        SysRole role = roleMapper.selectById(dto.getRoleId());
        AssertUtil.notNull(role, "目标角色不存在");
        AssertUtil.isTrue(role.getStatus() != null && role.getStatus() == 1, "目标角色已禁用");
        AssertUtil.isFalse("admin".equals(role.getRoleCode()), "不可申请超级管理员角色");

        Long pending = requestMapper.selectCount(new LambdaQueryWrapper<SysPermRequest>()
                .eq(SysPermRequest::getApplicantId, userId)
                .eq(SysPermRequest::getRoleId, dto.getRoleId())
                .eq(SysPermRequest::getStatus, PENDING));
        AssertUtil.isTrue(pending == 0, "该角色已有待审批申请");

        SysPermRequest req = new SysPermRequest();
        req.setId(IdWorker.getId());
        req.setRequestNo(genRequestNo());
        req.setApplicantId(userId);
        req.setRoleId(dto.getRoleId());
        req.setReason(dto.getReason().trim());
        req.setStatus(PENDING);
        requestMapper.insert(req);
        addLog(req.getId(), null, PENDING, userId, "提交申请");
    }

    @Override
    public PageResult<PermRequestVO> pageMine(PermRequestQuery query) {
        long userId = StpUtil.getLoginIdAsLong();
        return pageBy(query, new LambdaQueryWrapper<SysPermRequest>()
                .eq(SysPermRequest::getApplicantId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        SysPermRequest req = requestMapper.selectById(id);
        AssertUtil.notNull(req, "申请单不存在");
        AssertUtil.isTrue(userId == req.getApplicantId(), "只能撤回本人申请");
        AssertUtil.isTrue(PENDING.equals(req.getStatus()), "仅待审批可撤回");

        int n = requestMapper.update(null, new LambdaUpdateWrapper<SysPermRequest>()
                .eq(SysPermRequest::getId, id)
                .eq(SysPermRequest::getStatus, PENDING)
                .eq(SysPermRequest::getDeleted, 0)
                .set(SysPermRequest::getStatus, CANCELLED));
        AssertUtil.isTrue(n == 1, "申请状态已变更");
        addLog(id, PENDING, CANCELLED, userId, "撤回申请");
    }

    @Override
    public PageResult<PermRequestVO> pageTodo(PermRequestQuery query) {
        return pageBy(query, new LambdaQueryWrapper<SysPermRequest>()
                .eq(SysPermRequest::getStatus, PENDING));
    }

    @Override
    public PageResult<PermRequestVO> pageDone(PermRequestQuery query) {
        long approverId = StpUtil.getLoginIdAsLong();
        return pageBy(query, new LambdaQueryWrapper<SysPermRequest>()
                .eq(SysPermRequest::getApproverId, approverId)
                .in(SysPermRequest::getStatus, APPROVED, REJECTED));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, PermRequestDecideDTO dto) {
        decide(id, true, dto == null ? null : dto.getOpinion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, PermRequestDecideDTO dto) {
        decide(id, false, dto == null ? null : dto.getOpinion());
    }

    private void decide(Long id, boolean pass, String opinion) {
        long approverId = StpUtil.getLoginIdAsLong();
        SysPermRequest req = requestMapper.selectById(id);
        AssertUtil.notNull(req, "申请单不存在");
        AssertUtil.isTrue(PENDING.equals(req.getStatus()), "申请已处理");
        AssertUtil.isFalse(approverId == req.getApplicantId(), "不能审批本人申请");

        SysUser applicant = userMapper.selectById(req.getApplicantId());
        AssertUtil.notNull(applicant, "申请人不存在或已删除");
        AssertUtil.isTrue(applicant.getStatus() != null && applicant.getStatus() == 1, "申请人已禁用");

        if (pass) {
            SysRole role = roleMapper.selectById(req.getRoleId());
            AssertUtil.notNull(role, "目标角色不存在或已删除");
            AssertUtil.isTrue(role.getStatus() != null && role.getStatus() == 1, "目标角色已禁用");
            AssertUtil.isFalse("admin".equals(role.getRoleCode()), "不可赋超管角色");
        }

        String to = pass ? APPROVED : REJECTED;
        String op = StringUtils.hasText(opinion) ? opinion.trim() : (pass ? "同意" : "驳回");
        LocalDateTime now = LocalDateTime.now();

        // cas思想：利用数据库行锁确保只有一个线程能更新
        int n = requestMapper.update(null, new LambdaUpdateWrapper<SysPermRequest>()
                .eq(SysPermRequest::getId, id)
                .eq(SysPermRequest::getStatus, PENDING)
                .eq(SysPermRequest::getDeleted, 0)
                .set(SysPermRequest::getStatus, to)
                .set(SysPermRequest::getApproverId, approverId)
                .set(SysPermRequest::getApproveOpinion, op)
                .set(SysPermRequest::getApproveTime, now));
        AssertUtil.isTrue(n == 1, "申请已被他人处理");

        if (pass) {
            List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(req.getApplicantId());
            if (roleIds == null || !roleIds.contains(req.getRoleId())) {
                SysUserRole ur = new SysUserRole();
                ur.setId(IdWorker.getId());
                ur.setUserId(req.getApplicantId());
                ur.setRoleId(req.getRoleId());
                ur.setCreateTime(now);
                userRoleMapper.insert(ur);
            }
            StpUtil.logout(req.getApplicantId());
        }
        addLog(id, PENDING, to, approverId, op);
    }

    private PageResult<PermRequestVO> pageBy(PermRequestQuery query, LambdaQueryWrapper<SysPermRequest> base) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();
        if (StringUtils.hasText(query.getStatus())) {
            base.eq(SysPermRequest::getStatus, query.getStatus().trim());
        }
        base.orderByDesc(SysPermRequest::getCreateTime);
        Page<SysPermRequest> page = requestMapper.selectPage(new Page<>(pageNo, pageSize), base);
        List<PermRequestVO> vos = toVos(page.getRecords());
        return PageResult.of(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private List<PermRequestVO> toVos(List<SysPermRequest> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        Set<Long> roleIds = new HashSet<>();
        for (SysPermRequest r : records) {
            userIds.add(r.getApplicantId());
            if (r.getApproverId() != null) {
                userIds.add(r.getApproverId());
            }
            roleIds.add(r.getRoleId());
        }
        Map<Long, SysUser> userMap = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getId, userIds)).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        Map<Long, SysRole> roleMap = roleIds.isEmpty()
                ? Map.of()
                : roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds)).stream()
                        .collect(Collectors.toMap(SysRole::getId, r -> r, (a, b) -> a));

        List<PermRequestVO> list = new ArrayList<>();
        for (SysPermRequest r : records) {
            PermRequestVO vo = new PermRequestVO();
            vo.setId(r.getId());
            vo.setRequestNo(r.getRequestNo());
            vo.setApplicantId(r.getApplicantId());
            SysUser applicant = userMap.get(r.getApplicantId());
            if (applicant != null) {
                vo.setApplicantCode(applicant.getUserCode());
                vo.setApplicantName(applicant.getUserName());
            }
            vo.setRoleId(r.getRoleId());
            SysRole role = roleMap.get(r.getRoleId());
            if (role != null) {
                vo.setRoleCode(role.getRoleCode());
                vo.setRoleName(role.getRoleName());
            }
            vo.setReason(r.getReason());
            vo.setStatus(r.getStatus());
            vo.setApproverId(r.getApproverId());
            if (r.getApproverId() != null) {
                SysUser approver = userMap.get(r.getApproverId());
                if (approver != null) {
                    vo.setApproverCode(approver.getUserCode());
                }
            }
            vo.setApproveOpinion(r.getApproveOpinion());
            vo.setApproveTime(r.getApproveTime());
            vo.setCreateTime(r.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    private void addLog(Long requestId, String from, String to, Long operatorId, String opinion) {
        SysPermRequestLog log = new SysPermRequestLog();
        log.setId(IdWorker.getId());
        log.setRequestId(requestId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorId(operatorId);
        log.setOpinion(opinion);
        log.setCreateTime(LocalDateTime.now());
        logMapper.insert(log);
    }

    private String genRequestNo() {
        return "PR" + IdWorker.getIdStr();
    }
}
