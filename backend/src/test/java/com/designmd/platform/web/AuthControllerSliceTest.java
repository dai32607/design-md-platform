package com.designmd.platform.web;

import com.designmd.platform.config.JwtAuthenticationFilter;
import com.designmd.platform.config.SecurityConfig;
import com.designmd.platform.controller.AuthController;
import com.designmd.platform.entity.User;
import com.designmd.platform.service.UserService;
import com.designmd.platform.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 接口切片测试（@WebMvcTest + 真实 SecurityConfig/JWT 过滤器，不连数据库）。
 * <p>覆盖点：注册 / 登录 / 当前用户 / 自助注销四个端点的正常与异常分支，
 * 以及 SecurityConfig 对 {@code /api/auth/**} 的放行规则与 {@code DELETE /api/auth/account} 的登录要求。
 * <p>测试通过真实 JwtUtil 签发 token，端到端验证「过滤器解析 token → 注入 Authentication」链路。
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
class AuthControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("GET /api/auth/me 匿名访问：HTTP 200 + 业务 code 401（修复后的 NPE 兜底行为）")
    void meAnonymousKeepsBusinessCode401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或 token 无效"));
    }

    @Test
    @DisplayName("GET /api/auth/me 携带有效 token：返回当前登录用户")
    void meWithValidTokenReturnsUser() throws Exception {
        when(userService.getByUsername("alice")).thenReturn(user(7L, "alice", "USER"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(9L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("POST /api/auth/register：注册成功返回用户信息")
    void registerReturnsCreatedUser() throws Exception {
        when(userService.register(any(), any(), any())).thenReturn(user(12L, "newbie", "USER"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newbie\",\"password\":\"123456\",\"nickname\":\"新同学\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("newbie"));

        verify(userService).register("newbie", "123456", "新同学");
    }

    @Test
    @DisplayName("POST /api/auth/login：账密正确返回 token")
    void loginReturnsToken() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("token", "jwt-token-value");
        data.put("user", user(7L, "alice", "USER"));
        when(userService.login("alice", "123456")).thenReturn(data);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("jwt-token-value"));
    }

    @Test
    @DisplayName("POST /api/auth/login：账密错误时全局异常处理映射为 HTTP 400 + code 400")
    void loginFailureMappedToHttp400() throws Exception {
        when(userService.login(any(), any())).thenThrow(new IllegalArgumentException("用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("DELETE /api/auth/account 无 token：被 Spring Security 拦截返回 HTTP 401")
    void deleteAccountWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(delete("/api/auth/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"123456\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("DELETE /api/auth/account 密码不匹配：HTTP 400，用户不被删除")
    void deleteAccountWithWrongPasswordIsRejected() throws Exception {
        when(userService.getByUsername("alice")).thenReturn(user(7L, "alice", "USER"));
        doThrow(new IllegalArgumentException("密码错误")).when(userService).deleteAccount(eq(7L), eq("wrong-pass"));

        mockMvc.perform(delete("/api/auth/account")
                        .header("Authorization", bearer(7L, "alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong-pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码错误"));

        verify(userService).deleteAccount(7L, "wrong-pass");
    }

    @Test
    @DisplayName("DELETE /api/auth/account 密码正确：注销成功")
    void deleteAccountSucceeds() throws Exception {
        when(userService.getByUsername("alice")).thenReturn(user(7L, "alice", "USER"));

        mockMvc.perform(delete("/api/auth/account")
                        .header("Authorization", bearer(7L, "alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userService).deleteAccount(7L, "123456");
    }

    private String bearer(Long userId, String username, String role) {
        return "Bearer " + jwtUtil.generateToken(userId, username, role);
    }

    private User user(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }
}
