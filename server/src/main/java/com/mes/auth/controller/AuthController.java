package com.mes.auth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.auth.dto.ChangePasswordDTO;
import com.mes.auth.dto.LoginDTO;
import com.mes.auth.dto.RegisterDTO;
import com.mes.auth.service.AuthService;
import com.mes.auth.vo.LoginVO;
import com.mes.auth.vo.UserInfoVO;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册（需 user:add） */
    @SaCheckPermission("user:add")
    @OperLog(module = "认证", action = "注册")
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return R.ok();
    }

    /** 登录 */
    @OperLog(module = "认证", action = "登录")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    /** 退出 */
    @OperLog(module = "认证", action = "退出")
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    /** 当前用户信息 */
    @GetMapping("/info")
    public R<UserInfoVO> info() {
        return R.ok(authService.getInfo());
    }

    /** 本人修改密码（成功后注销，需重新登录） */
    @OperLog(module = "账号", action = "修改密码")
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(dto);
        return R.ok();
    }
}
