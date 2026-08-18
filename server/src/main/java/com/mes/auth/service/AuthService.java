package com.mes.auth.service;

import com.mes.auth.dto.ChangePasswordDTO;
import com.mes.auth.dto.LoginDTO;
import com.mes.auth.dto.RegisterDTO;
import com.mes.auth.vo.LoginVO;
import com.mes.auth.vo.UserInfoVO;

/**
 * 认证服务
 */
public interface AuthService {

    /** 注册 */
    void register(RegisterDTO dto);

    /** 登录 */
    LoginVO login(LoginDTO dto);

    /** 退出 */
    void logout();

    /** 当前用户信息 */
    UserInfoVO getInfo();

    /** 本人修改密码（成功后注销会话） */
    void changePassword(ChangePasswordDTO dto);
}
