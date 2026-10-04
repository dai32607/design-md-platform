package com.designmd.platform.service.impl;

import com.designmd.platform.common.BizException;
import com.designmd.platform.config.LlmClient;
import com.designmd.platform.entity.Brand;
import com.designmd.platform.entity.DesignFile;
import com.designmd.platform.entity.DesignToken;
import com.designmd.platform.service.AgentService;
import com.designmd.platform.service.BrandService;
import com.designmd.platform.service.DesignFileService;
import com.designmd.platform.service.DesignTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 闭环服务：一句话生成品牌完整规范。
 * 复用现有 BrandService.createBrand / DesignFileService / DesignTokenService，
 * 不另写一套解析逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final LlmClient llmClient;
    private final BrandService brandService;
    private final DesignFileService designFileService;
    private final DesignTokenService designTokenService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateSpec(String prompt, String brandName, List<String> colors, Long userId) {
        String rawPrompt = prompt == null ? "" : prompt.trim();
        if (rawPrompt.isEmpty()) {
            throw new BizException(400, "prompt 不能为空");
        }

        // 归一化校验用户色板（非法格式报 400）
        List<String> normalizedColors;
        try {
            normalizedColors = LlmClient.normalizeColors(colors);
        } catch (IllegalArgumentException e) {
            throw new BizException(400, e.getMessage());
        }

        // 品牌名：优先取入参，为空则从 prompt 截取前 12 字符
        String name = brandName == null ? "" : brandName.trim();
        if (name.isEmpty()) {
            name = rawPrompt.length() > 12 ? rawPrompt.substring(0, 12) : rawPrompt;
        }
        if (name.isEmpty()) {
            name = "AI 生成品牌";
        }

        // 1. AI 生成 DESIGN.md 规范内容（mock 或真实 LLM，传入用户色板）
        //    真实调用失败时 LlmClient 内部已降级为本地模板内容，不抛异常，保证生成请求不会 500
        LlmClient.SpecResult spec = llmClient.generateDesignSpecResult(rawPrompt, name, normalizedColors);
        String specText = spec.content();
        if (specText == null || specText.trim().isEmpty()) {
            throw new BizException(400, "AI 未生成有效的设计规范内容，请重试");
        }
        if (spec.degraded()) {
            log.warn("Agent AI 调用降级为本地模板: reason={}, cost={}ms", spec.degradeReason(), spec.elapsedMs());
        }

        // 2. 创建品牌（source=custom，creatorId=当前登录用户）
        Brand brand = new Brand();
        brand.setName(name);
        brand.setSource("custom");
        brand.setCreatorId(userId);
        brand.setDescription("由 AI 根据需求生成：" + rawPrompt);
        Brand created = brandService.createBrand(brand);

        // 3. 保存 DESIGN.md 文件（复用现有文件保存逻辑）
        DesignFile file = designFileService.saveDesignFile(created.getId(), name + " - AI 生成规范", specText);

        // 4. Token 解析入库（复用现有解析逻辑）
        List<DesignToken> tokens = designTokenService.parseAndSaveTokens(created.getId(), specText);
        log.info("Agent generated brand id={}, name={}, tokens={}", created.getId(), created.getName(), tokens.size());

        Map<String, Object> result = new HashMap<>();
        result.put("brandId", created.getId());
        result.put("brandName", created.getName());
        result.put("fileId", file.getId());
        result.put("specText", specText);
        result.put("tokenCount", tokens.size());
        result.put("colorsUsed", normalizedColors);
        result.put("mock", spec.mock());
        result.put("degraded", spec.degraded());
        result.put("degradeReason", spec.degradeReason());
        result.put("elapsedMs", spec.elapsedMs());
        result.put("promptTokens", spec.promptTokens());
        result.put("completionTokens", spec.completionTokens());
        result.put("totalTokens", spec.totalTokens());
        return result;
    }
}
