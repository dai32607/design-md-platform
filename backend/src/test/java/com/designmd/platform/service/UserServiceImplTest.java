package com.designmd.platform.service;

import com.designmd.platform.entity.User;
import com.designmd.platform.mapper.UserMapper;
import com.designmd.platform.service.impl.UserServiceImpl;
import com.designmd.platform.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 单元测试（Mockito 模拟 Mapper 与密码编码器，不连数据库）。
 * <p>覆盖点：注册校验与角色默认值、登录签发 JWT、注销账号的安全约束、
 * 管理端删除用户的四条保护规则（内置 admin / 删除自己 / 最后一个 ADMIN）与重置密码校验。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserServiceImpl userService;

    private User user(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("$2a$10$encoded");
        user.setRole(role);
        return user;
    }

    @Test
    @DisplayName("register：用户名为空抛 IllegalArgumentException")
    void registerRequiresUsername() {
        assertThatThrownBy(() -> userService.register("  ", "123456", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户名不能为空");
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("register：密码长度不足 6 位抛 IllegalArgumentException")
    void registerRequiresPasswordLength() {
        assertThatThrownBy(() -> userService.register("alice", "12345", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密码长度不能少于 6 位");
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("register：用户名已存在抛 IllegalArgumentException")
    void registerRejectsDuplicatedUsername() {
        when(userMapper.selectOne(any())).thenReturn(user(1L, "alice", "USER"));

        assertThatThrownBy(() -> userService.register("alice", "123456", "Alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户名已存在");
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("register：成功注册默认 USER 角色，昵称缺省时回退为用户名")
    void registerDefaultsRoleAndNickname() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$10$encoded");

        User registered = userService.register("  alice  ", "123456", "  ");

        assertThat(registered.getUsername()).isEqualTo("alice");
        assertThat(registered.getNickname()).isEqualTo("alice");
        assertThat(registered.getRole()).isEqualTo("USER");
        assertThat(registered.getPassword()).isEqualTo("$2a$10$encoded");
        verify(userMapper).insert(registered);
    }

    @Test
    @DisplayName("login：用户不存在或密码错误统一抛「用户名或密码错误」")
    void loginRejectsWrongCredential() {
        when(userMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> userService.login("alice", "123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户名或密码错误");

        when(userMapper.selectOne(any())).thenReturn(user(7L, "alice", "USER"));
        when(passwordEncoder.matches("bad-pass", "$2a$10$encoded")).thenReturn(false);
        assertThatThrownBy(() -> userService.login("alice", "bad-pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    @DisplayName("login：账密正确时返回 JWT 与用户信息")
    void loginIssuesToken() {
        when(userMapper.selectOne(any())).thenReturn(user(7L, "alice", "USER"));
        when(passwordEncoder.matches("123456", "$2a$10$encoded")).thenReturn(true);
        when(jwtUtil.generateToken(7L, "alice", "USER")).thenReturn("jwt-token");

        Map<String, Object> data = userService.login("alice", "123456");

        assertThat(data).containsEntry("token", "jwt-token");
        assertThat(data.get("user")).isInstanceOf(User.class);
        assertThat(((User) data.get("user")).getUsername()).isEqualTo("alice");
        verify(jwtUtil).generateToken(7L, "alice", "USER");
    }

    @Test
    @DisplayName("deleteAccount：密码错误抛 IllegalArgumentException")
    void deleteAccountRejectsWrongPassword() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "alice", "USER"));
        when(passwordEncoder.matches("bad-pass", "$2a$10$encoded")).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteAccount(7L, "bad-pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密码错误");
        verify(userMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("deleteAccount：内置 admin 账号禁止自助注销")
    void deleteAccountProtectsBuiltinAdmin() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "admin", "ADMIN"));
        when(passwordEncoder.matches("see-local-config", "$2a$10$encoded")).thenReturn(true);

        assertThatThrownBy(() -> userService.deleteAccount(1L, "see-local-config"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内置管理员账号不允许注销");
        verify(userMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("deleteAccount：密码正确且非内置账号时删除成功")
    void deleteAccountSucceeds() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "alice", "USER"));
        when(passwordEncoder.matches("123456", "$2a$10$encoded")).thenReturn(true);

        userService.deleteAccount(7L, "123456");

        verify(userMapper).deleteById(7L);
    }

    @Test
    @DisplayName("deleteUser：禁止删除内置 admin 账号")
    void deleteUserProtectsBuiltinAdmin() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "admin", "ADMIN"));

        assertThatThrownBy(() -> userService.deleteUser(9L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内置管理员账号不允许删除");
        verify(userMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("deleteUser：禁止删除自己")
    void deleteUserRejectsSelf() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "alice", "USER"));

        assertThatThrownBy(() -> userService.deleteUser(7L, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能删除自己的账号");
        verify(userMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("deleteUser：禁止删除最后一个管理员")
    void deleteUserKeepsLastAdmin() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "admin2", "ADMIN"));
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> userService.deleteUser(9L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能删除最后一个管理员");
        verify(userMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("deleteUser：删除普通用户成功")
    void deleteUserSucceeds() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, "bob", "USER"));

        userService.deleteUser(1L, 5L);

        verify(userMapper).deleteById(5L);
    }

    @Test
    @DisplayName("resetPassword：新密码不足 6 位抛 IllegalArgumentException")
    void resetPasswordValidatesLength() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, "bob", "USER"));

        assertThatThrownBy(() -> userService.resetPassword(5L, "123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("新密码长度不能少于 6 位");
        verify(userMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("resetPassword：成功时只更新密码字段（加密后写库）")
    void resetPasswordEncodesNewPassword() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, "bob", "USER"));
        when(passwordEncoder.encode("newpass123")).thenReturn("$2a$10$new-encoded");

        userService.resetPassword(5L, "newpass123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(5L);
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$10$new-encoded");
    }

    @Test
    @DisplayName("getByUsername：按用户名精确查询，找不到返回 null")
    void getByUsernameReturnsNullWhenAbsent() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThat(userService.getByUsername("nobody")).isNull();
        verify(userMapper).selectOne(any());
    }
}
