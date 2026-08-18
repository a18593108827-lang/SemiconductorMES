package com.mes.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.mes.common.PasswordUtil;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 初始化默认管理员：admin / 123456，绑定 admin 角色
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;

    @Override
    public void run(ApplicationArguments args) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<>());
        if (count > 0) {
            return;
        }

        SysUser admin = new SysUser();
        admin.setUserCode("admin");
        admin.setUserName("管理员");
        admin.setPassword(PasswordUtil.encode("123456"));
        admin.setStatus(1);
        admin.setMustChangePwd(0);
        admin.setSource("local");
        sysUserMapper.insert(admin);
        sysUserMapper.insertUserRole(IdWorker.getId(), admin.getId());
        log.info("已初始化默认用户 admin / 123456");
    }
}
