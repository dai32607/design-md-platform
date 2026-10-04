package com.designmd.platform.service;

import com.designmd.platform.common.BizException;
import com.designmd.platform.config.LlmClient;
import com.designmd.platform.entity.Brand;
import com.designmd.platform.entity.DesignFile;
import com.designmd.platform.entity.DesignToken;
import com.designmd.platform.service.impl.AgentServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AgentService（AI 生成规范闭环）单元测试，全部依赖以 Mock 注入，不连数据库、不调外网。
 * <p>覆盖点：入参校验（prompt / 色值）、品牌与文件与 Token 的闭环落库、
 * 品牌名缺省截取规则、mock 与降级状态透出。
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    private static final String SPEC = """
            # Aurora 设计规范

            ## 2. Colors（色彩体系）

            - --color-primary: #2563EB

            ## 3. Typography（字体规范）

            - Font Size: 16px

            ## 4. Spacing（间距规范）

            - Padding: 8px
            """;

    @Mock
    private LlmClient llmClient;

    @Mock
    private BrandService brandService;

    @Mock
    private DesignFileService designFileService;

    @Mock
    private DesignTokenService designTokenService;

    @InjectMocks
    private AgentServiceImpl agentService;

    private DesignToken token(String category, String name, String value) {
        DesignToken token = new DesignToken();
        token.setCategory(category);
        token.setTokenName(name);
        token.setTokenValue(value);
        return token;
    }

    @Test
    @DisplayName("prompt 为空：抛 BizException(400) 且不触发任何下游调用")
    void promptRequired() {
        assertThatThrownBy(() -> agentService.generateSpec("   ", "Aurora", null, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("prompt 不能为空");
        assertThatThrownBy(() -> agentService.generateSpec(null, null, null, 9L))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(llmClient, brandService, designFileService, designTokenService);
    }

    @Test
    @DisplayName("色值非法：抛 BizException(400)，不调用 LLM 与入库")
    void invalidColorRejected() {
        assertThatThrownBy(() -> agentService.generateSpec("极简科技风", "Aurora", List.of("red"), 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非法颜色格式");

        verifyNoInteractions(llmClient, brandService, designFileService, designTokenService);
    }

    @Test
    @DisplayName("完整闭环：生成内容 → 建品牌(source=custom) → 存 DESIGN.md → 解析 Token 入库")
    void generateSpecClosesLoop() {
        when(llmClient.generateDesignSpecResult(anyString(), anyString(), anyList()))
                .thenReturn(new LlmClient.SpecResult(SPEC, true, false, null, 3L, 0, 0, 0));
        when(brandService.createBrand(any())).thenAnswer(invocation -> {
            Brand brand = invocation.getArgument(0);
            brand.setId(100L);
            return brand;
        });
        when(designFileService.saveDesignFile(eq(100L), anyString(), eq(SPEC))).thenAnswer(invocation -> {
            DesignFile file = new DesignFile();
            file.setId(200L);
            file.setBrandId(invocation.getArgument(0));
            file.setTitle(invocation.getArgument(1));
            file.setContent(invocation.getArgument(2));
            return file;
        });
        when(designTokenService.parseAndSaveTokens(100L, SPEC)).thenReturn(List.of(
                token("COLORS", "Parsed Color 1", "#2563EB"),
                token("TYPOGRAPHY", "Font Size 1", "16px"),
                token("SPACING", "Padding 1", "8px")));

        Map<String, Object> result = agentService.generateSpec("极简科技风品牌", "Aurora", List.of("#2563EB"), 9L);

        assertThat(result).containsEntry("brandId", 100L)
                .containsEntry("brandName", "Aurora")
                .containsEntry("fileId", 200L)
                .containsEntry("tokenCount", 3)
                .containsEntry("mock", true)
                .containsEntry("degraded", false);
        assertThat(result.get("colorsUsed")).isEqualTo(List.of("#2563EB"));
        assertThat(result.get("specText")).isEqualTo(SPEC);

        ArgumentCaptor<Brand> brandCaptor = ArgumentCaptor.forClass(Brand.class);
        verify(brandService).createBrand(brandCaptor.capture());
        Brand created = brandCaptor.getValue();
        assertThat(created.getName()).isEqualTo("Aurora");
        assertThat(created.getSource()).isEqualTo("custom");
        assertThat(created.getCreatorId()).isEqualTo(9L);
        assertThat(created.getDescription()).contains("极简科技风品牌");

        verify(designFileService).saveDesignFile(100L, "Aurora - AI 生成规范", SPEC);
        verify(designTokenService).parseAndSaveTokens(100L, SPEC);
    }

    @Test
    @DisplayName("未指定品牌名时从 prompt 截取前 12 字符作为品牌名")
    void brandNameDerivedFromPrompt() {
        String prompt = "为科技公司打造极简风格的设计系统";
        when(llmClient.generateDesignSpecResult(anyString(), anyString(), anyList()))
                .thenReturn(new LlmClient.SpecResult(SPEC, true, false, null, 1L, 0, 0, 0));
        when(brandService.createBrand(any())).thenAnswer(invocation -> {
            Brand brand = invocation.getArgument(0);
            brand.setId(101L);
            return brand;
        });
        when(designFileService.saveDesignFile(any(), anyString(), anyString())).thenAnswer(invocation -> {
            DesignFile file = new DesignFile();
            file.setId(201L);
            return file;
        });
        when(designTokenService.parseAndSaveTokens(any(), anyString())).thenReturn(List.of());

        agentService.generateSpec(prompt, "  ", null, 9L);

        ArgumentCaptor<Brand> brandCaptor = ArgumentCaptor.forClass(Brand.class);
        verify(brandService).createBrand(brandCaptor.capture());
        assertThat(brandCaptor.getValue().getName()).isEqualTo(prompt.substring(0, 12));
    }

    @Test
    @DisplayName("色值归一化后传给 LLM，并回写 colorsUsed")
    void colorsNormalizedBeforeLlmCall() {
        when(llmClient.generateDesignSpecResult(anyString(), anyString(), anyList()))
                .thenReturn(new LlmClient.SpecResult(SPEC, true, false, null, 1L, 0, 0, 0));
        when(brandService.createBrand(any())).thenAnswer(invocation -> {
            Brand brand = invocation.getArgument(0);
            brand.setId(102L);
            return brand;
        });
        when(designFileService.saveDesignFile(any(), anyString(), anyString())).thenAnswer(invocation -> {
            DesignFile file = new DesignFile();
            file.setId(202L);
            return file;
        });
        when(designTokenService.parseAndSaveTokens(any(), anyString())).thenReturn(List.of());

        Map<String, Object> result = agentService.generateSpec("极简科技风品牌", "Aurora", List.of("#abc"), 9L);

        verify(llmClient).generateDesignSpecResult(eq("极简科技风品牌"), eq("Aurora"), eq(List.of("#AABBCC")));
        assertThat(result.get("colorsUsed")).isEqualTo(List.of("#AABBCC"));
    }

    @Test
    @DisplayName("LLM 返回空内容时抛 BizException(400)，不创建品牌")
    void emptySpecRejected() {
        when(llmClient.generateDesignSpecResult(anyString(), anyString(), anyList()))
                .thenReturn(new LlmClient.SpecResult("   ", true, false, null, 1L, 0, 0, 0));

        assertThatThrownBy(() -> agentService.generateSpec("极简科技风品牌", "Aurora", null, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("AI 未生成有效的设计规范内容");

        verify(brandService, never()).createBrand(any());
        verify(designTokenService, never()).parseAndSaveTokens(any(), anyString());
    }

    @Test
    @DisplayName("真实调用降级时仍完成闭环，并把 degraded / 原因透出给前端")
    void degradedResultStillPersisted() {
        when(llmClient.generateDesignSpecResult(anyString(), anyString(), anyList()))
                .thenReturn(new LlmClient.SpecResult(SPEC, false, true, "HTTP 500", 12L, 0, 0, 0));
        when(brandService.createBrand(any())).thenAnswer(invocation -> {
            Brand brand = invocation.getArgument(0);
            brand.setId(103L);
            return brand;
        });
        when(designFileService.saveDesignFile(any(), anyString(), anyString())).thenAnswer(invocation -> {
            DesignFile file = new DesignFile();
            file.setId(203L);
            return file;
        });
        when(designTokenService.parseAndSaveTokens(any(), anyString())).thenReturn(List.of());

        Map<String, Object> result = agentService.generateSpec("极简科技风品牌", "Aurora", null, 9L);

        assertThat(result).containsEntry("mock", false)
                .containsEntry("degraded", true)
                .containsEntry("degradeReason", "HTTP 500");
        verify(designFileService).saveDesignFile(103L, "Aurora - AI 生成规范", SPEC);
    }
}
