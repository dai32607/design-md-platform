package com.designmd.platform.controller;

import com.designmd.platform.common.BizException;
import com.designmd.platform.common.PageResult;
import com.designmd.platform.common.Result;
import com.designmd.platform.entity.Brand;
import com.designmd.platform.entity.DesignFile;
import com.designmd.platform.entity.DesignToken;
import com.designmd.platform.service.BrandService;
import com.designmd.platform.service.DesignFileService;
import com.designmd.platform.service.DesignTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DesignMdController {

    private final BrandService brandService;
    private final DesignTokenService designTokenService;
    private final DesignFileService designFileService;

    private static final Set<String> ALLOWED_LOGO_EXTENSIONS = Set.of("png", "jpg", "jpeg", "svg", "webp");
    private static final long MAX_LOGO_SIZE = 2 * 1024 * 1024; // 2MB

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    // ========== 品牌接口 ==========

    @GetMapping("/brands")
    public Result<PageResult<Brand>> listBrands(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "false") boolean mine,
            Authentication authentication) {
        Long userId = currentUserId(authentication);
        if (mine && userId == null) {
            throw new BizException(401, "请先登录后再查看我的品牌");
        }
        return Result.ok(brandService.pageList(page, pageSize, keyword, source, mine, userId));
    }

    @GetMapping("/brands/{id}")
    public Result<Brand> getBrand(@PathVariable Long id) {
        return Result.ok(brandService.getById(id));
    }

    /**
     * 普通登录用户创建品牌：强制 source=custom，禁止创建 system 品牌
     * SecurityConfig 中 POST /api/brands/** 无特殊放行，会落入 anyRequest().authenticated()，需登录访问
     */
    @PostMapping("/brands")
    public Result<Brand> createBrandByUser(@RequestBody Brand brand, Authentication authentication) {
        brand.setSource("custom");
        brand.setCreatorId(currentUserId(authentication));
        return Result.ok(brandService.createBrand(brand));
    }

    /**
     * 普通登录用户修改品牌信息：
     * - 仅品牌创建者本人可修改；ADMIN 可修改任意 custom 品牌
     * - 预置品牌（source=system）一律禁止修改
     * - 只允许修改 name / logoUrl / website / description
     */
    @PutMapping("/brands/{id}")
    public Result<Brand> updateBrandByUser(@PathVariable Long id, @RequestBody Brand brand, Authentication authentication) {
        Long userId = currentUserId(authentication);
        boolean isAdmin = isAdmin(authentication);
        Brand exist = brandService.getById(id);
        if ("system".equals(exist.getSource())) {
            throw new BizException(400, "预置品牌不允许修改");
        }
        if (!isAdmin && !Objects.equals(exist.getCreatorId(), userId)) {
            throw new BizException(403, "无权限修改该品牌");
        }
        return Result.ok(brandService.updateBrand(id, brand));
    }

    /**
     * 普通登录用户删除品牌：
     * - 仅品牌创建者本人可删除；ADMIN 可删除任意 custom 品牌
     * - 预置品牌（source=system）一律禁止删除
     * - 删除会级联清除该品牌下全部 Token 与 DESIGN.md 文件
     */
    @DeleteMapping("/brands/{id}")
    public Result<Void> deleteBrandByUser(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserId(authentication);
        boolean isAdmin = isAdmin(authentication);
        Brand exist = brandService.getById(id);
        if ("system".equals(exist.getSource())) {
            throw new BizException(400, "预置品牌不允许删除");
        }
        if (!isAdmin && !Objects.equals(exist.getCreatorId(), userId)) {
            throw new BizException(403, "无权限删除该品牌");
        }
        brandService.deleteBrand(id);
        return Result.ok();
    }

    private Long currentUserId(Authentication authentication) {
        Object details = authentication == null ? null : authentication.getDetails();
        if (details instanceof Number) {
            return ((Number) details).longValue();
        }
        return null;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Token 改删的归属校验（与品牌级规则一致）：
     * - 预置品牌（source=system）的 Token 一律禁止修改/删除；
     * - 非品牌创建者（且非 ADMIN）无权限修改/删除。
     */
    private void checkTokenOwner(Long tokenId, Authentication authentication) {
        Long userId = currentUserId(authentication);
        boolean isAdmin = isAdmin(authentication);
        DesignToken token = designTokenService.getById(tokenId);
        Brand brand = brandService.getById(token.getBrandId());
        if ("system".equals(brand.getSource())) {
            throw new BizException(400, "预置品牌不允许修改");
        }
        if (!isAdmin && !Objects.equals(brand.getCreatorId(), userId)) {
            throw new BizException(403, "无权限修改该品牌");
        }
    }

    // ========== 设计 Token 接口 ==========

    @GetMapping("/brands/{brandId}/tokens")
    public Result<PageResult<DesignToken>> listTokens(
            @PathVariable Long brandId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "0") long pageSize) {
        return Result.ok(designTokenService.pageByBrandId(brandId, category, page, pageSize));
    }

    @GetMapping("/brands/{brandId}/token-categories")
    public Result<List<String>> getTokenCategories(@PathVariable Long brandId) {
        return Result.ok(designTokenService.getCategoriesByBrandId(brandId));
    }

    @PutMapping("/tokens/{id}")
    public Result<DesignToken> updateToken(@PathVariable Long id, @RequestBody DesignToken token, Authentication authentication) {
        checkTokenOwner(id, authentication);
        return Result.ok(designTokenService.updateToken(id, token));
    }

    @DeleteMapping("/tokens/{id}")
    public Result<Void> deleteToken(@PathVariable Long id, Authentication authentication) {
        checkTokenOwner(id, authentication);
        designTokenService.deleteToken(id);
        return Result.ok();
    }

    // ========== DESIGN.md 文件接口 ==========

    @GetMapping("/brands/{brandId}/files")
    public Result<List<DesignFile>> listDesignFiles(@PathVariable Long brandId) {
        return Result.ok(designFileService.listByBrandId(brandId));
    }

    @GetMapping("/files/{id}")
    public Result<DesignFile> getDesignFile(@PathVariable Long id) {
        return Result.ok(designFileService.getById(id));
    }

    // ========== DESIGN.md 上传解析接口 ==========

    @PostMapping("/files/upload")
    public Result<Map<String, Object>> uploadDesignMd(
            @RequestParam("file") MultipartFile file,
            @RequestParam("brandId") Long brandId,
            Authentication authentication) {
        if (file.isEmpty()) {
            return Result.fail(400, "文件不能为空");
        }
        // 校验品牌存在且允许上传（预置品牌不允许上传文件 -> 403 Forbidden；非创建者且非 ADMIN -> 403）
        Brand brand = brandService.getById(brandId);
        if ("system".equals(brand.getSource())) {
            throw new BizException(403, "预置品牌不允许上传文件");
        }
        if (!isAdmin(authentication) && !Objects.equals(brand.getCreatorId(), currentUserId(authentication))) {
            throw new BizException(403, "无权限上传文件");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".md")) {
            return Result.fail(400, "仅支持上传 .md 格式的 Markdown 文件");
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            // 保存文件记录（同名去重：已存在则更新内容，不新增）
            String title = originalFilename.replaceAll("\\.md$", "");
            DesignFile existing = designFileService.findByBrandIdAndTitle(brandId, title);
            boolean updated = false;
            DesignFile designFile;
            if (existing != null) {
                designFile = designFileService.updateContent(existing.getId(), content);
                updated = true;
                // 同名更新：先清理旧的自动解析 Token，避免重复叠加
                List<DesignToken> oldTokens = designTokenService.listByBrandId(brandId);
                for (DesignToken token : oldTokens) {
                    if (token.getDescription() != null && token.getDescription().startsWith("从 DESIGN.md 自动解析")) {
                        designTokenService.deleteToken(token.getId());
                    }
                }
            } else {
                designFile = designFileService.saveDesignFile(brandId, title, content);
            }
            // 解析并保存 Token
            List<DesignToken> tokens = designTokenService.parseAndSaveTokens(brandId, content);
            // 返回结果
            Map<String, Object> data = new HashMap<>();
            data.put("file", designFile);
            data.put("parsedTokens", tokens);
            data.put("tokenCount", tokens.size());
            data.put("updated", updated);
            return Result.ok(data);
        } catch (IOException e) {
            return Result.fail(400, "文件读取失败: " + e.getMessage());
        }
    }

    // ========== 品牌 Logo 接口（二进制存库 + 本地读图） ==========

    /**
     * 上传品牌 Logo：图片二进制写入数据库（离线演示不依赖 uploads 目录与外网），
     * 同时保留一份落盘到 uploads/logo/ 作为兼容备份（不参与展示）。
     * 返回的品牌 logoUrl 统一指向本地读图接口 /api/brands/{id}/logo?v=&lt;版本&gt;。
     */
    @PostMapping("/brands/{id}/logo")
    public Result<Brand> uploadBrandLogo(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {
        if (file.isEmpty()) {
            return Result.fail(400, "文件不能为空");
        }
        String extension = getExtension(file.getOriginalFilename());
        if (extension == null || !ALLOWED_LOGO_EXTENSIONS.contains(extension)) {
            return Result.fail(400, "仅支持 png/jpg/jpeg/svg/webp 格式的图片");
        }
        if (file.getSize() > MAX_LOGO_SIZE) {
            return Result.fail(400, "图片大小不能超过 2MB");
        }
        // 校验品牌存在（不存在时抛出 IllegalArgumentException -> 400）
        Brand brand = brandService.getById(id);
        // 预置品牌不允许更换 Logo
        if ("system".equals(brand.getSource())) {
            return Result.fail(400, "预置品牌不允许修改");
        }
        // 非创建者且非 ADMIN 不允许更换他人 custom 品牌 Logo
        if (!isAdmin(authentication) && !Objects.equals(brand.getCreatorId(), currentUserId(authentication))) {
            throw new BizException(403, "无权限修改该品牌");
        }

        byte[] data = file.getBytes();
        String mime = resolveLogoMime(extension);

        // 兼容保留：仍落盘一份到 uploads/logo/<uuid>.<ext>，仅作备份，失败不阻断入库
        try {
            Path dir = Paths.get(uploadDir, "logo");
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "." + extension).toAbsolutePath();
            Files.write(target, data);
        } catch (IOException ignored) {
            // 落盘失败不影响主流程（展示已改为读库）
        }

        return Result.ok(brandService.updateLogo(id, data, mime));
    }

    /**
     * 品牌 Logo 读图接口：从数据库读出二进制并直接返回。
     * - Content-Type 按库中 MIME 返回；
     * - 带 Cache-Control 与 ETag / Last-Modified，支持 304 协商缓存；
     * - 未设置头像时返回 404，由前端图片兜底逻辑展示首字母占位，不会出现破图图标。
     * 该路径属于 GET /api/brands/**，SecurityConfig 已放行，无需登录即可访问。
     */
    @GetMapping("/brands/{id}/logo")
    public ResponseEntity<byte[]> getBrandLogo(@PathVariable Long id, HttpServletRequest request) {
        Brand brand = brandService.getById(id);
        byte[] data = brand.getLogoData();
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }
        long lastModified = brand.getLogoUpdateTime() == null
                ? -1L
                : brand.getLogoUpdateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String etag = "\"" + id + "-" + data.length + "-" + (lastModified < 0 ? 0L : lastModified) + "\"";
        if (new ServletWebRequest(request).checkNotModified(etag, lastModified)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(brand.getLogoMime());
        } catch (Exception e) {
            mediaType = MediaType.IMAGE_PNG;
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .eTag(etag);
        if (lastModified >= 0) {
            builder.lastModified(lastModified);
        }
        return builder.body(data);
    }

    /** 扩展名 -> MIME 类型（用于入库存储与读图返回的 Content-Type） */
    private String resolveLogoMime(String extension) {
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "svg":
                return "image/svg+xml";
            case "webp":
                return "image/webp";
            case "png":
            default:
                return "image/png";
        }
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return null;
        }
        return filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}
