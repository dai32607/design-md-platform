package com.designmd.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.designmd.platform.common.PageResult;
import com.designmd.platform.entity.User;
import com.designmd.platform.mapper.UserMapper;
import com.designmd.platform.service.UserService;
import com.designmd.platform.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public User register(String username, String password, String nickname) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于 6 位");
        }
        if (getByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname == null || nickname.trim().isEmpty() ? username.trim() : nickname.trim());
        user.setRole("USER");
        userMapper.insert(user);
        return user;
    }

    @Override
    public Map<String, Object> login(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        User user = getByUsername(username.trim());
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        return data;
    }

    @Override
    public User getByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
    }

    @Override
    public void deleteAccount(Long userId, String password) {
        if (userId == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("密码错误");
        }
        // 内置 admin 账号禁止自助注销，避免误删管理入口
        if ("admin".equals(user.getUsername())) {
            throw new IllegalArgumentException("内置管理员账号不允许注销");
        }
        userMapper.deleteById(userId);
    }

    @Override
    public PageResult<User> pageList(long page, long pageSize) {
        Page<User> p = new Page<>(page, pageSize);
        Page<User> result = userMapper.selectPage(p, new LambdaQueryWrapper<User>()
                .orderByAsc(User::getId));
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Override
    public void resetPassword(Long userId, String newPassword) {
        if (userId == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度不能少于 6 位");
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
    }

    @Override
    public void deleteUser(Long operatorId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        User target = userMapper.selectById(userId);
        if (target == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        // 禁止删除内置 admin 账号
        if (target.getId() == 1L || "admin".equals(target.getUsername())) {
            throw new IllegalArgumentException("内置管理员账号不允许删除");
        }
        // 禁止删除自己
        if (operatorId != null && operatorId.equals(userId)) {
            throw new IllegalArgumentException("不能删除自己的账号");
        }
        // 禁止删除最后一个 ADMIN
        if ("ADMIN".equals(target.getRole())) {
            Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getRole, "ADMIN"));
            if (adminCount != null && adminCount <= 1) {
                throw new IllegalArgumentException("不能删除最后一个管理员");
            }
        }
        userMapper.deleteById(userId);
    }
}
