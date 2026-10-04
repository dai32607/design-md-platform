package com.designmd.platform.config;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * LLM 客户端：配置驱动，兼容 OpenAI 格式（base-url / api-key / model）。
 * 未配置 api-key 时启用 mock 模式，返回一份结构完整的示例 DESIGN.md，
 * 内容与平台现有解析格式兼容（colors / typography / spacing / components）。
 * 支持用户指定品牌色板：传入 colors 时，colors 板块只使用这些颜色（不足 5 个用同色系明暗变体补足）。
 * 健壮性：可配置连接/读取超时（快速失败）、失败自动重试（指数退避）、耗尽后降级为本地模板返回，
 * 全程记录耗时与失败原因且不打印 api-key。
 */
@Slf4j
@Component
public class LlmClient {

    private static final Pattern HEX_COLOR = Pattern.compile("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");

    @Value("${llm.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.model:deepseek-chat}")
    private String model;

    /** 建立连接超时（毫秒），超时快速失败 */
    @Value("${llm.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    /** 读取响应超时（毫秒），避免长耗时调用拖垮请求线程 */
    @Value("${llm.read-timeout-ms:60000}")
    private int readTimeoutMs;

    /** 失败重试次数（仅对网络异常 / 超时 / 429 / 5xx 生效），总尝试次数 = maxRetries + 1 */
    @Value("${llm.max-retries:2}")
    private int maxRetries;

    /** 重试退避基数（毫秒），按指数增长并封顶 5s */
    @Value("${llm.retry-backoff-ms:800}")
    private long retryBackoffMs;

    /**
     * 是否 mock 模式：只要 llm.api-key 为空即走本地模板，无需改动任何业务代码。
     */
    public boolean isMockMode() {
        return apiKey == null || apiKey.trim().isEmpty();
    }

    /**
     * 根据品牌需求描述生成 DESIGN.md 规范内容（兼容旧签名）。
     * colors 为归一化后的用户色板（可为空）；mock 模式下按色板或关键词生成。
     */
    public String generateDesignSpec(String prompt, String brandName, List<String> colors) {
        return generateDesignSpecResult(prompt, brandName, colors).content();
    }

    /**
     * 生成规范并返回调用元信息（是否 mock / 是否降级 / 耗时）。
     * 真实调用失败时不会抛异常中断业务，而是降级为本地模板内容并带上明确提示，
     * 保证前端生成请求始终可用（不返回 500）。
     */
    public SpecResult generateDesignSpecResult(String prompt, String brandName, List<String> colors) {
        if (isMockMode()) {
            log.info("LlmClient using MOCK mode (llm.api-key not configured), model={}, userColors={}", model, colors);
            return new SpecResult(mockDesignSpec(prompt, brandName, colors), true, false, null, 0L, 0, 0, 0);
        }
        long start = System.currentTimeMillis();
        try {
            RemoteResponse remote = callRemoteWithRetry(prompt, colors);
            long cost = System.currentTimeMillis() - start;
            String content = remote.content();
            if (content == null || content.trim().isEmpty()) {
                throw new LlmCallException("AI 服务返回内容为空", false);
            }
            log.info("LlmClient remote call succeeded: model={}, cost={}ms, contentLength={}, promptTokens={}, completionTokens={}, totalTokens={}",
                    model, cost, content.length(), remote.promptTokens(), remote.completionTokens(), remote.totalTokens());
            return new SpecResult(content.trim(), false, false, null, cost,
                    remote.promptTokens(), remote.completionTokens(), remote.totalTokens());
        } catch (LlmCallException e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("LlmClient remote call failed, fallback to local template: model={}, cost={}ms, reason={}",
                    model, cost, e.getMessage());
            return new SpecResult(fallbackSpec(prompt, brandName, colors, e.getMessage()), false, true, e.getMessage(), cost, 0, 0, 0);
        }
    }

    // ========== 真实调用（OpenAI 兼容 /chat/completions） ==========

    /**
     * 带重试的远程调用：总尝试次数 = maxRetries + 1；仅对可重试错误（网络异常 / 超时 / 429 / 5xx）重试，
     * 退避时间为 retryBackoffMs 的指数增长（1x / 2x / 4x...，封顶 5s）。
     */
    private RemoteResponse callRemoteWithRetry(String prompt, List<String> colors) {
        int attempts = Math.max(1, maxRetries + 1);
        LlmCallException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long start = System.currentTimeMillis();
            try {
                RemoteResponse remote = callRemote(prompt, colors);
                log.info("LlmClient attempt {}/{} succeeded, cost={}ms, totalTokens={}",
                        attempt, attempts, System.currentTimeMillis() - start, remote.totalTokens());
                return remote;
            } catch (LlmCallException e) {
                lastError = e;
                long cost = System.currentTimeMillis() - start;
                if (!e.isRetryable() || attempt >= attempts) {
                    log.warn("LlmClient attempt {}/{} failed: cost={}ms, retryable={}, reason={}",
                            attempt, attempts, cost, e.isRetryable(), e.getMessage());
                    break;
                }
                long backoff = Math.min(retryBackoffMs * (1L << (attempt - 1)), 5000L);
                log.warn("LlmClient attempt {}/{} failed: cost={}ms, reason={}, retrying after {}ms",
                        attempt, attempts, cost, e.getMessage(), backoff);
                sleepQuietly(backoff);
            }
        }
        throw lastError == null ? new LlmCallException("AI 服务调用失败", false) : lastError;
    }

    /**
     * 单次远程调用（OpenAI 兼容 /chat/completions）。
     * 分别设置连接超时与读取超时，超时快速失败；错误按是否可重试分类抛出。
     */
    private RemoteResponse callRemote(String prompt, List<String> colors) {
        String systemContent = "你是一名资深设计系统专家。请根据用户对品牌的描述，输出一份完整的 DESIGN.md 设计规范 Markdown 文档。"
                + "必须包含以下板块：1. Design Principles（设计原则）；2. Colors（色彩体系，每行用 \"--color-xxx: #RRGGBB\" 格式列出色值，必须使用完整 6 位十六进制色值）；"
                + "3. Typography（字体规范，必须包含 \"Font Size: xxxpx\"、\"Font Family: ...\"、\"Line Height: ...\" 行）；"
                + "4. Spacing（间距规范，必须包含 \"Padding: xxxpx\"、\"Margin: xxxpx\"、\"Gap: xxxpx\"、\"Border Radius: xxxpx\" 行）；"
                + "5. Components（核心组件，如 Button / Card / Input 的设计要点）。直接输出 Markdown 正文，不要任何额外解释。";
        if (colors != null && !colors.isEmpty()) {
            systemContent += " 用户指定了品牌色板，Colors 板块必须只使用这些颜色：" + String.join(", ", colors)
                    + "（可按需生成明暗变体，但主色必须是用户提供的色值）。";
        }

        JSONObject systemMsg = new JSONObject();
        systemMsg.set("role", "system");
        systemMsg.set("content", systemContent);

        JSONObject userMsg = new JSONObject();
        userMsg.set("role", "user");
        userMsg.set("content", prompt);

        JSONArray messages = new JSONArray();
        messages.add(systemMsg);
        messages.add(userMsg);

        JSONObject body = new JSONObject();
        body.set("model", model);
        body.set("messages", messages);
        body.set("temperature", 0.7);

        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        // 注意：日志只记录 url / model / 超时参数，绝不打印 api-key
        log.info("LlmClient calling remote: url={}, model={}, connectTimeout={}ms, readTimeout={}ms",
                url, model, connectTimeoutMs, readTimeoutMs);
        String rawBody = null;
        try (HttpResponse resp = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .body(body.toString())
                .setConnectionTimeout(connectTimeoutMs)
                .setReadTimeout(readTimeoutMs)
                .execute()) {
            int status = resp.getStatus();
            rawBody = resp.body();
            if (status != 200) {
                boolean retryable = status == 429 || status >= 500;
                log.warn("LlmClient remote response error: status={}, retryable={}, body={}",
                        status, retryable, abbreviate(rawBody));
                throw new LlmCallException("AI 服务调用失败: HTTP " + status, retryable);
            }
            JSONObject json = JSONUtil.parseObj(rawBody);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("LlmClient remote response has no choices: body={}", abbreviate(rawBody));
                throw new LlmCallException("AI 服务返回为空，请稍后重试", true);
            }
            String content = choices.getJSONObject(0).getJSONObject("message").getStr("content");
            JSONObject usage = json.getJSONObject("usage");
            int promptTokens = 0;
            int completionTokens = 0;
            int totalTokens = 0;
            if (usage != null) {
                promptTokens = usage.getInt("prompt_tokens", 0);
                completionTokens = usage.getInt("completion_tokens", 0);
                totalTokens = usage.getInt("total_tokens", promptTokens + completionTokens);
            }
            return new RemoteResponse(content == null ? "" : content.trim(), promptTokens, completionTokens, totalTokens);
        } catch (LlmCallException e) {
            throw e;
        } catch (Exception e) {
            // 网络异常 / 连接或读取超时 / 响应解析失败：可重试
            log.warn("LlmClient remote call error: {}, body={}", e.toString(), abbreviate(rawBody));
            throw new LlmCallException("AI 服务调用失败: " + e.getMessage(), true);
        }
    }

    /** 重试等待；被中断时立即停止重试并向上抛出。 */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LlmCallException("AI 调用被中断", false);
        }
    }

    /** 日志用截断，避免超长响应体刷屏。 */
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "...(truncated)" : t;
    }

    /**
     * 降级内容：复用本地模板，并在文首插入醒目的降级说明，
     * 保证「请求不失败、内容可用、来源可辨识」。
     */
    private String fallbackSpec(String prompt, String brandName, List<String> colors, String reason) {
        String spec = mockDesignSpec(prompt, brandName, colors);
        String notice = "> ⚠️ 本次为降级结果：调用真实大模型失败（" + (reason == null ? "未知原因" : reason)
                + "），已自动切换为本地模板生成，内容可用但非模型输出。";
        int idx = spec.indexOf("\n\n");
        return idx < 0 ? notice + "\n\n" + spec : spec.substring(0, idx) + "\n\n" + notice + spec.substring(idx);
    }

    // ========== Mock 模式（未配置 api-key） ==========

    /**
     * 根据需求描述选择一个调色板，增强"看起来真实"的效果。
     */
    private String[] pickPalette(String prompt) {
        String text = prompt == null ? "" : prompt.toLowerCase();
        if (text.contains("咖啡") || text.contains("coffee") || text.contains("烘焙") || text.contains("暖")) {
            return new String[]{"#6F4E37", "#C89F7B", "#F3E9DC", "#2E1B12"};
        }
        if (text.contains("科技") || text.contains("极简") || text.contains("现代") || text.contains("简约")
                || text.contains("tech") || text.contains("minimal") || text.contains("digital")) {
            return new String[]{"#2563EB", "#0F172A", "#F8FAFC", "#94A3B8"};
        }
        if (text.contains("健康") || text.contains("自然") || text.contains("绿色") || text.contains("生态")
                || text.contains("eco") || text.contains("green") || text.contains("organic")) {
            return new String[]{"#16A34A", "#14532D", "#F0FDF4", "#86EFAC"};
        }
        if (text.contains("奢华") || text.contains("高端") || text.contains("金") || text.contains("luxury")) {
            return new String[]{"#B8860B", "#1C1917", "#FAF7F2", "#D6C9A6"};
        }
        return new String[]{"#1F2937", "#4B5563", "#F9FAFB", "#E5E7EB"};
    }

    /**
     * 生成颜色方案：返回 5 个语义色（primary/secondary/accent/background/text）+ 额外辅助色。
     * 用户提供 colors 时：主色必须是用户色值，不足 5 个用同色系明暗变体补足，超过 5 个的作辅助色。
     */
    private ColorScheme buildColorScheme(List<String> colors, String prompt) {
        if (colors == null || colors.isEmpty()) {
            String[] p = pickPalette(prompt);
            return new ColorScheme(
                    p[0], p[1], shade(p[0], -0.20), p[2], "#111827",
                    new ArrayList<>());
        }
        List<String> list = new ArrayList<>(colors);
        List<String> additional = new ArrayList<>();
        if (list.size() > 5) {
            additional.addAll(list.subList(5, list.size()));
            list = new ArrayList<>(list.subList(0, 5));
        }
        String primary = list.get(0);
        String secondary = list.size() > 1 ? list.get(1) : shade(primary, 0.35);
        String accent = list.size() > 2 ? list.get(2) : shade(secondary, -0.18);
        String background = list.size() > 3 ? list.get(3) : shade(primary, 0.92);
        String text = list.size() > 4 ? list.get(4) : shade(primary, -0.70);
        return new ColorScheme(primary, secondary, accent, background, text, additional);
    }

    private String mockDesignSpec(String prompt, String brandName, List<String> colors) {
        String title = brandName == null || brandName.trim().isEmpty() ? "AI 生成品牌" : brandName.trim();
        String desc = prompt == null ? "" : prompt.trim();
        ColorScheme scheme = buildColorScheme(colors, desc);

        StringBuilder additionalBlock = new StringBuilder();
        if (!scheme.additional.isEmpty()) {
            additionalBlock.append("- Additional Colors:");
            for (int i = 0; i < scheme.additional.size(); i++) {
                additionalBlock.append("\n  - --color-support-").append(i + 1).append(": ").append(scheme.additional.get(i));
            }
        }

        // 组件内引用色：均从主色生成变体，保证规范整体只使用用户色系
        String btnText = shade(scheme.primary, -0.90);
        String surfaceAlt = shade(scheme.primary, 0.97);
        String mutedText = shade(scheme.primary, -0.50);

        return MOCK_TEMPLATE
                .replace("{brandName}", title)
                .replace("{prompt}", desc)
                .replace("{primary}", scheme.primary)
                .replace("{secondary}", scheme.secondary)
                .replace("{accent}", scheme.accent)
                .replace("{background}", scheme.background)
                .replace("{text}", scheme.text)
                .replace("{btnText}", btnText)
                .replace("{surfaceAlt}", surfaceAlt)
                .replace("{mutedText}", mutedText)
                .replace("{colorAdditional}", additionalBlock.toString());
    }

    // ========== 颜色工具 ==========

    /**
     * 生成同色系明暗变体：factor > 0 向白色混合（变亮），factor < 0 向黑色混合（变暗）。
     */
    private String shade(String hex, double factor) {
        int[] rgb = parseHex(hex);
        int r, g, b;
        if (factor >= 0) {
            r = (int) Math.round(rgb[0] + (255 - rgb[0]) * factor);
            g = (int) Math.round(rgb[1] + (255 - rgb[1]) * factor);
            b = (int) Math.round(rgb[2] + (255 - rgb[2]) * factor);
        } else {
            double f = -factor;
            r = (int) Math.round(rgb[0] * (1 - f));
            g = (int) Math.round(rgb[1] * (1 - f));
            b = (int) Math.round(rgb[2] * (1 - f));
        }
        return String.format("#%02X%02X%02X", Math.min(255, Math.max(0, r)),
                Math.min(255, Math.max(0, g)), Math.min(255, Math.max(0, b)));
    }

    private int[] parseHex(String hex) {
        String h = hex.replace("#", "");
        if (h.length() == 3) {
            return new int[]{
                    Integer.parseInt(h.substring(0, 1) + h.substring(0, 1), 16),
                    Integer.parseInt(h.substring(1, 2) + h.substring(1, 2), 16),
                    Integer.parseInt(h.substring(2, 3) + h.substring(2, 3), 16)
            };
        }
        return new int[]{
                Integer.parseInt(h.substring(0, 2), 16),
                Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16)
        };
    }

    /** 校验并归一化用户颜色：#RGB 展开为 #RRGGBB，统一大写；空值忽略。 */
    public static List<String> normalizeColors(List<String> colors) {
        if (colors == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String raw : colors) {
            if (raw == null) {
                continue;
            }
            String c = raw.trim().toUpperCase();
            if (c.isEmpty()) {
                continue;
            }
            if (!HEX_COLOR.matcher(c).matches()) {
                throw new IllegalArgumentException("非法颜色格式: " + raw + "，需为 #RGB 或 #RRGGBB");
            }
            if (c.length() == 4) { // #RGB -> #RRGGBB
                c = "#" + c.charAt(1) + c.charAt(1) + c.charAt(2) + c.charAt(2) + c.charAt(3) + c.charAt(3);
            }
            if (!result.contains(c)) {
                result.add(c);
            }
        }
        return result;
    }

    private record ColorScheme(String primary, String secondary, String accent,
                               String background, String text, List<String> additional) {
    }

    /**
     * 生成结果元信息：mock 表示未配置 key 的本地模板；degraded 表示真实调用失败后自动降级；
     * token 字段来自模型返回的 usage，mock / 降级时为 0。
     */
    public record SpecResult(String content, boolean mock, boolean degraded, String degradeReason, long elapsedMs,
                             int promptTokens, int completionTokens, int totalTokens) {
    }

    /** 单次远程调用的返回：正文 + token 用量（usage）。 */
    private record RemoteResponse(String content, int promptTokens, int completionTokens, int totalTokens) {
    }

    /** LLM 调用异常：retryable 标识是否可重试（网络异常 / 超时 / 429 / 5xx 为 true）。 */
    public static class LlmCallException extends RuntimeException {
        private final boolean retryable;

        public LlmCallException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    /**
     * Mock 示例模板：结构完整（colors/typography/spacing/components），
     * 格式与平台现有解析器兼容（#hex / Font Size / Font Family / Line Height / Padding / Margin / Gap / Border Radius）。
     */
    private static final String MOCK_TEMPLATE = """
            # {brandName} 设计规范（DESIGN.md）

            > 本规范由 AI 根据以下需求自动生成：{prompt}
            > 生成引擎：MOCK 演示模式（未配置 llm.api-key 时启用）

            ## 1. Design Principles（设计原则）

            - **清晰（Clarity）**：信息层级分明，主次有序，让用户一眼聚焦核心内容。
            - **一致（Consistency）**：全局使用统一的颜色、字体与间距尺度，组件可复用。
            - **友好（Friendly）**：圆润的圆角与留白营造亲和、舒适的品牌感知。
            - **克制（Restraint）**：用色克制，强调色仅用于关键操作与重点信息。

            ## 2. Colors（色彩体系）

            - --color-primary: {primary}
            - --color-secondary: {secondary}
            - --color-accent: {accent}
            - --color-background: {background}
            - --color-text: {text}
            {colorAdditional}

            ## 3. Typography（字体规范）

            - Font Size: 48px
            - Font Size: 32px
            - Font Size: 24px
            - Font Size: 16px
            - Font Size: 14px
            - Font Size: 12px
            - Font Family: "Inter", -apple-system, "Segoe UI", Roboto, "PingFang SC", "Microsoft YaHei", sans-serif
            - Line Height: 1.6
            - Line Height: 1.5
            - Line Height: 1.4
            - Font Weight: 700
            - Font Weight: 500
            - Font Weight: 400

            ## 4. Spacing（间距规范）

            - Spacing Unit: 4px
            - Padding: 16px
            - Padding: 24px
            - Padding: 8px 16px
            - Margin: 8px
            - Margin: 24px
            - Gap: 12px
            - Gap: 16px
            - Border Radius: 8px
            - Border Radius: 12px
            - Border Radius: 999px

            ## 5. Components（核心组件）

            ### Button（按钮）

            - 默认按钮：背景 {primary}，文字 {btnText}，高度 40px，内边距 16px，圆角 8px。
            - 次要按钮：背景 {surfaceAlt}，边框 1px solid {secondary}，文字 {text}。
            - 禁用态：背景 {background}，文字 {mutedText}。
            - 悬停态：背景加深 10%，提供清晰反馈。

            ### Card（卡片）

            - 背景 {surfaceAlt}，圆角 12px，阴影 0 4px 16px rgba(0, 0, 0, 0.06)，内边距 24px。
            - 卡片间距使用 16px Gap 栅格化排列。

            ### Input（输入框）

            - 高度 40px，背景 {background}，圆角 8px，聚焦时边框使用 {primary}。
            - 错误态：边框 {accent}，并附错误提示文案。

            ## 6. Usage（使用建议）

            - 所有颜色、字号、间距值请以本规范为准，禁止在业务代码中硬编码设计值。
            - 间距遵循 4px 基准网格，按钮与输入框统一 40px 高度，保持触控友好。
            - 强调色 {primary} 只用于主操作按钮、链接与选中态，避免大面积使用。
            """;
}
