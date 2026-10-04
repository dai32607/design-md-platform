package com.designmd.platform.web;

import com.designmd.platform.common.PageResult;
import com.designmd.platform.config.JwtAuthenticationFilter;
import com.designmd.platform.config.SecurityConfig;
import com.designmd.platform.controller.AdminBrandController;
import com.designmd.platform.controller.AdminUserController;
import com.designmd.platform.controller.StatsController;
import com.designmd.platform.entity.Brand;
import com.designmd.platform.entity.User;
import com.designmd.platform.service.BrandService;
import com.designmd.platform.service.StatsService;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端与统计接口切片测试（StatsController / AdminUserController / AdminBrandController）。
 * <p>覆盖点：{@code /api/stats/**} 与 {@code /api/admin/**} 的 ADMIN 角色强制校验
 * （匿名 401、普通用户 403、ADMIN 放行），以及管理端用户删除、密码重置、品牌增删改的调用契约。
 */
@WebMvcTest({StatsController.class, AdminUserController.class, AdminBrandController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
class AdminAndStatsSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private StatsService statsService;

    @MockBean
    private UserService userService;

    @MockBean
    private BrandService brandService;

    // ==================== 鉴权 / 权限规则 ====================

    @Test
    @DisplayName("GET /api/stats/overview 匿名 401")
    void statsAnonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/stats/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或 token 无效"));
    }

    @Test
    @DisplayName("GET /api/stats/overview 普通用户：403 无权限访问")
    void statsWithUserRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/api/stats/overview").header("Authorization", bearer(9L, "alice", "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限访问"));

        verify(statsService, never()).overview();
    }

    @Test
    @DisplayName("GET /api/stats/overview ADMIN：返回汇总指标")
    void statsWithAdminRoleIsAllowed() throws Exception {
        Map<String, Object> overview = new HashMap<>();
        overview.put("brandCount", 24);
        overview.put("tokenCount", 1331);
        overview.put("userCount", 4);
        overview.put("fileCount", 24);
        when(statsService.overview()).thenReturn(overview);

        mockMvc.perform(get("/api/stats/overview").header("Authorization", bearer(1L, "admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.brandCount").value(24))
                .andExpect(jsonPath("$.data.tokenCount").value(1331));
    }

    @Test
    @DisplayName("GET /api/stats/category-dist ADMIN：返回分类分布")
    void categoryDistWithAdminRole() throws Exception {
        when(statsService.categoryDist()).thenReturn(List.of(Map.of("category", "SPACING", "count", 535)));

        mockMvc.perform(get("/api/stats/category-dist").header("Authorization", bearer(1L, "admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category").value("SPACING"));
    }

    @Test
    @DisplayName("GET /api/admin/users 普通用户：403")
    void adminUsersWithUserRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(9L, "alice", "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权限访问"));

        verify(userService, never()).pageList(anyLong(), anyLong());
    }

    @Test
    @DisplayName("POST /api/admin/brands 匿名 401")
    void adminCreateBrandAnonymousIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Apple\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 管理端业务契约 ====================

    @Test
    @DisplayName("GET /api/admin/users ADMIN：返回用户分页")
    void adminListUsers() throws Exception {
        when(userService.pageList(1L, 10L)).thenReturn(new PageResult<>(4L, List.of(user(1L, "admin", "ADMIN"))));

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(1L, "admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.records[0].username").value("admin"));
    }

    @Test
    @DisplayName("PUT /api/admin/users/{id}/password ADMIN：重置密码")
    void adminResetPassword() throws Exception {
        mockMvc.perform(put("/api/admin/users/5/password")
                        .header("Authorization", bearer(1L, "admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userService).resetPassword(5L, "newpass123");
    }

    @Test
    @DisplayName("DELETE /api/admin/users/{id} ADMIN：以操作者 id 调用删除保护逻辑")
    void adminDeleteUserUsesOperatorId() throws Exception {
        when(userService.getByUsername("admin")).thenReturn(user(1L, "admin", "ADMIN"));

        mockMvc.perform(delete("/api/admin/users/5").header("Authorization", bearer(1L, "admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userService).deleteUser(1L, 5L);
    }

    @Test
    @DisplayName("DELETE /api/admin/users/{id} 删除自己：服务层约束抛业务异常映射为 400")
    void adminDeleteSelfIsRejected() throws Exception {
        when(userService.getByUsername("admin")).thenReturn(user(1L, "admin", "ADMIN"));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("不能删除当前登录用户"))
                .when(userService).deleteUser(eq(1L), eq(1L));

        mockMvc.perform(delete("/api/admin/users/1").header("Authorization", bearer(1L, "admin", "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能删除当前登录用户"));
    }

    @Test
    @DisplayName("POST /api/admin/brands ADMIN：管理员创建品牌（不强制 custom）")
    void adminCreateBrand() throws Exception {
        when(brandService.createBrand(any())).thenAnswer(inv -> {
            Brand brand = inv.getArgument(0);
            brand.setId(40L);
            return brand;
        });

        mockMvc.perform(post("/api/admin/brands")
                        .header("Authorization", bearer(1L, "admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ant Design\",\"source\":\"system\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(40))
                .andExpect(jsonPath("$.data.source").value("system"));
    }

    @Test
    @DisplayName("DELETE /api/admin/brands/{id} ADMIN：删除品牌")
    void adminDeleteBrand() throws Exception {
        mockMvc.perform(delete("/api/admin/brands/7").header("Authorization", bearer(1L, "admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(brandService).deleteBrand(7L);
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
