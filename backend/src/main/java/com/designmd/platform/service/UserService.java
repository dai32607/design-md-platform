package com.designmd.platform.service;

import com.designmd.platform.common.PageResult;
import com.designmd.platform.entity.User;

import java.util.Map;

public interface UserService {

    User register(String username, String password, String nickname);

    /**
     * 登录成功返回 token 与用户信息
     */
    Map<String, Object> login(String username, String password);

    User getByUsername(String username);

    /**
     * 用户自助注销：校验密码后删除当前用户
     */
    void deleteAccount(Long userId, String password);

    /**
     * 管理员：用户分页列表（不含密码）
     */
    PageResult<User> pageList(long page, long pageSize);

    /**
     * 管理员：重置指定用户密码
     */
    void resetPassword(Long userId, String newPassword);

    /**
     * 管理员：删除指定用户（禁止删除自己 / 最后一个 ADMIN / 内置 admin）
     */
    void deleteUser(Long operatorId, Long userId);
}
