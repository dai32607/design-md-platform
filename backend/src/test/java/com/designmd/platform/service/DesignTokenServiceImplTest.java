package com.designmd.platform.service;

import com.designmd.platform.common.PageResult;
import com.designmd.platform.entity.DesignToken;
import com.designmd.platform.mapper.DesignTokenMapper;
import com.designmd.platform.service.impl.DesignTokenServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DesignTokenService 单元测试（Mockito 模拟 Mapper，不连数据库）。
 * <p>覆盖点：DESIGN.md 内容 → Token 正则解析与去重入库、分类统计、分页全量分支、
 * Token 更新与删除的边界（不存在时抛 IllegalArgumentException）。
 */
@ExtendWith(MockitoExtension.class)
class DesignTokenServiceImplTest {

    @Mock
    private DesignTokenMapper designTokenMapper;

    @InjectMocks
    private DesignTokenServiceImpl designTokenService;

    /** 样例规范：颜色 3 行（其中 1 行为大小写不同的重复色）、字体 4 行、间距 4 行 */
    private static final String SAMPLE_MD = """
            # Aurora 设计规范

            ## 2. Colors（色彩体系）

            - --color-primary: #2563EB
            - --color-secondary: #0F172A
            - --color-primary-alias: #2563eb

            ## 3. Typography（字体规范）

            - Font Size: 48px
            - Font Size: 16px
            - Font Family: "Inter", sans-serif
            - Line Height: 1.6

            ## 4. Spacing（间距规范）

            - Padding: 16px
            - Margin: 8px
            - Gap: 12px
            - Border Radius: 8px
            """;

    private DesignToken token(Long id, Long brandId, String category, String name, String value) {
        DesignToken token = new DesignToken();
        token.setId(id);
        token.setBrandId(brandId);
        token.setCategory(category);
        token.setTokenName(name);
        token.setTokenValue(value);
        return token;
    }

    @Test
    @DisplayName("parseAndSaveTokens：解析 COLORS / TYPOGRAPHY / SPACING 三类并去重入库")
    void parseAndSaveTokens() {
        List<DesignToken> tokens = designTokenService.parseAndSaveTokens(7L, SAMPLE_MD);

        assertThat(tokens).hasSize(10);
        assertThat(tokens).allSatisfy(t -> {
            assertThat(t.getBrandId()).isEqualTo(7L);
            assertThat(t.getTokenValue()).isNotBlank();
            // 描述前缀是上传同名文件时识别"自动解析 Token"的依据
            assertThat(t.getDescription()).startsWith("从 DESIGN.md 自动解析");
        });
        assertThat(colors(tokens)).containsExactly("#2563EB", "#0F172A");
        assertThat(categories(tokens, "COLORS")).isEqualTo(2);
        assertThat(categories(tokens, "TYPOGRAPHY")).isEqualTo(4);
        assertThat(categories(tokens, "SPACING")).isEqualTo(4);
        verify(designTokenMapper, times(10)).insert(any(DesignToken.class));
    }

    @Test
    @DisplayName("parseAndSaveTokens：内容为空或只有标题时不入库，也不访问 Mapper")
    void parseAndSaveTokensWithEmptyContent() {
        assertThat(designTokenService.parseAndSaveTokens(7L, "")).isEmpty();
        assertThat(designTokenService.parseAndSaveTokens(7L, "# 只有标题的文档")).isEmpty();

        verifyNoInteractions(designTokenMapper);
    }

    @Test
    @DisplayName("updateToken：Token 不存在时抛 IllegalArgumentException")
    void updateTokenNotFound() {
        when(designTokenMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> designTokenService.updateToken(99L, new DesignToken()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token 不存在");
        verify(designTokenMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateToken：只覆盖名称/值/描述，保留原品牌与分类")
    void updateTokenKeepsBrandAndCategory() {
        DesignToken existing = token(5L, 7L, "COLORS", "旧名称", "#000000");
        when(designTokenMapper.selectById(5L)).thenReturn(existing);

        DesignToken patch = new DesignToken();
        patch.setTokenName("主色");
        patch.setTokenValue("#2563EB");
        patch.setDescription("手工调整");
        patch.setCategory("SPACING");
        patch.setBrandId(999L);

        DesignToken updated = designTokenService.updateToken(5L, patch);

        assertThat(updated.getTokenName()).isEqualTo("主色");
        assertThat(updated.getTokenValue()).isEqualTo("#2563EB");
        assertThat(updated.getDescription()).isEqualTo("手工调整");
        assertThat(updated.getCategory()).isEqualTo("COLORS");
        assertThat(updated.getBrandId()).isEqualTo(7L);
        verify(designTokenMapper).updateById(existing);
    }

    @Test
    @DisplayName("deleteToken：影响 0 行时抛 IllegalArgumentException")
    void deleteTokenNotFound() {
        when(designTokenMapper.deleteById(9L)).thenReturn(0);

        assertThatThrownBy(() -> designTokenService.deleteToken(9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token 不存在");
    }

    @Test
    @DisplayName("deleteToken：删除成功返回 true")
    void deleteTokenSuccess() {
        when(designTokenMapper.deleteById(9L)).thenReturn(1);

        assertThat(designTokenService.deleteToken(9L)).isTrue();
        verify(designTokenMapper).deleteById(9L);
    }

    @Test
    @DisplayName("pageByBrandId：pageSize <= 0 时返回该品牌全部 Token（不分页）")
    void pageByBrandIdWithoutPaging() {
        when(designTokenMapper.selectList(any())).thenReturn(List.of(
                token(1L, 7L, "COLORS", "主色", "#2563EB"),
                token(2L, 7L, "SPACING", "Padding 1", "16px")));

        PageResult<DesignToken> result = designTokenService.pageByBrandId(7L, null, 1, 0);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(2);
    }

    @Test
    @DisplayName("getCategoriesByBrandId：分类去重且保持出现顺序")
    void getCategoriesByBrandId() {
        when(designTokenMapper.selectList(any())).thenReturn(Arrays.asList(
                token(1L, 7L, "COLORS", "主色", "#2563EB"),
                token(2L, 7L, "COLORS", "辅色", "#0F172A"),
                token(3L, 7L, "SPACING", "Padding 1", "16px")));

        assertThat(designTokenService.getCategoriesByBrandId(7L)).containsExactly("COLORS", "SPACING");
    }

    @Test
    @DisplayName("listByBrandIdAndCategory：委托自定义 Mapper 方法查询")
    void listByBrandIdAndCategory() {
        when(designTokenMapper.selectByBrandIdAndCategory(7L, "COLORS"))
                .thenReturn(List.of(token(1L, 7L, "COLORS", "主色", "#2563EB")));

        assertThat(designTokenService.listByBrandIdAndCategory(7L, "COLORS")).hasSize(1);
        verify(designTokenMapper).selectByBrandIdAndCategory(7L, "COLORS");
    }

    private List<String> colors(List<DesignToken> tokens) {
        return tokens.stream()
                .filter(t -> "COLORS".equals(t.getCategory()))
                .map(DesignToken::getTokenValue)
                .toList();
    }

    private long categories(List<DesignToken> tokens, String category) {
        return tokens.stream().filter(t -> category.equals(t.getCategory())).count();
    }
}
