package com.designmd.platform.controller;

import com.designmd.platform.common.PageResult;
import com.designmd.platform.common.Result;
import com.designmd.platform.entity.User;
import com.designmd.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理接口（仅 ADMIN 角色可访问，由 SecurityConfig 统一鉴权）
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public Result<PageResult<User>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(userService.pageList(page, pageSize));
    }

    /**
     * 重置指定用户密码（body 含 newPassword，BCrypt 加密存储）
     */
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("newPassword"));
        return Result.ok();
    }

    /**
     * 删除指定用户（禁止删除自己 / 最后一个 ADMIN / 内置 admin）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        User operator = userService.getByUsername(authentication.getName());
        userService.deleteUser(operator.getId(), id);
        return Result.ok();
    }
}
