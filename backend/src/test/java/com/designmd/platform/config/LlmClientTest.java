package com.designmd.platform.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LlmClient 单元测试（纯单元测试，不启动 Spring 容器，不依赖外网）。
 * <p>覆盖点：mock / 真实调用模式切换、mock 模板结构、用户色板生效、
 * 真实调用失败时的降级容错，以及色值归一化校验 {@code normalizeColors}。
 * <p>降级用例把 base-url 指向本机不可达端口，连接被立即拒绝，无需真实网络。
 */
class LlmClientTest {

    private static final String UNREACHABLE_BASE_URL = "http://127.0.0.1:1";

    private LlmClient mockClient;

    @BeforeEach
    void setUp() {
        mockClient = newClient("", 0);
    }

    private LlmClient newClient(String apiKey, int maxRetries) {
        LlmClient client = new LlmClient();
        ReflectionTestUtils.setField(client, "baseUrl", UNREACHABLE_BASE_URL);
        ReflectionTestUtils.setField(client, "apiKey", apiKey);
        ReflectionTestUtils.setField(client, "model", "deepseek-chat");
        ReflectionTestUtils.setField(client, "connectTimeoutMs", 1000);
        ReflectionTestUtils.setField(client, "readTimeoutMs", 1000);
        ReflectionTestUtils.setField(client, "maxRetries", maxRetries);
        ReflectionTestUtils.setField(client, "retryBackoffMs", 1L);
        return client;
    }

    @Test
    @DisplayName("未配置 api-key 时进入 mock 模式，配置后进入真实调用模式")
    void mockModeSwitchByApiKey() {
        assertThat(mockClient.isMockMode()).isTrue();
        assertThat(newClient("   ", 0).isMockMode()).isTrue();
        assertThat(newClient("sk-test-key", 0).isMockMode()).isFalse();
    }

    @Test
    @DisplayName("mock 模式返回结构完整的 DESIGN.md（含色彩/字体/间距板块）")
    void mockModeReturnsFullSpec() {
        LlmClient.SpecResult result = mockClient.generateDesignSpecResult("极简科技风品牌", "Aurora", List.of());

        assertThat(result.mock()).isTrue();
        assertThat(result.degraded()).isFalse();
        assertThat(result.degradeReason()).isNull();
        assertThat(result.totalTokens()).isZero();
        assertThat(result.content())
                .contains("Aurora 设计规范")
                .contains("MOCK 演示模式")
                .contains("## 2. Colors")
                .contains("## 3. Typography")
                .contains("## 4. Spacing")
                .contains("Font Size:")
                .contains("Padding:")
                .contains("Border Radius:");
    }

    @Test
    @DisplayName("mock 模式使用用户指定色板（主色为用户传入色值）")
    void mockModeUsesUserColors() {
        LlmClient.SpecResult result = mockClient.generateDesignSpecResult("极简科技风品牌", "Aurora", List.of("#123456"));

        assertThat(result.content()).contains("--color-primary: #123456");
    }

    @Test
    @DisplayName("兼容旧签名的 generateDesignSpec 与 SpecResult.content 内容一致")
    void legacyGenerateDesignSpecKeepsSameContent() {
        String legacy = mockClient.generateDesignSpec("极简科技风品牌", "Aurora", List.of());

        assertThat(legacy).isEqualTo(mockClient.generateDesignSpecResult("极简科技风品牌", "Aurora", List.of()).content());
    }

    @Test
    @DisplayName("真实调用失败（服务不可达）时降级为本地模板且不抛异常")
    void remoteFailureFallsBackToLocalTemplate() {
        LlmClient client = newClient("sk-test-key", 0);

        LlmClient.SpecResult result = client.generateDesignSpecResult("极简科技风品牌", "Aurora", List.of());

        assertThat(result.mock()).isFalse();
        assertThat(result.degraded()).isTrue();
        assertThat(result.degradeReason()).isNotBlank();
        assertThat(result.content()).contains("本次为降级结果").contains("Aurora 设计规范");
    }

    @Test
    @DisplayName("normalizeColors：大小写归一、#RGB 展开、去重并忽略空值")
    void normalizeColors() {
        List<String> normalized = LlmClient.normalizeColors(
                Arrays.asList("#abc", "#ABC", "#AABBCC", " #123456 ", null, "  "));

        assertThat(normalized).containsExactly("#AABBCC", "#123456");
        assertThat(LlmClient.normalizeColors(null)).isEmpty();
        assertThat(LlmClient.normalizeColors(new ArrayList<>())).isEmpty();
    }

    @Test
    @DisplayName("normalizeColors：非法颜色格式抛出 IllegalArgumentException")
    void normalizeColorsRejectsInvalidFormat() {
        assertThatThrownBy(() -> LlmClient.normalizeColors(List.of("red")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法颜色格式");
        assertThatThrownBy(() -> LlmClient.normalizeColors(List.of("#12345")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法颜色格式");
    }
}
