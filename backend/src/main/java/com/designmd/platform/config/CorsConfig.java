package com.designmd.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /uploads/** -> backend/uploads/ 目录，供前端访问上传的 Logo 图片
        String location = resolveUploadDir().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }

    /**
     * 解析上传目录：
     * - 绝对路径：直接使用；
     * - 相对路径：基于 jar/classes 所在目录向上两级（backend 目录）解析，避免依赖进程 CWD。
     *   开发模式 codePath = backend/target/classes，jar 模式 codePath = backend/target/xxx.jar，
     *   两者父目录均为 backend/target，再向上即 backend 目录。
     */
    private Path resolveUploadDir() {
        Path uploadPath = Paths.get(uploadDir);
        if (uploadPath.isAbsolute()) {
            return uploadPath.normalize();
        }
        try {
            Path codePath = Paths.get(CorsConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path parent = codePath.getParent();
            if (parent != null && parent.getParent() != null) {
                return parent.getParent().resolve(uploadPath).normalize();
            }
            return Paths.get("").toAbsolutePath().resolve(uploadPath).normalize();
        } catch (URISyntaxException e) {
            return Paths.get(uploadDir).toAbsolutePath().normalize();
        }
    }
}
