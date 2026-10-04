package com.designmd.platform.service;

import com.designmd.platform.common.PageResult;
import com.designmd.platform.entity.DesignToken;

import java.util.List;

public interface DesignTokenService {

    List<DesignToken> listByBrandId(Long brandId);

    /**
     * 分页查询 Token；pageSize <= 0 时返回全量（total 为全部数量）。
     */
    PageResult<DesignToken> pageByBrandId(Long brandId, String category, long page, long pageSize);

    List<DesignToken> listByBrandIdAndCategory(Long brandId, String category);

    List<String> getCategoriesByBrandId(Long brandId);

    /**
     * 按 id 查询 Token；不存在时抛出 IllegalArgumentException（全局映射为 400）。
     */
    DesignToken getById(Long id);

    List<DesignToken> parseAndSaveTokens(Long brandId, String markdownContent);

    DesignToken updateToken(Long id, DesignToken token);

    boolean deleteToken(Long id);
}
