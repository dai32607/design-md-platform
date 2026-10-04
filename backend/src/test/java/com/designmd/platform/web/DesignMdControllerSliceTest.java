package com.designmd.platform.web;

import com.designmd.platform.config.JwtAuthenticationFilter;
import com.designmd.platform.config.SecurityConfig;
import com.designmd.platform.common.PageResult;
import com.designmd.platform.controller.DesignMdController;
import com.designmd.platform.entity.Brand;
import com.designmd.platform.entity.DesignFile;
import com.designmd.platform.entity.DesignToken;
import com.designmd.platform.service.BrandService;
import com.designmd.platform.service.DesignFileService;
import com.designmd.platform.service.DesignTokenService;
import com.designmd.platform.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DesignMdController 接口切片测试（核心业务接口：品牌 / Token / DESIGN.md 文件 / Logo 读图）。
 * <p>覆盖点：
 * <ol>
 *   <li>放行规则：GET /api/brands/** 匿名可读，POST/PUT/DELETE 需登录；</li>
 *   <li>权限规则：预置品牌（system）禁改禁删、非创建者不可改删、创建品牌强制 source=custom；</li>
 *   <li>共用返回体与全局异常映射（业务异常 → 对应 HTTP 状态码 + code）；</li>
 *   <li>Logo 二进制读图接口的 ETag / 304 协商缓存与未设置头像时的 404；</li>
 *   <li>DESIGN.md 上传：预置品牌 403、同名文件覆盖时清理旧自动解析 Token；</li>
 * </ol>
 */
@WebMvcTest(DesignMdController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
class DesignMdControllerSliceTest {

    private static final String ADMIN_MD = "## Colors\n- --color-primary: #2563EB\n- --color-text: #0F172A\n"
            + "## Typography\n- Font Size: 16px\n- Font Family: Inter\n"
            + "## Spacing\n- Padding: 16px\n- Gap: 12px";

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

    // ==================== 品牌列表 / 详情 ====================

    @Test
    @DisplayName("GET /api/brands 匿名可访问：走服务端分页返回品牌列表")
    void listBrandsIsPublic() throws Exception {
        when(brandService.pageList(anyLong(), anyLong(), any(), any(), anyBoolean(), any()))
                .thenReturn(new PageResult<>(24L, List.of(brand(1L, "Apple", "system", null))));

        mockMvc.perform(get("/api/brands").param("page", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(24))
                .andExpect(jsonPath("$.data.records[0].name").value("Apple"));
    }

    @Test
    @DisplayName("GET /api/brands?mine=true 匿名访问：业务 401（提示先登录）")
    void listMineAnonymousReturns401() throws Exception {
        mockMvc.perform(get("/api/brands").param("mine", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录后再查看我的品牌"));
    }

    @Test
    @DisplayName("GET /api/brands/{id} 匿名可访问：返回品牌详情")
    void getBrandIsPublic() throws Exception {
        when(brandService.getById(1L)).thenReturn(brand(1L, "Apple", "system", null));

        mockMvc.perform(get("/api/brands/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Apple"));
    }

    @Test
    @DisplayName("GET /api/brands/{id} 品牌不存在：HTTP 400（业务异常映射）")
    void getBrandNotFoundMappedTo400() throws Exception {
        when(brandService.getById(9L)).thenThrow(new IllegalArgumentException("品牌不存在: 9"));

        mockMvc.perform(get("/api/brands/9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("品牌不存在: 9"));
    }

    // ==================== 品牌创建 / 修改 / 删除的权限规则 ====================

    @Test
    @DisplayName("POST /api/brands 匿名：被 Spring Security 拦截返回 401")
    void createBrandRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aurora\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("POST /api/brands 登录用户：强制 source=custom，creatorId 取 token 中的 userId")
    void createBrandForcesCustomSource() throws Exception {
        when(brandService.createBrand(any())).thenAnswer(inv -> {
            Brand brand = inv.getArgument(0);
            brand.setId(30L);
            return brand;
        });

        mockMvc.perform(post("/api/brands")
                        .header("Authorization", bearer(9L, "alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aurora\",\"source\":\"system\",\"creatorId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(30))
                .andExpect(jsonPath("$.data.source").value("custom"))
                .andExpect(jsonPath("$.data.creatorId").value(9));

        ArgumentCaptor<Brand> captor = ArgumentCaptor.forClass(Brand.class);
        verify(brandService).createBrand(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("custom");
        assertThat(captor.getValue().getCreatorId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("PUT /api/brands/{id} 预置品牌：HTTP 400 且不落库")
    void updateSystemBrandIsRejected() throws Exception {
        when(brandService.getById(2L)).thenReturn(brand(2L, "Apple", "system", null));

        mockMvc.perform(put("/api/brands/2")
                        .header("Authorization", bearer(9L, "alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Apple2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("预置品牌不允许修改"));

        verify(brandService, never()).updateBrand(any(), any());
    }

    @Test
    @DisplayName("PUT /api/brands/{id} 非创建者：HTTP 403")
    void updateOthersBrandIsForbidden() throws Exception {
        when(brandService.getById(3L)).thenReturn(brand(3L, "Discord", "custom", 5L));

        mockMvc.perform(put("/api/brands/3")
                        .header("Authorization", bearer(9L, "alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Discord2\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限修改该品牌"));

        verify(brandService, never()).updateBrand(any(), any());
    }

    @Test
    @DisplayName("PUT /api/brands/{id} 创建者本人：允许修改")
    void updateOwnBrandSucceeds() throws Exception {
        when(brandService.getById(3L)).thenReturn(brand(3L, "Discord", "custom", 9L));
        when(brandService.updateBrand(eq(3L), any())).thenReturn(brand(3L, "Discord2", "custom", 9L));

        mockMvc.perform(put("/api/brands/3")
                        .header("Authorization", bearer(9L, "alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Discord2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("Discord2"));

        verify(brandService).updateBrand(eq(3L), any());
    }

    @Test
    @DisplayName("DELETE /api/brands/{id} 预置品牌：HTTP 400 且不执行删除")
    void deleteSystemBrandIsRejected() throws Exception {
        when(brandService.getById(2L)).thenReturn(brand(2L, "Apple", "system", null));

        mockMvc.perform(delete("/api/brands/2").header("Authorization", bearer(9L, "alice", "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("预置品牌不允许删除"));

        verify(brandService, never()).deleteBrand(any());
    }

    @Test
    @DisplayName("DELETE /api/brands/{id} 创建者本人：删除并级联清理")
    void deleteOwnBrandSucceeds() throws Exception {
        when(brandService.getById(3L)).thenReturn(brand(3L, "Discord", "custom", 9L));

        mockMvc.perform(delete("/api/brands/3").header("Authorization", bearer(9L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(brandService).deleteBrand(3L);
    }

    @Test
    @DisplayName("DELETE /api/brands/{id} ADMIN：可删除他人 custom 品牌")
    void adminCanDeleteOthersCustomBrand() throws Exception {
        when(brandService.getById(3L)).thenReturn(brand(3L, "Discord", "custom", 5L));

        mockMvc.perform(delete("/api/brands/3").header("Authorization", bearer(1L, "admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(brandService).deleteBrand(3L);
    }

    // ==================== Token 接口 ====================

    @Test
    @DisplayName("GET /api/brands/{brandId}/tokens 匿名可访问：按分类分页返回")
    void listTokensIsPublic() throws Exception {
        when(designTokenService.pageByBrandId(anyLong(), any(), anyLong(), anyLong()))
                .thenReturn(new PageResult<>(2L, List.of(token(1L, 3L, "COLORS", "--color-primary", "#2563EB"))));

        mockMvc.perform(get("/api/brands/3/tokens").param("category", "COLORS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].tokenName").value("--color-primary"));
    }

    @Test
    @DisplayName("PUT /api/tokens/{id} 未登录：401；Token 不存在：服务层异常映射为 400")
    void updateTokenFailureMappedTo400() throws Exception {
        doThrow(new IllegalArgumentException("Token 不存在或已删除"))
                .when(designTokenService).getById(5L);

        mockMvc.perform(put("/api/tokens/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenValue\":\"#FFFFFF\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/tokens/5")
                        .header("Authorization", bearer(9L, "alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenValue\":\"#FFFFFF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Token 不存在或已删除"));

        verify(designTokenService, never()).updateToken(any(), any());
    }

    @Test
    @DisplayName("DELETE /api/tokens/{id} 登录用户（品牌创建者）：删除成功")
    void deleteTokenSucceeds() throws Exception {
        when(designTokenService.getById(6L)).thenReturn(token(6L, 9L, "COLORS", "--color-primary", "#2563EB"));
        when(brandService.getById(9L)).thenReturn(brand(9L, "linear", "custom", 9L));

        mockMvc.perform(delete("/api/tokens/6").header("Authorization", bearer(9L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(designTokenService).deleteToken(6L);
    }

    // ==================== DESIGN.md 文件接口 ====================

    @Test
    @DisplayName("GET /api/brands/{brandId}/files 匿名可访问：返回文件列表")
    void listDesignFilesIsPublic() throws Exception {
        when(designFileService.listByBrandId(3L)).thenReturn(List.of(designFile(10L, 3L, "spec")));

        mockMvc.perform(get("/api/brands/3/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("spec"));
    }

    @Test
    @DisplayName("POST /api/files/upload 预置品牌：HTTP 403 拒绝上传")
    void uploadDesignMdForSystemBrandIsForbidden() throws Exception {
        when(brandService.getById(2L)).thenReturn(brand(2L, "Apple", "system", null));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", "spec.md", "text/markdown", ADMIN_MD.getBytes(StandardCharsets.UTF_8)))
                        .param("brandId", "2")
                        .header("Authorization", bearer(9L, "alice", "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("预置品牌不允许上传文件"));

        verify(designFileService, never()).saveDesignFile(any(), any(), any());
    }

    @Test
    @DisplayName("POST /api/files/upload 同名文件：覆盖内容并清理旧的自动解析 Token")
    void uploadDesignMdReplacesAutoParsedTokens() throws Exception {
        when(brandService.getById(3L)).thenReturn(brand(3L, "Discord", "custom", 9L));
        DesignFile existing = designFile(10L, 3L, "spec");
        when(designFileService.findByBrandIdAndTitle(3L, "spec")).thenReturn(existing);
        when(designFileService.updateContent(10L, ADMIN_MD)).thenReturn(existing);
        DesignToken autoOld = token(21L, 3L, "COLORS", "--color-primary", "#111111");
        autoOld.setDescription("从 DESIGN.md 自动解析");
        DesignToken manual = token(22L, 3L, "COLORS", "--color-manual", "#222222");
        manual.setDescription("手工维护的令牌");
        when(designTokenService.listByBrandId(3L)).thenReturn(List.of(autoOld, manual));
        when(designTokenService.parseAndSaveTokens(3L, ADMIN_MD))
                .thenReturn(List.of(token(31L, 3L, "COLORS", "--color-primary", "#2563EB")));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", "spec.md", "text/markdown", ADMIN_MD.getBytes(StandardCharsets.UTF_8)))
                        .param("brandId", "3")
                        .header("Authorization", bearer(9L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.updated").value(true))
                .andExpect(jsonPath("$.data.tokenCount").value(1));

        verify(designTokenService).deleteToken(21L);
        verify(designTokenService, never()).deleteToken(22L);
    }

    @Test
    @DisplayName("POST /api/files/upload 非 .md 文件：HTTP 200 + 业务 code 400（项目统一返回风格）")
    void uploadNonMarkdownRejected() throws Exception {
        when(brandService.getById(3L)).thenReturn(brand(3L, "Discord", "custom", 9L));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", "spec.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)))
                        .param("brandId", "3")
                        .header("Authorization", bearer(9L, "alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅支持上传 .md 格式的 Markdown 文件"));

        verify(designFileService, never()).saveDesignFile(any(), any(), any());
    }

    // ==================== Logo 二进制读图 ====================

    @Test
    @DisplayName("GET /api/brands/{id}/logo 匿名可访问：返回二进制图片并带 ETag")
    void getBrandLogoReturnsBinary() throws Exception {
        Brand brand = brand(1L, "Apple", "system", null);
        brand.setLogoData(new byte[]{1, 2, 3});
        brand.setLogoMime("image/png");
        when(brandService.getById(1L)).thenReturn(brand);

        mockMvc.perform(get("/api/brands/1/logo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("ETag", "\"1-3-0\""))
                .andExpect(header().exists("Cache-Control"));
    }

    @Test
    @DisplayName("GET /api/brands/{id}/logo 命中 If-None-Match：返回 304 协商缓存")
    void getBrandLogoSupportsNotModified() throws Exception {
        Brand brand = brand(1L, "Apple", "system", null);
        brand.setLogoData(new byte[]{1, 2, 3});
        brand.setLogoMime("image/png");
        when(brandService.getById(1L)).thenReturn(brand);

        MvcResult result = mockMvc.perform(get("/api/brands/1/logo"))
                .andExpect(status().isOk())
                .andReturn();
        String etag = result.getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();

        mockMvc.perform(get("/api/brands/1/logo").header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("GET /api/brands/{id}/logo 未设置头像：返回 404（前端首字母兜底）")
    void getBrandLogoWithoutDataReturns404() throws Exception {
        when(brandService.getById(4L)).thenReturn(brand(4L, "Nebula", "custom", 9L));

        mockMvc.perform(get("/api/brands/4/logo"))
                .andExpect(status().isNotFound());
    }

    // ==================== 辅助方法 ====================

    private String bearer(Long userId, String username, String role) {
        return "Bearer " + jwtUtil.generateToken(userId, username, role);
    }

    private Brand brand(Long id, String name, String source, Long creatorId) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setName(name);
        brand.setSource(source);
        brand.setCreatorId(creatorId);
        return brand;
    }

    private DesignToken token(Long id, Long brandId, String category, String name, String value) {
        DesignToken token = new DesignToken();
        token.setId(id);
        token.setBrandId(brandId);
        token.setCategory(category);
        token.setTokenName(name);
        token.setTokenValue(value);
        return token;
    }

    private DesignFile designFile(Long id, Long brandId, String title) {
        DesignFile file = new DesignFile();
        file.setId(id);
        file.setBrandId(brandId);
        file.setTitle(title);
        return file;
    }
}
