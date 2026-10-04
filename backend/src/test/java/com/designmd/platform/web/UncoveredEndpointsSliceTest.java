package com.designmd.platform.web;

import com.designmd.platform.config.JwtAuthenticationFilter;
import com.designmd.platform.config.SecurityConfig;
import com.designmd.platform.controller.AdminBrandController;
import com.designmd.platform.controller.DesignMdController;
import com.designmd.platform.controller.StatsController;
import com.designmd.platform.entity.Brand;
import com.designmd.platform.entity.DesignFile;
import com.designmd.platform.entity.DesignToken;
import com.designmd.platform.service.BrandService;
import com.designmd.platform.service.DesignFileService;
import com.designmd.platform.service.DesignTokenService;
import com.designmd.platform.service.StatsService;
import com.designmd.platform.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端点覆盖补齐测试：针对既有 10 个测试类未覆盖的 6 个端点补测，使全部 29 个 REST 端点均有实际用例。
 * <p>本次补齐清单：
 * <ul>
 *   <li>{@code PUT /api/admin/brands/{id}} —— 管理员修改品牌</li>
 *   <li>{@code GET /api/brands/{brandId}/token-categories} —— 品牌 Token 分类列表（GET /api/brands/** 放行）</li>
 *   <li>{@code POST /api/brands/{id}/logo} —— Logo 上传（格式/大小/预置品牌校验 + 入库）</li>
 *   <li>{@code GET /api/files/{id}} —— DESIGN.md 单文件读取（记录其真实鉴权行为）</li>
 *   <li>{@code POST /api/files/upload} —— DESIGN.md 上传解析入库（新增 / 同名更新两条路径）</li>
 *   <li>{@code GET /api/stats/brand-tokens}、{@code GET /api/stats/trend} —— 统计接口剩余两个</li>
 * </ul>
 * Logo 落盘的兼容备份目录通过 {@code app.upload-dir} 指向 {@code target/test-uploads}，避免污染工程根目录。
 */
@WebMvcTest({DesignMdController.class, AdminBrandController.class, StatsController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties = "app.upload-dir=target/test-uploads")
class UncoveredEndpointsSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private BrandService brandService;

    @MockBean
    private DesignTokenService designTokenService;

    @MockBean
    private DesignFileService designFileService;

    @MockBean
    private StatsService statsService;

    // ==================== 1. PUT /api/admin/brands/{id} ====================

    @Test
    @DisplayName("PUT /api/admin/brands/{id} 匿名：401")
    void adminUpdateBrandAnonymousIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/admin/brands/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NewName\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verify(brandService, never()).updateBrand(any(), any());
    }

    @Test
    @DisplayName("PUT /api/admin/brands/{id} ADMIN：按 id 调用服务层更新")
    void adminUpdateBrand() throws Exception {
        when(brandService.updateBrand(eq(7L), any())).thenAnswer(inv -> {
            Brand brand = inv.getArgument(1);
            brand.setId(7L);
            brand.setSource("system");
            brand.setName("Ant Design");
            return brand;
        });

        mockMvc.perform(put("/api/admin/brands/7")
                        .header("Authorization", bearer(1L, "admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ant Design\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.name").value("Ant Design"));

        verify(brandService).updateBrand(eq(7L), any());
    }

    // ==================== 2. GET /api/brands/{brandId}/token-categories ====================

    @Test
    @DisplayName("GET /api/brands/{brandId}/token-categories 匿名放行：返回分类列表")
    void tokenCategoriesIsPublic() throws Exception {
        when(designTokenService.getCategoriesByBrandId(3L))
                .thenReturn(List.of("COLORS", "SPACING", "TYPOGRAPHY"));

        mockMvc.perform(get("/api/brands/3/token-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0]").value("COLORS"));

        verify(designTokenService).getCategoriesByBrandId(3L);
    }

    // ==================== 3. POST /api/brands/{id}/logo ====================

    @Test
    @DisplayName("POST /api/brands/{id}/logo 匿名：401")
    void uploadLogoAnonymousIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/brands/9/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", pngBytes())))
                .andExpect(status().isUnauthorized());

        verify(brandService, never()).updateLogo(any(), any(), anyString());
    }

    @Test
    @DisplayName("POST /api/brands/{id}/logo 登录用户 + 合法 png：二进制入库并返回 logoUrl")
    void uploadLogoSuccess() throws Exception {
        Brand custom = brand(9L, "linear", "custom", 2L);
        when(brandService.getById(9L)).thenReturn(custom);
        when(brandService.updateLogo(eq(9L), any(), eq("image/png"))).thenAnswer(inv -> {
            Brand b = brand(9L, "linear", "custom");
            b.setLogoMime("image/png");
            b.setLogoUrl("/api/brands/9/logo?v=1");
            return b;
        });

        mockMvc.perform(multipart("/api/brands/9/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", pngBytes()))
                        .header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.logoMime").value("image/png"))
                .andExpect(jsonPath("$.data.logoUrl").value("/api/brands/9/logo?v=1"));

        verify(brandService).updateLogo(eq(9L), any(), eq("image/png"));
    }

    @Test
    @DisplayName("POST /api/brands/{id}/logo 非图片扩展名：业务 code 400 拦截")
    void uploadLogoWithIllegalExtension() throws Exception {
        mockMvc.perform(multipart("/api/brands/9/logo")
                        .file(new MockMultipartFile("file", "note.txt", "text/plain", "hi".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅支持 png/jpg/jpeg/svg/webp 格式的图片"));

        verify(brandService, never()).updateLogo(any(), any(), anyString());
    }

    @Test
    @DisplayName("POST /api/brands/{id}/logo 空文件：业务 code 400")
    void uploadLogoWithEmptyFile() throws Exception {
        mockMvc.perform(multipart("/api/brands/9/logo")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件不能为空"));
    }

    @Test
    @DisplayName("POST /api/brands/{id}/logo 超过 2MB：业务 code 400")
    void uploadLogoTooLarge() throws Exception {
        mockMvc.perform(multipart("/api/brands/9/logo")
                        .file(new MockMultipartFile("file", "big.png", "image/png", new byte[3 * 1024 * 1024]))
                        .header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("图片大小不能超过 2MB"));

        verify(brandService, never()).updateLogo(any(), any(), anyString());
    }

    @Test
    @DisplayName("POST /api/brands/{id}/logo 非创建者：HTTP 403 且不入库")
    void uploadLogoForOtherOwnerIsForbidden() throws Exception {
        when(brandService.getById(9L)).thenReturn(brand(9L, "linear", "custom", 7L));

        mockMvc.perform(multipart("/api/brands/9/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", pngBytes()))
                        .header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限修改该品牌"));

        verify(brandService, never()).updateLogo(any(), any(), anyString());
    }

    @Test
    @DisplayName("POST /api/brands/{id}/logo ADMIN：可更换他人 custom 品牌 Logo")
    void uploadLogoByAdminForOtherOwner() throws Exception {
        when(brandService.getById(9L)).thenReturn(brand(9L, "linear", "custom", 7L));
        when(brandService.updateLogo(eq(9L), any(), eq("image/png"))).thenAnswer(inv -> {
            Brand b = brand(9L, "linear", "custom", 7L);
            b.setLogoMime("image/png");
            b.setLogoUrl("/api/brands/9/logo?v=1");
            return b;
        });

        mockMvc.perform(multipart("/api/brands/9/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", pngBytes()))
                        .header("Authorization", bearer(1L, "admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.logoUrl").value("/api/brands/9/logo?v=1"));

        verify(brandService).updateLogo(eq(9L), any(), eq("image/png"));
    }

    @Test
    @DisplayName("POST /api/brands/{id}/logo 预置品牌：业务 code 400 且不入库")
    void uploadLogoForSystemBrandIsRejected() throws Exception {
        when(brandService.getById(1L)).thenReturn(brand(1L, "Apple", "system"));

        mockMvc.perform(multipart("/api/brands/1/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", pngBytes()))
                        .header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("预置品牌不允许修改"));

        verify(brandService, never()).updateLogo(any(), any(), anyString());
    }

    // ==================== 4. PUT/DELETE /api/tokens/{id} 归属校验 ====================

    @Test
    @DisplayName("PUT /api/tokens/{id} 预置品牌 Token：HTTP 400 且不更新")
    void updateTokenForSystemBrandIsRejected() throws Exception {
        DesignToken t = token(50L, "COLORS", "#111111");
        t.setBrandId(1L);
        when(designTokenService.getById(50L)).thenReturn(t);
        when(brandService.getById(1L)).thenReturn(brand(1L, "Apple", "system"));

        mockMvc.perform(put("/api/tokens/50")
                        .header("Authorization", bearer(2L, "alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenValue\":\"#FFFFFF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("预置品牌不允许修改"));

        verify(designTokenService, never()).updateToken(any(), any());
    }

    @Test
    @DisplayName("PUT /api/tokens/{id} 非创建者：HTTP 403")
    void updateTokenForOtherOwnerIsForbidden() throws Exception {
        DesignToken t = token(51L, "COLORS", "#111111");
        t.setBrandId(9L);
        when(designTokenService.getById(51L)).thenReturn(t);
        when(brandService.getById(9L)).thenReturn(brand(9L, "linear", "custom", 7L));

        mockMvc.perform(put("/api/tokens/51")
                        .header("Authorization", bearer(2L, "alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenValue\":\"#FFFFFF\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限修改该品牌"));

        verify(designTokenService, never()).updateToken(any(), any());
    }

    @Test
    @DisplayName("PUT /api/tokens/{id} 创建者本人：允许更新")
    void updateTokenByOwnerSucceeds() throws Exception {
        DesignToken t = token(52L, "COLORS", "#111111");
        t.setBrandId(9L);
        when(designTokenService.getById(52L)).thenReturn(t);
        when(brandService.getById(9L)).thenReturn(brand(9L, "linear", "custom", 2L));
        when(designTokenService.updateToken(eq(52L), any())).thenAnswer(inv -> {
            DesignToken updated = token(52L, "COLORS", "#FFFFFF");
            updated.setBrandId(9L);
            return updated;
        });

        mockMvc.perform(put("/api/tokens/52")
                        .header("Authorization", bearer(2L, "alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenValue\":\"#FFFFFF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tokenValue").value("#FFFFFF"));

        verify(designTokenService).updateToken(eq(52L), any());
    }

    @Test
    @DisplayName("PUT /api/tokens/{id} ADMIN：可更新他人 custom 品牌 Token")
    void updateTokenByAdminSucceeds() throws Exception {
        DesignToken t = token(53L, "COLORS", "#111111");
        t.setBrandId(9L);
        when(designTokenService.getById(53L)).thenReturn(t);
        when(brandService.getById(9L)).thenReturn(brand(9L, "linear", "custom", 7L));
        when(designTokenService.updateToken(eq(53L), any())).thenReturn(t);

        mockMvc.perform(put("/api/tokens/53")
                        .header("Authorization", bearer(1L, "admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenValue\":\"#FFFFFF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(designTokenService).updateToken(eq(53L), any());
    }

    @Test
    @DisplayName("DELETE /api/tokens/{id} 预置品牌 Token：HTTP 400 且不删除")
    void deleteTokenForSystemBrandIsRejected() throws Exception {
        DesignToken t = token(54L, "COLORS", "#111111");
        t.setBrandId(1L);
        when(designTokenService.getById(54L)).thenReturn(t);
        when(brandService.getById(1L)).thenReturn(brand(1L, "Apple", "system"));

        mockMvc.perform(delete("/api/tokens/54").header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("预置品牌不允许修改"));

        verify(designTokenService, never()).deleteToken(any());
    }

    @Test
    @DisplayName("DELETE /api/tokens/{id} 非创建者：HTTP 403")
    void deleteTokenForOtherOwnerIsForbidden() throws Exception {
        DesignToken t = token(55L, "COLORS", "#111111");
        t.setBrandId(9L);
        when(designTokenService.getById(55L)).thenReturn(t);
        when(brandService.getById(9L)).thenReturn(brand(9L, "linear", "custom", 7L));

        mockMvc.perform(delete("/api/tokens/55").header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限修改该品牌"));

        verify(designTokenService, never()).deleteToken(any());
    }

    @Test
    @DisplayName("DELETE /api/tokens/{id} 创建者本人：删除成功")
    void deleteTokenByOwnerSucceeds() throws Exception {
        DesignToken t = token(56L, "COLORS", "#111111");
        t.setBrandId(9L);
        when(designTokenService.getById(56L)).thenReturn(t);
        when(brandService.getById(9L)).thenReturn(brand(9L, "linear", "custom", 2L));
        when(designTokenService.deleteToken(56L)).thenReturn(true);

        mockMvc.perform(delete("/api/tokens/56").header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(designTokenService).deleteToken(56L);
    }

    // ==================== 5. GET /api/files/{id} ====================

    @Test
    @DisplayName("GET /api/files/{id} 匿名放行：与前端 /file/:id 公开路由对齐，无需登录即可读取 DESIGN.md")
    void getDesignFileAnonymousIsPublic() throws Exception {
        DesignFile file = new DesignFile();
        file.setId(6L);
        file.setBrandId(9L);
        file.setTitle("Linear");
        file.setContent("# Linear\n\n## Colors");
        when(designFileService.getById(6L)).thenReturn(file);

        mockMvc.perform(get("/api/files/6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("Linear"))
                .andExpect(jsonPath("$.data.content").value("# Linear\n\n## Colors"));

        verify(designFileService).getById(6L);
    }

    @Test
    @DisplayName("GET /api/files/{id} 已登录：返回 DESIGN.md 文件内容")
    void getDesignFileWithToken() throws Exception {
        DesignFile file = new DesignFile();
        file.setId(6L);
        file.setBrandId(9L);
        file.setTitle("Linear");
        file.setContent("# Linear\n\n## Colors");
        when(designFileService.getById(6L)).thenReturn(file);

        mockMvc.perform(get("/api/files/6").header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("Linear"))
                .andExpect(jsonPath("$.data.content").value("# Linear\n\n## Colors"));

        verify(designFileService).getById(6L);
    }

    // ==================== 5. POST /api/files/upload ====================

    @Test
    @DisplayName("POST /api/files/upload 匿名：401")
    void uploadDesignMdAnonymousIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/files/upload")
                        .file(mdFile("NewBrand.md"))
                        .param("brandId", "9"))
                .andExpect(status().isUnauthorized());

        verify(designFileService, never()).saveDesignFile(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/files/upload 登录用户 + 新文件：保存文件并解析 Token 入库")
    void uploadDesignMdCreatesNewFile() throws Exception {
        when(brandService.getById(9L)).thenReturn(brand(9L, "linear", "custom", 2L));
        when(designFileService.findByBrandIdAndTitle(9L, "NewBrand")).thenReturn(null);
        when(designFileService.saveDesignFile(eq(9L), eq("NewBrand"), anyString())).thenAnswer(inv -> {
            DesignFile f = new DesignFile();
            f.setId(31L);
            f.setBrandId(9L);
            f.setTitle(inv.getArgument(1));
            f.setContent(inv.getArgument(2));
            return f;
        });
        when(designTokenService.parseAndSaveTokens(eq(9L), anyString()))
                .thenReturn(List.of(token(101L, "COLORS", "#111111"), token(102L, "SPACING", "8px")));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(mdFile("NewBrand.md"))
                        .param("brandId", "9")
                        .header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tokenCount").value(2))
                .andExpect(jsonPath("$.data.updated").value(false))
                .andExpect(jsonPath("$.data.file.title").value("NewBrand"));

        verify(designFileService).saveDesignFile(eq(9L), eq("NewBrand"), anyString());
        verify(designFileService, never()).updateContent(any(), anyString());
        verify(designTokenService).parseAndSaveTokens(eq(9L), anyString());
    }

    @Test
    @DisplayName("POST /api/files/upload 同名已存在：更新内容并清理旧的自动解析 Token")
    void uploadDesignMdUpdatesExistingFile() throws Exception {
        when(brandService.getById(9L)).thenReturn(brand(9L, "linear", "custom", 2L));
        DesignFile existing = new DesignFile();
        existing.setId(11L);
        existing.setBrandId(9L);
        existing.setTitle("NewBrand");
        when(designFileService.findByBrandIdAndTitle(9L, "NewBrand")).thenReturn(existing);
        when(designFileService.updateContent(eq(11L), anyString())).thenAnswer(inv -> {
            DesignFile f = new DesignFile();
            f.setId(11L);
            f.setTitle("NewBrand");
            f.setContent(inv.getArgument(1));
            return f;
        });
        DesignToken auto = token(201L, "COLORS", "#222222");
        auto.setDescription("从 DESIGN.md 自动解析：颜色");
        DesignToken manual = token(202L, "SPACING", "16px");
        manual.setDescription("人工维护");
        when(designTokenService.listByBrandId(9L)).thenReturn(List.of(auto, manual));
        when(designTokenService.parseAndSaveTokens(eq(9L), anyString())).thenReturn(List.of(auto));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(mdFile("NewBrand.md"))
                        .param("brandId", "9")
                        .header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.updated").value(true))
                .andExpect(jsonPath("$.data.file.id").value(11));

        verify(designFileService).updateContent(eq(11L), anyString());
        verify(designFileService, never()).saveDesignFile(any(), anyString(), anyString());
        verify(designTokenService).deleteToken(201L);
        verify(designTokenService, never()).deleteToken(202L);
    }

    @Test
    @DisplayName("POST /api/files/upload 非创建者：HTTP 403 且不保存")
    void uploadDesignMdForOtherOwnerIsForbidden() throws Exception {
        when(brandService.getById(9L)).thenReturn(brand(9L, "linear", "custom", 7L));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(mdFile("NewBrand.md"))
                        .param("brandId", "9")
                        .header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限上传文件"));

        verify(designFileService, never()).saveDesignFile(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/files/upload 预置品牌：HTTP 403 + code 403")
    void uploadDesignMdForSystemBrandIsForbidden() throws Exception {
        when(brandService.getById(1L)).thenReturn(brand(1L, "Apple", "system"));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(mdFile("Apple.md"))
                        .param("brandId", "1")
                        .header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("预置品牌不允许上传文件"));

        verify(designFileService, never()).saveDesignFile(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/files/upload 非 .md 扩展名：业务 code 400")
    void uploadDesignMdWithWrongExtension() throws Exception {
        when(brandService.getById(9L)).thenReturn(brand(9L, "linear", "custom", 2L));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", "spec.txt", "text/plain", "# hi".getBytes(StandardCharsets.UTF_8)))
                        .param("brandId", "9")
                        .header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅支持上传 .md 格式的 Markdown 文件"));

        verify(designFileService, never()).saveDesignFile(any(), anyString(), anyString());
    }

    // ==================== 6. GET /api/stats/brand-tokens 与 /api/stats/trend ====================

    @Test
    @DisplayName("GET /api/stats/brand-tokens 普通用户：403")
    void brandTokensWithUserRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/api/stats/brand-tokens").header("Authorization", bearer(2L, "alice", "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限访问"));

        verify(statsService, never()).brandTokens();
    }

    @Test
    @DisplayName("GET /api/stats/brand-tokens ADMIN：返回各品牌 Token 数")
    void brandTokensWithAdminRole() throws Exception {
        when(statsService.brandTokens())
                .thenReturn(List.of(Map.of("brandName", "Linear", "count", 412), Map.of("brandName", "Vercel", "count", 388)));

        mockMvc.perform(get("/api/stats/brand-tokens").header("Authorization", bearer(1L, "admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].brandName").value("Linear"))
                .andExpect(jsonPath("$.data[0].count").value(412));
    }

    @Test
    @DisplayName("GET /api/stats/trend ADMIN：返回按天聚合的品牌/用户新增趋势")
    void trendWithAdminRole() throws Exception {
        when(statsService.trend())
                .thenReturn(List.of(Map.of("date", "2026-09-10", "brandCount", 2, "userCount", 1)));

        mockMvc.perform(get("/api/stats/trend").header("Authorization", bearer(1L, "admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].date").value("2026-09-10"))
                .andExpect(jsonPath("$.data[0].brandCount").value(2))
                .andExpect(jsonPath("$.data[0].userCount").value(1));
    }

    // ==================== 辅助方法 ====================

    private String bearer(Long userId, String username, String role) {
        return "Bearer " + jwtUtil.generateToken(userId, username, role);
    }

    private Brand brand(Long id, String name, String source) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setName(name);
        brand.setSource(source);
        return brand;
    }

    private Brand brand(Long id, String name, String source, Long creatorId) {
        Brand brand = brand(id, name, source);
        brand.setCreatorId(creatorId);
        return brand;
    }

    private DesignToken token(Long id, String category, String value) {
        DesignToken token = new DesignToken();
        token.setId(id);
        token.setCategory(category);
        token.setTokenValue(value);
        return token;
    }

    private MockMultipartFile mdFile(String filename) {
        String content = "# Brand\n\n## Colors\n- #111111\n\n## Spacing\n- 8px\n";
        return new MockMultipartFile("file", filename, "text/markdown", content.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    }
}
