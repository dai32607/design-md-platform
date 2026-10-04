package com.designmd.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.designmd.platform.common.PageResult;
import com.designmd.platform.entity.DesignToken;
import com.designmd.platform.mapper.DesignTokenMapper;
import com.designmd.platform.service.DesignTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DesignTokenServiceImpl implements DesignTokenService {

    private final DesignTokenMapper designTokenMapper;

    @Override
    public List<DesignToken> listByBrandId(Long brandId) {
        return designTokenMapper.selectList(
                new LambdaQueryWrapper<DesignToken>()
                        .eq(DesignToken::getBrandId, brandId)
                        .orderByAsc(DesignToken::getCategory, DesignToken::getId)
        );
    }

    @Override
    public PageResult<DesignToken> pageByBrandId(Long brandId, String category, long page, long pageSize) {
        LambdaQueryWrapper<DesignToken> qw = new LambdaQueryWrapper<DesignToken>()
                .eq(DesignToken::getBrandId, brandId)
                .eq(category != null && !category.isEmpty(), DesignToken::getCategory, category)
                .orderByAsc(DesignToken::getCategory, DesignToken::getId);
        if (pageSize <= 0) {
            List<DesignToken> all = designTokenMapper.selectList(qw);
            return new PageResult<>(all.size(), all);
        }
        Page<DesignToken> p = new Page<>(page, pageSize);
        Page<DesignToken> result = designTokenMapper.selectPage(p, qw);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Override
    public List<DesignToken> listByBrandIdAndCategory(Long brandId, String category) {
        return designTokenMapper.selectByBrandIdAndCategory(brandId, category);
    }

    @Override
    public List<String> getCategoriesByBrandId(Long brandId) {
        List<DesignToken> tokens = listByBrandId(brandId);
        return tokens.stream()
                .map(DesignToken::getCategory)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<DesignToken> parseAndSaveTokens(Long brandId, String markdownContent) {
        List<DesignToken> parsed = new ArrayList<>();
        Set<String> seenTokens = new HashSet<>();

        // 1. 解析颜色：匹配 #RGB 或 #RRGGBB
        Pattern hexPattern = Pattern.compile("#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})\\b");
        Matcher hexMatcher = hexPattern.matcher(markdownContent);
        int colorIndex = 0;
        while (hexMatcher.find()) {
            String hexValue = hexMatcher.group().toUpperCase();
            String key = "COLORS:" + hexValue;
            if (seenTokens.add(key)) {
                colorIndex++;
                DesignToken token = new DesignToken();
                token.setBrandId(brandId);
                token.setCategory("COLORS");
                token.setTokenName("Parsed Color " + colorIndex);
                token.setTokenValue(hexValue);
                token.setDescription("从 DESIGN.md 自动解析");
                parsed.add(token);
            }
        }

        // 2. 解析字体：font-size（兼容 "Font Size:" / "font-size:" 两种写法）
        Pattern fontSizePattern = Pattern.compile("[Ff]ont[-\\s]?[Ss]ize:\\s*([^;\\n]+)");
        Matcher fsMatcher = fontSizePattern.matcher(markdownContent);
        int fontIndex = 0;
        while (fsMatcher.find()) {
            String value = fsMatcher.group(1).trim();
            String key = "TYPOGRAPHY:font-size:" + value;
            if (seenTokens.add(key)) {
                fontIndex++;
                DesignToken token = new DesignToken();
                token.setBrandId(brandId);
                token.setCategory("TYPOGRAPHY");
                token.setTokenName("Font Size " + fontIndex);
                token.setTokenValue(value);
                token.setDescription("从 DESIGN.md 自动解析（font-size）");
                parsed.add(token);
            }
        }

        // 3. 解析字体：font-family / line-height（兼容 "Font Family:" / "font-family:" / "Line Height:" / "line-height:"）
        Pattern fontFamilyPattern = Pattern.compile("[Ff]ont[-\\s]?[Ff]amily:\\s*([^;\\n]+)");
        Matcher ffMatcher = fontFamilyPattern.matcher(markdownContent);
        while (ffMatcher.find()) {
            String value = ffMatcher.group(1).trim();
            String key = "TYPOGRAPHY:font-family:" + value;
            if (seenTokens.add(key)) {
                fontIndex++;
                DesignToken token = new DesignToken();
                token.setBrandId(brandId);
                token.setCategory("TYPOGRAPHY");
                token.setTokenName("Font Family " + fontIndex);
                token.setTokenValue(value);
                token.setDescription("从 DESIGN.md 自动解析（font-family）");
                parsed.add(token);
            }
        }

        // 3b. 解析字体：line-height
        Pattern lineHeightPattern = Pattern.compile("[Ll]ine[-\\s]?[Hh]eight:\\s*([^;\\n]+)");
        Matcher lhMatcher = lineHeightPattern.matcher(markdownContent);
        while (lhMatcher.find()) {
            String value = lhMatcher.group(1).trim();
            String key = "TYPOGRAPHY:line-height:" + value;
            if (seenTokens.add(key)) {
                fontIndex++;
                DesignToken token = new DesignToken();
                token.setBrandId(brandId);
                token.setCategory("TYPOGRAPHY");
                token.setTokenName("Line Height " + fontIndex);
                token.setTokenValue(value);
                token.setDescription("从 DESIGN.md 自动解析（line-height）");
                parsed.add(token);
            }
        }

        // 4. 解析间距：padding/margin/gap（兼容 "Padding:" / "padding:" / "Padding Button:" 等）
        Pattern spacingPattern = Pattern.compile("([Pp]adding|[Mm]argin|[Gg]ap|Border[-\\s]?[Rr]adius)\\s*[:\\-]?\\s*([^;\\n]+)");
        Matcher spMatcher = spacingPattern.matcher(markdownContent);
        int spacingIndex = 0;
        while (spMatcher.find()) {
            String rawProp = spMatcher.group(1).toLowerCase();
            String value = spMatcher.group(2).trim();
            String key = "SPACING:" + rawProp + ":" + value;
            if (seenTokens.add(key)) {
                spacingIndex++;
                DesignToken token = new DesignToken();
                token.setBrandId(brandId);
                token.setCategory("SPACING");
                token.setTokenName(rawProp.substring(0, 1).toUpperCase() + rawProp.substring(1) + " " + spacingIndex);
                token.setTokenValue(value);
                token.setDescription("从 DESIGN.md 自动解析（" + rawProp + "）");
                parsed.add(token);
            }
        }

        // 批量保存
        for (DesignToken token : parsed) {
            designTokenMapper.insert(token);
        }

        return parsed;
    }

    @Override
    public DesignToken getById(Long id) {
        DesignToken token = designTokenMapper.selectById(id);
        if (token == null) {
            throw new IllegalArgumentException("Token 不存在或已删除");
        }
        return token;
    }

    @Override
    public DesignToken updateToken(Long id, DesignToken token) {
        DesignToken existing = designTokenMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Token 不存在或已删除");
        }
        existing.setTokenName(token.getTokenName());
        existing.setTokenValue(token.getTokenValue());
        existing.setDescription(token.getDescription());
        designTokenMapper.updateById(existing);
        return existing;
    }

    @Override
    public boolean deleteToken(Long id) {
        int rows = designTokenMapper.deleteById(id);
        if (rows <= 0) {
            throw new IllegalArgumentException("Token 不存在或已删除");
        }
        return true;
    }
}
