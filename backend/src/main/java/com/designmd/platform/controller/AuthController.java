package com.designmd.platform.controller;

import com.designmd.platform.common.Result;
import com.designmd.platform.entity.User;
import com.designmd.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户认证接口：注册 / 登录 / 当前用户
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<User> register(@RequestBody Map<String, String> body) {
        User user = userService.register(body.get("username"), body.get("password"), body.get("nickname"));
        return Result.ok(user);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return Result.ok(userService.login(body.get("username"), body.get("password")));
    }

    @GetMapping("/me")
    public Result<User> me(Authentication authentication) {
        if (authentication == null || "anonymousUser".equals(authentication.getPrincipal())) {
            return Result.fail(401, "未登录或 token 无效");
        }
        User user = userService.getByUsername(authentication.getName());
        return Result.ok(user);
    }

    /**
     * 用户自助注销：需携带 token，body 中 password 与当前用户密码匹配后删除账号
     */
    @DeleteMapping("/account")
    public Result<Void> deleteAccount(Authentication authentication, @RequestBody Map<String, String> body) {
        if (authentication == null || "anonymousUser".equals(authentication.getPrincipal())) {
            return Result.fail(401, "未登录或 token 无效");
        }
        User user = userService.getByUsername(authentication.getName());
        if (user == null) {
            return Result.fail(401, "用户不存在");
        }
        userService.deleteAccount(user.getId(), body.get("password"));
        return Result.ok();
    }
}
